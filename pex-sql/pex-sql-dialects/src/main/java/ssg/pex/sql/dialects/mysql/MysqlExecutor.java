package ssg.pex.sql.dialects.mysql;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.SqlSupport.SetClause;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.executor.ExpressionEvaluator;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectFunctionRegistry;

import java.util.*;

/**
 * Handles MySQL-specific SQL execution:
 * UPSERT, REPLACE INTO, SHOW, DESCRIBE.
 */
public class MysqlExecutor {

    private final InMemoryDatabase db;
    private final MysqlParser parser;

    public MysqlExecutor(InMemoryDatabase db) {
        this.db = db;
        this.parser = new MysqlParser();
    }

    public Result<Object> execute(String sql, DialectFunctionRegistry functions) {
        try {
            String trimmed = sql.trim();

            // REPLACE INTO
            if (parser.isReplaceInto(trimmed)) {
                return executeReplaceInto(trimmed);
            }

            // SHOW statements
            if (parser.isShowStatement(trimmed)) {
                return executeShow(trimmed);
            }

            // DESCRIBE
            if (parser.isDescribe(trimmed)) {
                return executeDescribe(trimmed);
            }

            // INSERT ... ON DUPLICATE KEY UPDATE
            if (parser.hasOnDuplicateKey(trimmed)) {
                return executeUpsert(trimmed, functions);
            }

            // Delegate to core
            return db.execute(trimmed);
        } catch (Exception e) {
            return Result.failure("MYSQL_ERROR", e.getMessage(), e);
        }
    }

    private Result<Object> executeReplaceInto(String sql) {
        MysqlNodes.ReplaceIntoNode node = parser.parseReplaceInto(sql);
        if (node == null) {
            return Result.failure("MYSQL_ERROR", "Invalid REPLACE INTO syntax");
        }

        Table table = db.defaultSchema().getTable(node.table());
        if (table == null) {
            return Result.failure("MYSQL_ERROR", "Table not found: " + node.table());
        }

        int affected = 0;
        for (List<Object> valueRow : node.values()) {
            // Find PK column(s)
            List<String> pkColumns = findPrimaryKeyColumns(table);

            if (!pkColumns.isEmpty()) {
                // Delete existing row with matching PK
                int pkIdx = table.getColumnIndex(pkColumns.getFirst());
                int valIdx = node.columns().indexOf(pkColumns.getFirst());
                if (pkIdx >= 0 && valIdx >= 0) {
                    Object newPkVal = valueRow.get(valIdx);
                    List<Row> rows = table.scan();
                    for (int i = rows.size() - 1; i >= 0; i--) {
                        Object existingPk = rows.get(i).getValue(pkIdx);
                        if (ExpressionEvaluator.compareValues(existingPk, newPkVal) == 0) {
                            table.removeRow(i);
                            affected++; // count the delete
                            break;
                        }
                    }
                }
            }

            // Insert new row
            Row row = new Row(table.columns().size());
            for (int i = 0; i < node.columns().size() && i < valueRow.size(); i++) {
                int colIdx = table.getColumnIndex(node.columns().get(i));
                if (colIdx >= 0) {
                    row.setValue(colIdx, valueRow.get(i));
                }
            }
            table.addRow(row);
            affected++;
        }

        return Result.success(DmlResult.of(affected));
    }

    private Result<Object> executeShow(String sql) {
        MysqlNodes.ShowStatement stmt = parser.parseShow(sql);
        if (stmt == null) {
            return Result.failure("MYSQL_ERROR", "Invalid SHOW syntax");
        }

        return switch (stmt.type()) {
            case TABLES -> {
                List<String> tableNames = new ArrayList<>(db.defaultSchema().tables().keySet());
                Collections.sort(tableNames);
                List<Row> rows = new ArrayList<>();
                for (String name : tableNames) {
                    rows.add(new Row(new Object[]{name}));
                }
                yield Result.success(new QueryResult(List.of("TABLE_NAME"), rows, 0));
            }
            case DATABASES -> {
                List<String> schemas = new ArrayList<>(db.schemas().keySet());
                List<Row> rows = new ArrayList<>();
                for (String name : schemas) {
                    rows.add(new Row(new Object[]{name}));
                }
                yield Result.success(new QueryResult(List.of("DATABASE"), rows, 0));
            }
            case COLUMNS -> {
                Table table = db.defaultSchema().getTable(stmt.argument());
                if (table == null) {
                    yield Result.failure("MYSQL_ERROR", "Table not found: " + stmt.argument());
                }
                List<Row> rows = new ArrayList<>();
                for (Column col : table.columns()) {
                    rows.add(new Row(new Object[]{
                            col.name(), col.dataType().name(), col.nullable() ? "YES" : "NO",
                            col.defaultValue(), col.autoIncrement() ? "auto_increment" : ""
                    }));
                }
                yield Result.success(new QueryResult(
                        List.of("Field", "Type", "Null", "Default", "Extra"), rows, 0));
            }
        };
    }

    private Result<Object> executeDescribe(String sql) {
        MysqlNodes.DescribeStatement stmt = parser.parseDescribe(sql);
        if (stmt == null) {
            return Result.failure("MYSQL_ERROR", "Invalid DESCRIBE syntax");
        }

        Table table = db.defaultSchema().getTable(stmt.tableName());
        if (table == null) {
            return Result.failure("MYSQL_ERROR", "Table not found: " + stmt.tableName());
        }

        List<Row> rows = new ArrayList<>();
        for (Column col : table.columns()) {
            rows.add(new Row(new Object[]{
                    col.name(), col.dataType().name(), col.nullable() ? "YES" : "NO",
                    "", col.defaultValue(), col.autoIncrement() ? "auto_increment" : ""
            }));
        }
        return Result.success(new QueryResult(
                List.of("Field", "Type", "Null", "Key", "Default", "Extra"), rows, 0));
    }

    private Result<Object> executeUpsert(String sql, DialectFunctionRegistry functions) {
        String insertPart = parser.extractInsertPart(sql);
        List<SetClause> updateClauses = parser.parseOnDuplicateKeyUpdate(sql);

        // Pre-check for duplicates (PEX doesn't enforce PK constraints on INSERT)
        MysqlNodes.ReplaceIntoNode parsed = parser.parseInsertValues(insertPart);
        if (parsed != null) {
            Table table = db.defaultSchema().getTable(parsed.table());
            if (table != null && !parsed.values().isEmpty()) {
                List<String> pkCols = findPrimaryKeyColumns(table);
                if (pkCols.isEmpty()) {
                    // Fallback: first NOT NULL column, then first column
                    for (Column col : table.columns()) {
                        if (!col.nullable()) { pkCols = java.util.List.of(col.name()); break; }
                    }
                    if (pkCols.isEmpty() && !table.columns().isEmpty()) {
                        pkCols = java.util.List.of(table.columns().get(0).name());
                    }
                }
                if (!pkCols.isEmpty()) {
                    String keyCol = pkCols.get(0);
                    int keyColInTable = table.getColumnIndex(keyCol);
                    int keyColInInsert = parsed.columns().indexOf(keyCol);
                    if (keyColInTable >= 0 && keyColInInsert >= 0
                            && keyColInInsert < parsed.values().get(0).size()) {
                        Object keyVal = parsed.values().get(0).get(keyColInInsert);
                        boolean isDuplicate = table.scan().stream().anyMatch(
                                r -> ExpressionEvaluator.compareValues(r.getValue(keyColInTable), keyVal) == 0);
                        if (isDuplicate) {
                            return executeOnDuplicateUpdate(insertPart, updateClauses, parsed);
                        }
                    }
                }
            }
        }

        Result<Object> insertResult = db.execute(insertPart);
        if (insertResult.isFailure()) {
            String errorMsg = insertResult.error().message();
            if (errorMsg != null && (errorMsg.contains("PRIMARY KEY") || errorMsg.contains("UNIQUE") ||
                    errorMsg.contains("Duplicate"))) {
                return executeOnDuplicateUpdate(insertPart, updateClauses);
            }
            return insertResult;
        }
        return insertResult;
    }

    private Result<Object> executeOnDuplicateUpdate(String insertSql, List<SetClause> updateClauses) {
        return executeOnDuplicateUpdate(insertSql, updateClauses, null);
    }

    private Result<Object> executeOnDuplicateUpdate(String insertSql, List<SetClause> updateClauses,
                                                     MysqlNodes.ReplaceIntoNode insertNode) {
        // Extract table name from INSERT INTO tablename
        String upper = insertSql.toUpperCase();
        int intoIdx = upper.indexOf("INTO ");
        if (intoIdx < 0) return Result.failure("MYSQL_ERROR", "Cannot parse INSERT for upsert");

        String afterInto = insertSql.substring(intoIdx + 5).trim();
        String tableName = afterInto.split("[\\s(]")[0];

        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) {
            return Result.failure("MYSQL_ERROR", "Table not found: " + tableName);
        }

        // Find conflicting row by PK
        List<Row> rows = table.scan();
        int conflictIdx = rows.size() - 1; // fallback to last row
        if (insertNode != null && !insertNode.values().isEmpty()) {
            List<String> pkCols = findPrimaryKeyColumns(table);
            if (!pkCols.isEmpty()) {
                String keyCol = pkCols.get(0);
                int keyColInTable = table.getColumnIndex(keyCol);
                int keyColInInsert = insertNode.columns().indexOf(keyCol);
                if (keyColInTable >= 0 && keyColInInsert >= 0
                        && keyColInInsert < insertNode.values().get(0).size()) {
                    Object keyVal = insertNode.values().get(0).get(keyColInInsert);
                    for (int i = 0; i < rows.size(); i++) {
                        if (ExpressionEvaluator.compareValues(rows.get(i).getValue(keyColInTable), keyVal) == 0) {
                            conflictIdx = i;
                            break;
                        }
                    }
                }
            }
        }

        int affected = 0;
        if (conflictIdx >= 0 && conflictIdx < rows.size()) {
            Row conflictRow = rows.get(conflictIdx);
            Row updated = conflictRow.copy();
            for (SetClause clause : updateClauses) {
                int colIdx = table.getColumnIndex(clause.column());
                if (colIdx >= 0) {
                    Object val = clause.value();
                    // Resolve VALUES(col) references
                    if (val instanceof String strVal && strVal.toUpperCase().startsWith("VALUES(")
                            && strVal.endsWith(")") && insertNode != null) {
                        String refCol = strVal.substring(7, strVal.length() - 1).trim();
                        int refColInInsert = insertNode.columns().indexOf(refCol);
                        if (refColInInsert >= 0 && !insertNode.values().isEmpty()
                                && refColInInsert < insertNode.values().get(0).size()) {
                            val = insertNode.values().get(0).get(refColInInsert);
                        }
                    }
                    updated.setValue(colIdx, val);
                }
            }
            table.updateRow(conflictIdx, updated);
            affected = 1;
        }

        return Result.success(DmlResult.of(affected));
    }

    private List<String> findPrimaryKeyColumns(Table table) {
        List<String> pkCols = new ArrayList<>();
        for (var constraint : table.constraints()) {
            if (constraint instanceof ssg.pex.sql.ast.SqlSupport.PrimaryKeyConstraint pk) {
                pkCols.addAll(pk.columns());
                break;
            }
        }
        return pkCols;
    }
}

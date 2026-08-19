package ssg.pex.sql.dbms.executor;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.InsertNode;
import ssg.pex.sql.ast.UpdateNode;
import ssg.pex.sql.ast.DeleteNode;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.DmlResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes INSERT, UPDATE, DELETE with constraint enforcement.
 */
public class DmlExecutor {

    private final ExpressionEvaluator evaluator;
    private final QueryExecutor queryExecutor;

    public DmlExecutor() {
        this.evaluator = new ExpressionEvaluator();
        this.queryExecutor = new QueryExecutor();
    }

    public DmlExecutor(QueryExecutor queryExecutor) {
        this.evaluator = new ExpressionEvaluator();
        this.queryExecutor = queryExecutor;
    }

    public Result<DmlResult> executeInsert(InsertNode node, Schema schema) {
        try {
            Table table = schema.getTable(node.tableName());
            if (table == null) {
                return Result.failure("SQL_ERROR", "Table not found: " + node.tableName());
            }

            List<List<Object>> valueRows;
            if (node.subquery() != null) {
                // INSERT ... SELECT
                var queryResult = queryExecutor.execute(node.subquery(), schema);
                if (queryResult.isFailure()) {
                    return Result.failure(queryResult.error());
                }
                valueRows = new ArrayList<>();
                for (Row row : queryResult.value().rows()) {
                    var vals = new ArrayList<Object>();
                    for (int i = 0; i < row.columnCount(); i++) {
                        vals.add(row.getValue(i));
                    }
                    valueRows.add(vals);
                }
            } else {
                valueRows = node.valueRows();
            }

            List<String> targetColumns = node.columns();
            if (targetColumns.isEmpty()) {
                targetColumns = table.columns().stream().map(Column::name).toList();
            }

            var generatedKeys = new ArrayList<Object>();
            int affectedRows = 0;

            for (List<Object> values : valueRows) {
                Row row = new Row(table.columns().size());

                // Set default values first
                for (Column col : table.columns()) {
                    if (col.defaultValue() != null) {
                        row.setValue(col.ordinal(), col.defaultValue());
                    }
                }

                // Set auto-increment values
                for (Column col : table.columns()) {
                    if (col.autoIncrement()) {
                        long autoVal = table.nextAutoIncrement(col.name());
                        row.setValue(col.ordinal(), autoVal);
                        generatedKeys.add(autoVal);
                    }
                }

                // Set provided values
                for (int i = 0; i < targetColumns.size() && i < values.size(); i++) {
                    int colIdx = table.getColumnIndex(targetColumns.get(i));
                    if (colIdx >= 0) {
                        Object val = values.get(i);
                        Column col = table.columns().get(colIdx);
                        if (col.autoIncrement() && val != null) {
                            // Override auto-increment with explicit value
                            row.setValue(colIdx, val);
                        } else if (!col.autoIncrement()) {
                            row.setValue(colIdx, val);
                        }
                    }
                }

                // Validate constraints
                var validationError = validateConstraints(table, row, schema, -1);
                if (validationError != null) {
                    return Result.failure("CONSTRAINT_VIOLATION", validationError);
                }

                table.addRow(row);
                affectedRows++;
            }

            return Result.success(DmlResult.of(affectedRows, generatedKeys));
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    public Result<DmlResult> executeUpdate(UpdateNode node, Schema schema) {
        try {
            Table table = schema.getTable(node.tableName());
            if (table == null) {
                return Result.failure("SQL_ERROR", "Table not found: " + node.tableName());
            }

            List<Row> rows = table.scan();
            int affectedRows = 0;

            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                boolean matches = true;

                if (node.where() != null) {
                    Object result = evaluator.evaluate(node.where().condition(), row, table.columns());
                    matches = ExpressionEvaluator.isTruthy(result);
                }

                if (matches) {
                    Row updatedRow = row.copy();
                    for (SetClause set : node.setClauses()) {
                        int colIdx = table.getColumnIndex(set.column());
                        if (colIdx >= 0) {
                            Object val = set.value();
                            if (val instanceof SqlExpression expr) {
                                val = evaluator.evaluate(expr, row, table.columns());
                            }
                            updatedRow.setValue(colIdx, val);
                        }
                    }

                    var validationError = validateConstraints(table, updatedRow, schema, i);
                    if (validationError != null) {
                        return Result.failure("CONSTRAINT_VIOLATION", validationError);
                    }

                    table.updateRow(i, updatedRow);
                    affectedRows++;
                }
            }

            return Result.success(DmlResult.of(affectedRows));
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    public Result<DmlResult> executeDelete(DeleteNode node, Schema schema) {
        try {
            Table table = schema.getTable(node.tableName());
            if (table == null) {
                return Result.failure("SQL_ERROR", "Table not found: " + node.tableName());
            }

            List<Row> rows = table.scan();
            var toRemove = new ArrayList<Integer>();

            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                boolean matches = true;

                if (node.where() != null) {
                    Object result = evaluator.evaluate(node.where().condition(), row, table.columns());
                    matches = ExpressionEvaluator.isTruthy(result);
                }

                if (matches) {
                    toRemove.add(i);
                }
            }

            // Check FK constraints before deleting
            for (int idx : toRemove) {
                Row row = rows.get(idx);
                var fkError = checkForeignKeyOnDelete(table, row, schema);
                if (fkError != null) {
                    return Result.failure("CONSTRAINT_VIOLATION", fkError);
                }
            }

            // Remove in reverse order to maintain correct indices
            int removed = 0;
            for (int i = toRemove.size() - 1; i >= 0; i--) {
                table.removeRow(toRemove.get(i));
                removed++;
            }

            return Result.success(DmlResult.of(removed));
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    // ---- Constraint validation ----

    private String validateConstraints(Table table, Row row, Schema schema, int excludeRowIndex) {
        for (Column col : table.columns()) {
            // NOT NULL check
            if (!col.nullable() && !col.autoIncrement() && row.getValue(col.ordinal()) == null) {
                return "NOT NULL constraint violated for column: " + col.name();
            }
        }

        for (TableConstraint constraint : table.constraints()) {
            switch (constraint) {
                case PrimaryKeyConstraint pk -> {
                    var error = validateUniqueness(table, row, pk.columns(), excludeRowIndex);
                    if (error != null) return "PRIMARY KEY constraint violated: " + error;
                    // PK columns must be non-null
                    for (String colName : pk.columns()) {
                        int idx = table.getColumnIndex(colName);
                        if (idx >= 0 && row.getValue(idx) == null) {
                            return "PRIMARY KEY column '" + colName + "' cannot be NULL";
                        }
                    }
                }
                case UniqueConstraint uc -> {
                    var error = validateUniqueness(table, row, uc.columns(), excludeRowIndex);
                    if (error != null) return "UNIQUE constraint violated: " + error;
                }
                case ForeignKeyConstraint fk -> {
                    var error = validateForeignKey(fk, row, table, schema);
                    if (error != null) return error;
                }
                case CheckConstraint cc -> {
                    Object result = evaluator.evaluate(cc.expression(), row, table.columns());
                    if (!ExpressionEvaluator.isTruthy(result)) {
                        return "CHECK constraint violated" + (cc.name() != null ? ": " + cc.name() : "");
                    }
                }
            }
        }

        // Check column-level primary key
        for (Column col : table.columns()) {
            // If column has primaryKey flag set via ColumnDef, it would have been added as a PrimaryKeyConstraint
            // during table creation. No need for additional check here.
        }

        return null;
    }

    private String validateUniqueness(Table table, Row newRow, List<String> columns, int excludeRowIndex) {
        for (int i = 0; i < table.rows().size(); i++) {
            if (i == excludeRowIndex) continue;
            Row existing = table.rows().get(i);
            boolean allMatch = true;
            for (String colName : columns) {
                int idx = table.getColumnIndex(colName);
                if (idx >= 0) {
                    Object newVal = newRow.getValue(idx);
                    Object existingVal = existing.getValue(idx);
                    if (!Objects.equals(newVal, existingVal) ||
                            (newVal != null && ExpressionEvaluator.compareValues(newVal, existingVal) != 0)) {
                        allMatch = false;
                        break;
                    }
                }
            }
            if (allMatch) {
                return "Duplicate value for columns: " + columns;
            }
        }
        return null;
    }

    private String validateForeignKey(ForeignKeyConstraint fk, Row row, Table table, Schema schema) {
        Table refTable = schema.getTable(fk.refTable());
        if (refTable == null) return "Referenced table not found: " + fk.refTable();

        // Check if all FK column values are null (allowed)
        boolean allNull = true;
        for (String colName : fk.columns()) {
            int idx = table.getColumnIndex(colName);
            if (idx >= 0 && row.getValue(idx) != null) {
                allNull = false;
                break;
            }
        }
        if (allNull) return null;

        // Check if referenced row exists
        for (Row refRow : refTable.scan()) {
            boolean matches = true;
            for (int i = 0; i < fk.columns().size(); i++) {
                int fkIdx = table.getColumnIndex(fk.columns().get(i));
                int refIdx = refTable.getColumnIndex(fk.refColumns().get(i));
                if (fkIdx < 0 || refIdx < 0) { matches = false; break; }

                Object fkVal = row.getValue(fkIdx);
                Object refVal = refRow.getValue(refIdx);
                if (ExpressionEvaluator.compareValues(fkVal, refVal) != 0) {
                    matches = false;
                    break;
                }
            }
            if (matches) return null;
        }

        return "FOREIGN KEY constraint violated: no matching row in " + fk.refTable();
    }

    private String checkForeignKeyOnDelete(Table table, Row row, Schema schema) {
        // Check if any other table has a FK referencing this table
        for (Table otherTable : schema.tables().values()) {
            for (TableConstraint constraint : otherTable.constraints()) {
                if (constraint instanceof ForeignKeyConstraint fk && fk.refTable().equalsIgnoreCase(table.name())) {
                    // Check if any row in the other table references this row
                    for (Row otherRow : otherTable.scan()) {
                        boolean matches = true;
                        for (int i = 0; i < fk.refColumns().size(); i++) {
                            int refIdx = table.getColumnIndex(fk.refColumns().get(i));
                            int fkIdx = otherTable.getColumnIndex(fk.columns().get(i));
                            if (refIdx < 0 || fkIdx < 0) { matches = false; break; }

                            Object refVal = row.getValue(refIdx);
                            Object fkVal = otherRow.getValue(fkIdx);
                            if (ExpressionEvaluator.compareValues(refVal, fkVal) != 0) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches) {
                            return "FOREIGN KEY constraint violated: row is referenced by " + otherTable.name();
                        }
                    }
                }
            }
        }
        return null;
    }
}

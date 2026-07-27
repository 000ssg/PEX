package ssg.pex.sql.olap.executor;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.SelectNode;
import ssg.pex.sql.ast.SqlSupport.ColumnDef;
import ssg.pex.sql.ast.SqlSupport.SqlDataType;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.olap.ast.CteDefinition;
import ssg.pex.sql.olap.ast.WithClause;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Executes WITH (CTE) queries.
 * Non-recursive: execute CTE query once, make result available as virtual table.
 * Recursive: iterative fixpoint until no new rows.
 */
public class CteExecutor {

    private final InMemoryDatabase database;

    public CteExecutor(InMemoryDatabase database) {
        this.database = database;
    }

    /**
     * Execute a WITH clause. Creates temporary tables for each CTE, executes the final query,
     * then cleans up the temporary tables.
     */
    public Result<QueryResult> execute(WithClause withClause, String finalQuery) {
        var createdTables = new ArrayList<String>();
        try {
            for (CteDefinition cte : withClause.ctes()) {
                if (withClause.recursive()) {
                    executeRecursiveCte(cte);
                } else {
                    executeNonRecursiveCte(cte);
                }
                createdTables.add(cte.name());
            }

            // Execute the final query
            var result = database.execute(finalQuery);
            if (result.isFailure()) {
                return Result.failure(result.error());
            }

            if (result.value() instanceof QueryResult qr) {
                return Result.success(qr);
            }
            return Result.failure("CTE_ERROR", "Final query did not produce a QueryResult");
        } catch (Exception e) {
            return Result.failure("CTE_ERROR", e.getMessage(), e);
        } finally {
            // Clean up temporary CTE tables
            for (String tableName : createdTables) {
                database.defaultSchema().removeTable(tableName);
            }
        }
    }

    private void executeNonRecursiveCte(CteDefinition cte) {
        // Execute the CTE query
        var result = database.execute(buildSelectSql(cte.query()));
        if (result.isFailure()) {
            throw new RuntimeException("CTE query failed: " + result.error().message());
        }

        if (result.value() instanceof QueryResult qr) {
            createTableFromResult(cte.name(), cte.columnAliases(), qr);
        } else {
            throw new RuntimeException("CTE query did not produce a result set");
        }
    }

    private void executeRecursiveCte(CteDefinition cte) {
        // The CTE query is expected to be a UNION ALL of anchor and recursive terms
        // For simplicity, we detect the pattern and handle it
        // First, create the CTE table from the anchor term (first SELECT)
        String sql = buildSelectSql(cte.query());

        // Execute anchor query
        var anchorResult = database.execute(sql);
        if (anchorResult.isFailure()) {
            throw new RuntimeException("Recursive CTE anchor failed: " + anchorResult.error().message());
        }

        if (anchorResult.value() instanceof QueryResult qr) {
            createTableFromResult(cte.name(), cte.columnAliases(), qr);

            // Now iterate: keep executing the CTE query until no new rows
            int maxIterations = 1000;
            for (int iter = 0; iter < maxIterations; iter++) {
                var iterResult = database.execute(sql);
                if (iterResult.isFailure()) break;

                if (iterResult.value() instanceof QueryResult iterQr) {
                    Table cteTable = database.defaultSchema().getTable(cte.name());
                    int beforeCount = cteTable.rowCount();

                    // Add new rows that don't already exist
                    var existingRows = new LinkedHashSet<>(cteTable.rows());
                    int newRows = 0;
                    for (Row row : iterQr.rows()) {
                        if (!existingRows.contains(row)) {
                            cteTable.addRow(row);
                            existingRows.add(row);
                            newRows++;
                        }
                    }

                    if (newRows == 0) break; // Fixpoint reached
                }
            }
        }
    }

    private void createTableFromResult(String tableName, List<String> columnAliases, QueryResult qr) {
        List<String> colNames = columnAliases.isEmpty() ? qr.columnNames() : columnAliases;

        var columnDefs = new ArrayList<ColumnDef>();
        var columns = new ArrayList<Column>();
        for (int i = 0; i < colNames.size(); i++) {
            String name = colNames.get(i);
            columnDefs.add(new ColumnDef(name, SqlDataType.VARCHAR, true, null, false, false));
            columns.add(new Column(name, SqlDataType.VARCHAR, true, null, false, i));
        }

        var table = new Table(tableName, columns, List.of());
        for (Row row : qr.rows()) {
            // Ensure row has correct number of columns
            if (row.columnCount() == colNames.size()) {
                table.addRow(row);
            } else {
                Object[] values = new Object[colNames.size()];
                for (int i = 0; i < colNames.size() && i < row.columnCount(); i++) {
                    values[i] = row.getValue(i);
                }
                table.addRow(new Row(values));
            }
        }

        database.defaultSchema().addTable(table);
    }

    private String buildSelectSql(SelectNode select) {
        var sb = new StringBuilder("SELECT ");

        if (select.distinct()) sb.append("DISTINCT ");

        for (int i = 0; i < select.selectItems().size(); i++) {
            if (i > 0) sb.append(", ");
            var item = select.selectItems().get(i);
            if (item.star()) {
                sb.append(item.expression());
            } else {
                sb.append(item.expression());
                if (item.alias() != null) {
                    sb.append(" AS ").append(item.alias());
                }
            }
        }

        if (select.from() != null) {
            sb.append(" FROM ");
            for (int i = 0; i < select.from().tables().size(); i++) {
                if (i > 0) sb.append(", ");
                var tableRef = select.from().tables().get(i);
                sb.append(tableRef.tableName());
                if (tableRef.alias() != null) {
                    sb.append(" AS ").append(tableRef.alias());
                }
                if (tableRef.join() != null) {
                    var join = tableRef.join();
                    sb.append(" ").append(join.type().name()).append(" JOIN ");
                    sb.append(join.tableName());
                    if (join.alias() != null) {
                        sb.append(" AS ").append(join.alias());
                    }
                    if (join.on() != null) {
                        sb.append(" ON ").append(expressionToSql(join.on()));
                    }
                }
            }
        }

        if (select.where() != null) {
            sb.append(" WHERE ").append(expressionToSql(select.where().condition()));
        }

        if (select.groupBy() != null) {
            sb.append(" GROUP BY ").append(String.join(", ", select.groupBy().columns()));
        }

        if (select.having() != null) {
            sb.append(" HAVING ").append(expressionToSql(select.having().condition()));
        }

        if (select.orderBy() != null) {
            sb.append(" ORDER BY ");
            for (int i = 0; i < select.orderBy().items().size(); i++) {
                if (i > 0) sb.append(", ");
                var item = select.orderBy().items().get(i);
                sb.append(item.column()).append(item.ascending() ? " ASC" : " DESC");
            }
        }

        if (select.limit() != null) {
            sb.append(" LIMIT ").append(select.limit().limit());
            if (select.limit().offset() > 0) {
                sb.append(" OFFSET ").append(select.limit().offset());
            }
        }

        return sb.toString();
    }

    private String expressionToSql(ssg.pex.sql.ast.SqlExpression expr) {
        return switch (expr) {
            case ssg.pex.sql.ast.SqlExpression.ColumnRef cr ->
                    cr.table() != null ? cr.table() + "." + cr.column() : cr.column();
            case ssg.pex.sql.ast.SqlExpression.LiteralExpr le -> {
                if (le.value() == null) yield "NULL";
                if (le.value() instanceof String s) yield "'" + s + "'";
                yield le.value().toString();
            }
            case ssg.pex.sql.ast.SqlExpression.BinaryExpr be ->
                    expressionToSql(be.left()) + " " + be.operator() + " " + expressionToSql(be.right());
            case ssg.pex.sql.ast.SqlExpression.UnaryExpr ue ->
                    ue.operator() + " " + expressionToSql(ue.operand());
            case ssg.pex.sql.ast.SqlExpression.FunctionExpr fe ->
                    fe.name() + "(" + fe.args().stream().map(this::expressionToSql)
                            .reduce((a, b) -> a + ", " + b).orElse("") + ")";
            case ssg.pex.sql.ast.SqlExpression.AggregateExpr ae ->
                    ae.func().name() + "(" + (ae.distinct() ? "DISTINCT " : "") +
                            (ae.arg() != null ? expressionToSql(ae.arg()) : "*") + ")";
            case ssg.pex.sql.ast.SqlExpression.StarExpr ignored -> "*";
            case ssg.pex.sql.ast.SqlExpression.IsNullExpr ine ->
                    expressionToSql(ine.value()) + (ine.negated() ? " IS NOT NULL" : " IS NULL");
            default -> expr.toString();
        };
    }
}

package ssg.pex.sql.dbms.executor;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.SelectNode;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes SELECT statements through the pipeline:
 * FROM -> JOIN -> WHERE -> GROUP BY -> HAVING -> SELECT -> DISTINCT -> ORDER BY -> LIMIT
 */
public class QueryExecutor {

    private final ExpressionEvaluator evaluator;
    private final JoinEngine joinEngine;
    private final AggregateEngine aggregateEngine;
    private final SortEngine sortEngine;

    public QueryExecutor() {
        this.evaluator = new ExpressionEvaluator();
        this.joinEngine = new JoinEngine(evaluator);
        this.aggregateEngine = new AggregateEngine();
        this.sortEngine = new SortEngine();
    }

    public Result<QueryResult> execute(SelectNode select, Schema schema) {
        try {
            long startTime = System.nanoTime();

            // Whether the query needs the full row set before it can produce results.
            // ORDER BY, GROUP BY, DISTINCT and aggregates all require full materialisation.
            boolean needsFullDataset = select.orderBy() != null
                    || select.groupBy() != null
                    || select.distinct()
                    || hasAggregateExpressions(select.selectItems());

            // Push-down hint: resolveFrom() will filter cross-join rows inline when a
            // WHERE condition is present, avoiding materialising non-matching pairs.
            SqlExpression pushdownCondition = (select.where() != null && hasCrossJoin(select.from()))
                    ? select.where().condition() : null;
            // Early-limit hint for the stream: stop producing rows once we have enough.
            // Only safe when ORDER BY / GROUP BY / DISTINCT are absent.
            int earlyStreamLimit = (!needsFullDataset && select.limit() != null)
                    ? select.limit().offset() + select.limit().limit()
                    : Integer.MAX_VALUE;

            // 1. FROM — returns a lazy stream; no materialisation yet for cross-joins.
            Stream<Row> rowStream;
            List<Column> columns;

            if (select.from() != null) {
                var fromResult = resolveFrom(select.from(), schema, pushdownCondition, earlyStreamLimit);
                rowStream = fromResult.stream();
                columns = fromResult.columns();
            } else {
                rowStream = Stream.of(new Row(0));
                columns = List.of();
            }

            // 2. WHERE — lazy filter on the stream; avoids evaluating after materialisation.
            if (select.where() != null) {
                final List<Column> cols = columns;
                final SqlExpression cond = select.where().condition();
                rowStream = rowStream.filter(row -> {
                    try {
                        return ExpressionEvaluator.isTruthy(evaluator.evaluate(cond, row, cols));
                    } catch (Exception e) {
                        return false;
                    }
                });
            }

            // 3. Early LIMIT on the stream — only when the full dataset is not required.
            //    This lets the stream stop producing (and merging) rows as soon as enough
            //    pass the WHERE filter, giving O(limit) memory instead of O(n×m).
            if (!needsFullDataset && select.limit() != null) {
                int skip = select.limit().offset();
                int take = select.limit().limit();
                if (skip > 0) rowStream = rowStream.skip(skip);
                rowStream = rowStream.limit(take);
            }

            // Materialise: GROUP BY / ORDER BY / DISTINCT / projection all need a List.
            List<Row> rows = rowStream.collect(Collectors.toList());

            // 3. GROUP BY + aggregates
            boolean hasAggregates = hasAggregateExpressions(select.selectItems());
            if (select.groupBy() != null || hasAggregates) {
                var groupByResult = executeGroupBy(rows, columns, select);
                rows = groupByResult.rows;
                columns = groupByResult.columns;

                // 4. HAVING clause
                if (select.having() != null) {
                    rows = filterRows(rows, columns, select.having().condition());
                }
            }

            // 5. SELECT clause - project columns
            var projectionResult = projectColumns(rows, columns, select.selectItems(), schema);
            rows = projectionResult.rows;
            columns = projectionResult.columns;
            List<String> columnNames = projectionResult.names;

            // 6. DISTINCT
            if (select.distinct()) {
                rows = removeDuplicates(rows);
            }

            // 7. ORDER BY
            if (select.orderBy() != null) {
                rows = sortEngine.sort(rows, select.orderBy().items(), columns);
            }

            // 8. LIMIT/OFFSET — only when not already applied in the stream above
            //    (i.e. ORDER BY / GROUP BY / DISTINCT required full materialisation first)
            if (needsFullDataset && select.limit() != null) {
                int offset = select.limit().offset();
                int limit = select.limit().limit();
                if (offset > 0 && offset < rows.size()) {
                    rows = rows.subList(offset, rows.size());
                } else if (offset >= rows.size()) {
                    rows = List.of();
                }
                if (limit < rows.size()) {
                    rows = rows.subList(0, limit);
                }
            }

            long elapsed = System.nanoTime() - startTime;
            return Result.success(new QueryResult(columnNames, new ArrayList<>(rows), elapsed));
        } catch (Exception e) {
            return Result.failure("SQL_EXEC_ERROR", e.getMessage(), e);
        }
    }

    // ---- FROM resolution ----

    // Lazy FROM result: stream is not yet materialised.
    private record FromResult(Stream<Row> stream, List<Column> columns) {}

    private boolean hasCrossJoin(FromClause from) {
        return from != null && from.tables().size() > 1;
    }

    private FromResult resolveFrom(FromClause from, Schema schema,
                                   SqlExpression pushdownCondition, int earlyLimit) {
        Stream<Row> stream = null;
        List<Column> columns = null;

        for (TableRef tableRef : from.tables()) {
            Table table = schema.getTable(tableRef.tableName());
            if (table == null) {
                // Check if it's a view
                View view = schema.getView(tableRef.tableName());
                if (view != null) {
                    var viewResult = execute(view.definition(), schema);
                    if (viewResult.isSuccess()) {
                        var qr = viewResult.value();
                        var viewCols = new ArrayList<Column>();
                        for (int i = 0; i < qr.columnNames().size(); i++) {
                            viewCols.add(new Column(qr.columnNames().get(i), SqlDataType.VARCHAR, true, null, false, i));
                        }
                        if (stream == null) {
                            stream = qr.rows().stream();
                            columns = viewCols;
                        }
                    }
                    continue;
                }
                throw new RuntimeException("Table not found: " + tableRef.tableName());
            }

            String prefix = tableRef.alias() != null ? tableRef.alias() : tableRef.tableName();

            var qualifiedCols = new ArrayList<Column>();
            for (Column col : table.columns()) {
                qualifiedCols.add(new Column(prefix + "." + col.name(), col.dataType(), col.nullable(),
                        col.defaultValue(), col.autoIncrement(), col.ordinal()));
            }

            if (stream == null) {
                stream = table.scan().stream();
                columns = qualifiedCols;

                // Partial push-down for single-table predicates:
                // Extract only the AND-conjuncts that can be fully resolved using
                // the current (single-table) column list.
                if (pushdownCondition != null) {
                    List<SqlExpression> applicable = extractApplicableConjuncts(pushdownCondition, qualifiedCols);
                    if (!applicable.isEmpty()) {
                        final List<Column> firstCols = columns;
                        final List<SqlExpression> filters = applicable;
                        stream = stream.filter(row -> {
                            try {
                                for (SqlExpression cond : filters) {
                                    Object r = evaluator.evaluate(cond, row, firstCols);
                                    if (!ExpressionEvaluator.isTruthy(r)) return false;
                                }
                                return true;
                            } catch (Exception ignored) {
                                return true;
                            }
                        });
                    }
                }
            } else {
                // Build merged column list before the join so push-down can reference
                // columns from both sides.
                var mergedCols = new ArrayList<>(columns);
                for (int i = 0; i < qualifiedCols.size(); i++) {
                    Column c = qualifiedCols.get(i);
                    mergedCols.add(new Column(c.name(), c.dataType(), c.nullable(), c.defaultValue(),
                            c.autoIncrement(), columns.size() + i));
                }

                // Snapshot the right table once — the stream for the left side is consumed
                // lazily for each row, so the right side must be replayable.
                List<Row> rightSnapshot = table.scan();

                // Partial predicate push-down for 3+ table joins:
                // Extract AND-conjuncts resolvable with the columns available so far.
                final List<Column> finalMergedCols = mergedCols;

                // Lazy cross-join: flatMap produces merged rows on demand.
                // No ArrayList allocation until the terminal collect() in execute().
                stream = stream.flatMap(left ->
                        rightSnapshot.stream().map(right -> Row.merge(left, right))
                );

                if (pushdownCondition != null) {
                    List<SqlExpression> applicable = extractApplicableConjuncts(pushdownCondition, finalMergedCols);
                    if (!applicable.isEmpty()) {
                        final List<SqlExpression> filters = applicable;
                        stream = stream.filter(merged -> {
                            try {
                                for (SqlExpression cond : filters) {
                                    Object r = evaluator.evaluate(cond, merged, finalMergedCols);
                                    if (!ExpressionEvaluator.isTruthy(r)) return false;
                                }
                                return true;
                            } catch (Exception ignored) {
                                return true;
                            }
                        });
                    }
                }

                columns = mergedCols;
            }

            // Explicit JOIN requires materialisation because JoinEngine operates on Lists.
            if (tableRef.join() != null) {
                List<Row> materialized = stream.collect(Collectors.toList());
                stream = null;
                var joinResult = executeJoin(materialized, columns, tableRef.join(), schema);
                stream = joinResult.stream();
                columns = joinResult.columns();
            }
        }

        if (stream == null) {
            stream = Stream.of(new Row(0));
            columns = List.of();
        }

        return new FromResult(stream, columns);
    }

    private FromResult executeJoin(List<Row> leftRows, List<Column> leftCols,
                                   JoinClause joinClause, Schema schema) {
        Table rightTable = schema.getTable(joinClause.tableName());
        if (rightTable == null) {
            throw new RuntimeException("Table not found for join: " + joinClause.tableName());
        }

        String prefix = joinClause.alias() != null ? joinClause.alias() : joinClause.tableName();
        var rightCols = new ArrayList<Column>();
        for (Column col : rightTable.columns()) {
            rightCols.add(new Column(prefix + "." + col.name(), col.dataType(), col.nullable(),
                    col.defaultValue(), col.autoIncrement(), col.ordinal()));
        }

        List<Row> rightRows = rightTable.scan();
        List<Row> joinedRows = joinEngine.join(leftRows, leftCols, rightRows, rightCols, joinClause);

        var mergedCols = new ArrayList<>(leftCols);
        for (int i = 0; i < rightCols.size(); i++) {
            Column c = rightCols.get(i);
            mergedCols.add(new Column(c.name(), c.dataType(), c.nullable(), c.defaultValue(),
                    c.autoIncrement(), leftCols.size() + i));
        }

        return new FromResult(joinedRows.stream(), mergedCols);
    }

    // ---- WHERE filtering ----

    private List<Row> filterRows(List<Row> rows, List<Column> columns, SqlExpression condition) {
        var filtered = new ArrayList<Row>();
        for (Row row : rows) {
            Object result = evaluator.evaluate(condition, row, columns);
            if (ExpressionEvaluator.isTruthy(result)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    // ---- GROUP BY ----

    private record GroupByResult(List<Row> rows, List<Column> columns) {}

    private GroupByResult executeGroupBy(List<Row> rows, List<Column> columns, SelectNode select) {
        List<String> groupByColumns = select.groupBy() != null ? select.groupBy().columns() : List.of();

        // Resolve group-by column names to qualified names
        List<String> resolvedGroupCols = new ArrayList<>();
        for (String gbCol : groupByColumns) {
            String resolved = resolveColumnName(gbCol, columns);
            resolvedGroupCols.add(resolved);
        }

        Map<List<Object>, List<Row>> groups;
        if (resolvedGroupCols.isEmpty()) {
            // No GROUP BY but has aggregates - treat all rows as one group
            groups = new LinkedHashMap<>();
            groups.put(List.of(), rows);
        } else {
            groups = aggregateEngine.groupRows(rows, resolvedGroupCols, columns);
        }

        // Build result columns from select items
        var resultColumns = new ArrayList<Column>();
        int colIdx = 0;
        for (SelectItem item : select.selectItems()) {
            String name = item.alias() != null ? item.alias() : item.expression();
            resultColumns.add(new Column(name, SqlDataType.VARCHAR, true, null, false, colIdx++));
        }

        // Build result rows
        var resultRows = new ArrayList<Row>();
        for (var entry : groups.entrySet()) {
            List<Row> groupRows = entry.getValue();
            var values = new Object[select.selectItems().size()];

            for (int i = 0; i < select.selectItems().size(); i++) {
                SelectItem item = select.selectItems().get(i);
                values[i] = evaluateSelectItem(item, groupRows, columns);
            }

            resultRows.add(new Row(values));
        }

        return new GroupByResult(resultRows, resultColumns);
    }

    private Object evaluateSelectItem(SelectItem item, List<Row> groupRows, List<Column> columns) {
        if (item.star()) return null;

        // Try to parse the expression text to determine what to evaluate
        String expr = item.expression();

        // Check for aggregate functions
        String upperExpr = expr.toUpperCase();
        for (AggregateFunction func : AggregateFunction.values()) {
            if (upperExpr.startsWith(func.name() + "(")) {
                return evaluateAggregateFromString(func, expr, groupRows, columns);
            }
        }

        // Non-aggregate column - return value from first row in group
        if (!groupRows.isEmpty()) {
            Row firstRow = groupRows.getFirst();
            int colIdx = findColumnIndex(expr, columns);
            if (colIdx >= 0) {
                return firstRow.getValue(colIdx);
            }
        }
        return null;
    }

    private Object evaluateAggregateFromString(AggregateFunction func, String expr, List<Row> groupRows, List<Column> columns) {
        // Extract column name from e.g. "COUNT(id)" or "SUM(amount)"
        int openParen = expr.indexOf('(');
        int closeParen = expr.lastIndexOf(')');
        if (openParen < 0 || closeParen < 0) return null;

        String inner = expr.substring(openParen + 1, closeParen).trim();
        boolean distinct = false;
        if (inner.toUpperCase().startsWith("DISTINCT ")) {
            distinct = true;
            inner = inner.substring(9).trim();
        }

        if (inner.equals("*")) {
            // COUNT(*)
            return aggregateEngine.computeAggregate(func, groupRows.stream().map(r -> (Object) r).collect(Collectors.toList()), distinct);
        }

        int colIdx = findColumnIndex(inner, columns);
        if (colIdx >= 0) {
            var values = aggregateEngine.extractColumnValues(groupRows, colIdx);
            return aggregateEngine.computeAggregate(func, values, distinct);
        }

        return null;
    }

    // ---- SELECT projection ----

    private record ProjectionResult(List<Row> rows, List<Column> columns, List<String> names) {}

    private ProjectionResult projectColumns(List<Row> rows, List<Column> columns,
                                            List<SelectItem> selectItems, Schema schema) {
        var resultNames = new ArrayList<String>();
        var resultColumns = new ArrayList<Column>();

        // Expand star expressions
        var expandedItems = new ArrayList<SelectItem>();
        for (SelectItem item : selectItems) {
            if (item.star() && item.expression().equals("*")) {
                // Expand to all columns
                for (Column col : columns) {
                    String name = col.name();
                    int dot = name.indexOf('.');
                    String simpleName = dot >= 0 ? name.substring(dot + 1) : name;
                    expandedItems.add(new SelectItem(name, null, false));
                }
            } else if (item.star() && item.expression().endsWith(".*")) {
                // Expand table.*
                String tablePrefix = item.expression().substring(0, item.expression().length() - 2);
                for (Column col : columns) {
                    if (col.name().toLowerCase().startsWith(tablePrefix.toLowerCase() + ".")) {
                        expandedItems.add(new SelectItem(col.name(), null, false));
                    }
                }
            } else {
                expandedItems.add(item);
            }
        }

        // Determine output columns
        int colIdx = 0;
        for (SelectItem item : expandedItems) {
            String displayName;
            if (item.alias() != null) {
                displayName = item.alias();
            } else {
                String expr = item.expression();
                int dot = expr.indexOf('.');
                displayName = dot >= 0 ? expr.substring(dot + 1) : expr;
            }
            resultNames.add(displayName);
            resultColumns.add(new Column(displayName, SqlDataType.VARCHAR, true, null, false, colIdx++));
        }

        // Project rows
        var resultRows = new ArrayList<Row>();
        for (Row row : rows) {
            var values = new Object[expandedItems.size()];
            for (int i = 0; i < expandedItems.size(); i++) {
                SelectItem item = expandedItems.get(i);
                values[i] = resolveSelectValue(item, row, columns);
            }
            resultRows.add(new Row(values));
        }

        return new ProjectionResult(resultRows, resultColumns, resultNames);
    }

    private Object resolveSelectValue(SelectItem item, Row row, List<Column> columns) {
        String expr = item.expression();

        // Direct column reference
        int colIdx = findColumnIndex(expr, columns);
        if (colIdx >= 0) {
            return row.getValue(colIdx);
        }

        // Try the alias (handles post-GROUP-BY projection where column is named by alias)
        if (item.alias() != null) {
            int aliasIdx = findColumnIndex(item.alias(), columns);
            if (aliasIdx >= 0) return row.getValue(aliasIdx);
        }

        // Try without table prefix
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i).name();
            int dot = name.indexOf('.');
            if (dot >= 0 && name.substring(dot + 1).equalsIgnoreCase(expr)) {
                return row.getValue(i);
            }
        }

        // Try parsing as expression
        try {
            var parser = new ssg.pex.sql.grammar.SqlParser();
            var parseResult = parser.parse("SELECT " + expr + " FROM dual");
            // If it's a literal expression contained in the select, try to evaluate it
        } catch (Exception ignored) {}

        // Try evaluating as a simple literal
        try {
            if (expr.matches("-?\\d+")) return Long.parseLong(expr);
            if (expr.matches("-?\\d+\\.\\d+")) return Double.parseDouble(expr);
        } catch (NumberFormatException ignored) {}

        return null;
    }

    // ---- DISTINCT ----

    private List<Row> removeDuplicates(List<Row> rows) {
        var seen = new LinkedHashSet<Row>();
        seen.addAll(rows);
        return new ArrayList<>(seen);
    }

    // ---- Helpers ----

    private boolean hasAggregateExpressions(List<SelectItem> items) {
        for (SelectItem item : items) {
            if (item.star()) continue;
            String upper = item.expression().toUpperCase();
            if (upper.startsWith("COUNT(") || upper.startsWith("SUM(") ||
                    upper.startsWith("AVG(") || upper.startsWith("MIN(") ||
                    upper.startsWith("MAX(") || upper.startsWith("GROUP_CONCAT(")) {
                return true;
            }
        }
        return false;
    }

    private String resolveColumnName(String name, List<Column> columns) {
        // Exact match
        for (Column col : columns) {
            if (col.name().equalsIgnoreCase(name)) {
                return col.name();
            }
        }
        // Try with prefix
        for (Column col : columns) {
            int dot = col.name().indexOf('.');
            if (dot >= 0 && col.name().substring(dot + 1).equalsIgnoreCase(name)) {
                return col.name();
            }
        }
        return name;
    }

    private int findColumnIndex(String name, List<Column> columns) {
        // Exact match
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        // Try suffix match (without table prefix)
        for (int i = 0; i < columns.size(); i++) {
            String colName = columns.get(i).name();
            int dot = colName.indexOf('.');
            if (dot >= 0 && colName.substring(dot + 1).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    // ---- Predicate push-down helpers ----

    /**
     * Extract AND-conjuncts from {@code expr} that can be fully evaluated
     * using the given column list (i.e. all ColumnRef nodes within them can be resolved).
     * Complex predicates that reference unavailable columns are excluded.
     */
    private List<SqlExpression> extractApplicableConjuncts(SqlExpression expr, List<Column> availableCols) {
        var result = new ArrayList<SqlExpression>();
        collectConjuncts(expr, result);
        result.removeIf(conj -> !isFullyResolvable(conj, availableCols));
        return result;
    }

    /** Flatten an AND tree into its leaf conjuncts. */
    private void collectConjuncts(SqlExpression expr, List<SqlExpression> out) {
        if (expr instanceof SqlExpression.BinaryExpr be && be.operator().equalsIgnoreCase("AND")) {
            collectConjuncts(be.left(), out);
            collectConjuncts(be.right(), out);
        } else {
            out.add(expr);
        }
    }

    /**
     * Returns {@code true} if every {@link SqlExpression.ColumnRef} in {@code expr}
     * can be resolved against {@code availableCols}.
     */
    private boolean isFullyResolvable(SqlExpression expr, List<Column> availableCols) {
        return switch (expr) {
            case SqlExpression.ColumnRef cr -> canResolveColumnRef(cr, availableCols);
            case SqlExpression.LiteralExpr ignored -> true;
            case SqlExpression.BinaryExpr be ->
                    isFullyResolvable(be.left(), availableCols) && isFullyResolvable(be.right(), availableCols);
            case SqlExpression.UnaryExpr ue -> isFullyResolvable(ue.operand(), availableCols);
            case SqlExpression.FunctionExpr fe ->
                    fe.args().stream().allMatch(a -> isFullyResolvable(a, availableCols));
            case SqlExpression.IsNullExpr ine -> isFullyResolvable(ine.value(), availableCols);
            case SqlExpression.LikeExpr le -> isFullyResolvable(le.value(), availableCols);
            case SqlExpression.BetweenExpr be ->
                    isFullyResolvable(be.value(), availableCols)
                    && isFullyResolvable(be.low(), availableCols)
                    && isFullyResolvable(be.high(), availableCols);
            case SqlExpression.InExpr ie ->
                    isFullyResolvable(ie.value(), availableCols)
                    && ie.list().stream().allMatch(e -> isFullyResolvable(e, availableCols));
            default -> false; // subqueries, aggregates — don't push down
        };
    }

    private boolean canResolveColumnRef(SqlExpression.ColumnRef cr, List<Column> availableCols) {
        for (Column col : availableCols) {
            String name = col.name();
            int dot = name.indexOf('.');
            String simpleName = dot >= 0 ? name.substring(dot + 1) : name;
            String tablePrefix = dot >= 0 ? name.substring(0, dot) : null;

            boolean nameMatch = simpleName.equalsIgnoreCase(cr.column()) || name.equalsIgnoreCase(cr.column());
            boolean tableMatch = cr.table() == null || tablePrefix == null
                    || tablePrefix.equalsIgnoreCase(cr.table());
            if (nameMatch && tableMatch) return true;
        }
        return false;
    }
}

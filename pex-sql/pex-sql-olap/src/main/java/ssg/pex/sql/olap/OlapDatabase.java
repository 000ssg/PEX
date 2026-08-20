package ssg.pex.sql.olap;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.SqlNode;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.olap.ast.*;
import ssg.pex.sql.olap.executor.*;
import ssg.pex.sql.olap.parser.OlapSqlParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends InMemoryDatabase with OLAP query execution.
 * Wraps an InMemoryDatabase instance and adds window functions, CTEs,
 * CUBE/ROLLUP, MERGE, and PIVOT/UNPIVOT support.
 */
public class OlapDatabase implements AutoCloseable {

    private final InMemoryDatabase database;
    private final OlapSqlParser parser;
    private final WindowFunctionExecutor windowExecutor;
    private final CteExecutor cteExecutor;
    private final GroupingSetExecutor groupingSetExecutor;
    private final MergeExecutor mergeExecutor;
    private final PivotExecutor pivotExecutor;

    public OlapDatabase() {
        this(new InMemoryDatabase());
    }

    public OlapDatabase(InMemoryDatabase database) {
        this.database = database;
        this.parser = new OlapSqlParser();
        this.windowExecutor = new WindowFunctionExecutor();
        this.cteExecutor = new CteExecutor(database);
        this.groupingSetExecutor = new GroupingSetExecutor(database);
        this.mergeExecutor = new MergeExecutor(database);
        this.pivotExecutor = new PivotExecutor(database);
    }

    /**
     * Execute SQL with OLAP extensions.
     */
    public Result<QueryResult> executeOlap(String sql) {
        try {
            var parseResult = parser.parse(sql);
            if (parseResult.isFailure()) {
                return Result.failure(parseResult.error());
            }

            Object parsed = parseResult.value();

            if (parsed instanceof WithClause withClause) {
                return executeCte(withClause, sql);
            }

            if (parsed instanceof MergeNode merge) {
                var mergeResult = mergeExecutor.execute(merge);
                if (mergeResult.isFailure()) {
                    return Result.failure(mergeResult.error());
                }
                // Return a simple result showing affected rows
                return Result.success(new QueryResult(
                        List.of("affected_rows"),
                        List.of(new ssg.pex.sql.dbms.Row(new Object[]{(long) mergeResult.value().affectedRows()})),
                        0
                ));
            }

            // Standard SQL — check for OLAP extensions before delegating to core
            if (parsed instanceof SqlNode node) {
                String upper = sql.trim().toUpperCase();
                // Window functions: contain OVER keyword
                if (upper.contains(" OVER ")) {
                    return executeWindowFunctionQuery(sql);
                }
                // ROLLUP / CUBE in GROUP BY
                if (upper.contains("GROUP BY") &&
                        (upper.contains(" ROLLUP(") || upper.contains(" CUBE("))) {
                    return executeGroupingSetQuery(sql);
                }
                var coreResult = database.execute(node);
                if (coreResult.isFailure()) {
                    return Result.failure(coreResult.error());
                }
                if (coreResult.value() instanceof QueryResult qr) {
                    return Result.success(qr);
                }
                // DDL/DML returns DmlResult — return empty QueryResult so callers
                // know the statement succeeded without falling back to a second execution.
                return Result.success(new QueryResult(List.of(), List.of(), 0));
            }

            return Result.failure("OLAP_ERROR", "Unsupported statement type: " + parsed.getClass().getSimpleName());
        } catch (Exception e) {
            return Result.failure("OLAP_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Execute a window function query.
     * First executes the base SELECT, then applies window functions to the result.
     */
    public Result<QueryResult> executeWindowFunction(String baseSql, List<WindowFunctionCall> windowCalls) {
        try {
            var baseResult = database.execute(baseSql);
            if (baseResult.isFailure()) {
                return Result.failure(baseResult.error());
            }
            if (baseResult.value() instanceof QueryResult qr) {
                var result = windowExecutor.execute(qr, windowCalls);
                return Result.success(result);
            }
            return Result.failure("OLAP_ERROR", "Base query did not return a result set");
        } catch (Exception e) {
            return Result.failure("OLAP_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Execute a CTE query.
     */
    public Result<QueryResult> executeCte(WithClause withClause, String originalSql) {
        // Extract the final SELECT from after the CTE definitions
        String finalQuery = extractFinalQuery(originalSql);
        return cteExecutor.execute(withClause, finalQuery);
    }

    /**
     * Execute a query with CUBE, ROLLUP, or GROUPING SETS.
     */
    public Result<QueryResult> executeGroupingSet(String tableName, GroupingSetSpec spec,
                                                   List<String> aggregates, String whereClause) {
        return groupingSetExecutor.execute(tableName, spec, aggregates, whereClause);
    }

    /**
     * Execute a MERGE statement.
     */
    public Result<DmlResult> executeMerge(String sql) {
        var parseResult = parser.parse(sql);
        if (parseResult.isFailure()) {
            return Result.failure(parseResult.error());
        }
        if (parseResult.value() instanceof MergeNode merge) {
            return mergeExecutor.execute(merge);
        }
        return Result.failure("MERGE_ERROR", "Not a MERGE statement");
    }

    /**
     * Execute a MERGE node directly.
     */
    public Result<DmlResult> executeMerge(MergeNode merge) {
        return mergeExecutor.execute(merge);
    }

    /**
     * Execute a PIVOT operation.
     */
    public Result<QueryResult> executePivot(String tableName, PivotClause pivot, List<String> groupByColumns) {
        return pivotExecutor.executePivot(tableName, pivot, groupByColumns);
    }

    /**
     * Execute an UNPIVOT operation.
     */
    public Result<QueryResult> executeUnpivot(String tableName, UnpivotClause unpivot, List<String> keepColumns) {
        return pivotExecutor.executeUnpivot(tableName, unpivot, keepColumns);
    }

    /**
     * Parse a window function expression.
     */
    public WindowFunctionCall parseWindowFunction(String expr) {
        return parser.parseWindowFunction(expr);
    }

    /**
     * Parse a grouping set specification.
     */
    public GroupingSetSpec parseGroupingSet(String expr) {
        return parser.parseGroupingSet(expr);
    }

    /**
     * Parse a PIVOT clause.
     */
    public PivotClause parsePivot(String expr) {
        return parser.parsePivot(expr);
    }

    /**
     * Parse an UNPIVOT clause.
     */
    public UnpivotClause parseUnpivot(String expr) {
        return parser.parseUnpivot(expr);
    }

    /**
     * Access the underlying InMemoryDatabase for setup and core SQL operations.
     */
    public InMemoryDatabase database() {
        return database;
    }

    /**
     * Convenience: execute core SQL directly.
     */
    public Result<Object> execute(String sql) {
        return database.execute(sql);
    }

    @Override
    public void close() {
        database.close();
    }

    // ---- Window function query execution ----

    private Result<QueryResult> executeWindowFunctionQuery(String sql) {
        int fromIdx = findUnparenthesizedKeyword(sql, "FROM");
        if (fromIdx < 0) return Result.failure("OLAP_ERROR", "Missing FROM in window query");

        String fromAndBeyond = sql.substring(fromIdx);
        String selectPart = sql.substring("SELECT ".length(), fromIdx).trim();

        List<String> windowItems = new ArrayList<>();
        List<String> regularItems = new ArrayList<>();
        for (String item : splitSelectItems(selectPart)) {
            if (item.toUpperCase().contains(" OVER ")) {
                windowItems.add(item.trim());
            } else {
                regularItems.add(item.trim());
            }
        }

        if (windowItems.isEmpty()) {
            return Result.failure("OLAP_ERROR", "No window functions found");
        }

        String baseSelect = "SELECT " + (regularItems.isEmpty() ? "*" : String.join(", ", regularItems))
                + " " + fromAndBeyond;
        Result<Object> baseResult = database.execute(baseSelect);
        if (baseResult.isFailure()) return Result.failure(baseResult.error());
        if (!(baseResult.value() instanceof QueryResult baseQr)) {
            return Result.failure("OLAP_ERROR", "Base window query did not return result set");
        }

        List<WindowFunctionCall> wfCalls = new ArrayList<>();
        List<String> wfAliases = new ArrayList<>();
        for (String wfItem : windowItems) {
            String wfExpr = removeAlias(wfItem);
            String alias = extractAlias(wfItem);
            try {
                wfCalls.add(parser.parseWindowFunction(wfExpr));
                wfAliases.add(alias);
            } catch (Exception e) {
                return Result.failure("OLAP_ERROR", "Cannot parse window function: " + wfItem);
            }
        }

        QueryResult wfResult = windowExecutor.execute(baseQr, wfCalls);

        // Rename window function columns to their aliases
        if (!wfAliases.isEmpty()) {
            List<String> cols = new ArrayList<>(wfResult.columnNames());
            int baseColCount = baseQr.columnNames().size();
            for (int i = 0; i < wfAliases.size() && (baseColCount + i) < cols.size(); i++) {
                if (wfAliases.get(i) != null) {
                    cols.set(baseColCount + i, wfAliases.get(i));
                }
            }
            wfResult = new QueryResult(cols, wfResult.rows(), wfResult.executionTimeNanos());
        }

        // Sort the result rows by the first window function's ORDER BY (if present)
        for (WindowFunctionCall wf : wfCalls) {
            if (wf.overClause() != null && wf.overClause().orderBy() != null
                    && !wf.overClause().orderBy().items().isEmpty()) {
                List<String> finalCols = wfResult.columnNames();
                List<ssg.pex.sql.ast.SqlSupport.OrderByItem> orderItems = wf.overClause().orderBy().items();
                List<ssg.pex.sql.dbms.Row> sortedRows = new ArrayList<>(wfResult.rows());
                sortedRows.sort((a, b) -> {
                    for (ssg.pex.sql.ast.SqlSupport.OrderByItem item : orderItems) {
                        int idx = finalCols.indexOf(item.column());
                        if (idx < 0) continue;
                        Object va = a.getValue(idx);
                        Object vb = b.getValue(idx);
                        int cmp = ssg.pex.sql.dbms.executor.ExpressionEvaluator.compareValues(va, vb);
                        if (cmp != 0) return item.ascending() ? cmp : -cmp;
                    }
                    return 0;
                });
                wfResult = new QueryResult(finalCols, sortedRows, wfResult.executionTimeNanos());
                break;
            }
        }

        return Result.success(wfResult);
    }

    // ---- Grouping set query execution (ROLLUP / CUBE) ----

    private Result<QueryResult> executeGroupingSetQuery(String sql) {
        int fromIdx = findUnparenthesizedKeyword(sql, "FROM");
        if (fromIdx < 0) return Result.failure("OLAP_ERROR", "Missing FROM in ROLLUP query");

        String afterFrom = sql.substring(fromIdx + 5).trim();
        String tableName = afterFrom.split("\\s+")[0];

        int groupByIdx = findUnparenthesizedKeyword(sql, "GROUP");
        if (groupByIdx < 0) return Result.failure("OLAP_ERROR", "Missing GROUP BY in ROLLUP query");

        String whereClause = null;
        int whereIdx = findUnparenthesizedKeyword(sql, "WHERE");
        if (whereIdx >= 0 && whereIdx < groupByIdx) {
            whereClause = sql.substring(whereIdx + 6, groupByIdx).trim();
        }

        String groupByPart = sql.substring(groupByIdx + "GROUP BY ".length()).trim();
        String upperGbp = groupByPart.toUpperCase();

        GroupingSetSpec.GroupingType type;
        String colList;
        if (upperGbp.startsWith("ROLLUP(")) {
            type = GroupingSetSpec.GroupingType.ROLLUP;
            colList = groupByPart.substring(7, groupByPart.lastIndexOf(')'));
        } else if (upperGbp.startsWith("CUBE(")) {
            type = GroupingSetSpec.GroupingType.CUBE;
            colList = groupByPart.substring(5, groupByPart.lastIndexOf(')'));
        } else {
            return Result.failure("OLAP_ERROR", "Unsupported grouping type: " + groupByPart);
        }

        List<String> groupCols = java.util.Arrays.stream(colList.split(","))
                .map(String::trim).collect(java.util.stream.Collectors.toList());
        List<List<String>> sets = groupCols.stream()
                .map(java.util.List::of).collect(java.util.stream.Collectors.toList());
        GroupingSetSpec spec = new GroupingSetSpec(type, sets, null);

        String selectPart = sql.substring("SELECT ".length(), fromIdx).trim();
        List<String> aggregates = new ArrayList<>();
        List<String> aggregateAliases = new ArrayList<>();
        for (String item : splitSelectItems(selectPart)) {
            String iu = item.toUpperCase();
            if (iu.contains("SUM(") || iu.contains("COUNT(") || iu.contains("AVG(")
                    || iu.contains("MIN(") || iu.contains("MAX(")) {
                String alias = extractAlias(item.trim());
                String expr = removeAlias(item.trim());
                aggregates.add(expr);
                aggregateAliases.add(alias);
            }
        }

        Result<QueryResult> gsResult = groupingSetExecutor.execute(tableName, spec, aggregates, whereClause);
        if (gsResult.isFailure()) return gsResult;

        // Rename aggregate columns to their aliases
        QueryResult qr = gsResult.value();
        boolean hasAliases = aggregateAliases.stream().anyMatch(a -> a != null);
        if (hasAliases) {
            List<String> cols = new ArrayList<>(qr.columnNames());
            int groupColCount = cols.size() - aggregates.size();
            for (int i = 0; i < aggregateAliases.size(); i++) {
                if (aggregateAliases.get(i) != null && (groupColCount + i) < cols.size()) {
                    cols.set(groupColCount + i, aggregateAliases.get(i));
                }
            }
            qr = new QueryResult(cols, qr.rows(), qr.executionTimeNanos());
        }
        return Result.success(qr);
    }

    // ---- Helpers ----

    private List<String> splitSelectItems(String clause) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < clause.length(); i++) {
            char c = clause.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                items.add(clause.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < clause.length()) items.add(clause.substring(start).trim());
        return items;
    }

    private String removeAlias(String expr) {
        return expr.trim().replaceAll("(?i)\\s+AS\\s+\\w+\\s*$", "").trim();
    }

    private String extractAlias(String expr) {
        expr = expr.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\s+AS\\s+(\\w+)\\s*$").matcher(expr);
        return m.find() ? m.group(1) : null;
    }

    private int findUnparenthesizedKeyword(String sql, String keyword) {
        String upper = sql.toUpperCase();
        int depth = 0;
        for (int i = 0; i <= sql.length() - keyword.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (depth == 0 && upper.startsWith(keyword, i)) {
                boolean prevOk = i == 0 || !Character.isLetterOrDigit(sql.charAt(i - 1));
                boolean nextOk = i + keyword.length() >= sql.length()
                        || !Character.isLetterOrDigit(sql.charAt(i + keyword.length()));
                if (prevOk && nextOk) return i;
            }
        }
        return -1;
    }

    /**
     * Extract the final query from a CTE SQL string (after all CTE definitions).
     */
    private String extractFinalQuery(String sql) {
        // Find the last unparenthesized SELECT
        String upper = sql.toUpperCase();
        int depth = 0;
        int lastSelectPos = -1;

        for (int i = 0; i < sql.length() - 6; i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && upper.startsWith("SELECT", i)) {
                lastSelectPos = i;
            }
        }

        if (lastSelectPos > 0) {
            return sql.substring(lastSelectPos);
        }
        return sql;
    }
}

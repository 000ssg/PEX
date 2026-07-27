package ssg.pex.sql.olap.executor;

import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlSupport.OrderByClause;
import ssg.pex.sql.ast.SqlSupport.OrderByItem;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.executor.ExpressionEvaluator;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.olap.ast.FrameBound;
import ssg.pex.sql.olap.ast.FrameSpec;
import ssg.pex.sql.olap.ast.OverClause;
import ssg.pex.sql.olap.ast.WindowFunctionCall;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluates window functions over result sets.
 * Groups rows by PARTITION BY, orders within each partition,
 * applies frame specification, and computes the window function.
 */
public class WindowFunctionExecutor {

    private static final Set<String> WINDOW_FUNCTIONS = Set.of(
            "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE",
            "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE",
            "PERCENT_RANK", "CUME_DIST",
            "PERCENTILE_CONT", "PERCENTILE_DISC",
            "SUM", "COUNT", "AVG", "MIN", "MAX"
    );

    public static boolean isWindowFunction(String name) {
        return WINDOW_FUNCTIONS.contains(name.toUpperCase());
    }

    /**
     * Evaluate a list of window function calls and append their results as new columns.
     */
    public QueryResult execute(QueryResult input, List<WindowFunctionCall> windowCalls) {
        if (windowCalls.isEmpty()) return input;

        List<Row> rows = new ArrayList<>(input.rows());
        List<String> columnNames = new ArrayList<>(input.columnNames());

        // Build column list for lookup
        var columns = new ArrayList<Column>();
        for (int i = 0; i < columnNames.size(); i++) {
            columns.add(new Column(columnNames.get(i),
                    ssg.pex.sql.ast.SqlSupport.SqlDataType.VARCHAR, true, null, false, i));
        }

        // For each window function, compute values for all rows
        List<Object[]> windowResults = new ArrayList<>();
        for (WindowFunctionCall wf : windowCalls) {
            Object[] values = computeWindowFunction(wf, rows, columns);
            windowResults.add(values);
            columnNames.add(wf.functionName().toLowerCase());
        }

        // Build new rows with appended window function values
        var resultRows = new ArrayList<Row>();
        for (int i = 0; i < rows.size(); i++) {
            Row original = rows.get(i);
            Object[] newValues = new Object[original.columnCount() + windowCalls.size()];
            System.arraycopy(original.values(), 0, newValues, 0, original.columnCount());
            for (int w = 0; w < windowCalls.size(); w++) {
                newValues[original.columnCount() + w] = windowResults.get(w)[i];
            }
            resultRows.add(new Row(newValues));
        }

        return new QueryResult(columnNames, resultRows, input.executionTimeNanos());
    }

    private Object[] computeWindowFunction(WindowFunctionCall wf, List<Row> rows, List<Column> columns) {
        Object[] results = new Object[rows.size()];
        OverClause over = wf.overClause();

        // Partition the rows
        Map<List<Object>, List<Integer>> partitions = partitionRows(rows, columns, over.partitionBy());

        for (var entry : partitions.entrySet()) {
            List<Integer> partitionIndices = entry.getValue();

            // Sort within partition
            List<Integer> sorted = new ArrayList<>(partitionIndices);
            if (over.orderBy() != null) {
                sortIndices(sorted, rows, columns, over.orderBy());
            }

            // Compute the function for each row in this partition
            computeForPartition(wf, rows, columns, sorted, results);
        }

        return results;
    }

    private Map<List<Object>, List<Integer>> partitionRows(List<Row> rows, List<Column> columns, List<String> partitionBy) {
        var partitions = new LinkedHashMap<List<Object>, List<Integer>>();
        for (int i = 0; i < rows.size(); i++) {
            List<Object> key = new ArrayList<>();
            for (String col : partitionBy) {
                key.add(getColumnValue(rows.get(i), col, columns));
            }
            partitions.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        return partitions;
    }

    private void sortIndices(List<Integer> indices, List<Row> rows, List<Column> columns, OrderByClause orderBy) {
        indices.sort((a, b) -> {
            for (OrderByItem item : orderBy.items()) {
                Object va = getColumnValue(rows.get(a), item.column(), columns);
                Object vb = getColumnValue(rows.get(b), item.column(), columns);
                int cmp = ExpressionEvaluator.compareValues(va, vb);
                if (cmp != 0) {
                    return item.ascending() ? cmp : -cmp;
                }
            }
            return 0;
        });
    }

    @SuppressWarnings("unchecked")
    private void computeForPartition(WindowFunctionCall wf, List<Row> rows, List<Column> columns,
                                     List<Integer> sorted, Object[] results) {
        String func = wf.functionName().toUpperCase();
        int partitionSize = sorted.size();
        boolean hasOrderBy = wf.overClause().orderBy() != null;

        switch (func) {
            case "ROW_NUMBER" -> {
                for (int i = 0; i < partitionSize; i++) {
                    results[sorted.get(i)] = (long) (i + 1);
                }
            }
            case "RANK" -> {
                computeRank(wf, rows, columns, sorted, results, false);
            }
            case "DENSE_RANK" -> {
                computeRank(wf, rows, columns, sorted, results, true);
            }
            case "NTILE" -> {
                int numBuckets = getIntArg(wf, 0, 4);
                for (int i = 0; i < partitionSize; i++) {
                    // NTILE distributes rows as evenly as possible
                    int bucket = (int) (((long) i * numBuckets) / partitionSize) + 1;
                    results[sorted.get(i)] = (long) bucket;
                }
            }
            case "LAG" -> {
                int offset = getIntArg(wf, 1, 1);
                Object defaultVal = getDefaultArg(wf, 2);
                for (int i = 0; i < partitionSize; i++) {
                    if (i - offset >= 0) {
                        String colName = getColumnArgName(wf);
                        results[sorted.get(i)] = getColumnValue(rows.get(sorted.get(i - offset)), colName, columns);
                    } else {
                        results[sorted.get(i)] = defaultVal;
                    }
                }
            }
            case "LEAD" -> {
                int offset = getIntArg(wf, 1, 1);
                Object defaultVal = getDefaultArg(wf, 2);
                for (int i = 0; i < partitionSize; i++) {
                    if (i + offset < partitionSize) {
                        String colName = getColumnArgName(wf);
                        results[sorted.get(i)] = getColumnValue(rows.get(sorted.get(i + offset)), colName, columns);
                    } else {
                        results[sorted.get(i)] = defaultVal;
                    }
                }
            }
            case "FIRST_VALUE" -> {
                String colName = getColumnArgName(wf);
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    results[sorted.get(i)] = getColumnValue(rows.get(sorted.get(frame[0])), colName, columns);
                }
            }
            case "LAST_VALUE" -> {
                String colName = getColumnArgName(wf);
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    results[sorted.get(i)] = getColumnValue(rows.get(sorted.get(frame[1])), colName, columns);
                }
            }
            case "NTH_VALUE" -> {
                String colName = getColumnArgName(wf);
                int n = getIntArg(wf, 1, 1);
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    int targetIdx = frame[0] + n - 1;
                    if (targetIdx <= frame[1]) {
                        results[sorted.get(i)] = getColumnValue(rows.get(sorted.get(targetIdx)), colName, columns);
                    } else {
                        results[sorted.get(i)] = null;
                    }
                }
            }
            case "PERCENT_RANK" -> {
                if (partitionSize <= 1) {
                    for (int i = 0; i < partitionSize; i++) {
                        results[sorted.get(i)] = 0.0;
                    }
                } else {
                    // Need rank values first
                    long[] ranks = new long[partitionSize];
                    computeRankValues(wf, rows, columns, sorted, ranks, false);
                    for (int i = 0; i < partitionSize; i++) {
                        results[sorted.get(i)] = (double) (ranks[i] - 1) / (partitionSize - 1);
                    }
                }
            }
            case "CUME_DIST" -> {
                long[] ranks = new long[partitionSize];
                computeRankValues(wf, rows, columns, sorted, ranks, false);
                for (int i = 0; i < partitionSize; i++) {
                    // CUME_DIST = number of rows with value <= current / total rows
                    // Find the last row with the same rank
                    long currentRank = ranks[i];
                    int countLE = 0;
                    for (int j = 0; j < partitionSize; j++) {
                        if (ranks[j] <= currentRank) countLE = j + 1;
                    }
                    // Count how many rows have same or smaller order value
                    // Actually CUME_DIST = (number of rows <= current) / total
                    // Simplify: count of rows at or before the last peer
                    int lastPeer = i;
                    for (int j = i + 1; j < partitionSize; j++) {
                        if (ranks[j] == currentRank) {
                            lastPeer = j;
                        } else {
                            break;
                        }
                    }
                    results[sorted.get(i)] = (double) (lastPeer + 1) / partitionSize;
                }
            }
            case "PERCENTILE_CONT" -> {
                double percentile = getDoubleArg(wf, 0, 0.5);
                String colName = getOrderByColumnName(wf);
                // Collect all values in partition order
                var values = new ArrayList<Double>();
                for (int idx : sorted) {
                    Object v = getColumnValue(rows.get(idx), colName, columns);
                    if (v instanceof Number n) values.add(n.doubleValue());
                }
                double result = interpolatePercentile(values, percentile);
                for (int idx : sorted) {
                    results[idx] = result;
                }
            }
            case "PERCENTILE_DISC" -> {
                double percentile = getDoubleArg(wf, 0, 0.5);
                String colName = getOrderByColumnName(wf);
                var values = new ArrayList<Object>();
                for (int idx : sorted) {
                    values.add(getColumnValue(rows.get(idx), colName, columns));
                }
                int targetIdx = (int) Math.ceil(percentile * values.size()) - 1;
                if (targetIdx < 0) targetIdx = 0;
                if (targetIdx >= values.size()) targetIdx = values.size() - 1;
                Object result = values.get(targetIdx);
                for (int idx : sorted) {
                    results[idx] = result;
                }
            }
            case "SUM" -> {
                String colName = getColumnArgName(wf);
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    double sum = 0;
                    boolean hasFloating = false;
                    long longSum = 0;
                    for (int j = frame[0]; j <= frame[1]; j++) {
                        Object v = getColumnValue(rows.get(sorted.get(j)), colName, columns);
                        if (v instanceof Number n) {
                            if (v instanceof Double || v instanceof Float) {
                                hasFloating = true;
                                sum += n.doubleValue();
                            } else {
                                longSum += n.longValue();
                                sum += n.doubleValue();
                            }
                        }
                    }
                    results[sorted.get(i)] = hasFloating ? sum : longSum;
                }
            }
            case "COUNT" -> {
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    long count = frame[1] - frame[0] + 1;
                    results[sorted.get(i)] = count;
                }
            }
            case "AVG" -> {
                String colName = getColumnArgName(wf);
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    double sum = 0;
                    int count = 0;
                    for (int j = frame[0]; j <= frame[1]; j++) {
                        Object v = getColumnValue(rows.get(sorted.get(j)), colName, columns);
                        if (v instanceof Number n) {
                            sum += n.doubleValue();
                            count++;
                        }
                    }
                    results[sorted.get(i)] = count > 0 ? sum / count : null;
                }
            }
            case "MIN" -> {
                String colName = getColumnArgName(wf);
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    Object min = null;
                    for (int j = frame[0]; j <= frame[1]; j++) {
                        Object v = getColumnValue(rows.get(sorted.get(j)), colName, columns);
                        if (v != null && (min == null || ExpressionEvaluator.compareValues(v, min) < 0)) {
                            min = v;
                        }
                    }
                    results[sorted.get(i)] = min;
                }
            }
            case "MAX" -> {
                String colName = getColumnArgName(wf);
                for (int i = 0; i < partitionSize; i++) {
                    int[] frame = resolveFrame(wf.overClause().frame(), i, partitionSize, hasOrderBy);
                    Object max = null;
                    for (int j = frame[0]; j <= frame[1]; j++) {
                        Object v = getColumnValue(rows.get(sorted.get(j)), colName, columns);
                        if (v != null && (max == null || ExpressionEvaluator.compareValues(v, max) > 0)) {
                            max = v;
                        }
                    }
                    results[sorted.get(i)] = max;
                }
            }
            default -> {
                for (int i = 0; i < partitionSize; i++) {
                    results[sorted.get(i)] = null;
                }
            }
        }
    }

    private void computeRank(WindowFunctionCall wf, List<Row> rows, List<Column> columns,
                             List<Integer> sorted, Object[] results, boolean dense) {
        long[] ranks = new long[sorted.size()];
        computeRankValues(wf, rows, columns, sorted, ranks, dense);
        for (int i = 0; i < sorted.size(); i++) {
            results[sorted.get(i)] = ranks[i];
        }
    }

    private void computeRankValues(WindowFunctionCall wf, List<Row> rows, List<Column> columns,
                                   List<Integer> sorted, long[] ranks, boolean dense) {
        OverClause over = wf.overClause();
        if (sorted.isEmpty()) return;

        ranks[0] = 1;
        long currentRank = 1;
        int duplicateCount = 1;

        for (int i = 1; i < sorted.size(); i++) {
            boolean sameAsPrev = true;
            if (over.orderBy() != null) {
                for (OrderByItem item : over.orderBy().items()) {
                    Object curr = getColumnValue(rows.get(sorted.get(i)), item.column(), columns);
                    Object prev = getColumnValue(rows.get(sorted.get(i - 1)), item.column(), columns);
                    if (ExpressionEvaluator.compareValues(curr, prev) != 0) {
                        sameAsPrev = false;
                        break;
                    }
                }
            }

            if (sameAsPrev) {
                ranks[i] = currentRank;
                duplicateCount++;
            } else {
                if (dense) {
                    currentRank++;
                } else {
                    currentRank += duplicateCount;
                }
                ranks[i] = currentRank;
                duplicateCount = 1;
            }
        }
    }

    /**
     * Resolve frame bounds to [startIdx, endIdx] within the partition.
     */
    private int[] resolveFrame(FrameSpec frame, int currentIdx, int partitionSize) {
        return resolveFrame(frame, currentIdx, partitionSize, true);
    }

    private int[] resolveFrame(FrameSpec frame, int currentIdx, int partitionSize, boolean hasOrderBy) {
        if (frame == null) {
            if (!hasOrderBy) {
                // No ORDER BY and no frame: entire partition
                return new int[]{0, partitionSize - 1};
            }
            // ORDER BY present but no explicit frame: UNBOUNDED PRECEDING to CURRENT ROW
            return new int[]{0, currentIdx};
        }

        int start = resolveBound(frame.start(), currentIdx, partitionSize);
        int end = resolveBound(frame.end(), currentIdx, partitionSize);

        start = Math.max(0, start);
        end = Math.min(partitionSize - 1, end);

        return new int[]{start, end};
    }

    private int resolveBound(FrameBound bound, int currentIdx, int partitionSize) {
        return switch (bound.type()) {
            case UNBOUNDED_PRECEDING -> 0;
            case N_PRECEDING -> currentIdx - bound.offset();
            case CURRENT_ROW -> currentIdx;
            case N_FOLLOWING -> currentIdx + bound.offset();
            case UNBOUNDED_FOLLOWING -> partitionSize - 1;
        };
    }

    private Object getColumnValue(Row row, String colName, List<Column> columns) {
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i).name();
            if (name.equalsIgnoreCase(colName)) {
                return row.getValue(i);
            }
            int dot = name.indexOf('.');
            if (dot >= 0 && name.substring(dot + 1).equalsIgnoreCase(colName)) {
                return row.getValue(i);
            }
        }
        return null;
    }

    private String getColumnArgName(WindowFunctionCall wf) {
        if (wf.args().isEmpty()) return "*";
        SqlExpression arg = wf.args().getFirst();
        if (arg instanceof SqlExpression.ColumnRef cr) {
            return cr.column();
        }
        if (arg instanceof SqlExpression.StarExpr) {
            return "*";
        }
        return arg.toString();
    }

    private String getOrderByColumnName(WindowFunctionCall wf) {
        if (wf.overClause().orderBy() != null && !wf.overClause().orderBy().items().isEmpty()) {
            return wf.overClause().orderBy().items().getFirst().column();
        }
        return getColumnArgName(wf);
    }

    private int getIntArg(WindowFunctionCall wf, int argIdx, int defaultVal) {
        if (wf.args().size() > argIdx) {
            SqlExpression arg = wf.args().get(argIdx);
            if (arg instanceof SqlExpression.LiteralExpr le && le.value() instanceof Number n) {
                return n.intValue();
            }
        }
        return defaultVal;
    }

    private double getDoubleArg(WindowFunctionCall wf, int argIdx, double defaultVal) {
        if (wf.args().size() > argIdx) {
            SqlExpression arg = wf.args().get(argIdx);
            if (arg instanceof SqlExpression.LiteralExpr le && le.value() instanceof Number n) {
                return n.doubleValue();
            }
        }
        return defaultVal;
    }

    private Object getDefaultArg(WindowFunctionCall wf, int argIdx) {
        if (wf.args().size() > argIdx) {
            SqlExpression arg = wf.args().get(argIdx);
            if (arg instanceof SqlExpression.LiteralExpr le) {
                return le.value();
            }
        }
        return null;
    }

    private double interpolatePercentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        if (sorted.size() == 1) return sorted.getFirst();

        double idx = percentile * (sorted.size() - 1);
        int lower = (int) Math.floor(idx);
        int upper = (int) Math.ceil(idx);
        if (lower == upper || upper >= sorted.size()) return sorted.get(lower);

        double fraction = idx - lower;
        return sorted.get(lower) + fraction * (sorted.get(upper) - sorted.get(lower));
    }
}

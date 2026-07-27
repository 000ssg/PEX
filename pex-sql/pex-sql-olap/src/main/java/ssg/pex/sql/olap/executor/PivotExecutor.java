package ssg.pex.sql.olap.executor;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.olap.ast.PivotClause;
import ssg.pex.sql.olap.ast.UnpivotClause;

import java.util.*;

/**
 * Executes PIVOT and UNPIVOT operations.
 * PIVOT: rows to columns based on aggregate + column values.
 * UNPIVOT: columns to rows.
 */
public class PivotExecutor {

    private final InMemoryDatabase database;

    public PivotExecutor(InMemoryDatabase database) {
        this.database = database;
    }

    /**
     * Execute a PIVOT operation on a table.
     *
     * @param tableName        source table name
     * @param pivot            the pivot clause
     * @param groupByColumns   columns to group by (the non-pivoted, non-aggregated columns)
     * @return pivoted result
     */
    public Result<QueryResult> executePivot(String tableName, PivotClause pivot, List<String> groupByColumns) {
        try {
            Table table = database.defaultSchema().getTable(tableName);
            if (table == null) {
                return Result.failure("PIVOT_ERROR", "Table not found: " + tableName);
            }

            String aggFunc = pivot.aggregateFunction().toUpperCase();
            String aggCol = pivot.aggregateColumn();
            String forCol = pivot.forColumn();
            List<Object> inValues = pivot.inValues();

            // Build result column names: group columns + one column per pivot value
            var columnNames = new ArrayList<String>();
            columnNames.addAll(groupByColumns);
            for (Object val : inValues) {
                columnNames.add(val.toString());
            }

            int forColIdx = table.getColumnIndex(forCol);
            int aggColIdx = table.getColumnIndex(aggCol);
            int[] groupColIdxs = groupByColumns.stream()
                    .mapToInt(table::getColumnIndex)
                    .toArray();

            // Group rows by the group-by columns
            var groups = new LinkedHashMap<List<Object>, Map<Object, List<Object>>>();
            for (Row row : table.scan()) {
                List<Object> groupKey = new ArrayList<>();
                for (int idx : groupColIdxs) {
                    groupKey.add(row.getValue(idx));
                }

                Object pivotValue = row.getValue(forColIdx);
                Object aggValue = row.getValue(aggColIdx);

                groups.computeIfAbsent(groupKey, k -> new LinkedHashMap<>())
                        .computeIfAbsent(pivotValue, k -> new ArrayList<>())
                        .add(aggValue);
            }

            // Build result rows
            var resultRows = new ArrayList<Row>();
            for (var entry : groups.entrySet()) {
                List<Object> groupKey = entry.getKey();
                Map<Object, List<Object>> pivotValues = entry.getValue();

                Object[] values = new Object[columnNames.size()];
                // Copy group key values
                for (int i = 0; i < groupKey.size(); i++) {
                    values[i] = groupKey.get(i);
                }

                // Compute aggregate for each pivot value
                for (int i = 0; i < inValues.size(); i++) {
                    Object pivotKey = inValues.get(i);
                    List<Object> aggValues = pivotValues.get(pivotKey);
                    if (aggValues == null) {
                        // Try string comparison
                        for (var pv : pivotValues.entrySet()) {
                            if (pv.getKey() != null && pv.getKey().toString().equals(pivotKey.toString())) {
                                aggValues = pv.getValue();
                                break;
                            }
                        }
                    }
                    values[groupKey.size() + i] = computeAggregate(aggFunc, aggValues);
                }

                resultRows.add(new Row(values));
            }

            return Result.success(new QueryResult(columnNames, resultRows, 0));
        } catch (Exception e) {
            return Result.failure("PIVOT_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Execute an UNPIVOT operation on a table.
     *
     * @param tableName    source table name
     * @param unpivot      the unpivot clause
     * @param keepColumns  columns to keep as-is (non-unpivoted columns)
     * @return unpivoted result
     */
    public Result<QueryResult> executeUnpivot(String tableName, UnpivotClause unpivot, List<String> keepColumns) {
        try {
            Table table = database.defaultSchema().getTable(tableName);
            if (table == null) {
                return Result.failure("UNPIVOT_ERROR", "Table not found: " + tableName);
            }

            // Build result column names
            var columnNames = new ArrayList<String>();
            columnNames.addAll(keepColumns);
            columnNames.add(unpivot.nameColumn());
            columnNames.add(unpivot.valueColumn());

            int[] keepColIdxs = keepColumns.stream()
                    .mapToInt(table::getColumnIndex)
                    .toArray();
            int[] sourceColIdxs = unpivot.sourceColumns().stream()
                    .mapToInt(table::getColumnIndex)
                    .toArray();

            var resultRows = new ArrayList<Row>();
            for (Row row : table.scan()) {
                for (int s = 0; s < unpivot.sourceColumns().size(); s++) {
                    Object value = row.getValue(sourceColIdxs[s]);
                    if (value == null) continue; // Skip null values in unpivot

                    Object[] values = new Object[columnNames.size()];
                    for (int k = 0; k < keepColIdxs.length; k++) {
                        values[k] = row.getValue(keepColIdxs[k]);
                    }
                    values[keepColIdxs.length] = unpivot.sourceColumns().get(s);
                    values[keepColIdxs.length + 1] = value;
                    resultRows.add(new Row(values));
                }
            }

            return Result.success(new QueryResult(columnNames, resultRows, 0));
        } catch (Exception e) {
            return Result.failure("UNPIVOT_ERROR", e.getMessage(), e);
        }
    }

    private Object computeAggregate(String func, List<Object> values) {
        if (values == null || values.isEmpty()) return null;

        return switch (func) {
            case "SUM" -> {
                double sum = 0;
                boolean hasFloat = false;
                long longSum = 0;
                for (Object v : values) {
                    if (v instanceof Number n) {
                        if (v instanceof Double || v instanceof Float) {
                            hasFloat = true;
                            sum += n.doubleValue();
                        } else {
                            longSum += n.longValue();
                            sum += n.doubleValue();
                        }
                    }
                }
                yield hasFloat ? (Object) sum : longSum;
            }
            case "COUNT" -> (long) values.stream().filter(Objects::nonNull).count();
            case "AVG" -> {
                double sum = 0;
                int count = 0;
                for (Object v : values) {
                    if (v instanceof Number n) {
                        sum += n.doubleValue();
                        count++;
                    }
                }
                yield count > 0 ? sum / count : null;
            }
            case "MIN" -> values.stream()
                    .filter(Objects::nonNull)
                    .min((a, b) -> ssg.pex.sql.dbms.executor.ExpressionEvaluator.compareValues(a, b))
                    .orElse(null);
            case "MAX" -> values.stream()
                    .filter(Objects::nonNull)
                    .max((a, b) -> ssg.pex.sql.dbms.executor.ExpressionEvaluator.compareValues(a, b))
                    .orElse(null);
            default -> null;
        };
    }
}

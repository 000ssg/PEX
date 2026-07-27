package ssg.pex.sql.streaming.engine;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.SqlExpression.AggregateFunction;
import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes aggregations over window contents for streaming queries.
 */
public class StreamAggregator {

    /**
     * Compute a single aggregate function over the events in a window for a given column.
     */
    public Object aggregate(AggregateFunction func, List<StreamEvent> events, String column) {
        List<Object> values;
        if (column == null || column.equals("*")) {
            // COUNT(*) - use event objects themselves
            values = events.stream().map(e -> (Object) e).collect(Collectors.toList());
        } else {
            values = events.stream()
                    .map(e -> e.values().get(column))
                    .collect(Collectors.toList());
        }
        return computeAggregate(func, values);
    }

    /**
     * Aggregate a complete window, producing a QueryResult.
     *
     * @param window        the window with events
     * @param funcs         aggregate functions to compute
     * @param columns       column names for each aggregate (null or "*" for COUNT(*))
     * @param outputNames   display names for result columns
     * @param groupByColumns columns to group by (empty list for no grouping)
     */
    public Result<QueryResult> aggregateWindow(Window window, List<AggregateFunction> funcs,
                                                List<String> columns, List<String> outputNames,
                                                List<String> groupByColumns) {
        try {
            long startNanos = System.nanoTime();
            List<StreamEvent> events = window.events();

            if (groupByColumns.isEmpty()) {
                // Single group: all events
                var values = new Object[funcs.size()];
                for (int i = 0; i < funcs.size(); i++) {
                    values[i] = aggregate(funcs.get(i), events, columns.get(i));
                }
                var row = new Row(values);
                long elapsed = System.nanoTime() - startNanos;
                return Result.success(new QueryResult(outputNames, List.of(row), elapsed));
            } else {
                // Group by
                Map<List<Object>, List<StreamEvent>> groups = groupEvents(events, groupByColumns);
                var rows = new ArrayList<Row>();
                var resultNames = new ArrayList<>(groupByColumns);
                resultNames.addAll(outputNames);

                for (var entry : groups.entrySet()) {
                    List<Object> groupKey = entry.getKey();
                    List<StreamEvent> groupEvents = entry.getValue();
                    var values = new Object[groupByColumns.size() + funcs.size()];
                    for (int g = 0; g < groupByColumns.size(); g++) {
                        values[g] = groupKey.get(g);
                    }
                    for (int i = 0; i < funcs.size(); i++) {
                        values[groupByColumns.size() + i] = aggregate(funcs.get(i), groupEvents, columns.get(i));
                    }
                    rows.add(new Row(values));
                }
                long elapsed = System.nanoTime() - startNanos;
                return Result.success(new QueryResult(resultNames, rows, elapsed));
            }
        } catch (Exception e) {
            return Result.failure("STREAM_AGGREGATE_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Group events by the specified columns.
     */
    public Map<List<Object>, List<StreamEvent>> groupEvents(List<StreamEvent> events,
                                                             List<String> groupByColumns) {
        var groups = new LinkedHashMap<List<Object>, List<StreamEvent>>();
        for (StreamEvent event : events) {
            var key = new ArrayList<Object>();
            for (String col : groupByColumns) {
                key.add(event.values().get(col));
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }
        return groups;
    }

    private Object computeAggregate(AggregateFunction func, List<Object> values) {
        return switch (func) {
            case COUNT -> (long) values.stream().filter(Objects::nonNull).count();
            case SUM -> computeSum(values);
            case AVG -> computeAvg(values);
            case MIN -> computeMin(values);
            case MAX -> computeMax(values);
            case GROUP_CONCAT -> values.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        };
    }

    private Object computeSum(List<Object> values) {
        boolean hasFloat = false;
        long longSum = 0;
        double doubleSum = 0.0;
        boolean hasAny = false;
        for (Object v : values) {
            if (v == null) continue;
            if (v instanceof Double d) { hasFloat = true; doubleSum += d; hasAny = true; }
            else if (v instanceof Float f) { hasFloat = true; doubleSum += f; hasAny = true; }
            else if (v instanceof Long l) { longSum += l; doubleSum += l; hasAny = true; }
            else if (v instanceof Integer i) { longSum += i; doubleSum += i; hasAny = true; }
            else if (v instanceof Number n) { longSum += n.longValue(); doubleSum += n.doubleValue(); hasAny = true; }
        }
        if (!hasAny) return 0L;
        if (hasFloat) return doubleSum;
        return longSum;
    }

    private Object computeAvg(List<Object> values) {
        long count = 0;
        double sum = 0;
        for (Object v : values) {
            if (v == null) continue;
            if (v instanceof Number n) { sum += n.doubleValue(); count++; }
        }
        return count == 0 ? null : sum / count;
    }

    @SuppressWarnings("unchecked")
    private Object computeMin(List<Object> values) {
        Object min = null;
        for (Object v : values) {
            if (v == null) continue;
            if (min == null || ((Comparable<Object>) v).compareTo(min) < 0) min = v;
        }
        return min;
    }

    @SuppressWarnings("unchecked")
    private Object computeMax(List<Object> values) {
        Object max = null;
        for (Object v : values) {
            if (v == null) continue;
            if (max == null || ((Comparable<Object>) v).compareTo(max) > 0) max = v;
        }
        return max;
    }
}

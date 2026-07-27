package ssg.pex.sql.dbms.executor;

import ssg.pex.sql.ast.SqlExpression.AggregateFunction;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes aggregates: COUNT, SUM, AVG, MIN, MAX, GROUP_CONCAT.
 */
public class AggregateEngine {

    /**
     * Groups rows by the specified columns. Returns a map from group key values to the rows in that group.
     */
    public Map<List<Object>, List<Row>> groupRows(List<Row> rows, List<String> groupByColumns, List<Column> columns) {
        var groups = new LinkedHashMap<List<Object>, List<Row>>();

        int[] groupColIndexes = new int[groupByColumns.size()];
        for (int i = 0; i < groupByColumns.size(); i++) {
            String colName = groupByColumns.get(i);
            groupColIndexes[i] = findColumnIndex(colName, columns);
        }

        for (Row row : rows) {
            List<Object> key = new ArrayList<>();
            for (int idx : groupColIndexes) {
                key.add(idx >= 0 ? row.getValue(idx) : null);
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        return groups;
    }

    /**
     * Computes an aggregate function over a list of values.
     */
    public Object computeAggregate(AggregateFunction func, List<Object> values, boolean distinct) {
        List<Object> effectiveValues = values;
        if (distinct) {
            effectiveValues = values.stream().distinct().collect(Collectors.toList());
        }

        return switch (func) {
            case COUNT -> (long) effectiveValues.stream().filter(Objects::nonNull).count();
            case SUM -> computeSum(effectiveValues);
            case AVG -> computeAvg(effectiveValues);
            case MIN -> computeMin(effectiveValues);
            case MAX -> computeMax(effectiveValues);
            case GROUP_CONCAT -> effectiveValues.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        };
    }

    /**
     * Extracts values for a specific column from a list of rows.
     */
    public List<Object> extractColumnValues(List<Row> rows, int columnIndex) {
        return rows.stream()
                .map(r -> r.getValue(columnIndex))
                .collect(Collectors.toList());
    }

    private Object computeSum(List<Object> values) {
        boolean hasFloatingPoint = false;
        long longSum = 0;
        double doubleSum = 0.0;
        boolean hasAny = false;
        for (Object v : values) {
            if (v == null) continue;
            if (v instanceof Double d) {
                hasFloatingPoint = true;
                doubleSum += d;
                hasAny = true;
            } else if (v instanceof Float f) {
                hasFloatingPoint = true;
                doubleSum += f;
                hasAny = true;
            } else if (v instanceof Long l) {
                longSum += l;
                doubleSum += l;
                hasAny = true;
            } else if (v instanceof Integer i) {
                longSum += i;
                doubleSum += i;
                hasAny = true;
            } else if (v instanceof Number n) {
                longSum += n.longValue();
                doubleSum += n.doubleValue();
                hasAny = true;
            }
        }
        if (!hasAny) return Long.valueOf(0L);
        if (hasFloatingPoint) return Double.valueOf(doubleSum);
        return Long.valueOf(longSum);
    }

    private Object computeAvg(List<Object> values) {
        long count = 0;
        double sum = 0;
        for (Object v : values) {
            if (v == null) continue;
            if (v instanceof Number n) {
                sum += n.doubleValue();
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    @SuppressWarnings("unchecked")
    private Object computeMin(List<Object> values) {
        Object min = null;
        for (Object v : values) {
            if (v == null) continue;
            if (min == null || ExpressionEvaluator.compareValues(v, min) < 0) {
                min = v;
            }
        }
        return min;
    }

    @SuppressWarnings("unchecked")
    private Object computeMax(List<Object> values) {
        Object max = null;
        for (Object v : values) {
            if (v == null) continue;
            if (max == null || ExpressionEvaluator.compareValues(v, max) > 0) {
                max = v;
            }
        }
        return max;
    }

    private int findColumnIndex(String name, List<Column> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}

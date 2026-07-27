package ssg.pex.nosql;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates a filter {@link Document} against a stored {@link Document}.
 *
 * <p>Supported operators:
 * <ul>
 *   <li>Plain field equality: {@code {"name": "Alice"}}</li>
 *   <li>Comparison: {@code $eq, $ne, $gt, $gte, $lt, $lte}</li>
 *   <li>Membership: {@code $in, $nin}</li>
 *   <li>Presence: {@code $exists}</li>
 *   <li>Pattern: {@code $regex}</li>
 *   <li>Logical: {@code $and, $or, $not}</li>
 *   <li>Dot notation for nested fields: {@code "address.city"}</li>
 * </ul>
 */
public final class QueryEvaluator {

    private QueryEvaluator() {}

    /**
     * Returns {@code true} if {@code stored} matches the {@code filter}.
     * An empty filter matches every document.
     */
    public static boolean matches(Document filter, Document stored) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object condition = entry.getValue();

            if (key.startsWith("$")) {
                // Top-level logical operator
                if (!applyLogical(key, condition, stored)) {
                    return false;
                }
            } else {
                // Field condition
                Object fieldValue = resolveField(key, stored);
                if (!evaluateFieldCondition(condition, fieldValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Resolves a (possibly dot-notated) field from a document.
     * Returns {@code null} if any segment is missing or not a map.
     */
    @SuppressWarnings("unchecked")
    public static Object resolveField(String fieldPath, Map<String, Object> doc) {
        String[] parts = fieldPath.split("\\.", 2);
        Object value = doc.get(parts[0]);
        if (parts.length == 1) {
            return value;
        }
        if (value instanceof Map<?, ?> nested) {
            return resolveField(parts[1], (Map<String, Object>) nested);
        }
        return null;
    }

    /**
     * Evaluates a single field condition. {@code condition} may be:
     * <ul>
     *   <li>A plain value (equality check)</li>
     *   <li>A {@link Map} with {@code $} operator keys</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static boolean evaluateFieldCondition(Object condition, Object fieldValue) {
        if (condition instanceof Map<?, ?> condMap && hasOperatorKey((Map<String, Object>) condMap)) {
            // Operator sub-document
            for (Map.Entry<?, ?> entry : condMap.entrySet()) {
                String op = (String) entry.getKey();
                Object operand = entry.getValue();
                if (!applyOperator(op, operand, fieldValue)) {
                    return false;
                }
            }
            return true;
        }
        // Plain equality
        return objectsEqual(condition, fieldValue);
    }

    private static boolean hasOperatorKey(Map<String, Object> map) {
        for (String key : map.keySet()) {
            if (key.startsWith("$")) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean applyOperator(String op, Object operand, Object fieldValue) {
        return switch (op) {
            case "$eq" -> objectsEqual(operand, fieldValue);
            case "$ne" -> !objectsEqual(operand, fieldValue);
            case "$gt" -> fieldValue != null && safeCompare(fieldValue, operand) > 0;
            case "$gte" -> fieldValue != null && safeCompare(fieldValue, operand) >= 0;
            case "$lt" -> fieldValue != null && safeCompare(fieldValue, operand) < 0;
            case "$lte" -> fieldValue != null && safeCompare(fieldValue, operand) <= 0;
            case "$in" -> {
                Collection<?> list = toCollection(operand);
                yield list.stream().anyMatch(v -> objectsEqual(v, fieldValue));
            }
            case "$nin" -> {
                Collection<?> list = toCollection(operand);
                yield list.stream().noneMatch(v -> objectsEqual(v, fieldValue));
            }
            case "$exists" -> {
                boolean shouldExist = Boolean.TRUE.equals(operand) || Integer.valueOf(1).equals(operand);
                yield shouldExist == (fieldValue != null);
            }
            case "$regex" -> {
                if (fieldValue == null) yield false;
                String pattern = operand.toString();
                yield Pattern.compile(pattern).matcher(fieldValue.toString()).find();
            }
            default -> throw new NoSqlException("NOSQL_QUERY_ERROR", "Unknown operator: " + op);
        };
    }

    @SuppressWarnings("unchecked")
    private static boolean applyLogical(String op, Object operand, Document stored) {
        return switch (op) {
            case "$and" -> {
                List<Document> conditions = toDocumentList(operand);
                yield conditions.stream().allMatch(c -> matches(c, stored));
            }
            case "$or" -> {
                List<Document> conditions = toDocumentList(operand);
                yield conditions.stream().anyMatch(c -> matches(c, stored));
            }
            case "$not" -> {
                if (operand instanceof Map<?, ?> m) {
                    Document subFilter = new Document((Map<String, Object>) m);
                    yield !matches(subFilter, stored);
                }
                yield true;
            }
            case "$nor" -> {
                List<Document> conditions = toDocumentList(operand);
                yield conditions.stream().noneMatch(c -> matches(c, stored));
            }
            default -> throw new NoSqlException("NOSQL_QUERY_ERROR", "Unknown logical operator: " + op);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Document> toDocumentList(Object operand) {
        if (operand instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof Map<?, ?> m ? new Document((Map<String, Object>) m) : (Document) item)
                    .toList();
        }
        throw new NoSqlException("NOSQL_QUERY_ERROR", "Expected list for logical operator, got: " + operand);
    }

    @SuppressWarnings("unchecked")
    private static Collection<?> toCollection(Object operand) {
        if (operand instanceof Collection<?> c) return c;
        throw new NoSqlException("NOSQL_QUERY_ERROR", "Expected array operand, got: " + operand);
    }

    /** Compares two values. Returns 0 if both null, throws if types incompatible. */
    private static int compare(Object a, Object b) {
        return safeCompare(a, b);
    }

    /** Null-safe comparison. Nulls sort before non-nulls. */
    @SuppressWarnings("unchecked")
    private static int safeCompare(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        if (a instanceof Comparable ca) {
            try { return ca.compareTo(b); } catch (ClassCastException ignored) {}
        }
        // Fall back to string comparison
        return a.toString().compareTo(b.toString());
    }

    private static boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        return a.equals(b);
    }
}

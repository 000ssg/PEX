package ssg.pex.nosql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies an update document to a stored {@link Document}.
 *
 * <p>Supported operators:
 * <ul>
 *   <li>{@code $set}      — set field(s)</li>
 *   <li>{@code $unset}    — remove field(s)</li>
 *   <li>{@code $inc}      — numeric increment/decrement</li>
 *   <li>{@code $push}     — append to array</li>
 *   <li>{@code $pull}     — remove value from array</li>
 *   <li>{@code $addToSet} — append only if not already present</li>
 *   <li>{@code $rename}   — rename field</li>
 * </ul>
 * If no {@code $} operators are present the entire update document is treated as {@code $set}.
 */
public final class UpdateEvaluator {

    private UpdateEvaluator() {}

    /**
     * Applies {@code update} to a copy of {@code target} and returns the mutated copy.
     * The original document is not modified.
     */
    public static Document apply(Document update, Document target) {
        Document result = target.copy();

        boolean hasOperators = update.keySet().stream().anyMatch(k -> k.startsWith("$"));

        if (!hasOperators) {
            // Treat as $set
            for (Map.Entry<String, Object> entry : update.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
            return result;
        }

        for (Map.Entry<String, Object> entry : update.entrySet()) {
            String op = entry.getKey();
            Object operand = entry.getValue();
            applyOperator(op, operand, result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void applyOperator(String op, Object operand, Document doc) {
        if (!(operand instanceof Map<?, ?> opMap)) {
            throw new NoSqlException("NOSQL_UPDATE_ERROR", "Operand for " + op + " must be a document");
        }
        Map<String, Object> fields = (Map<String, Object>) opMap;

        switch (op) {
            case "$set" -> {
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    setNestedField(doc, e.getKey(), e.getValue());
                }
            }
            case "$unset" -> {
                for (String field : fields.keySet()) {
                    removeNestedField(doc, field);
                }
            }
            case "$inc" -> {
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    Object current = doc.get(e.getKey());
                    double increment = toDouble(e.getValue());
                    if (current == null) {
                        doc.put(e.getKey(), increment);
                    } else {
                        double newVal = toDouble(current) + increment;
                        // Preserve integer type if both operands are integers
                        if (isIntegral(current) && isIntegral(e.getValue())) {
                            doc.put(e.getKey(), (long) newVal);
                        } else {
                            doc.put(e.getKey(), newVal);
                        }
                    }
                }
            }
            case "$push" -> {
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    Object arr = doc.get(e.getKey());
                    if (arr == null) {
                        List<Object> list = new ArrayList<>();
                        list.add(e.getValue());
                        doc.put(e.getKey(), list);
                    } else if (arr instanceof List<?> list) {
                        @SuppressWarnings("unchecked")
                        List<Object> mutableList = new ArrayList<>((List<Object>) list);
                        mutableList.add(e.getValue());
                        doc.put(e.getKey(), mutableList);
                    }
                }
            }
            case "$pull" -> {
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    Object arr = doc.get(e.getKey());
                    if (arr instanceof List<?> list) {
                        @SuppressWarnings("unchecked")
                        List<Object> mutableList = new ArrayList<>((List<Object>) list);
                        mutableList.removeIf(v -> QueryEvaluator.matches(
                                Document.of(Document.ID_FIELD, e.getValue()),
                                Document.of(Document.ID_FIELD, v)) ||
                                objectsEqual(v, e.getValue()));
                        doc.put(e.getKey(), mutableList);
                    }
                }
            }
            case "$addToSet" -> {
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    Object arr = doc.get(e.getKey());
                    if (arr == null) {
                        List<Object> list = new ArrayList<>();
                        list.add(e.getValue());
                        doc.put(e.getKey(), list);
                    } else if (arr instanceof List<?> list) {
                        boolean present = list.stream().anyMatch(v -> objectsEqual(v, e.getValue()));
                        if (!present) {
                            @SuppressWarnings("unchecked")
                            List<Object> mutableList = new ArrayList<>((List<Object>) list);
                            mutableList.add(e.getValue());
                            doc.put(e.getKey(), mutableList);
                        }
                    }
                }
            }
            case "$rename" -> {
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    if (doc.containsKey(e.getKey())) {
                        Object val = doc.remove(e.getKey());
                        doc.put(e.getValue().toString(), val);
                    }
                }
            }
            case "$mul" -> {
                for (Map.Entry<String, Object> e : fields.entrySet()) {
                    Object current = doc.get(e.getKey());
                    if (current != null) {
                        double result = toDouble(current) * toDouble(e.getValue());
                        if (isIntegral(current) && isIntegral(e.getValue())) {
                            doc.put(e.getKey(), (long) result);
                        } else {
                            doc.put(e.getKey(), result);
                        }
                    }
                }
            }
            default -> throw new NoSqlException("NOSQL_UPDATE_ERROR", "Unknown update operator: " + op);
        }
    }

    private static void setNestedField(Document doc, String fieldPath, Object value) {
        String[] parts = fieldPath.split("\\.", 2);
        if (parts.length == 1) {
            doc.put(fieldPath, value);
        } else {
            Object nested = doc.get(parts[0]);
            Document nestedDoc;
            if (nested instanceof Document nd) {
                nestedDoc = nd;
            } else {
                nestedDoc = new Document();
                doc.put(parts[0], nestedDoc);
            }
            setNestedField(nestedDoc, parts[1], value);
        }
    }

    private static void removeNestedField(Document doc, String fieldPath) {
        String[] parts = fieldPath.split("\\.", 2);
        if (parts.length == 1) {
            doc.remove(fieldPath);
        } else {
            Object nested = doc.get(parts[0]);
            if (nested instanceof Document nestedDoc) {
                removeNestedField(nestedDoc, parts[1]);
            }
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        throw new NoSqlException("NOSQL_UPDATE_ERROR", "Expected number, got: " + value);
    }

    private static boolean isIntegral(Object value) {
        return value instanceof Integer || value instanceof Long;
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

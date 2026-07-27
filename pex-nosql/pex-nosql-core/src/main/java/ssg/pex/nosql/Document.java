package ssg.pex.nosql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A NoSQL document backed by a {@link LinkedHashMap}.
 * Insertion order is preserved. The special field {@value #ID_FIELD} holds the document identifier.
 */
public class Document extends LinkedHashMap<String, Object> {

    /** Reserved field name for the document primary key. */
    public static final String ID_FIELD = "_id";

    /** Creates an empty document. */
    public Document() {
        super();
    }

    /** Creates a document pre-populated from the given map. */
    public Document(Map<String, Object> map) {
        super(map);
    }

    /**
     * Convenience factory accepting alternating key-value pairs.
     * Example: {@code Document.of("name", "Alice", "age", 30)}.
     *
     * @param kv alternating keys (String) and values (Object)
     * @return new Document
     * @throws IllegalArgumentException if the number of arguments is odd or a key is not a String
     */
    public static Document of(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("Document.of() requires an even number of arguments");
        }
        Document doc = new Document();
        for (int i = 0; i < kv.length; i += 2) {
            if (!(kv[i] instanceof String key)) {
                throw new IllegalArgumentException("Key at index " + i + " is not a String: " + kv[i]);
            }
            doc.put(key, kv[i + 1]);
        }
        return doc;
    }

    /**
     * Returns the value of the {@value #ID_FIELD} field, or {@code null} if not set.
     */
    public Object id() {
        return get(ID_FIELD);
    }

    /**
     * Deep-copies this document. Nested maps and lists are recursively copied.
     */
    @SuppressWarnings("unchecked")
    public Document copy() {
        Document copy = new Document();
        for (Map.Entry<String, Object> entry : this.entrySet()) {
            copy.put(entry.getKey(), deepCopy(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> m) {
            Document nested = new Document();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                nested.put((String) e.getKey(), deepCopy(e.getValue()));
            }
            return nested;
        } else if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value; // primitives, strings, numbers — immutable, safe to share
    }

    @Override
    public String toString() {
        return "Document" + super.toString();
    }
}

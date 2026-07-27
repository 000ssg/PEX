package ssg.pex.nosql;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Field projection specification: either include-only or exclude-only.
 */
public final class Projection {

    public enum Mode { INCLUDE, EXCLUDE, NONE }

    private final Mode mode;
    private final Set<String> fields;

    private Projection(Mode mode, Set<String> fields) {
        this.mode = mode;
        this.fields = fields;
    }

    /** No-op projection — all fields returned. */
    public static Projection none() {
        return new Projection(Mode.NONE, Set.of());
    }

    /** Include only the listed fields (plus _id). */
    public static Projection include(String... fields) {
        return new Projection(Mode.INCLUDE, new HashSet<>(Arrays.asList(fields)));
    }

    /** Exclude the listed fields. */
    public static Projection exclude(String... fields) {
        return new Projection(Mode.EXCLUDE, new HashSet<>(Arrays.asList(fields)));
    }

    /** Apply this projection to a document, returning a new projected copy. */
    public Document apply(Document doc) {
        if (mode == Mode.NONE) {
            return doc.copy();
        }
        Document result = new Document();
        if (mode == Mode.INCLUDE) {
            // Always include _id unless explicitly excluded
            if (doc.containsKey(Document.ID_FIELD)) {
                result.put(Document.ID_FIELD, doc.get(Document.ID_FIELD));
            }
            for (String field : fields) {
                if (doc.containsKey(field)) {
                    result.put(field, doc.get(field));
                }
            }
        } else { // EXCLUDE
            for (String key : doc.keySet()) {
                if (!fields.contains(key)) {
                    result.put(key, doc.get(key));
                }
            }
        }
        return result;
    }

    public Mode mode() { return mode; }
    public Set<String> fields() { return fields; }
}

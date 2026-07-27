package ssg.pex.nosql;

/**
 * Represents a single entry in an in-memory index.
 *
 * @param field     the indexed field name
 * @param unique    whether the index enforces uniqueness
 */
public record IndexEntry(String field, boolean unique) {

    /** A non-unique index on {@code field}. */
    public static IndexEntry nonUnique(String field) {
        return new IndexEntry(field, false);
    }

    /** A unique index on {@code field}. */
    public static IndexEntry unique(String field) {
        return new IndexEntry(field, true);
    }
}

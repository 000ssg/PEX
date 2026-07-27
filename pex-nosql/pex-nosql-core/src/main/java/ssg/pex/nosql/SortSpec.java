package ssg.pex.nosql;

/**
 * Describes a sort specification: a field name and direction.
 *
 * @param field     the document field to sort by (supports dot notation)
 * @param direction {@code 1} for ascending, {@code -1} for descending
 */
public record SortSpec(String field, int direction) {

    public SortSpec {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("Sort direction must be 1 (asc) or -1 (desc), got: " + direction);
        }
    }

    /** Ascending sort on the given field. */
    public static SortSpec asc(String field) {
        return new SortSpec(field, 1);
    }

    /** Descending sort on the given field. */
    public static SortSpec desc(String field) {
        return new SortSpec(field, -1);
    }

    public boolean isAscending() {
        return direction == 1;
    }
}

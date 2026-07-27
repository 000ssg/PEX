package ssg.pex.nosql;

import java.util.List;

/**
 * Lazily-composable result of a find operation.
 * All transformation methods ({@link #sort}, {@link #limit}, {@link #skip}, {@link #project},
 * {@link #projectExclude}) return a new {@code FindResult} with the transformation applied;
 * the underlying data is not mutated.
 */
public interface FindResult extends Iterable<Document> {

    /** Materialise all matching documents into an in-memory list. */
    List<Document> toList();

    /** Returns the number of matching documents. */
    long count();

    /**
     * Returns a new {@code FindResult} sorted by {@code field}.
     *
     * @param field     document field name (dot notation supported)
     * @param direction {@code 1} for ascending, {@code -1} for descending
     */
    FindResult sort(String field, int direction);

    /** Returns a new {@code FindResult} limited to at most {@code n} documents. */
    FindResult limit(int n);

    /** Returns a new {@code FindResult} that skips the first {@code n} documents. */
    FindResult skip(int n);

    /**
     * Returns a new {@code FindResult} keeping only the specified fields (include projection).
     * {@code _id} is always included.
     */
    FindResult project(String... includeFields);

    /**
     * Returns a new {@code FindResult} omitting the specified fields (exclude projection).
     */
    FindResult projectExclude(String... excludeFields);
}

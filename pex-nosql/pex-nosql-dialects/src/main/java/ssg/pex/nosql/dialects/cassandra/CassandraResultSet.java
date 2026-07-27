package ssg.pex.nosql.dialects.cassandra;

import ssg.pex.nosql.Document;

import java.util.List;

/**
 * Result of executing a CQL statement.
 */
public final class CassandraResultSet {

    private final List<Document> rows;
    private final boolean applied;
    private final long countResult;

    private CassandraResultSet(List<Document> rows, boolean applied, long countResult) {
        this.rows = List.copyOf(rows);
        this.applied = applied;
        this.countResult = countResult;
    }

    /** Creates a result with a list of rows. */
    public static CassandraResultSet ofRows(List<Document> rows) {
        return new CassandraResultSet(rows, true, rows.size());
    }

    /** Creates an empty applied result. */
    public static CassandraResultSet empty() {
        return new CassandraResultSet(List.of(), true, 0);
    }

    /** Creates a COUNT(*) result. */
    public static CassandraResultSet ofCount(long n) {
        Document countDoc = new Document();
        countDoc.put("count", n);
        return new CassandraResultSet(List.of(countDoc), true, n);
    }

    /** Returns the result rows. */
    public List<Document> rows() {
        return rows;
    }

    /**
     * For lightweight transactions (LWT) — always {@code true} in this simulation.
     */
    public boolean wasApplied() {
        return applied;
    }

    /** Returns the count value for COUNT(*) queries. */
    public long count() {
        return countResult;
    }

    /** Returns {@code true} if there are no rows. */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public String toString() {
        return "CassandraResultSet{rows=" + rows.size() + ", applied=" + applied + "}";
    }
}

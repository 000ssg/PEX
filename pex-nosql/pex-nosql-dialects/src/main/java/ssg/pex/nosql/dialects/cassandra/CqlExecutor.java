package ssg.pex.nosql.dialects.cassandra;

import ssg.pex.nosql.InMemoryNoSqlDatabase;

/**
 * Executes CQL statements against an {@link InMemoryNoSqlDatabase}.
 * Thin wrapper around {@link CqlParser} providing batch execution support.
 */
public final class CqlExecutor {

    private final CqlParser parser;

    public CqlExecutor(InMemoryNoSqlDatabase database) {
        this.parser = new CqlParser(database);
    }

    CqlExecutor(CqlParser parser) {
        this.parser = parser;
    }

    public CqlParser parser() {
        return parser;
    }

    /**
     * Executes a single CQL statement with optional bound parameters.
     */
    public CassandraResultSet execute(String cql, Object... params) {
        return parser.execute(cql, params);
    }

    /**
     * Executes multiple CQL statements sequentially (simulated BATCH).
     * Each statement is executed independently; atomicity is not enforced.
     */
    public void executeBatch(String... statements) {
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()
                    && !trimmed.equalsIgnoreCase("BEGIN BATCH")
                    && !trimmed.equalsIgnoreCase("APPLY BATCH")) {
                parser.execute(trimmed);
            }
        }
    }
}

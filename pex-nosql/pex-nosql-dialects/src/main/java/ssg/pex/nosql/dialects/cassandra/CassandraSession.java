package ssg.pex.nosql.dialects.cassandra;

import ssg.pex.nosql.InMemoryNoSqlDatabase;

/**
 * Represents an active Cassandra session bound to a keyspace.
 */
public final class CassandraSession implements AutoCloseable {

    private final String keyspace;
    private final CqlExecutor executor;
    private boolean closed = false;

    CassandraSession(String keyspace, CqlExecutor executor) {
        this.keyspace = keyspace;
        this.executor = executor;
        // Switch to the keyspace
        executor.execute("USE " + keyspace);
    }

    // ── Session API ───────────────────────────────────────────────────────────

    /**
     * Executes a CQL statement with no bound parameters.
     */
    public CassandraResultSet execute(String cql) {
        checkOpen();
        return executor.execute(cql);
    }

    /**
     * Prepares a CQL statement for repeated execution with bound parameters.
     */
    public PreparedStatement prepare(String cql) {
        checkOpen();
        return new PreparedStatement(cql, keyspace);
    }

    /**
     * Executes a prepared statement with the given parameter values.
     */
    public CassandraResultSet execute(PreparedStatement stmt, Object... params) {
        checkOpen();
        return executor.execute(stmt.cql(), params);
    }

    /**
     * Executes multiple CQL statements as a batch (simulated; not atomic).
     */
    public void executeBatch(String... cqls) {
        checkOpen();
        executor.executeBatch(cqls);
    }

    public String keyspace() {
        return keyspace;
    }

    @Override
    public void close() {
        closed = true;
    }

    private void checkOpen() {
        if (closed) {
            throw new ssg.pex.nosql.NoSqlException("CASSANDRA_SESSION_CLOSED",
                    "CassandraSession is already closed");
        }
    }
}

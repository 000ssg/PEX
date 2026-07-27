package ssg.pex.nosql.dialects.cassandra;

import ssg.pex.nosql.InMemoryNoSqlDatabase;

/**
 * Cassandra-flavored database entry point.
 * Wraps an {@link InMemoryNoSqlDatabase} and exposes a CQL-based session API.
 *
 * <p>Usage:
 * <pre>{@code
 * try (var db = new CassandraDatabase();
 *      var session = db.connect("my_keyspace")) {
 *     session.execute("CREATE TABLE my_keyspace.users (id UUID PRIMARY KEY, name TEXT)");
 *     session.execute("INSERT INTO my_keyspace.users (id, name) VALUES (uuid(), 'Alice')");
 *     var result = session.execute("SELECT * FROM my_keyspace.users");
 *     result.rows().forEach(System.out::println);
 * }
 * }</pre>
 */
public class CassandraDatabase implements AutoCloseable {

    private final InMemoryNoSqlDatabase database;
    private final CqlExecutor executor;
    private boolean closed = false;

    public CassandraDatabase() {
        this.database = new InMemoryNoSqlDatabase();
        this.executor = new CqlExecutor(database);
    }

    public CassandraDatabase(InMemoryNoSqlDatabase database) {
        this.database = database;
        this.executor = new CqlExecutor(database);
    }

    /**
     * Opens a session connected to the given keyspace.
     * Creates a dummy keyspace namespace (no-op in memory).
     */
    public CassandraSession connect(String keyspace) {
        checkOpen();
        return new CassandraSession(keyspace, executor);
    }

    /** Returns the underlying in-memory database. */
    public InMemoryNoSqlDatabase underlying() {
        return database;
    }

    /** Returns the CQL executor (for testing / direct access). */
    public CqlExecutor executor() {
        return executor;
    }

    @Override
    public void close() {
        closed = true;
        database.close();
    }

    private void checkOpen() {
        if (closed) {
            throw new ssg.pex.nosql.NoSqlException("CASSANDRA_DB_CLOSED",
                    "CassandraDatabase is already closed");
        }
    }
}

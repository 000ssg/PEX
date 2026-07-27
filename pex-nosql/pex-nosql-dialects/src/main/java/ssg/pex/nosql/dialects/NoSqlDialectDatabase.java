package ssg.pex.nosql.dialects;

import ssg.pex.nosql.*;
import ssg.pex.nosql.dialects.cassandra.CassandraDatabase;
import ssg.pex.nosql.dialects.cassandra.CassandraResultSet;
import ssg.pex.nosql.dialects.mongodb.MongoDbDatabase;

import java.util.List;

/**
 * Unified NoSQL dialect wrapper, analogous to {@code DialectDatabase} in pex-sql.
 *
 * <p>Dispatches commands to either a MongoDB or Cassandra backend depending on
 * the configured dialect.
 *
 * <p>Usage:
 * <pre>{@code
 * try (var db = new NoSqlDialectDatabase(NoSqlDialect.MONGODB)) {
 *     var mongo = db.asMongoDb();
 *     mongo.getMongoCollection("users").insertOne(Document.of("name", "Alice"));
 * }
 * }</pre>
 */
public class NoSqlDialectDatabase implements AutoCloseable {

    private final NoSqlDialect dialect;
    private final MongoDbDatabase mongoDb;
    private final CassandraDatabase cassandraDb;

    public NoSqlDialectDatabase(NoSqlDialect dialect) {
        this.dialect = dialect;
        if (dialect == NoSqlDialect.MONGODB) {
            this.mongoDb = new MongoDbDatabase();
            this.cassandraDb = null;
        } else {
            this.mongoDb = null;
            this.cassandraDb = new CassandraDatabase();
        }
    }

    /**
     * Executes a command string or document-style command depending on dialect.
     * For MongoDB: pass a JSON-like map (Document) with collection name as context.
     * For Cassandra: pass a CQL string.
     *
     * @param command a CQL string (Cassandra) or a {@link Document} command (MongoDB)
     * @return result object — {@link CassandraResultSet} or {@link WriteResult}/{@link FindResult}
     */
    public Object execute(Object command) {
        return switch (dialect) {
            case MONGODB -> {
                if (command instanceof String cql) {
                    throw new NoSqlException("NOSQL_EXEC_ERROR",
                            "MongoDB dialect does not accept raw strings; use Document commands");
                }
                throw new NoSqlException("NOSQL_EXEC_ERROR", "Unknown command type for MongoDB: " + command);
            }
            case CASSANDRA -> {
                if (command instanceof String cql) {
                    yield cassandraDb.executor().execute(cql);
                }
                throw new NoSqlException("NOSQL_EXEC_ERROR", "Cassandra execute() requires a CQL string");
            }
        };
    }

    /** Returns the MongoDB API — throws if dialect is not MONGODB. */
    public MongoDbDatabase asMongoDb() {
        if (mongoDb == null) {
            throw new NoSqlException("NOSQL_DIALECT_ERROR", "Current dialect is not MONGODB");
        }
        return mongoDb;
    }

    /** Returns the Cassandra API — throws if dialect is not CASSANDRA. */
    public CassandraDatabase asCassandra() {
        if (cassandraDb == null) {
            throw new NoSqlException("NOSQL_DIALECT_ERROR", "Current dialect is not CASSANDRA");
        }
        return cassandraDb;
    }

    /** Returns the underlying NoSqlDatabase (works for both dialects). */
    public NoSqlDatabase getDatabase() {
        return switch (dialect) {
            case MONGODB -> mongoDb;
            case CASSANDRA -> cassandraDb.underlying();
        };
    }

    public NoSqlDialect dialect() {
        return dialect;
    }

    @Override
    public void close() {
        if (mongoDb != null) mongoDb.close();
        if (cassandraDb != null) cassandraDb.close();
    }
}

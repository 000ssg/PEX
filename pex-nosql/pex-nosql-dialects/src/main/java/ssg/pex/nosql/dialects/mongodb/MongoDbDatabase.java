package ssg.pex.nosql.dialects.mongodb;

import ssg.pex.nosql.*;

import java.util.List;

/**
 * MongoDB-flavored database wrapper.
 *
 * <p>Delegates all {@link NoSqlDatabase} operations to an {@link InMemoryNoSqlDatabase}
 * and additionally exposes {@link #getMongoCollection(String)} for the MongoDB-specific API.
 */
public class MongoDbDatabase implements NoSqlDatabase {

    private final InMemoryNoSqlDatabase delegate;
    private final MongodbExecutor executor;

    public MongoDbDatabase() {
        this.delegate = new InMemoryNoSqlDatabase();
        this.executor = new MongodbExecutor(delegate);
    }

    public MongoDbDatabase(InMemoryNoSqlDatabase delegate) {
        this.delegate = delegate;
        this.executor = new MongodbExecutor(delegate);
    }

    // ── NoSqlDatabase ─────────────────────────────────────────────────────────

    @Override
    public NoSqlCollection getCollection(String name) {
        return delegate.getCollection(name);
    }

    @Override
    public NoSqlCollection createCollection(String name) {
        return delegate.createCollection(name);
    }

    @Override
    public boolean collectionExists(String name) {
        return delegate.collectionExists(name);
    }

    @Override
    public void dropCollection(String name) {
        delegate.dropCollection(name);
    }

    @Override
    public List<String> listCollectionNames() {
        return delegate.listCollectionNames();
    }

    @Override
    public void drop() {
        delegate.drop();
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ── MongoDB-specific API ──────────────────────────────────────────────────

    /**
     * Returns a {@link MongoCollection} wrapper for the named collection.
     * Creates the collection implicitly if it does not exist.
     */
    public MongoCollection getMongoCollection(String name) {
        return new MongoCollection(delegate.getCollection(name));
    }

    /**
     * Executes a raw command document against the named collection.
     *
     * @see MongodbExecutor#execute(String, Document)
     */
    public Object executeCommand(String collectionName, Document command) {
        return executor.execute(collectionName, command);
    }

    /** Returns the underlying in-memory database. */
    public InMemoryNoSqlDatabase underlying() {
        return delegate;
    }
}

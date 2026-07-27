package ssg.pex.nosql;

import java.util.List;

/**
 * Abstraction over a NoSQL database instance.
 * Manages collections (analogous to MongoDB databases or Cassandra keyspaces).
 */
public interface NoSqlDatabase extends AutoCloseable {

    /**
     * Returns the collection with the given name.
     * Creates the collection implicitly if it does not yet exist.
     */
    NoSqlCollection getCollection(String name);

    /**
     * Explicitly creates a collection with the given name.
     *
     * @throws NoSqlException if a collection with this name already exists
     */
    NoSqlCollection createCollection(String name);

    /** Returns {@code true} if a collection with the given name exists. */
    boolean collectionExists(String name);

    /** Drops the collection with the given name. No-op if it does not exist. */
    void dropCollection(String name);

    /** Returns the names of all collections in this database. */
    List<String> listCollectionNames();

    /** Drops this database and all its collections. */
    void drop();

    @Override
    void close();
}

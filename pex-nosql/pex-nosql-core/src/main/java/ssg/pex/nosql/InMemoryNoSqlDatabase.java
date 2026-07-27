package ssg.pex.nosql;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link NoSqlDatabase}.
 *
 * <p>Collections are created lazily via {@link #getCollection(String)}.
 * The {@link #createCollection(String)} method throws if a collection already exists.
 */
public class InMemoryNoSqlDatabase implements NoSqlDatabase {

    private final Map<String, InMemoryCollection> collections = new ConcurrentHashMap<>();

    public InMemoryNoSqlDatabase() {}

    @Override
    public InMemoryCollection getCollection(String name) {
        return collections.computeIfAbsent(name, n -> new InMemoryCollection(n, this));
    }

    @Override
    public InMemoryCollection createCollection(String name) {
        if (collections.containsKey(name)) {
            throw new NoSqlException("NOSQL_COLLECTION_EXISTS",
                    "Collection already exists: " + name);
        }
        InMemoryCollection col = new InMemoryCollection(name, this);
        collections.put(name, col);
        return col;
    }

    @Override
    public boolean collectionExists(String name) {
        return collections.containsKey(name);
    }

    @Override
    public void dropCollection(String name) {
        InMemoryCollection col = collections.remove(name);
        if (col != null) {
            col.drop();
        }
    }

    @Override
    public List<String> listCollectionNames() {
        return List.copyOf(collections.keySet());
    }

    @Override
    public void drop() {
        collections.values().forEach(InMemoryCollection::drop);
        collections.clear();
    }

    @Override
    public void close() {
        drop();
    }
}

package ssg.pex.nosql;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over a NoSQL collection (analogous to a MongoDB collection or a Cassandra table).
 */
public interface NoSqlCollection {

    /** The name of this collection. */
    String name();

    /** Inserts a single document. Generates {@code _id} if absent. */
    WriteResult insertOne(Document doc);

    /** Inserts multiple documents. Generates {@code _id} for each document that lacks one. */
    WriteResult insertMany(List<Document> docs);

    /** Returns all documents in this collection. */
    FindResult find();

    /** Returns documents matching {@code filter}. */
    FindResult find(Document filter);

    /** Returns the first document matching {@code filter}, or empty if none. */
    Optional<Document> findOne(Document filter);

    /** Returns the total number of documents in this collection. */
    long count();

    /** Returns the number of documents matching {@code filter}. */
    long count(Document filter);

    /**
     * Updates the first document matching {@code filter} using {@code update}.
     * Returns a {@link WriteResult} with {@code matchedCount} and {@code modifiedCount}.
     */
    WriteResult updateOne(Document filter, Document update);

    /**
     * Updates all documents matching {@code filter} using {@code update}.
     * Returns a {@link WriteResult} with {@code matchedCount} and {@code modifiedCount}.
     */
    WriteResult updateMany(Document filter, Document update);

    /**
     * Replaces the first document matching {@code filter} with {@code replacement}.
     * The replacement must not contain update operators.
     */
    WriteResult replaceOne(Document filter, Document replacement);

    /** Deletes the first document matching {@code filter}. */
    WriteResult deleteOne(Document filter);

    /** Deletes all documents matching {@code filter}. */
    WriteResult deleteMany(Document filter);

    /** Returns distinct values for {@code field} across all documents. */
    List<Object> distinct(String field);

    /** Returns distinct values for {@code field} in documents matching {@code filter}. */
    List<Object> distinct(String field, Document filter);

    /**
     * Runs an aggregation {@code pipeline} (list of stage documents) and returns the result.
     * Each stage document has exactly one key: the stage name (e.g. {@code "$match"}).
     */
    List<Document> aggregate(List<Document> pipeline);

    /**
     * Creates an index on the specified {@code keys} document (field → direction).
     *
     * @param keys   index key specification, e.g. {@code {"name": 1}}
     * @param unique whether to enforce uniqueness
     */
    void createIndex(Document keys, boolean unique);

    /** Drops this collection and removes all its documents. */
    void drop();

    /** Returns the number of documents, potentially using cached metadata. */
    long estimatedDocumentCount();
}

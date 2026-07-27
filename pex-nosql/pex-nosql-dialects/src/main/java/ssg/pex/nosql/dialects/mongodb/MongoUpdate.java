package ssg.pex.nosql.dialects.mongodb;

import ssg.pex.nosql.Document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for MongoDB-style update documents.
 *
 * <p>Use static factory methods to start a chain, then instance methods to add more operators:
 * <pre>{@code
 * MongoUpdate.set("name", "Bob").inc("age", 1)
 * MongoUpdate.unset("tempField").push("tags", "newTag")
 * }</pre>
 */
public final class MongoUpdate {

    private final Map<String, Document> operators = new LinkedHashMap<>();

    private MongoUpdate() {}

    // ── Static entry points (start a new builder) ─────────────────────────────

    /** Creates a new update starting with {@code $set}. */
    public static MongoUpdate set(String field, Object value) {
        return new MongoUpdate().andSet(field, value);
    }

    /** Creates a new update starting with {@code $unset}. */
    public static MongoUpdate unset(String field) {
        return new MongoUpdate().andUnset(field);
    }

    /** Creates a new update starting with {@code $inc}. */
    public static MongoUpdate inc(String field, Number amount) {
        return new MongoUpdate().andInc(field, amount);
    }

    /** Creates a new update starting with {@code $push}. */
    public static MongoUpdate push(String field, Object value) {
        return new MongoUpdate().andPush(field, value);
    }

    /** Creates a new update starting with {@code $pull}. */
    public static MongoUpdate pull(String field, Object value) {
        return new MongoUpdate().andPull(field, value);
    }

    /** Creates a new update starting with {@code $addToSet}. */
    public static MongoUpdate addToSet(String field, Object value) {
        return new MongoUpdate().andAddToSet(field, value);
    }

    /** Creates a new update starting with {@code $rename}. */
    public static MongoUpdate rename(String oldField, String newField) {
        return new MongoUpdate().andRename(oldField, newField);
    }

    // ── Instance chaining methods ──────────────────────────────────────────────

    /** Adds a {@code $set} operation. */
    public MongoUpdate andSet(String field, Object value) {
        operators.computeIfAbsent("$set", k -> new Document()).put(field, value);
        return this;
    }

    /** Adds an {@code $unset} operation. */
    public MongoUpdate andUnset(String field) {
        operators.computeIfAbsent("$unset", k -> new Document()).put(field, "");
        return this;
    }

    /** Adds an {@code $inc} operation. */
    public MongoUpdate andInc(String field, Number amount) {
        operators.computeIfAbsent("$inc", k -> new Document()).put(field, amount);
        return this;
    }

    /** Adds a {@code $push} operation. */
    public MongoUpdate andPush(String field, Object value) {
        operators.computeIfAbsent("$push", k -> new Document()).put(field, value);
        return this;
    }

    /** Adds a {@code $pull} operation. */
    public MongoUpdate andPull(String field, Object value) {
        operators.computeIfAbsent("$pull", k -> new Document()).put(field, value);
        return this;
    }

    /** Adds an {@code $addToSet} operation. */
    public MongoUpdate andAddToSet(String field, Object value) {
        operators.computeIfAbsent("$addToSet", k -> new Document()).put(field, value);
        return this;
    }

    /** Adds a {@code $rename} operation. */
    public MongoUpdate andRename(String oldField, String newField) {
        operators.computeIfAbsent("$rename", k -> new Document()).put(oldField, newField);
        return this;
    }

    /** Returns the update operator document suitable for passing to a collection update method. */
    public Document toDocument() {
        Document doc = new Document();
        for (Map.Entry<String, Document> e : operators.entrySet()) {
            doc.put(e.getKey(), e.getValue().copy());
        }
        return doc;
    }

    @Override
    public String toString() {
        return "MongoUpdate" + operators.toString();
    }
}

package ssg.pex.nosql.dialects.mongodb;

import ssg.pex.nosql.*;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB-flavored collection wrapper that adds typed query/update/pipeline support
 * on top of a {@link NoSqlCollection}.
 */
public final class MongoCollection {

    private final NoSqlCollection delegate;

    public MongoCollection(NoSqlCollection delegate) {
        this.delegate = delegate;
    }

    public String name() { return delegate.name(); }

    // ── Insert ────────────────────────────────────────────────────────────────

    public WriteResult insertOne(Document doc) { return delegate.insertOne(doc); }
    public WriteResult insertMany(List<Document> docs) { return delegate.insertMany(docs); }

    // ── Find ─────────────────────────────────────────────────────────────────

    public FindResult find() { return delegate.find(); }
    public FindResult find(Document filter) { return delegate.find(filter); }
    public FindResult find(MongoQuery query) { return delegate.find(query.toDocument()); }
    public Optional<Document> findOne(MongoQuery query) { return delegate.findOne(query.toDocument()); }

    // ── Count ─────────────────────────────────────────────────────────────────

    public long count() { return delegate.count(); }
    public long countDocuments(MongoQuery filter) { return delegate.count(filter.toDocument()); }

    // ── Update ───────────────────────────────────────────────────────────────

    public WriteResult updateOne(Document filter, Document update) { return delegate.updateOne(filter, update); }
    public WriteResult updateMany(Document filter, Document update) { return delegate.updateMany(filter, update); }
    public WriteResult updateOne(MongoQuery filter, MongoUpdate update) {
        return delegate.updateOne(filter.toDocument(), update.toDocument());
    }
    public WriteResult updateMany(MongoQuery filter, MongoUpdate update) {
        return delegate.updateMany(filter.toDocument(), update.toDocument());
    }
    public WriteResult replaceOne(Document filter, Document replacement) { return delegate.replaceOne(filter, replacement); }

    // ── Delete ───────────────────────────────────────────────────────────────

    public WriteResult deleteOne(Document filter) { return delegate.deleteOne(filter); }
    public WriteResult deleteMany(Document filter) { return delegate.deleteMany(filter); }
    public WriteResult deleteOne(MongoQuery filter) { return delegate.deleteOne(filter.toDocument()); }
    public WriteResult deleteMany(MongoQuery filter) { return delegate.deleteMany(filter.toDocument()); }

    // ── Aggregate ─────────────────────────────────────────────────────────────

    public List<Document> aggregate(List<Document> pipeline) { return delegate.aggregate(pipeline); }
    public List<Document> aggregate(MongoPipeline pipeline) { return delegate.aggregate(pipeline.toList()); }

    // ── Index ─────────────────────────────────────────────────────────────────

    public void createIndex(Document keys, boolean unique) { delegate.createIndex(keys, unique); }
    public void createIndex(String field, boolean unique, boolean sparse) {
        // sparse is noted but not enforced differently in this in-memory impl
        delegate.createIndex(Document.of(field, 1), unique);
    }

    // ── Distinct ──────────────────────────────────────────────────────────────

    public List<Object> distinct(String field) { return delegate.distinct(field); }
    public List<Object> distinct(String field, MongoQuery filter) {
        return delegate.distinct(field, filter.toDocument());
    }

    // ── Delegation ───────────────────────────────────────────────────────────

    public NoSqlCollection delegate() { return delegate; }
}

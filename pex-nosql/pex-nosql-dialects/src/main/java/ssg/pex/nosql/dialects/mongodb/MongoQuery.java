package ssg.pex.nosql.dialects.mongodb;

import ssg.pex.nosql.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder that produces a filter {@link Document} for MongoDB-style queries.
 *
 * <p>Examples:
 * <pre>{@code
 * MongoQuery.where("age").gt(25).and("name").regex("^A")
 * MongoQuery.eq("status", "active")
 * MongoQuery.and(MongoQuery.eq("x", 1), MongoQuery.gt("y", 2))
 * MongoQuery.in("status", List.of("active", "pending"))
 * MongoQuery.exists("email")
 * MongoQuery.empty()
 * }</pre>
 */
public final class MongoQuery {

    private final Document filter;

    // Current field being built (for chained where/and/or)
    private String currentField;

    private MongoQuery(Document filter) {
        this.filter = filter;
    }

    private MongoQuery() {
        this.filter = new Document();
    }

    // ── Static factory methods ────────────────────────────────────────────────

    /** Returns a query that matches all documents. */
    public static MongoQuery empty() {
        return new MongoQuery(new Document());
    }

    /** Begins building a field predicate. */
    public static FieldBuilder where(String field) {
        return new FieldBuilder(new MongoQuery(), field);
    }

    /** Shortcut: field equals value. */
    public static MongoQuery eq(String field, Object value) {
        Document filter = new Document();
        filter.put(field, value);
        return new MongoQuery(filter);
    }

    /** Shortcut: field not equals value. */
    public static MongoQuery ne(String field, Object value) {
        Document filter = new Document();
        filter.put(field, Document.of("$ne", value));
        return new MongoQuery(filter);
    }

    /** Shortcut: field greater than value. */
    public static MongoQuery gt(String field, Object value) {
        Document filter = new Document();
        filter.put(field, Document.of("$gt", value));
        return new MongoQuery(filter);
    }

    /** Shortcut: field greater than or equal to value. */
    public static MongoQuery gte(String field, Object value) {
        Document filter = new Document();
        filter.put(field, Document.of("$gte", value));
        return new MongoQuery(filter);
    }

    /** Shortcut: field less than value. */
    public static MongoQuery lt(String field, Object value) {
        Document filter = new Document();
        filter.put(field, Document.of("$lt", value));
        return new MongoQuery(filter);
    }

    /** Shortcut: field less than or equal to value. */
    public static MongoQuery lte(String field, Object value) {
        Document filter = new Document();
        filter.put(field, Document.of("$lte", value));
        return new MongoQuery(filter);
    }

    /** Shortcut: field value is in the list. */
    public static MongoQuery in(String field, List<?> values) {
        Document filter = new Document();
        filter.put(field, Document.of("$in", new ArrayList<>(values)));
        return new MongoQuery(filter);
    }

    /** Shortcut: field value is NOT in the list. */
    public static MongoQuery nin(String field, List<?> values) {
        Document filter = new Document();
        filter.put(field, Document.of("$nin", new ArrayList<>(values)));
        return new MongoQuery(filter);
    }

    /** Shortcut: field exists (or not). */
    public static MongoQuery exists(String field) {
        Document filter = new Document();
        filter.put(field, Document.of("$exists", true));
        return new MongoQuery(filter);
    }

    /** Shortcut: field does not exist. */
    public static MongoQuery notExists(String field) {
        Document filter = new Document();
        filter.put(field, Document.of("$exists", false));
        return new MongoQuery(filter);
    }

    /** Shortcut: field matches regex pattern. */
    public static MongoQuery regex(String field, String pattern) {
        Document filter = new Document();
        filter.put(field, Document.of("$regex", pattern));
        return new MongoQuery(filter);
    }

    /** Logical AND of multiple queries. */
    public static MongoQuery and(MongoQuery... queries) {
        List<Document> conditions = new ArrayList<>();
        for (MongoQuery q : queries) {
            conditions.add(q.toDocument());
        }
        Document filter = new Document();
        filter.put("$and", conditions);
        return new MongoQuery(filter);
    }

    /** Logical OR of multiple queries. */
    public static MongoQuery or(MongoQuery... queries) {
        List<Document> conditions = new ArrayList<>();
        for (MongoQuery q : queries) {
            conditions.add(q.toDocument());
        }
        Document filter = new Document();
        filter.put("$or", conditions);
        return new MongoQuery(filter);
    }

    /** Logical NOT of the given query. */
    public static MongoQuery not(MongoQuery query) {
        Document filter = new Document();
        filter.put("$not", query.toDocument());
        return new MongoQuery(filter);
    }

    // ── Instance chaining ─────────────────────────────────────────────────────

    /**
     * Adds an additional field constraint (logical AND with existing constraints).
     * Returns a {@link FieldBuilder} for fluent operator chaining.
     */
    public FieldBuilder and(String field) {
        return new FieldBuilder(this, field);
    }

    /** Returns the backing filter document. */
    public Document toDocument() {
        return filter.copy();
    }

    // ── Inner builder ─────────────────────────────────────────────────────────

    /** Fluent builder for a single field predicate. */
    public static final class FieldBuilder {

        private final MongoQuery query;
        private final String field;

        FieldBuilder(MongoQuery query, String field) {
            this.query = query;
            this.field = field;
        }

        public MongoQuery eq(Object value) {
            query.filter.put(field, value);
            return query;
        }

        public MongoQuery ne(Object value) {
            query.filter.put(field, Document.of("$ne", value));
            return query;
        }

        public MongoQuery gt(Object value) {
            query.filter.put(field, Document.of("$gt", value));
            return query;
        }

        public MongoQuery gte(Object value) {
            query.filter.put(field, Document.of("$gte", value));
            return query;
        }

        public MongoQuery lt(Object value) {
            query.filter.put(field, Document.of("$lt", value));
            return query;
        }

        public MongoQuery lte(Object value) {
            query.filter.put(field, Document.of("$lte", value));
            return query;
        }

        public MongoQuery in(List<?> values) {
            query.filter.put(field, Document.of("$in", new ArrayList<>(values)));
            return query;
        }

        public MongoQuery nin(List<?> values) {
            query.filter.put(field, Document.of("$nin", new ArrayList<>(values)));
            return query;
        }

        public MongoQuery exists() {
            query.filter.put(field, Document.of("$exists", true));
            return query;
        }

        public MongoQuery notExists() {
            query.filter.put(field, Document.of("$exists", false));
            return query;
        }

        public MongoQuery regex(String pattern) {
            query.filter.put(field, Document.of("$regex", pattern));
            return query;
        }
    }

    @Override
    public String toString() {
        return "MongoQuery" + filter.toString();
    }
}

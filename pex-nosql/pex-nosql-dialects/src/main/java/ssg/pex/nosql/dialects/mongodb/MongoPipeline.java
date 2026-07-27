package ssg.pex.nosql.dialects.mongodb;

import ssg.pex.nosql.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for MongoDB aggregation pipelines.
 *
 * <p>Example:
 * <pre>{@code
 * MongoPipeline.match(MongoQuery.gt("age", 20))
 *              .group("$dept", "count", MongoPipeline.sum(1))
 *              .sort("count", -1)
 *              .limit(10)
 * }</pre>
 */
public final class MongoPipeline {

    private final List<Document> stages = new ArrayList<>();

    private MongoPipeline() {}

    // ── Stage factories ───────────────────────────────────────────────────────

    /** Begins a pipeline with a {@code $match} stage. */
    public static MongoPipeline match(MongoQuery filter) {
        return new MongoPipeline().andMatch(filter);
    }

    /** Begins a pipeline with a {@code $match} stage using a raw Document. */
    public static MongoPipeline match(Document filter) {
        return new MongoPipeline().addMatchDoc(filter);
    }

    // ── Chaining ──────────────────────────────────────────────────────────────

    /** Adds a {@code $match} stage (instance chaining). */
    public MongoPipeline andMatch(MongoQuery filter) { return addMatch(filter); }

    public MongoPipeline sort(String field, int direction) {
        Document sortDoc = new Document();
        sortDoc.put(field, direction);
        Document stage = new Document();
        stage.put("$sort", sortDoc);
        stages.add(stage);
        return this;
    }

    public MongoPipeline limit(int n) {
        Document stage = new Document();
        stage.put("$limit", n);
        stages.add(stage);
        return this;
    }

    public MongoPipeline skip(int n) {
        Document stage = new Document();
        stage.put("$skip", n);
        stages.add(stage);
        return this;
    }

    /**
     * Adds a {@code $group} stage.
     *
     * @param idExpr   the group-by expression (e.g. {@code "$dept"} or {@code null})
     * @param field    output field name for the accumulator
     * @param accumulator the accumulator document (e.g. {@code MongoPipeline.sum(1)})
     */
    public MongoPipeline group(String idExpr, String field, Document accumulator) {
        Document groupSpec = new Document();
        groupSpec.put("_id", idExpr);
        groupSpec.put(field, accumulator);
        Document stage = new Document();
        stage.put("$group", groupSpec);
        stages.add(stage);
        return this;
    }

    /**
     * Adds a {@code $group} stage with multiple accumulators.
     */
    public MongoPipeline group(String idExpr, Document... fieldAccumulators) {
        Document groupSpec = new Document();
        groupSpec.put("_id", idExpr);
        for (int i = 0; i < fieldAccumulators.length - 1; i += 2) {
            // fieldAccumulators[i] = field name doc, [i+1] = accumulator doc
            // We expect them as pairs: name, accum, name, accum
        }
        // Alternative: accept varargs as field-accumulator pairs
        for (Document fa : fieldAccumulators) {
            for (java.util.Map.Entry<String, Object> e : fa.entrySet()) {
                groupSpec.put(e.getKey(), e.getValue());
            }
        }
        Document stage = new Document();
        stage.put("$group", groupSpec);
        stages.add(stage);
        return this;
    }

    public MongoPipeline project(Document spec) {
        Document stage = new Document();
        stage.put("$project", spec);
        stages.add(stage);
        return this;
    }

    public MongoPipeline unwind(String fieldRef) {
        Document stage = new Document();
        stage.put("$unwind", fieldRef);
        stages.add(stage);
        return this;
    }

    public MongoPipeline addFields(Document spec) {
        Document stage = new Document();
        stage.put("$addFields", spec);
        stages.add(stage);
        return this;
    }

    public MongoPipeline count(String fieldName) {
        Document stage = new Document();
        stage.put("$count", fieldName);
        stages.add(stage);
        return this;
    }

    public MongoPipeline lookup(String from, String localField, String foreignField, String as) {
        Document lookupSpec = new Document();
        lookupSpec.put("from", from);
        lookupSpec.put("localField", localField);
        lookupSpec.put("foreignField", foreignField);
        lookupSpec.put("as", as);
        Document stage = new Document();
        stage.put("$lookup", lookupSpec);
        stages.add(stage);
        return this;
    }

    // ── Accumulator helpers ───────────────────────────────────────────────────

    public static Document sum(Object expr) {
        return Document.of("$sum", expr);
    }

    public static Document avg(Object expr) {
        return Document.of("$avg", expr);
    }

    public static Document min(Object expr) {
        return Document.of("$min", expr);
    }

    public static Document max(Object expr) {
        return Document.of("$max", expr);
    }

    public static Document push(Object expr) {
        return Document.of("$push", expr);
    }

    public static Document first(Object expr) {
        return Document.of("$first", expr);
    }

    public static Document last(Object expr) {
        return Document.of("$last", expr);
    }

    /** Returns the pipeline as a list of stage documents. */
    public List<Document> toList() {
        return List.copyOf(stages);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MongoPipeline addMatch(MongoQuery filter) {
        Document stage = new Document();
        stage.put("$match", filter.toDocument());
        stages.add(stage);
        return this;
    }

    private MongoPipeline addMatchDoc(Document filter) {
        Document stage = new Document();
        stage.put("$match", filter);
        stages.add(stage);
        return this;
    }

    @Override
    public String toString() {
        return "MongoPipeline" + stages.toString();
    }
}

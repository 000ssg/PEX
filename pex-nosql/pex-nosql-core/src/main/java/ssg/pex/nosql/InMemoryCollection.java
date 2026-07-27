package ssg.pex.nosql;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link NoSqlCollection}.
 *
 * <p>Thread-safety: uses a {@link CopyOnWriteArrayList} for document storage so reads
 * don't block writes. Mutations are synchronized on {@code this}.
 */
public final class InMemoryCollection implements NoSqlCollection {

    private final String name;
    private final CopyOnWriteArrayList<Document> documents = new CopyOnWriteArrayList<>();

    /** Index metadata: field name → IndexEntry. */
    private final Map<String, IndexEntry> indexes = new LinkedHashMap<>();

    /** Index data: field name → set of field values (for unique enforcement). */
    private final Map<String, Set<Object>> uniqueIndexValues = new LinkedHashMap<>();

    /** TTL map: document _id → expiry timestamp in ms (epoch). */
    private final Map<Object, Long> ttlMap = new LinkedHashMap<>();

    private final InMemoryNoSqlDatabase database;
    private final AggregationEngine aggregationEngine;

    public InMemoryCollection(String name, InMemoryNoSqlDatabase database) {
        this.name = name;
        this.database = database;
        this.aggregationEngine = new AggregationEngine(database);
        // _id index is always unique
        indexes.put(Document.ID_FIELD, IndexEntry.unique(Document.ID_FIELD));
        uniqueIndexValues.put(Document.ID_FIELD, new LinkedHashSet<>());
    }

    @Override
    public String name() {
        return name;
    }

    // ── Insert ────────────────────────────────────────────────────────────────

    @Override
    public synchronized WriteResult insertOne(Document doc) {
        Document toInsert = prepareInsert(doc);
        documents.add(toInsert);
        return WriteResult.inserted(1, List.of(toInsert.id()));
    }

    @Override
    public synchronized WriteResult insertMany(List<Document> docs) {
        List<Object> ids = new ArrayList<>(docs.size());
        for (Document d : docs) {
            Document toInsert = prepareInsert(d);
            documents.add(toInsert);
            ids.add(toInsert.id());
        }
        return WriteResult.inserted(docs.size(), ids);
    }

    private Document prepareInsert(Document doc) {
        Document copy = doc.copy();
        // Assign _id if missing
        if (copy.id() == null) {
            copy.put(Document.ID_FIELD, UUID.randomUUID().toString());
        }
        // Enforce unique _id
        Object id = copy.id();
        Set<Object> idSet = uniqueIndexValues.get(Document.ID_FIELD);
        if (idSet.contains(id)) {
            throw new NoSqlException("NOSQL_DUPLICATE_KEY",
                    "Duplicate key for _id: " + id + " in collection: " + name);
        }
        idSet.add(id);
        // Enforce other unique indexes
        for (Map.Entry<String, IndexEntry> idxEntry : indexes.entrySet()) {
            String field = idxEntry.getKey();
            if (Document.ID_FIELD.equals(field)) continue;
            if (idxEntry.getValue().unique()) {
                Object val = copy.get(field);
                if (val != null) {
                    Set<Object> vals = uniqueIndexValues.computeIfAbsent(field, k -> new LinkedHashSet<>());
                    if (vals.contains(val)) {
                        throw new NoSqlException("NOSQL_DUPLICATE_KEY",
                                "Duplicate key for field '" + field + "': " + val);
                    }
                    vals.add(val);
                }
            }
        }
        return copy;
    }

    // ── Find ─────────────────────────────────────────────────────────────────

    @Override
    public FindResult find() {
        return new ListFindResult(liveDocuments());
    }

    @Override
    public FindResult find(Document filter) {
        List<Document> matched = liveDocuments().stream()
                .filter(d -> QueryEvaluator.matches(filter, d))
                .toList();
        return new ListFindResult(matched);
    }

    @Override
    public Optional<Document> findOne(Document filter) {
        return liveDocuments().stream()
                .filter(d -> QueryEvaluator.matches(filter, d))
                .findFirst()
                .map(Document::copy);
    }

    @Override
    public long count() {
        return liveDocuments().size();
    }

    @Override
    public long count(Document filter) {
        return liveDocuments().stream()
                .filter(d -> QueryEvaluator.matches(filter, d))
                .count();
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Override
    public synchronized WriteResult updateOne(Document filter, Document update) {
        List<Document> live = liveDocuments();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            if (isExpired(doc)) continue;
            if (QueryEvaluator.matches(filter, doc)) {
                Document updated = UpdateEvaluator.apply(update, doc);
                removeFromUniqueIndexes(doc);
                addToUniqueIndexes(updated);
                documents.set(i, updated);
                return WriteResult.modified(1);
            }
        }
        return WriteResult.none();
    }

    @Override
    public synchronized WriteResult updateMany(Document filter, Document update) {
        int matched = 0;
        int modified = 0;
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            if (isExpired(doc)) continue;
            if (QueryEvaluator.matches(filter, doc)) {
                matched++;
                Document updated = UpdateEvaluator.apply(update, doc);
                removeFromUniqueIndexes(doc);
                addToUniqueIndexes(updated);
                documents.set(i, updated);
                modified++;
            }
        }
        return new WriteResult(matched, modified, 0, 0, List.of());
    }

    @Override
    public synchronized WriteResult replaceOne(Document filter, Document replacement) {
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            if (isExpired(doc)) continue;
            if (QueryEvaluator.matches(filter, doc)) {
                Document copy = replacement.copy();
                // Preserve original _id
                if (copy.id() == null) {
                    copy.put(Document.ID_FIELD, doc.id());
                }
                removeFromUniqueIndexes(doc);
                addToUniqueIndexes(copy);
                documents.set(i, copy);
                return WriteResult.modified(1);
            }
        }
        return WriteResult.none();
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    public synchronized WriteResult deleteOne(Document filter) {
        Iterator<Document> it = documents.iterator();
        int idx = 0;
        for (Document doc : documents) {
            if (isExpired(doc) || QueryEvaluator.matches(filter, doc)) {
                documents.remove(doc);
                removeFromUniqueIndexes(doc);
                ttlMap.remove(doc.id());
                return WriteResult.deleted(1);
            }
        }
        return WriteResult.none();
    }

    @Override
    public synchronized WriteResult deleteMany(Document filter) {
        List<Document> toRemove = documents.stream()
                .filter(d -> isExpired(d) || QueryEvaluator.matches(filter, d))
                .toList();
        for (Document d : toRemove) {
            documents.remove(d);
            removeFromUniqueIndexes(d);
            ttlMap.remove(d.id());
        }
        return WriteResult.deleted(toRemove.size());
    }

    // ── Distinct ──────────────────────────────────────────────────────────────

    @Override
    public List<Object> distinct(String field) {
        return liveDocuments().stream()
                .map(d -> QueryEvaluator.resolveField(field, d))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Object> distinct(String field, Document filter) {
        return liveDocuments().stream()
                .filter(d -> QueryEvaluator.matches(filter, d))
                .map(d -> QueryEvaluator.resolveField(field, d))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    // ── Aggregate ─────────────────────────────────────────────────────────────

    @Override
    public List<Document> aggregate(List<Document> pipeline) {
        return aggregationEngine.execute(liveDocuments(), pipeline);
    }

    // ── Index ─────────────────────────────────────────────────────────────────

    @Override
    public synchronized void createIndex(Document keys, boolean unique) {
        for (Map.Entry<String, Object> e : keys.entrySet()) {
            String field = e.getKey();
            IndexEntry entry = new IndexEntry(field, unique);
            indexes.put(field, entry);
            if (unique) {
                Set<Object> vals = new LinkedHashSet<>();
                for (Document doc : liveDocuments()) {
                    Object v = doc.get(field);
                    if (v != null) {
                        if (!vals.add(v)) {
                            throw new NoSqlException("NOSQL_DUPLICATE_KEY",
                                    "Cannot create unique index on '" + field + "': duplicate value " + v);
                        }
                    }
                }
                uniqueIndexValues.put(field, vals);
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public synchronized void drop() {
        documents.clear();
        uniqueIndexValues.values().forEach(Set::clear);
        ttlMap.clear();
    }

    @Override
    public long estimatedDocumentCount() {
        return documents.size(); // includes expired; for estimation only
    }

    // ── TTL support ───────────────────────────────────────────────────────────

    /**
     * Stores TTL metadata for a document (keyed by _id).
     *
     * @param id        the document's _id
     * @param ttlMillis how long the document lives in milliseconds from now
     */
    public synchronized void setTtl(Object id, long ttlMillis) {
        ttlMap.put(id, System.currentTimeMillis() + ttlMillis);
    }

    private boolean isExpired(Document doc) {
        Object id = doc.id();
        if (id == null) return false;
        Long expiry = ttlMap.get(id);
        return expiry != null && System.currentTimeMillis() > expiry;
    }

    /** Returns only non-expired documents. */
    private List<Document> liveDocuments() {
        return documents.stream()
                .filter(d -> !isExpired(d))
                .toList();
    }

    // ── Index helpers ─────────────────────────────────────────────────────────

    private void removeFromUniqueIndexes(Document doc) {
        for (Map.Entry<String, IndexEntry> e : indexes.entrySet()) {
            if (e.getValue().unique()) {
                Set<Object> vals = uniqueIndexValues.get(e.getKey());
                if (vals != null) {
                    vals.remove(doc.get(e.getKey()));
                }
            }
        }
    }

    private void addToUniqueIndexes(Document doc) {
        for (Map.Entry<String, IndexEntry> e : indexes.entrySet()) {
            if (e.getValue().unique()) {
                Object val = doc.get(e.getKey());
                if (val != null) {
                    Set<Object> vals = uniqueIndexValues.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>());
                    vals.add(val);
                }
            }
        }
    }
}

package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class InMemoryCollectionTest {

    private InMemoryNoSqlDatabase db;
    private InMemoryCollection col;

    @BeforeEach
    void setUp() {
        db = new InMemoryNoSqlDatabase();
        col = (InMemoryCollection) db.getCollection("users");
    }

    @Test
    void name() {
        assertThat(col.name()).isEqualTo("users");
    }

    // ── Insert ─────────────────────────────────────────────────

    @Test
    void insertOneGeneratesId() {
        Document doc = Document.of("name", "Alice");
        WriteResult result = col.insertOne(doc);
        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.insertedIds()).hasSize(1);
        assertThat(col.count()).isEqualTo(1);
    }

    @Test
    void insertOneWithExistingId() {
        Document doc = Document.of("_id", "doc-1", "name", "Alice");
        WriteResult result = col.insertOne(doc);
        assertThat(result.insertedIds()).contains("doc-1");
    }

    @Test
    void insertOneDuplicateIdThrows() {
        col.insertOne(Document.of("_id", "doc-1", "name", "Alice"));
        assertThatThrownBy(() -> col.insertOne(Document.of("_id", "doc-1", "name", "Bob")))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Duplicate key");
    }

    @Test
    void insertOneOriginalNotMutated() {
        Document doc = Document.of("name", "Alice");
        col.insertOne(doc);
        assertThat(doc.containsKey("_id")).isFalse();
    }

    @Test
    void insertMany() {
        List<Document> docs = List.of(
                Document.of("name", "Alice"),
                Document.of("name", "Bob")
        );
        WriteResult result = col.insertMany(docs);
        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.insertedIds()).hasSize(2);
        assertThat(col.count()).isEqualTo(2);
    }

    @Test
    void insertManyDuplicateIdThrows() {
        col.insertOne(Document.of("_id", "doc-1"));
        List<Document> docs = List.of(
                Document.of("_id", "doc-2"),
                Document.of("_id", "doc-1")
        );
        assertThatThrownBy(() -> col.insertMany(docs))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Duplicate key");
    }

    // ── Find ───────────────────────────────────────────────────

    @Test
    void findReturnsAll() {
        col.insertOne(Document.of("name", "Alice"));
        col.insertOne(Document.of("name", "Bob"));
        FindResult result = col.find();
        assertThat(result.toList()).hasSize(2);
    }

    @Test
    void findWithFilter() {
        col.insertOne(Document.of("name", "Alice"));
        col.insertOne(Document.of("name", "Bob"));
        FindResult result = col.find(Document.of("name", "Alice"));
        assertThat(result.toList()).hasSize(1);
        assertThat(result.toList().get(0).get("name")).isEqualTo("Alice");
    }

    @Test
    void findOne() {
        col.insertOne(Document.of("name", "Alice"));
        col.insertOne(Document.of("name", "Bob"));
        Optional<Document> result = col.findOne(Document.of("name", "Alice"));
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().get("name")).isEqualTo("Alice");
    }

    @Test
    void findOneNotFound() {
        col.insertOne(Document.of("name", "Alice"));
        Optional<Document> result = col.findOne(Document.of("name", "Charlie"));
        assertThat(result.isPresent()).isFalse();
    }

    @Test
    void findOneReturnsCopy() {
        col.insertOne(Document.of("_id", "1", "name", "Alice"));
        Optional<Document> result = col.findOne(Document.of("name", "Alice"));
        result.get().put("extra", true);
        // Re-find should not have the extra field
        Optional<Document> again = col.findOne(Document.of("name", "Alice"));
        assertThat(again.get().containsKey("extra")).isFalse();
    }

    // ── Count ──────────────────────────────────────────────────

    @Test
    void count() {
        col.insertOne(Document.of("name", "Alice"));
        col.insertOne(Document.of("name", "Bob"));
        assertThat(col.count()).isEqualTo(2);
    }

    @Test
    void countWithFilter() {
        col.insertOne(Document.of("name", "Alice", "active", true));
        col.insertOne(Document.of("name", "Bob", "active", false));
        assertThat(col.count(Document.of("active", true))).isEqualTo(1);
    }

    // ── Update ─────────────────────────────────────────────────

    @Test
    void updateOne() {
        col.insertOne(Document.of("_id", "1", "name", "Alice"));
        WriteResult result = col.updateOne(
                Document.of("_id", "1"),
                Document.of("$set", Document.of("name", "Alicia"))
        );
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.modifiedCount()).isEqualTo(1);
        Optional<Document> found = col.findOne(Document.of("_id", "1"));
        assertThat(found.get().get("name")).isEqualTo("Alicia");
    }

    @Test
    void updateOneNoMatch() {
        col.insertOne(Document.of("_id", "1", "name", "Alice"));
        WriteResult result = col.updateOne(
                Document.of("_id", "999"),
                Document.of("$set", Document.of("name", "Bob"))
        );
        assertThat(result.matchedCount()).isZero();
        assertThat(result.modifiedCount()).isZero();
    }

    @Test
    void updateMany() {
        col.insertOne(Document.of("name", "Alice", "status", "active"));
        col.insertOne(Document.of("name", "Bob", "status", "active"));
        col.insertOne(Document.of("name", "Charlie", "status", "inactive"));
        WriteResult result = col.updateMany(
                Document.of("status", "active"),
                Document.of("$set", Document.of("status", "processed"))
        );
        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.modifiedCount()).isEqualTo(2);
    }

    @Test
    void replaceOne() {
        col.insertOne(Document.of("_id", "1", "name", "Alice", "age", 30));
        Document replacement = Document.of("_id", "1", "name", "Alicia");
        WriteResult result = col.replaceOne(Document.of("_id", "1"), replacement);
        assertThat(result.matchedCount()).isEqualTo(1);
        Optional<Document> found = col.findOne(Document.of("_id", "1"));
        assertThat(found.get().get("name")).isEqualTo("Alicia");
        assertThat(found.get()).doesNotContainKey("age");
    }

    @Test
    void replaceOneNoMatch() {
        col.insertOne(Document.of("_id", "1"));
        Document replacement = Document.of("_id", "2", "name", "Bob");
        WriteResult result = col.replaceOne(Document.of("_id", "999"), replacement);
        assertThat(result.matchedCount()).isZero();
    }

    // ── Delete ─────────────────────────────────────────────────

    @Test
    void deleteOne() {
        col.insertOne(Document.of("_id", "1", "name", "Alice"));
        col.insertOne(Document.of("_id", "2", "name", "Bob"));
        WriteResult result = col.deleteOne(Document.of("_id", "1"));
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(col.count()).isEqualTo(1);
    }

    @Test
    void deleteOneNoMatch() {
        col.insertOne(Document.of("_id", "1"));
        WriteResult result = col.deleteOne(Document.of("_id", "999"));
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    void deleteMany() {
        col.insertOne(Document.of("name", "Alice", "status", "active"));
        col.insertOne(Document.of("name", "Bob", "status", "active"));
        col.insertOne(Document.of("name", "Charlie", "status", "inactive"));
        WriteResult result = col.deleteMany(Document.of("status", "active"));
        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(col.count()).isEqualTo(1);
    }

    // ── Distinct ───────────────────────────────────────────────

    @Test
    void distinct() {
        col.insertOne(Document.of("city", "Helsinki"));
        col.insertOne(Document.of("city", "Tampere"));
        col.insertOne(Document.of("city", "Helsinki"));
        List<Object> result = col.distinct("city");
        assertThat(result).containsExactlyInAnyOrder("Helsinki", "Tampere");
    }

    @Test
    void distinctWithFilter() {
        col.insertOne(Document.of("city", "Helsinki", "active", true));
        col.insertOne(Document.of("city", "Tampere", "active", false));
        col.insertOne(Document.of("city", "Oulu", "active", true));
        List<Object> result = col.distinct("city", Document.of("active", true));
        assertThat(result).containsExactlyInAnyOrder("Helsinki", "Oulu");
    }

    // ── Index ──────────────────────────────────────────────────

    @Test
    void createIndex() {
        col.insertOne(Document.of("email", "a@b.com"));
        col.createIndex(Document.of("email", 1), false);
        // Should not throw
    }

    @Test
    void createUniqueIndexNoConflict() {
        col.insertOne(Document.of("email", "a@b.com"));
        col.insertOne(Document.of("email", "c@d.com"));
        col.createIndex(Document.of("email", 1), true);
        // Should succeed when values are unique
    }

    @Test
    void createUniqueIndexWithConflict() {
        col.insertOne(Document.of("email", "a@b.com"));
        col.insertOne(Document.of("email", "a@b.com"));
        assertThatThrownBy(() -> col.createIndex(Document.of("email", 1), true))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Cannot create unique index");
    }

    @Test
    void uniqueIndexEnforcedOnInsert() {
        col.createIndex(Document.of("email", 1), true);
        col.insertOne(Document.of("email", "a@b.com"));
        assertThatThrownBy(() -> col.insertOne(Document.of("email", "a@b.com")))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Duplicate key");
    }

    // ── Aggregate ──────────────────────────────────────────────

    @Test
    void aggregateEmptyPipeline() {
        col.insertOne(Document.of("name", "Alice"));
        List<Document> result = col.aggregate(List.of());
        assertThat(result).hasSize(1);
    }

    @Test
    void aggregateMatch() {
        col.insertOne(Document.of("name", "Alice", "status", "active"));
        col.insertOne(Document.of("name", "Bob", "status", "inactive"));
        List<Document> result = col.aggregate(List.of(
                Document.of("$match", Document.of("status", "active"))
        ));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("Alice");
    }

    // ── Drop ───────────────────────────────────────────────────

    @Test
    void drop() {
        col.insertOne(Document.of("name", "Alice"));
        col.drop();
        assertThat(col.count()).isZero();
    }

    // ── Estimated document count ───────────────────────────────

    @Test
    void estimatedDocumentCount() {
        col.insertOne(Document.of("name", "Alice"));
        col.insertOne(Document.of("name", "Bob"));
        assertThat(col.estimatedDocumentCount()).isEqualTo(2);
    }

    // ── TTL ────────────────────────────────────────────────────

    @Test
    void ttlExpiresDocument() throws InterruptedException {
        Document doc = Document.of("_id", "ttl-doc", "name", "Alice");
        col.insertOne(doc);
        col.setTtl("ttl-doc", 100); // 100ms TTL
        assertThat(col.count()).isEqualTo(1);
        Thread.sleep(150);
        assertThat(col.count()).isZero();
    }

    @Test
    void ttlNotExpired() {
        Document doc = Document.of("_id", "ttl-doc", "name", "Alice");
        col.insertOne(doc);
        col.setTtl("ttl-doc", 60_000); // 60 seconds
        assertThat(col.count()).isEqualTo(1);
    }
}

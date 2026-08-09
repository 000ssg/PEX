package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class AggregationEngineTest {

    private InMemoryNoSqlDatabase db;
    private AggregationEngine engine;
    private List<Document> docs;

    @BeforeEach
    void setUp() {
        db = new InMemoryNoSqlDatabase();
        engine = new AggregationEngine(db);
        docs = List.of(
                Document.of("_id", "1", "name", "Alice", "age", 30, "city", "Helsinki", "tags", List.of("java", "sql")),
                Document.of("_id", "2", "name", "Bob", "age", 25, "city", "Tampere", "tags", List.of("sql", "nosql")),
                Document.of("_id", "3", "name", "Charlie", "age", 35, "city", "Helsinki", "tags", List.of("nosql"))
        );
    }

    @Test
    void emptyPipeline() {
        List<Document> result = engine.execute(docs, List.of());
        assertThat(result).hasSize(3);
    }

    @Test
    void stageWithMultipleKeysThrows() {
        Document bad = new Document();
        bad.put("$match", Document.of("age", 30));
        bad.put("$sort", Document.of("name", 1));
        List<Document> pipeline = List.of(bad);
        assertThatThrownBy(() -> engine.execute(docs, pipeline))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("exactly one key");
    }

    @Test
    void unknownStageThrows() {
        List<Document> pipeline = List.of(Document.of("$unknown", "spec"));
        assertThatThrownBy(() -> engine.execute(docs, pipeline))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Unknown pipeline stage");
    }

    // ── $match ─────────────────────────────────────────────────

    @Test
    void match() {
        List<Document> pipeline = List.of(
                Document.of("$match", Document.of("city", "Helsinki"))
        );
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result).hasSize(2);
    }

    // ── $project ───────────────────────────────────────────────

    @Test
    void projectInclude() {
        List<Document> pipeline = List.of(
                Document.of("$project", Document.of("name", 1, "age", 1))
        );
        List<Document> result = engine.execute(docs, pipeline);
        for (Document d : result) {
            assertThat(d).containsKey("_id");
            assertThat(d).containsKey("name");
            assertThat(d).containsKey("age");
            assertThat(d).doesNotContainKey("city");
        }
    }

    @Test
    void projectExclude() {
        List<Document> pipeline = List.of(
                Document.of("$project", Document.of("tags", 0))
        );
        List<Document> result = engine.execute(docs, pipeline);
        for (Document d : result) {
            assertThat(d).doesNotContainKey("tags");
        }
    }

    // ── $sort ──────────────────────────────────────────────────

    @Test
    void sortAsc() {
        List<Document> pipeline = List.of(
                Document.of("$sort", Document.of("name", 1))
        );
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result.get(0).get("name")).isEqualTo("Alice");
        assertThat(result.get(2).get("name")).isEqualTo("Charlie");
    }

    @Test
    void sortDesc() {
        List<Document> pipeline = List.of(
                Document.of("$sort", Document.of("age", -1))
        );
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result.get(0).get("age")).isEqualTo(35);
        assertThat(result.get(2).get("age")).isEqualTo(25);
    }

    // ── $limit ─────────────────────────────────────────────────

    @Test
    void limit() {
        List<Document> pipeline = List.of(Document.of("$limit", 2));
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result).hasSize(2);
    }

    // ── $skip ──────────────────────────────────────────────────

    @Test
    void skip() {
        List<Document> pipeline = List.of(Document.of("$skip", 2));
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("Charlie");
    }

    // ── $group ─────────────────────────────────────────────────

    @Test
    void groupCount() {
        List<Document> pipeline = List.of(
                Document.of("$group", Document.of("_id", "$city", "total", Document.of("$sum", 1)))
        );
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result).hasSize(2);
        for (Document d : result) {
            assertThat(d.get("total")).isNotNull();
        }
    }

    // ── $unwind ────────────────────────────────────────────────

    @Test
    void unwind() {
        List<Document> pipeline = List.of(
                Document.of("$unwind", "$tags")
        );
        List<Document> result = engine.execute(docs, pipeline);
        // Alice has 2 tags, Bob has 2 tags, Charlie has 1 tag
        assertThat(result).hasSize(5);
    }

    // ── $count ─────────────────────────────────────────────────

    @Test
    void count() {
        List<Document> pipeline = List.of(Document.of("$count", "total"));
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("total")).isEqualTo(3L);
    }

    // ── $addFields ─────────────────────────────────────────────

    @Test
    void addFields() {
        List<Document> pipeline = List.of(
                Document.of("$addFields", Document.of("upperName", Document.of("$toUpper", "$name")))
        );
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result.get(0).get("upperName")).isEqualTo("ALICE");
    }

    // ── $lookup ────────────────────────────────────────────────

    @Test
    void lookup() {
        // Create a foreign collection
        InMemoryCollection orders = (InMemoryCollection) db.getCollection("orders");
        orders.insertOne(Document.of("_id", "o1", "userId", "1", "amount", 100));
        orders.insertOne(Document.of("_id", "o2", "userId", "1", "amount", 50));

        List<Document> pipeline = List.of(
                Document.of("$lookup", Document.of(
                        "from", "orders",
                        "localField", "_id",
                        "foreignField", "userId",
                        "as", "userOrders"
                ))
        );
        List<Document> result = engine.execute(docs, pipeline);
        Document alice = result.stream().filter(d -> d.get("_id").equals("1")).findFirst().get();
        assertThat(alice.get("userOrders")).isInstanceOf(List.class);
        assertThat((List<?>) alice.get("userOrders")).hasSize(2);

        Document charlie = result.stream().filter(d -> d.get("_id").equals("3")).findFirst().get();
        assertThat(charlie.get("userOrders")).isInstanceOf(List.class);
        assertThat((List<?>) charlie.get("userOrders")).isEmpty();
    }

    // ── Multi-stage pipeline ───────────────────────────────────

    @Test
    void multiStage() {
        List<Document> pipeline = List.of(
                Document.of("$match", Document.of("city", "Helsinki")),
                Document.of("$sort", Document.of("age", 1)),
                Document.of("$limit", 1),
                Document.of("$project", Document.of("name", 1, "age", 1))
        );
        List<Document> result = engine.execute(docs, pipeline);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("Alice");
    }
}

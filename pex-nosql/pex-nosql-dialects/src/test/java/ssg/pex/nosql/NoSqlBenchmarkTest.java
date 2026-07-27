package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import ssg.pex.nosql.dialects.mongodb.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Benchmark-style tests measuring throughput of core NoSQL operations.
 * Results are printed to stdout; assertions verify correctness and basic performance.
 */
@DisplayName("NoSQL Benchmark Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoSqlBenchmarkTest {

    private static final int BULK_COUNT = 10_000;
    private static final int QUERY_COUNT = 1_000;

    // ── Bulk Insert ───────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Bulk insert: 10k documents")
    void benchmarkBulkInsert() {
        var db = new InMemoryNoSqlDatabase();
        var col = db.getCollection("bench_insert");

        List<Document> docs = new ArrayList<>(BULK_COUNT);
        for (int i = 0; i < BULK_COUNT; i++) {
            docs.add(Document.of(
                    "_id", UUID.randomUUID().toString(),
                    "n", i,
                    "category", "cat" + (i % 10),
                    "value", Math.random() * 1000
            ));
        }

        long start = System.nanoTime();
        WriteResult result = col.insertMany(docs);
        long elapsed = System.nanoTime() - start;

        double ms = elapsed / 1_000_000.0;
        System.out.printf("[BENCHMARK] Bulk insert %d docs: %.2f ms (%.0f docs/sec)%n",
                BULK_COUNT, ms, BULK_COUNT / (elapsed / 1_000_000_000.0));

        assertThat(result.insertedCount()).isEqualTo(BULK_COUNT);
        assertThat(col.count()).isEqualTo(BULK_COUNT);
        assertThat(ms).isLessThan(5_000); // must complete within 5 seconds
        db.close();
    }

    // ── Filter Query ──────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Filter query: repeated equality filter on 10k documents")
    void benchmarkFilterQuery() {
        var db = new InMemoryNoSqlDatabase();
        var col = db.getCollection("bench_filter");

        List<Document> docs = new ArrayList<>(BULK_COUNT);
        for (int i = 0; i < BULK_COUNT; i++) {
            docs.add(Document.of("n", i, "category", "cat" + (i % 10)));
        }
        col.insertMany(docs);

        Document filter = Document.of("category", "cat5");

        long start = System.nanoTime();
        for (int i = 0; i < QUERY_COUNT; i++) {
            col.find(filter).toList();
        }
        long elapsed = System.nanoTime() - start;

        double ms = elapsed / 1_000_000.0;
        double qps = QUERY_COUNT / (elapsed / 1_000_000_000.0);
        System.out.printf("[BENCHMARK] Filter query x%d on %d docs: %.2f ms total (%.0f qps)%n",
                QUERY_COUNT, BULK_COUNT, ms, qps);

        long matchCount = col.count(filter);
        assertThat(matchCount).isEqualTo(1_000); // 10k / 10 categories = 1000 per category
        assertThat(ms).isLessThan(30_000);
        db.close();
    }

    // ── Aggregation ───────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("Aggregation: group by category and sum values on 10k documents")
    void benchmarkAggregation() {
        var db = new InMemoryNoSqlDatabase();
        var col = db.getCollection("bench_aggr");

        List<Document> docs = new ArrayList<>(BULK_COUNT);
        for (int i = 0; i < BULK_COUNT; i++) {
            docs.add(Document.of("category", "cat" + (i % 10), "value", (double) (i % 100)));
        }
        col.insertMany(docs);

        Document groupSpec = new Document();
        groupSpec.put("_id", "$category");
        groupSpec.put("total", Document.of("$sum", "$value"));
        groupSpec.put("count", Document.of("$sum", 1));
        List<Document> pipeline = List.of(Document.of("$group", groupSpec));

        long start = System.nanoTime();
        List<Document> result = col.aggregate(pipeline);
        long elapsed = System.nanoTime() - start;

        double ms = elapsed / 1_000_000.0;
        System.out.printf("[BENCHMARK] Aggregation (group+sum) on %d docs: %.2f ms%n", BULK_COUNT, ms);

        assertThat(result).hasSize(10);
        assertThat(ms).isLessThan(5_000);
        db.close();
    }

    // ── Index vs No-Index Lookup ───────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Index vs no-index: equality lookup on 10k documents")
    void benchmarkIndexVsNoIndex() {
        var db = new InMemoryNoSqlDatabase();
        var noIdxCol = db.getCollection("bench_no_idx");
        var idxCol = (InMemoryCollection) db.getCollection("bench_idx");

        List<Document> docs = new ArrayList<>(BULK_COUNT);
        for (int i = 0; i < BULK_COUNT; i++) {
            docs.add(Document.of("sku", "SKU-" + i, "category", "cat" + (i % 50)));
        }
        noIdxCol.insertMany(docs);
        idxCol.insertMany(docs);
        idxCol.createIndex(Document.of("sku", 1), true);

        Document filter = Document.of("sku", "SKU-5000");

        // No-index timing
        int noIdxRuns = 100;
        long startNoIdx = System.nanoTime();
        for (int i = 0; i < noIdxRuns; i++) {
            noIdxCol.find(filter).toList();
        }
        long noIdxMs = (System.nanoTime() - startNoIdx) / 1_000_000;

        // With index (still linear scan in this impl, but verifies correctness)
        long startIdx = System.nanoTime();
        for (int i = 0; i < noIdxRuns; i++) {
            idxCol.find(filter).toList();
        }
        long idxMs = (System.nanoTime() - startIdx) / 1_000_000;

        System.out.printf("[BENCHMARK] No-index lookup x%d: %d ms | With-index: %d ms%n",
                noIdxRuns, noIdxMs, idxMs);

        // Verify correctness
        assertThat(noIdxCol.find(filter).toList()).hasSize(1);
        assertThat(idxCol.find(filter).toList()).hasSize(1);
        db.close();
    }

    // ── MongoDB API Throughput ─────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("MongoDB API: MongoQuery builder bulk operations")
    void benchmarkMongoApiThroughput() {
        var db = new MongoDbDatabase();
        var col = db.getMongoCollection("bench_mongo");

        int insertCount = 5_000;
        List<Document> docs = new ArrayList<>(insertCount);
        for (int i = 0; i < insertCount; i++) {
            docs.add(Document.of("i", i, "type", i % 5 == 0 ? "special" : "normal"));
        }

        long insertStart = System.nanoTime();
        col.insertMany(docs);
        long insertMs = (System.nanoTime() - insertStart) / 1_000_000;

        long queryStart = System.nanoTime();
        long special = col.countDocuments(MongoQuery.eq("type", "special"));
        long queryMs = (System.nanoTime() - queryStart) / 1_000_000;

        System.out.printf("[BENCHMARK] MongoDB insert %d docs: %d ms, count query: %d ms%n",
                insertCount, insertMs, queryMs);

        assertThat(special).isEqualTo(insertCount / 5);
        assertThat(insertMs).isLessThan(10_000);
        db.close();
    }

    // ── Sort on large dataset ──────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Sort: order 10k documents by numeric field")
    void benchmarkSort() {
        var db = new InMemoryNoSqlDatabase();
        var col = db.getCollection("bench_sort");

        List<Document> docs = new ArrayList<>(BULK_COUNT);
        for (int i = 0; i < BULK_COUNT; i++) {
            docs.add(Document.of("score", BULK_COUNT - i)); // reverse order
        }
        col.insertMany(docs);

        long start = System.nanoTime();
        List<Document> sorted = col.find().sort("score", 1).toList();
        long elapsed = System.nanoTime() - start;

        double ms = elapsed / 1_000_000.0;
        System.out.printf("[BENCHMARK] Sort %d docs: %.2f ms%n", BULK_COUNT, ms);

        assertThat(sorted).hasSize(BULK_COUNT);
        // First should be score=1, last score=10000
        int first = ((Number) sorted.get(0).get("score")).intValue();
        int last = ((Number) sorted.get(BULK_COUNT - 1).get("score")).intValue();
        assertThat(first).isLessThan(last);
        assertThat(ms).isLessThan(5_000);
        db.close();
    }
}

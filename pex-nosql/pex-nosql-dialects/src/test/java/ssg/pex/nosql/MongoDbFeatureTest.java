package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import ssg.pex.nosql.dialects.mongodb.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * MongoDB-specific API tests: MongoQuery builder, MongoUpdate builder, MongoPipeline builder.
 * Covers 30+ scenarios including nested documents, array operations, and aggregation stages.
 */
@DisplayName("MongoDB Feature Tests")
class MongoDbFeatureTest {

    private MongoDbDatabase db;
    private MongoCollection col;

    @BeforeEach
    void setup() {
        db = new MongoDbDatabase();
        col = db.getMongoCollection("users");
        col.insertMany(List.of(
                Document.of("name", "Alice", "age", 30, "dept", "Engineering", "tags", List.of("java", "kotlin")),
                Document.of("name", "Bob", "age", 25, "dept", "Engineering", "tags", List.of("python")),
                Document.of("name", "Carol", "age", 35, "dept", "Sales", "tags", List.of("crm", "excel")),
                Document.of("name", "Dave", "age", 28, "dept", "Sales", "tags", List.of("crm")),
                Document.of("name", "Eve", "age", 40, "dept", "HR", "tags", List.of("hr-tools"))
        ));
    }

    @AfterEach
    void teardown() {
        db.close();
    }

    // ── MongoQuery ────────────────────────────────────────────────────────────

    @Test void queryEmpty() {
        assertThat(col.find(MongoQuery.empty()).toList()).hasSize(5);
    }

    @Test void queryEq() {
        assertThat(col.countDocuments(MongoQuery.eq("dept", "Sales"))).isEqualTo(2);
    }

    @Test void queryNe() {
        assertThat(col.countDocuments(MongoQuery.ne("dept", "Engineering"))).isEqualTo(3);
    }

    @Test void queryGt() {
        assertThat(col.countDocuments(MongoQuery.gt("age", 30))).isEqualTo(2);
    }

    @Test void queryGte() {
        assertThat(col.countDocuments(MongoQuery.gte("age", 30))).isEqualTo(3);
    }

    @Test void queryLt() {
        assertThat(col.countDocuments(MongoQuery.lt("age", 30))).isEqualTo(2);
    }

    @Test void queryLte() {
        assertThat(col.countDocuments(MongoQuery.lte("age", 30))).isEqualTo(3);
    }

    @Test void queryIn() {
        assertThat(col.countDocuments(MongoQuery.in("dept", List.of("Sales", "HR")))).isEqualTo(3);
    }

    @Test void queryNin() {
        assertThat(col.countDocuments(MongoQuery.nin("dept", List.of("Engineering")))).isEqualTo(3);
    }

    @Test void queryExists() {
        assertThat(col.countDocuments(MongoQuery.exists("tags"))).isEqualTo(5);
    }

    @Test void queryNotExists() {
        assertThat(col.countDocuments(MongoQuery.notExists("salary"))).isEqualTo(5);
    }

    @Test void queryRegex() {
        assertThat(col.countDocuments(MongoQuery.regex("name", "^A"))).isEqualTo(1);
    }

    @Test void queryAnd() {
        var q = MongoQuery.and(MongoQuery.eq("dept", "Engineering"), MongoQuery.gt("age", 25));
        assertThat(col.countDocuments(q)).isEqualTo(1); // only Alice age=30
    }

    @Test void queryOr() {
        var q = MongoQuery.or(MongoQuery.eq("name", "Alice"), MongoQuery.eq("name", "Eve"));
        assertThat(col.countDocuments(q)).isEqualTo(2);
    }

    @Test void queryNot() {
        var q = MongoQuery.not(MongoQuery.eq("dept", "Engineering"));
        assertThat(col.countDocuments(q)).isEqualTo(3);
    }

    @Test void queryFluentWhere() {
        var q = MongoQuery.where("age").gt(28).and("dept").eq("Engineering");
        assertThat(col.countDocuments(q)).isEqualTo(1); // Alice
    }

    @Test void queryFindOne() {
        var doc = col.findOne(MongoQuery.eq("name", "Carol"));
        assertThat(doc).isPresent();
        assertThat(doc.get().get("dept")).isEqualTo("Sales");
    }

    @Test void queryNestedField() {
        var nested = db.getMongoCollection("nested");
        nested.insertOne(Document.of("address", Document.of("city", "Helsinki")));
        nested.insertOne(Document.of("address", Document.of("city", "Stockholm")));
        assertThat(nested.countDocuments(MongoQuery.eq("address.city", "Helsinki"))).isEqualTo(1);
    }

    // ── MongoUpdate ───────────────────────────────────────────────────────────

    @Test void updateSet() {
        col.updateOne(MongoQuery.eq("name", "Bob"), MongoUpdate.set("age", 26));
        var doc = col.findOne(MongoQuery.eq("name", "Bob")).orElseThrow();
        assertThat(doc.get("age")).isEqualTo(26);
    }

    @Test void updateUnset() {
        col.updateOne(MongoQuery.eq("name", "Alice"), MongoUpdate.unset("dept"));
        var doc = col.findOne(MongoQuery.eq("name", "Alice")).orElseThrow();
        assertThat(doc.containsKey("dept")).isFalse();
    }

    @Test void updateInc() {
        col.updateOne(MongoQuery.eq("name", "Alice"), MongoUpdate.inc("age", 1));
        var doc = col.findOne(MongoQuery.eq("name", "Alice")).orElseThrow();
        assertThat(((Number) doc.get("age")).intValue()).isEqualTo(31);
    }

    @SuppressWarnings("unchecked")
    @Test void updatePush() {
        col.updateOne(MongoQuery.eq("name", "Bob"), MongoUpdate.push("tags", "java"));
        var doc = col.findOne(MongoQuery.eq("name", "Bob")).orElseThrow();
        assertThat((List<Object>) doc.get("tags")).contains("java");
    }

    @SuppressWarnings("unchecked")
    @Test void updatePull() {
        col.updateOne(MongoQuery.eq("name", "Alice"), MongoUpdate.pull("tags", "kotlin"));
        var doc = col.findOne(MongoQuery.eq("name", "Alice")).orElseThrow();
        assertThat((List<Object>) doc.get("tags")).doesNotContain("kotlin");
    }

    @SuppressWarnings("unchecked")
    @Test void updateAddToSet() {
        col.updateOne(MongoQuery.eq("name", "Bob"), MongoUpdate.addToSet("tags", "python"));
        var doc = col.findOne(MongoQuery.eq("name", "Bob")).orElseThrow();
        // "python" already present — should not duplicate
        long pythonCount = ((List<Object>) doc.get("tags")).stream().filter("python"::equals).count();
        assertThat(pythonCount).isEqualTo(1);
    }

    @Test void updateRename() {
        col.updateOne(MongoQuery.eq("name", "Dave"), MongoUpdate.rename("dept", "department"));
        var doc = col.findOne(MongoQuery.eq("name", "Dave")).orElseThrow();
        assertThat(doc.containsKey("department")).isTrue();
        assertThat(doc.containsKey("dept")).isFalse();
    }

    @Test void updateMany() {
        var result = col.updateMany(MongoQuery.eq("dept", "Engineering"), MongoUpdate.set("active", true));
        assertThat(result.modifiedCount()).isEqualTo(2);
        assertThat(col.countDocuments(MongoQuery.eq("active", true))).isEqualTo(2);
    }

    @Test void updateChained() {
        col.updateOne(MongoQuery.eq("name", "Alice"),
                MongoUpdate.set("status", "senior").andInc("age", 1));
        var doc = col.findOne(MongoQuery.eq("name", "Alice")).orElseThrow();
        assertThat(doc.get("status")).isEqualTo("senior");
        assertThat(((Number) doc.get("age")).intValue()).isEqualTo(31);
    }

    // ── MongoPipeline ─────────────────────────────────────────────────────────

    @Test void pipelineMatch() {
        var result = col.aggregate(MongoPipeline.match(MongoQuery.eq("dept", "Engineering")));
        assertThat(result).hasSize(2);
    }

    @Test void pipelineSortAndLimit() {
        var result = col.aggregate(
                MongoPipeline.match(MongoQuery.empty()).sort("age", 1).limit(3));
        assertThat(result).hasSize(3);
        assertThat(((Number) result.get(0).get("age")).intValue()).isLessThanOrEqualTo(
                ((Number) result.get(2).get("age")).intValue());
    }

    @Test void pipelineGroupByDept() {
        var result = col.aggregate(
                MongoPipeline.match(MongoQuery.empty())
                             .group("$dept", "count", MongoPipeline.sum(1)));
        assertThat(result).hasSize(3);
    }

    @Test void pipelineGroupSum() {
        var result = col.aggregate(
                MongoPipeline.match(MongoQuery.empty())
                             .group("$dept", "totalAge", MongoPipeline.sum("$age")));
        assertThat(result).hasSize(3);
        result.forEach(r -> assertThat(r.get("totalAge")).isInstanceOf(Number.class));
    }

    @Test void pipelineGroupAvg() {
        var result = col.aggregate(
                MongoPipeline.match(MongoQuery.empty())
                             .group("$dept", "avgAge", MongoPipeline.avg("$age")));
        assertThat(result).hasSize(3);
    }

    @Test void pipelineUnwind() {
        var result = col.aggregate(
                MongoPipeline.match(MongoQuery.empty()).unwind("$tags"));
        // Alice(2 tags) + Bob(1) + Carol(2) + Dave(1) + Eve(1) = 7
        assertThat(result).hasSize(7);
    }

    @Test void pipelineCountStage() {
        var result = col.aggregate(
                MongoPipeline.match(MongoQuery.eq("dept", "Engineering")).count("total"));
        assertThat(result).hasSize(1);
        assertThat(((Number) result.get(0).get("total")).longValue()).isEqualTo(2L);
    }

    @Test void pipelineLookup() {
        var orders = db.getMongoCollection("orders");
        orders.insertOne(Document.of("userId", "u1", "amount", 100));
        orders.insertOne(Document.of("userId", "u2", "amount", 200));

        var people = db.getMongoCollection("people");
        people.insertOne(Document.of("_id", "u1", "name", "Alice"));
        people.insertOne(Document.of("_id", "u2", "name", "Bob"));

        var result = orders.aggregate(
                MongoPipeline.match(MongoQuery.empty())
                             .lookup("people", "userId", "_id", "person"));
        assertThat(result).hasSize(2);
        result.forEach(r -> assertThat((List<?>) r.get("person")).hasSize(1));
    }

    @Test void pipelineSkip() {
        var result = col.aggregate(MongoPipeline.match(MongoQuery.empty()).skip(3));
        assertThat(result).hasSize(2);
    }

    @Test void pipelineAddFields() {
        var result = col.aggregate(
                MongoPipeline.match(MongoQuery.empty())
                             .addFields(Document.of("senior", Document.of("$gte", List.of("$age", 30)))));
        assertThat(result).hasSize(5);
    }
}

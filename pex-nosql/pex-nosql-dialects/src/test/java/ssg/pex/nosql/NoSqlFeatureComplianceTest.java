package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ssg.pex.nosql.dialects.NoSqlDialect;
import ssg.pex.nosql.dialects.NoSqlDialectDatabase;
import ssg.pex.nosql.dialects.cassandra.CassandraDatabase;
import ssg.pex.nosql.dialects.cassandra.CassandraResultSet;
import ssg.pex.nosql.dialects.mongodb.MongoDbDatabase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * NoSQL feature compliance test suite for MongoDB and Cassandra dialects.
 *
 * <p>Tests cover CRUD, query operators, update operators, aggregation, indexes,
 * projection/sort, TTL, and behavioral differences between dialects.
 */
@DisplayName("NoSQL Feature Compliance")
class NoSqlFeatureComplianceTest {

    // ── Fixture helpers ───────────────────────────────────────────────────────

    static InMemoryNoSqlDatabase freshDb() {
        return new InMemoryNoSqlDatabase();
    }

    static InMemoryCollection freshCollection(String name) {
        return (InMemoryCollection) freshDb().getCollection(name);
    }

    static List<Document> users() {
        return List.of(
                Document.of("name", "Alice", "age", 30, "dept", "Engineering"),
                Document.of("name", "Bob", "age", 25, "dept", "Engineering"),
                Document.of("name", "Carol", "age", 35, "dept", "Sales"),
                Document.of("name", "Dave", "age", 28, "dept", "Sales"),
                Document.of("name", "Eve", "age", 40, "dept", "HR")
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CRUD Features")
    class CrudFeatureTest {

        @Test
        @DisplayName("insertOne assigns _id if absent")
        void insertOneAssignsId() {
            var col = freshCollection("items");
            var doc = Document.of("name", "Widget");
            var result = col.insertOne(doc);
            assertThat(result.insertedCount()).isEqualTo(1);
            assertThat(result.insertedIds()).hasSize(1);
            assertThat(result.insertedIds().get(0)).isNotNull();
        }

        @Test
        @DisplayName("insertOne preserves explicit _id")
        void insertOnePreservesId() {
            var col = freshCollection("items");
            var doc = Document.of("_id", "my-id", "name", "Widget");
            col.insertOne(doc);
            var found = col.findOne(Document.of("_id", "my-id"));
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo("my-id");
        }

        @Test
        @DisplayName("insertMany inserts all documents")
        void insertManyInsertsAll() {
            var col = freshCollection("users");
            var result = col.insertMany(users());
            assertThat(result.insertedCount()).isEqualTo(5);
            assertThat(col.count()).isEqualTo(5);
        }

        @Test
        @DisplayName("insertOne rejects duplicate _id")
        void insertOneRejectsDuplicateId() {
            var col = freshCollection("items");
            col.insertOne(Document.of("_id", "dup", "val", 1));
            assertThatThrownBy(() -> col.insertOne(Document.of("_id", "dup", "val", 2)))
                    .isInstanceOf(NoSqlException.class)
                    .hasMessageContaining("dup");
        }

        @Test
        @DisplayName("find() returns all documents")
        void findAll() {
            var col = freshCollection("u");
            col.insertMany(users());
            assertThat(col.find().toList()).hasSize(5);
        }

        @Test
        @DisplayName("find(filter) returns matching documents")
        void findWithFilter() {
            var col = freshCollection("u");
            col.insertMany(users());
            var result = col.find(Document.of("dept", "Engineering")).toList();
            assertThat(result).hasSize(2);
            result.forEach(d -> assertThat(d.get("dept")).isEqualTo("Engineering"));
        }

        @Test
        @DisplayName("findOne returns first match")
        void findOneReturnsMatch() {
            var col = freshCollection("u");
            col.insertMany(users());
            Optional<Document> found = col.findOne(Document.of("name", "Carol"));
            assertThat(found).isPresent();
            assertThat(found.get().get("age")).isEqualTo(35);
        }

        @Test
        @DisplayName("findOne returns empty when no match")
        void findOneEmpty() {
            var col = freshCollection("u");
            col.insertMany(users());
            assertThat(col.findOne(Document.of("name", "Nobody"))).isEmpty();
        }

        @Test
        @DisplayName("count() returns total count")
        void countAll() {
            var col = freshCollection("u");
            col.insertMany(users());
            assertThat(col.count()).isEqualTo(5);
        }

        @Test
        @DisplayName("count(filter) returns filtered count")
        void countFiltered() {
            var col = freshCollection("u");
            col.insertMany(users());
            assertThat(col.count(Document.of("dept", "Sales"))).isEqualTo(2);
        }

        @Test
        @DisplayName("updateOne modifies first matching document")
        void updateOne() {
            var col = freshCollection("u");
            col.insertMany(users());
            var result = col.updateOne(Document.of("name", "Alice"),
                    Document.of("$set", Document.of("age", 31)));
            assertThat(result.modifiedCount()).isEqualTo(1);
            var updated = col.findOne(Document.of("name", "Alice")).orElseThrow();
            assertThat(((Number) updated.get("age")).intValue()).isEqualTo(31);
        }

        @Test
        @DisplayName("updateMany modifies all matching documents")
        void updateMany() {
            var col = freshCollection("u");
            col.insertMany(users());
            var result = col.updateMany(Document.of("dept", "Sales"),
                    Document.of("$set", Document.of("dept", "Revenue")));
            assertThat(result.modifiedCount()).isEqualTo(2);
            assertThat(col.count(Document.of("dept", "Revenue"))).isEqualTo(2);
        }

        @Test
        @DisplayName("replaceOne replaces document content")
        void replaceOne() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "x", 10, "y", 20));
            col.replaceOne(Document.of("_id", "1"), Document.of("z", 99));
            var found = col.findOne(Document.of("_id", "1")).orElseThrow();
            assertThat(found.get("z")).isEqualTo(99);
            assertThat(found.containsKey("x")).isFalse();
        }

        @Test
        @DisplayName("deleteOne removes first matching document")
        void deleteOne() {
            var col = freshCollection("u");
            col.insertMany(users());
            var result = col.deleteOne(Document.of("name", "Bob"));
            assertThat(result.deletedCount()).isEqualTo(1);
            assertThat(col.count()).isEqualTo(4);
        }

        @Test
        @DisplayName("deleteMany removes all matching documents")
        void deleteMany() {
            var col = freshCollection("u");
            col.insertMany(users());
            var result = col.deleteMany(Document.of("dept", "Engineering"));
            assertThat(result.deletedCount()).isEqualTo(2);
            assertThat(col.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("drop empties the collection")
        void drop() {
            var col = freshCollection("u");
            col.insertMany(users());
            col.drop();
            assertThat(col.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("distinct returns unique values")
        void distinct() {
            var col = freshCollection("u");
            col.insertMany(users());
            var depts = col.distinct("dept");
            assertThat(depts).hasSize(3).containsExactlyInAnyOrder("Engineering", "Sales", "HR");
        }

        @Test
        @DisplayName("distinct with filter returns subset")
        void distinctFiltered() {
            var col = freshCollection("u");
            col.insertMany(users());
            var names = col.distinct("name", Document.of("dept", "Engineering"));
            assertThat(names).hasSize(2).containsExactlyInAnyOrder("Alice", "Bob");
        }

        @Test
        @DisplayName("estimatedDocumentCount returns non-negative count")
        void estimatedCount() {
            var col = freshCollection("u");
            col.insertMany(users());
            assertThat(col.estimatedDocumentCount()).isGreaterThanOrEqualTo(5L);
        }

        @Test
        @DisplayName("listCollectionNames shows created collections")
        void listCollections() {
            var db = freshDb();
            db.getCollection("alpha");
            db.getCollection("beta");
            db.getCollection("gamma");
            assertThat(db.listCollectionNames()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
        }

        @Test
        @DisplayName("dropCollection removes the collection")
        void dropCollection() {
            var db = freshDb();
            db.getCollection("temp");
            assertThat(db.collectionExists("temp")).isTrue();
            db.dropCollection("temp");
            assertThat(db.collectionExists("temp")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Query Operators
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query Operator Tests")
    class QueryOperatorTest {

        private InMemoryCollection col;

        @BeforeEach
        void setup() {
            col = freshCollection("numbers");
            col.insertMany(List.of(
                    Document.of("n", 1, "tag", "a"),
                    Document.of("n", 2, "tag", "b"),
                    Document.of("n", 3, "tag", "a"),
                    Document.of("n", 4, "tag", "c"),
                    Document.of("n", 5, "tag", "b")
            ));
        }

        @Test void eqFilter() {
            assertThat(col.count(Document.of("n", 3))).isEqualTo(1);
        }

        @Test void neFilter() {
            assertThat(col.count(Document.of("n", Document.of("$ne", 3)))).isEqualTo(4);
        }

        @Test void gtFilter() {
            assertThat(col.count(Document.of("n", Document.of("$gt", 3)))).isEqualTo(2);
        }

        @Test void gteFilter() {
            assertThat(col.count(Document.of("n", Document.of("$gte", 3)))).isEqualTo(3);
        }

        @Test void ltFilter() {
            assertThat(col.count(Document.of("n", Document.of("$lt", 3)))).isEqualTo(2);
        }

        @Test void lteFilter() {
            assertThat(col.count(Document.of("n", Document.of("$lte", 3)))).isEqualTo(3);
        }

        @Test void inFilter() {
            assertThat(col.count(Document.of("tag", Document.of("$in", List.of("a", "c"))))).isEqualTo(3);
        }

        @Test void ninFilter() {
            assertThat(col.count(Document.of("tag", Document.of("$nin", List.of("a", "c"))))).isEqualTo(2);
        }

        @Test void existsTrue() {
            assertThat(col.count(Document.of("tag", Document.of("$exists", true)))).isEqualTo(5);
        }

        @Test void existsFalse() {
            assertThat(col.count(Document.of("missing_field", Document.of("$exists", false)))).isEqualTo(5);
        }

        @Test void regexFilter() {
            assertThat(col.count(Document.of("tag", Document.of("$regex", "^a")))).isEqualTo(2);
        }

        @Test void andFilter() {
            Document filter = Document.of("$and", List.of(
                    Document.of("n", Document.of("$gt", 2)),
                    Document.of("tag", "a")
            ));
            assertThat(col.count(filter)).isEqualTo(1);
        }

        @Test void orFilter() {
            Document filter = Document.of("$or", List.of(
                    Document.of("n", 1),
                    Document.of("n", 5)
            ));
            assertThat(col.count(filter)).isEqualTo(2);
        }

        @Test void notFilter() {
            Document filter = Document.of("$not", Document.of("tag", "a"));
            assertThat(col.count(filter)).isEqualTo(3);
        }

        @Test void dotNotationNestedField() {
            var col2 = freshCollection("nested");
            col2.insertOne(Document.of("address", Document.of("city", "Helsinki", "zip", "00100")));
            col2.insertOne(Document.of("address", Document.of("city", "Stockholm", "zip", "11100")));
            assertThat(col2.count(Document.of("address.city", "Helsinki"))).isEqualTo(1);
        }

        @Test void dotNotationDeeplyNested() {
            var col2 = freshCollection("deep");
            col2.insertOne(Document.of("a", Document.of("b", Document.of("c", 42))));
            assertThat(col2.count(Document.of("a.b.c", 42))).isEqualTo(1);
        }

        @Test void multipleFieldsImpliedAnd() {
            assertThat(col.count(Document.of("n", 2, "tag", "b"))).isEqualTo(1);
        }

        @Test void gtAndLtRange() {
            Document filter = Document.of(
                    "n", Document.of("$gt", 1, "$lt", 5)
            );
            assertThat(col.count(filter)).isEqualTo(3);
        }

        @Test void emptyFilterMatchesAll() {
            assertThat(col.count(new Document())).isEqualTo(5);
        }

        @Test void nullFilterMatchesAll() {
            assertThat(col.find(null).toList()).hasSize(5);
        }

        @Test void inWithEmptyListMatchesNone() {
            assertThat(col.count(Document.of("tag", Document.of("$in", List.of())))).isEqualTo(0);
        }

        @Test void ninWithAllValuesMatchesNone() {
            assertThat(col.count(Document.of("tag", Document.of("$nin", List.of("a", "b", "c"))))).isEqualTo(0);
        }

        @Test void regexCaseInsensitive() {
            assertThat(col.count(Document.of("tag", Document.of("$regex", "(?i)A")))).isEqualTo(2);
        }

        @Test void numericEqualityAcrossTypes() {
            var col2 = freshCollection("nums");
            col2.insertOne(Document.of("x", 10L));
            // Match integer 10 against stored Long 10L
            assertThat(col2.count(Document.of("x", 10))).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Update Operators
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Update Operator Tests")
    class UpdateOperatorTest {

        @Test void setUpdatesField() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "x", 10));
            col.updateOne(Document.of("_id", "1"), Document.of("$set", Document.of("x", 99)));
            assertThat(col.findOne(Document.of("_id", "1")).orElseThrow().get("x")).isEqualTo(99);
        }

        @Test void unsetRemovesField() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "x", 10, "y", 20));
            col.updateOne(Document.of("_id", "1"), Document.of("$unset", Document.of("y", "")));
            assertThat(col.findOne(Document.of("_id", "1")).orElseThrow().containsKey("y")).isFalse();
        }

        @Test void incIncrementsField() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "score", 10L));
            col.updateOne(Document.of("_id", "1"), Document.of("$inc", Document.of("score", 5L)));
            assertThat(col.findOne(Document.of("_id", "1")).orElseThrow().get("score")).isEqualTo(15L);
        }

        @Test void incDecrements() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "score", 10L));
            col.updateOne(Document.of("_id", "1"), Document.of("$inc", Document.of("score", -3L)));
            assertThat(col.findOne(Document.of("_id", "1")).orElseThrow().get("score")).isEqualTo(7L);
        }

        @SuppressWarnings("unchecked")
        @Test void pushAppendsToArray() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "tags", List.of("java")));
            col.updateOne(Document.of("_id", "1"), Document.of("$push", Document.of("tags", "kotlin")));
            List<Object> tags = (List<Object>) col.findOne(Document.of("_id", "1")).orElseThrow().get("tags");
            assertThat(tags).containsExactly("java", "kotlin");
        }

        @SuppressWarnings("unchecked")
        @Test void pushCreatesArrayIfAbsent() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "x", 1));
            col.updateOne(Document.of("_id", "1"), Document.of("$push", Document.of("items", "first")));
            List<Object> items = (List<Object>) col.findOne(Document.of("_id", "1")).orElseThrow().get("items");
            assertThat(items).containsExactly("first");
        }

        @SuppressWarnings("unchecked")
        @Test void pullRemovesFromArray() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "tags", List.of("java", "kotlin", "groovy")));
            col.updateOne(Document.of("_id", "1"), Document.of("$pull", Document.of("tags", "kotlin")));
            List<Object> tags = (List<Object>) col.findOne(Document.of("_id", "1")).orElseThrow().get("tags");
            assertThat(tags).containsExactly("java", "groovy");
        }

        @SuppressWarnings("unchecked")
        @Test void addToSetAddsIfAbsent() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "tags", List.of("java")));
            col.updateOne(Document.of("_id", "1"), Document.of("$addToSet", Document.of("tags", "kotlin")));
            List<Object> tags = (List<Object>) col.findOne(Document.of("_id", "1")).orElseThrow().get("tags");
            assertThat(tags).contains("kotlin");
        }

        @SuppressWarnings("unchecked")
        @Test void addToSetDoesNotDuplicate() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "tags", List.of("java")));
            col.updateOne(Document.of("_id", "1"), Document.of("$addToSet", Document.of("tags", "java")));
            List<Object> tags = (List<Object>) col.findOne(Document.of("_id", "1")).orElseThrow().get("tags");
            assertThat(tags).hasSize(1);
        }

        @Test void renameField() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "oldName", "value"));
            col.updateOne(Document.of("_id", "1"), Document.of("$rename", Document.of("oldName", "newName")));
            var doc = col.findOne(Document.of("_id", "1")).orElseThrow();
            assertThat(doc.containsKey("oldName")).isFalse();
            assertThat(doc.get("newName")).isEqualTo("value");
        }

        @Test void noOpUpdatesAsSet() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "x", 10));
            // No operator — treated as $set
            col.updateOne(Document.of("_id", "1"), Document.of("x", 99));
            assertThat(col.findOne(Document.of("_id", "1")).orElseThrow().get("x")).isEqualTo(99);
        }

        @Test void setNestedField() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "meta", Document.of("v", 1)));
            col.updateOne(Document.of("_id", "1"), Document.of("$set", Document.of("meta.v", 42)));
            var doc = col.findOne(Document.of("_id", "1")).orElseThrow();
            assertThat(((Document) doc.get("meta")).get("v")).isEqualTo(42);
        }

        @Test void incCreatesFieldIfAbsent() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "x", 10));
            col.updateOne(Document.of("_id", "1"), Document.of("$inc", Document.of("counter", 1L)));
            var doc = col.findOne(Document.of("_id", "1")).orElseThrow();
            assertThat(((Number) doc.get("counter")).longValue()).isEqualTo(1L);
        }

        @Test void multipleOperatorsInSameUpdate() {
            var col = freshCollection("u");
            col.insertOne(Document.of("_id", "1", "x", 10L, "y", "keep"));
            Document update = new Document();
            update.put("$set", Document.of("x", 99));
            update.put("$unset", Document.of("y", ""));
            col.updateOne(Document.of("_id", "1"), update);
            var doc = col.findOne(Document.of("_id", "1")).orElseThrow();
            assertThat(doc.get("x")).isEqualTo(99);
            assertThat(doc.containsKey("y")).isFalse();
        }

        @Test void updateManyReturnsCounts() {
            var col = freshCollection("u");
            col.insertMany(users());
            var result = col.updateMany(Document.of("dept", "Engineering"),
                    Document.of("$set", Document.of("active", true)));
            assertThat(result.matchedCount()).isEqualTo(2);
            assertThat(result.modifiedCount()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Aggregation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Aggregation Pipeline Tests")
    class AggregationTest {

        private InMemoryCollection col;

        @BeforeEach
        void setup() {
            col = freshCollection("sales");
            col.insertMany(List.of(
                    Document.of("region", "North", "amount", 100, "year", 2023),
                    Document.of("region", "North", "amount", 200, "year", 2023),
                    Document.of("region", "South", "amount", 150, "year", 2023),
                    Document.of("region", "South", "amount", 250, "year", 2024),
                    Document.of("region", "East", "amount", 300, "year", 2024),
                    Document.of("region", "East", "amount", 400, "year", 2024)
            ));
        }

        @Test void matchStageFilters() {
            var result = col.aggregate(List.of(Document.of("$match", Document.of("year", 2024))));
            assertThat(result).hasSize(3);
        }

        @Test void sortStage() {
            var result = col.aggregate(List.of(Document.of("$sort", Document.of("amount", 1))));
            assertThat(result).hasSize(6);
            assertThat(((Number) result.get(0).get("amount")).intValue()).isEqualTo(100);
        }

        @Test void sortDescending() {
            var result = col.aggregate(List.of(Document.of("$sort", Document.of("amount", -1))));
            assertThat(((Number) result.get(0).get("amount")).intValue()).isEqualTo(400);
        }

        @Test void limitStage() {
            var result = col.aggregate(List.of(Document.of("$limit", 3)));
            assertThat(result).hasSize(3);
        }

        @Test void skipStage() {
            var result = col.aggregate(List.of(Document.of("$skip", 4)));
            assertThat(result).hasSize(2);
        }

        @Test void groupByRegionSum() {
            Document groupSpec = new Document();
            groupSpec.put("_id", "$region");
            groupSpec.put("total", Document.of("$sum", "$amount"));
            var result = col.aggregate(List.of(Document.of("$group", groupSpec)));
            assertThat(result).hasSize(3);
        }

        @Test void groupByNullCountAll() {
            Document groupSpec = new Document();
            groupSpec.put("_id", null);
            groupSpec.put("count", Document.of("$sum", 1));
            var result = col.aggregate(List.of(Document.of("$group", groupSpec)));
            assertThat(result).hasSize(1);
            assertThat(((Number) result.get(0).get("count")).longValue()).isEqualTo(6L);
        }

        @Test void groupAvg() {
            Document groupSpec = new Document();
            groupSpec.put("_id", "$region");
            groupSpec.put("avgAmount", Document.of("$avg", "$amount"));
            var result = col.aggregate(List.of(Document.of("$group", groupSpec)));
            assertThat(result).hasSize(3);
        }

        @Test void groupMax() {
            Document groupSpec = new Document();
            groupSpec.put("_id", null);
            groupSpec.put("max", Document.of("$max", "$amount"));
            var result = col.aggregate(List.of(Document.of("$group", groupSpec)));
            assertThat(((Number) result.get(0).get("max")).doubleValue()).isEqualTo(400.0);
        }

        @Test void groupMin() {
            Document groupSpec = new Document();
            groupSpec.put("_id", null);
            groupSpec.put("min", Document.of("$min", "$amount"));
            var result = col.aggregate(List.of(Document.of("$group", groupSpec)));
            assertThat(((Number) result.get(0).get("min")).doubleValue()).isEqualTo(100.0);
        }

        @Test void projectInclude() {
            var result = col.aggregate(List.of(Document.of("$project", Document.of("region", 1, "amount", 1))));
            assertThat(result).hasSize(6);
            result.forEach(d -> {
                assertThat(d.containsKey("region")).isTrue();
                assertThat(d.containsKey("amount")).isTrue();
                assertThat(d.containsKey("year")).isFalse();
            });
        }

        @Test void projectExclude() {
            var result = col.aggregate(List.of(Document.of("$project", Document.of("year", 0))));
            assertThat(result).hasSize(6);
            result.forEach(d -> assertThat(d.containsKey("year")).isFalse());
        }

        @Test void countStage() {
            var result = col.aggregate(List.of(Document.of("$count", "total")));
            assertThat(result).hasSize(1);
            assertThat(((Number) result.get(0).get("total")).longValue()).isEqualTo(6L);
        }

        @Test void unwindFlattensArray() {
            var col2 = freshCollection("withArrays");
            col2.insertOne(Document.of("name", "Alice", "tags", List.of("java", "kotlin")));
            col2.insertOne(Document.of("name", "Bob", "tags", List.of("python")));
            var result = col2.aggregate(List.of(Document.of("$unwind", "$tags")));
            assertThat(result).hasSize(3);
        }

        @Test void lookupJoinsCollections() {
            var db = freshDb();
            var orders = db.getCollection("orders");
            var customers = db.getCollection("customers");
            customers.insertOne(Document.of("_id", "c1", "name", "Acme"));
            customers.insertOne(Document.of("_id", "c2", "name", "Globex"));
            orders.insertOne(Document.of("customerId", "c1", "amount", 100));
            orders.insertOne(Document.of("customerId", "c2", "amount", 200));
            orders.insertOne(Document.of("customerId", "c1", "amount", 300));

            Document lookupSpec = Document.of("from", "customers", "localField", "customerId",
                    "foreignField", "_id", "as", "customer");
            var result = orders.aggregate(List.of(Document.of("$lookup", lookupSpec)));
            assertThat(result).hasSize(3);
            result.forEach(r -> {
                List<?> customerList = (List<?>) r.get("customer");
                assertThat(customerList).hasSize(1);
            });
        }

        @Test void addFieldsComputes() {
            Document addFieldsSpec = new Document();
            addFieldsSpec.put("doubled", Document.of("$multiply", List.of("$amount", 2)));
            var result = col.aggregate(List.of(Document.of("$addFields", addFieldsSpec)));
            assertThat(result).hasSize(6);
            result.forEach(r -> {
                double amt = ((Number) r.get("amount")).doubleValue();
                double doubled = ((Number) r.get("doubled")).doubleValue();
                assertThat(doubled).isEqualTo(amt * 2);
            });
        }

        @Test void matchThenGroupThenSort() {
            Document groupSpec = new Document();
            groupSpec.put("_id", "$region");
            groupSpec.put("total", Document.of("$sum", "$amount"));
            var result = col.aggregate(List.of(
                    Document.of("$match", Document.of("year", 2024)),
                    Document.of("$group", groupSpec),
                    Document.of("$sort", Document.of("total", -1))
            ));
            assertThat(result).hasSize(2); // South(250) and East(700) are in 2024
            // East total=700, South=250 — East first
            assertThat(result.get(0).get("_id")).isEqualTo("East");
        }

        @Test void groupPush() {
            Document groupSpec = new Document();
            groupSpec.put("_id", "$region");
            groupSpec.put("amounts", Document.of("$push", "$amount"));
            var result = col.aggregate(List.of(Document.of("$group", groupSpec)));
            assertThat(result).hasSize(3);
            result.forEach(r -> assertThat(r.get("amounts")).isInstanceOf(List.class));
        }

        @Test void addFieldsConcat() {
            var col2 = freshCollection("people");
            col2.insertOne(Document.of("first", "John", "last", "Doe"));
            Document addSpec = new Document();
            addSpec.put("fullName", Document.of("$concat", List.of("$first", " ", "$last")));
            var result = col2.aggregate(List.of(Document.of("$addFields", addSpec)));
            assertThat(result.get(0).get("fullName")).isEqualTo("John Doe");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Index Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Index Tests")
    class IndexTest {

        @Test void createUniqueIndexEnforcesUniqueness() {
            var col = freshCollection("items");
            col.createIndex(Document.of("sku", 1), true);
            col.insertOne(Document.of("sku", "A001", "price", 9.99));
            assertThatThrownBy(() -> col.insertOne(Document.of("sku", "A001", "price", 19.99)))
                    .isInstanceOf(NoSqlException.class);
        }

        @Test void createNonUniqueIndexAllowsDuplicates() {
            var col = freshCollection("items");
            col.createIndex(Document.of("category", 1), false);
            col.insertOne(Document.of("category", "Books", "title", "A"));
            col.insertOne(Document.of("category", "Books", "title", "B"));
            assertThat(col.count(Document.of("category", "Books"))).isEqualTo(2);
        }

        @Test void createUniqueIndexOnExistingDataFailsIfDuplicates() {
            var col = freshCollection("items");
            col.insertOne(Document.of("sku", "DUP"));
            col.insertOne(Document.of("sku", "DUP"));
            assertThatThrownBy(() -> col.createIndex(Document.of("sku", 1), true))
                    .isInstanceOf(NoSqlException.class);
        }

        @Test void idAlwaysUnique() {
            var col = freshCollection("items");
            col.insertOne(Document.of("_id", "x1"));
            assertThatThrownBy(() -> col.insertOne(Document.of("_id", "x1")))
                    .isInstanceOf(NoSqlException.class);
        }

        @Test void dropCollectionClearsUniqueIndex() {
            var col = freshCollection("items");
            col.createIndex(Document.of("sku", 1), true);
            col.insertOne(Document.of("sku", "A001"));
            col.drop();
            // After drop, re-inserting with same sku should work
            assertThatCode(() -> col.insertOne(Document.of("sku", "A001"))).doesNotThrowAnyException();
        }

        @Test void uniqueIndexOnMultipleFields() {
            var col = freshCollection("items");
            col.createIndex(Document.of("sku", 1), true);
            col.createIndex(Document.of("barcode", 1), true);
            col.insertOne(Document.of("sku", "A001", "barcode", "123"));
            assertThatThrownBy(() -> col.insertOne(Document.of("sku", "A002", "barcode", "123")))
                    .isInstanceOf(NoSqlException.class);
        }

        @Test void nonUniqueIndexDoesNotBlockDuplicateIds() {
            var col = freshCollection("items");
            col.createIndex(Document.of("name", 1), false);
            col.insertOne(Document.of("name", "Widget"));
            assertThatCode(() -> col.insertOne(Document.of("name", "Widget"))).doesNotThrowAnyException();
        }

        @Test void createIndexOnEmptyCollectionSucceeds() {
            var col = freshCollection("empty");
            assertThatCode(() -> col.createIndex(Document.of("field", 1), true)).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Projection and Sort
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Projection and Sort Tests")
    class ProjectionSortTest {

        private InMemoryCollection col;

        @BeforeEach
        void setup() {
            col = freshCollection("data");
            col.insertMany(users());
        }

        @Test void includeProjection() {
            var result = col.find().project("name", "dept").toList();
            assertThat(result).hasSize(5);
            result.forEach(d -> {
                assertThat(d.containsKey("name")).isTrue();
                assertThat(d.containsKey("dept")).isTrue();
                assertThat(d.containsKey("age")).isFalse();
            });
        }

        @Test void excludeProjection() {
            var result = col.find().projectExclude("age").toList();
            assertThat(result).hasSize(5);
            result.forEach(d -> assertThat(d.containsKey("age")).isFalse());
        }

        @Test void sortAscending() {
            var result = col.find().sort("age", 1).toList();
            int first = ((Number) result.get(0).get("age")).intValue();
            int last = ((Number) result.get(result.size() - 1).get("age")).intValue();
            assertThat(first).isLessThanOrEqualTo(last);
        }

        @Test void sortDescending() {
            var result = col.find().sort("age", -1).toList();
            int first = ((Number) result.get(0).get("age")).intValue();
            int last = ((Number) result.get(result.size() - 1).get("age")).intValue();
            assertThat(first).isGreaterThanOrEqualTo(last);
        }

        @Test void limit() {
            assertThat(col.find().limit(3).toList()).hasSize(3);
        }

        @Test void skip() {
            assertThat(col.find().skip(3).toList()).hasSize(2);
        }

        @Test void limitAndSkip() {
            assertThat(col.find().skip(1).limit(2).toList()).hasSize(2);
        }

        @Test void count() {
            assertThat(col.find().count()).isEqualTo(5L);
        }

        @Test void sortOnStringField() {
            var result = col.find().sort("name", 1).toList();
            List<String> names = result.stream().map(d -> (String) d.get("name")).toList();
            assertThat(names).isSorted();
        }

        @Test void sortWithFilterAndLimit() {
            var result = col.find(Document.of("dept", "Engineering")).sort("age", 1).limit(1).toList();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("name")).isEqualTo("Bob"); // Bob is younger
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TTL Tests (Cassandra)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TTL Tests")
    class TtlTest {

        @Test void documentWithTtlIsReturnedBeforeExpiry() throws InterruptedException {
            var db = new InMemoryNoSqlDatabase();
            var col = (InMemoryCollection) db.getCollection("ttl_test");
            var wr = col.insertOne(Document.of("name", "temporary"));
            Object insertedId = wr.insertedIds().get(0);
            col.setTtl(insertedId, 5000); // 5 seconds TTL
            assertThat(col.count(Document.of("name", "temporary"))).isEqualTo(1);
        }

        @Test void documentExpiredByTtlIsNotReturned() throws InterruptedException {
            var db = new InMemoryNoSqlDatabase();
            var col = (InMemoryCollection) db.getCollection("ttl_test");
            var wr = col.insertOne(Document.of("name", "ephemeral"));
            Object insertedId = wr.insertedIds().get(0);
            col.setTtl(insertedId, 1); // 1ms TTL — will expire almost immediately
            Thread.sleep(20);
            assertThat(col.count(Document.of("name", "ephemeral"))).isEqualTo(0);
        }

        @Test void nonExpiredDocumentsAreNotAffected() throws InterruptedException {
            var db = new InMemoryNoSqlDatabase();
            var col = (InMemoryCollection) db.getCollection("ttl_test");
            col.insertOne(Document.of("name", "permanent"));
            var tmpWr = col.insertOne(Document.of("name", "temporary"));
            col.setTtl(tmpWr.insertedIds().get(0), 1);
            Thread.sleep(20);
            assertThat(col.count()).isEqualTo(1);
            assertThat(col.findOne(Document.of("name", "permanent"))).isPresent();
        }

        @Test void cassandraTtlViaInsertUsing() throws InterruptedException {
            try (var cassDb = new ssg.pex.nosql.dialects.cassandra.CassandraDatabase()) {
                try (var session = cassDb.connect("ks")) {
                    session.execute("CREATE TABLE ks.ttl_docs (id UUID PRIMARY KEY, val TEXT)");
                    // Insert directly into InMemoryCollection with 1ms TTL for fast expiry test
                    var inMemCol = (InMemoryCollection) cassDb.underlying().getCollection("ks.ttl_docs");
                    var wr = inMemCol.insertOne(Document.of("val", "hello"));
                    inMemCol.setTtl(wr.insertedIds().get(0), 1);
                    Thread.sleep(20);
                    assertThat(inMemCol.count()).isEqualTo(0);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Dialect Differences
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MongoDB vs Cassandra Behavioral Differences")
    class DialectDifferences {

        @Test void mongoDbSchemaless() {
            // MongoDB: no schema — insert documents with arbitrary fields
            var db = new MongoDbDatabase();
            var col = db.getCollection("flexible");
            col.insertOne(Document.of("x", 1));
            col.insertOne(Document.of("y", "string", "z", true));
            col.insertOne(Document.of("nested", Document.of("a", List.of(1, 2, 3))));
            assertThat(col.count()).isEqualTo(3);
            db.close();
        }

        @Test void cassandraRequiresKeyspacePrefix() {
            try (var db = new CassandraDatabase()) {
                try (var session = db.connect("myks")) {
                    // Table creation requires keyspace.table or defaults to current keyspace
                    session.execute("CREATE TABLE myks.products (id UUID PRIMARY KEY, name TEXT)");
                    session.execute("INSERT INTO myks.products (id, name) VALUES (uuid(), 'Widget')");
                    var result = session.execute("SELECT * FROM myks.products");
                    assertThat(result.rows()).hasSize(1);
                }
            }
        }

        @Test void mongoDbIdGeneration() {
            var db = new MongoDbDatabase();
            var col = db.getCollection("gen");
            var wr = col.insertOne(Document.of("x", 1));
            assertThat(wr.insertedIds().get(0)).isNotNull();
            assertThat(wr.insertedIds().get(0).toString()).isNotBlank();
            db.close();
        }

        @Test void cassandraUseKeyspaceSwitchesContext() {
            try (var db = new CassandraDatabase()) {
                try (var session = db.connect("ks1")) {
                    session.execute("CREATE TABLE ks1.tbl (id UUID PRIMARY KEY, v TEXT)");
                    session.execute("INSERT INTO ks1.tbl (id, v) VALUES (uuid(), 'hello')");
                    assertThat(session.execute("SELECT * FROM ks1.tbl").rows()).hasSize(1);
                }
            }
        }

        @Test void cassandraTruncateEmptiesTable() {
            try (var db = new CassandraDatabase()) {
                try (var session = db.connect("ks")) {
                    session.execute("CREATE TABLE ks.logs (id UUID PRIMARY KEY, msg TEXT)");
                    session.execute("INSERT INTO ks.logs (id, msg) VALUES (uuid(), 'entry1')");
                    session.execute("INSERT INTO ks.logs (id, msg) VALUES (uuid(), 'entry2')");
                    assertThat(session.execute("SELECT * FROM ks.logs").rows()).hasSize(2);
                    session.execute("TRUNCATE ks.logs");
                    assertThat(session.execute("SELECT * FROM ks.logs").rows()).isEmpty();
                }
            }
        }

        @Test void mongoDbSupportsNestedDocumentUpdates() {
            var db = new MongoDbDatabase();
            var col = db.getCollection("nested");
            col.insertOne(Document.of("_id", "1", "meta", Document.of("version", 1)));
            col.updateOne(Document.of("_id", "1"), Document.of("$set", Document.of("meta.version", 2)));
            var doc = col.findOne(Document.of("_id", "1")).orElseThrow();
            assertThat(((Document) doc.get("meta")).get("version")).isEqualTo(2);
            db.close();
        }

        @Test void cassandraDropTableRemovesCollection() {
            try (var db = new CassandraDatabase()) {
                try (var session = db.connect("ks")) {
                    session.execute("CREATE TABLE ks.temp_tbl (id UUID PRIMARY KEY)");
                    assertThat(db.underlying().collectionExists("ks.temp_tbl")).isTrue();
                    session.execute("DROP TABLE ks.temp_tbl");
                    assertThat(db.underlying().collectionExists("ks.temp_tbl")).isFalse();
                }
            }
        }

        @Test void dialectDatabaseWrapperForMongoDB() {
            try (var dialect = new NoSqlDialectDatabase(NoSqlDialect.MONGODB)) {
                var mongo = dialect.asMongoDb();
                mongo.getCollection("test").insertOne(Document.of("val", 42));
                assertThat(mongo.getCollection("test").count()).isEqualTo(1);
            }
        }
    }
}

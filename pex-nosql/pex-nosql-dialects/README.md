# pex-nosql-dialects

Dialect layer for pex-nosql. Provides two NoSQL dialect implementations on top
of the `pex-nosql-core` engine:

1. **MongoDB dialect** — fluent Java API matching the MongoDB driver pattern
2. **Cassandra dialect** — CQL string-based query execution

---

## MongoDB Dialect

### Key Classes

| Class | Description |
|-------|-------------|
| `MongoDbDatabase` | Top-level database; wraps `InMemoryNoSqlDatabase`; exposes `getMongoCollection(name)` |
| `MongoCollection` | Fluent collection API: `insertOne`, `insertMany`, `find(MongoQuery)`, `updateOne(MongoQuery, MongoUpdate)`, `deleteMany(MongoQuery)`, `aggregate(MongoPipeline)`, etc. |
| `MongoQuery` | Builder for filter documents: `MongoQuery.eq("field", val)`, `gt`, `lt`, `in`, `and`, `or`, `regex` |
| `MongoUpdate` | Builder for update documents: `MongoUpdate.set("field", val)`, `unset`, `inc`, `push`, `pull` |
| `MongoPipeline` | Builder for aggregation pipelines: `MongoPipeline.match(filter).group(...)..sort(...).limit(n)` |
| `NoSqlDialectDatabase` | Unified wrapper that holds either `MongoDbDatabase` or `CassandraDatabase` based on `NoSqlDialect` enum; access via `asMongoDb()` or `asCassandra()` |

### MongoDB Usage Example

```java
// Direct MongoDbDatabase
var mongo = new MongoDbDatabase();
var users = mongo.getMongoCollection("users");

// Insert
users.insertOne(Document.of("name", "Alice", "dept", "Engineering", "score", 95));
users.insertMany(List.of(
    Document.of("name", "Bob",   "dept", "Sales",       "score", 72),
    Document.of("name", "Carol", "dept", "Engineering", "score", 88)
));

// Query with MongoQuery
var engineers = users.find(MongoQuery.eq("dept", "Engineering")).toList();
var highScorers = users.find(MongoQuery.gt("score", 80)).toList();
var combined = users.find(MongoQuery.and(
    MongoQuery.eq("dept", "Engineering"),
    MongoQuery.gte("score", 90)
)).toList();

// Update
users.updateMany(
    MongoQuery.eq("dept", "Sales"),
    MongoUpdate.set("region", "North")
);

// Aggregation pipeline
var pipeline = MongoPipeline.match(MongoQuery.gt("score", 70))
    .group("$dept", "avgScore", Document.of("$avg", "$score"))
    .sort(Document.of("avgScore", -1))
    .limit(5);
var results = users.aggregate(pipeline).toList();

// Via NoSqlDialectDatabase
try (var db = new NoSqlDialectDatabase(NoSqlDialect.MONGODB)) {
    var col = db.asMongoDb().getMongoCollection("products");
    col.insertOne(Document.of("sku", "ABC", "price", 9.99));
}
```

### Grammar File

`mongodb.ebnf` in `src/main/resources/grammar/` — covers the `MongoQuery` / `MongoUpdate` / `MongoPipeline` DSL including all operators and builder methods.

---

## Cassandra Dialect

### Key Classes

| Class | Description |
|-------|-------------|
| `CassandraDatabase` | Top-level database; wraps `InMemoryNoSqlDatabase`; provides `connect(keyspace)` → `CassandraSession` and `executor()` for raw CQL |
| `CassandraSession` | Per-keyspace session: `execute(cql)`, `prepare(cql)` → `PreparedStatement`, `executeBatch(String... cqls)` |
| `CassandraResultSet` | Query result: `rows()` as `List<Map<String,Object>>`, `one()`, `wasApplied()` |
| `CassandraExecutor` | Internal CQL parser and executor routing CREATE TABLE, INSERT, SELECT, UPDATE, DELETE to the in-memory engine |

### Cassandra Usage Example

```java
// Via CassandraDatabase + CassandraSession
var cass = new CassandraDatabase();
var session = cass.connect("inventory");

// DDL
session.execute("CREATE TABLE products (id INT PRIMARY KEY, name TEXT, price DOUBLE)");

// DML
session.execute("INSERT INTO products (id, name, price) VALUES (1, 'Widget', 9.99)");
session.execute("INSERT INTO products (id, name, price) VALUES (2, 'Gadget', 24.99)");

// SELECT
var result = session.execute("SELECT name, price FROM products WHERE id = 1");
var row = result.one();
System.out.println(row.get("name"));   // Widget

// SELECT with ALLOW FILTERING
var cheap = session.execute("SELECT * FROM products WHERE price < 15.0 ALLOW FILTERING");

// Prepared statement
var stmt = session.prepare("INSERT INTO products (id, name, price) VALUES (?, ?, ?)");
session.execute(stmt, 3, "Doohickey", 4.50);

// Batch CQL
session.executeBatch(
    "INSERT INTO products (id, name, price) VALUES (10, 'A', 1.0)",
    "INSERT INTO products (id, name, price) VALUES (11, 'B', 2.0)"
);

// Via NoSqlDialectDatabase
try (var db = new NoSqlDialectDatabase(NoSqlDialect.CASSANDRA)) {
    var cass2 = db.asCassandra();
    cass2.executor().execute("CREATE TABLE t (id INT PRIMARY KEY, v TEXT)");
}
```

### Grammar File

`cassandra.ebnf` in `src/main/resources/grammar/` — covers the full CQL grammar: DDL (`CREATE TABLE`, `DROP TABLE`), DML (`INSERT`, `UPDATE`, `DELETE`), query (`SELECT`, `WHERE`, `ALLOW FILTERING`, `LIMIT`), prepared statements, and batch statements.

---

## Test Coverage (184 tests total)

| Test Class | Tests | Coverage |
|------------|------:|---------|
| `MongoDbFeatureTest` | ~60 | CRUD, query operators, update operators, aggregation pipeline, collection management |
| `CassandraFeatureTest` | ~50 | DDL, DML, SELECT with filters, COUNT, prepared statements, batch, ALLOW FILTERING |
| `NoSqlFeatureComplianceTest` | ~60 | Cross-dialect compliance: both MONGODB and CASSANDRA variants |
| `NoSqlBenchmarkTest` | ~14 | Performance: 10k insert, filter query, aggregation, batch operations |

Run tests:

```bash
./gradlew :pex-nosql-dialects:test
mvn test -pl pex-nosql/pex-nosql-dialects
```

---

## Dependencies

- `pex-nosql-core`
- `pex-base`

## SPI Registration

`NoSqlDialectsPlugin` (loadOrder = 240) is registered in:

```
src/main/resources/META-INF/services/ssg.pex.spi.PexPlugin
```

Content: `ssg.pex.nosql.dialects.NoSqlDialectsPlugin`

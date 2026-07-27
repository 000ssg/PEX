# pex-nosql-core

In-memory NoSQL engine for PEX. Provides a `Document` model, collection-level
CRUD, a rich query evaluator, an update evaluator, and a multi-stage aggregation
pipeline — all without any external dependencies beyond `pex-base`.

---

## Key Classes

| Class | Description |
|-------|-------------|
| `Document` | Immutable key-value map representing one NoSQL document; created via `Document.of(key, val, ...)` or `Document.from(Map)` |
| `NoSqlDatabase` | Interface: `getCollection`, `createCollection`, `collectionExists`, `dropCollection`, `listCollectionNames`, `drop` |
| `InMemoryNoSqlDatabase` | Default `NoSqlDatabase` implementation; collections created lazily via `getCollection(name)` |
| `NoSqlCollection` | Interface: `insertOne`, `insertMany`, `find`, `findOne`, `updateOne`, `updateMany`, `replaceOne`, `deleteOne`, `deleteMany`, `count`, `distinct`, `aggregate` |
| `InMemoryCollection` | Thread-safe `NoSqlCollection` implementation backed by `ConcurrentHashMap` |
| `QueryEvaluator` | Evaluates filter `Document` against a stored document; supports 15 operators |
| `UpdateEvaluator` | Applies update `Document` to a stored document; supports 7 update operators |
| `AggregationEngine` | Executes a list of pipeline stage `Document` objects; supports 10 stages |
| `FindResult` | Lazy result of a `find()` call; supports `toList()`, `stream()`, `count()`, `first()` |
| `WriteResult` | Result of a write operation: `insertedCount`, `modifiedCount`, `deletedCount` |
| `NoSqlPlugin` | `PexPlugin` SPI entry point; loadOrder = 210 |

---

## Query Operators (QueryEvaluator — 15 operators)

| Operator | Meaning |
|----------|---------|
| `$eq` | Equal (also plain value match) |
| `$ne` | Not equal |
| `$gt` | Greater than |
| `$gte` | Greater than or equal |
| `$lt` | Less than |
| `$lte` | Less than or equal |
| `$in` | Value in list |
| `$nin` | Value not in list |
| `$exists` | Field existence check |
| `$type` | Field type check |
| `$regex` | Regular expression match |
| `$and` | Logical AND of sub-filters |
| `$or` | Logical OR of sub-filters |
| `$not` | Logical NOT of a sub-filter |
| `$nor` | Logical NOR of sub-filters |

---

## Update Operators (UpdateEvaluator — 7 operators)

| Operator | Meaning |
|----------|---------|
| `$set` | Set field value |
| `$unset` | Remove field |
| `$inc` | Increment numeric field |
| `$mul` | Multiply numeric field |
| `$rename` | Rename field |
| `$push` | Append to array field |
| `$pull` | Remove matching elements from array |

---

## Aggregation Pipeline Stages (AggregationEngine — 10 stages)

| Stage | Meaning |
|-------|---------|
| `$match` | Filter documents |
| `$project` | Reshape documents |
| `$group` | Group with accumulators (`$sum`, `$avg`, `$min`, `$max`, `$count`, `$push`, `$addToSet`) |
| `$sort` | Sort documents |
| `$limit` | Limit output count |
| `$skip` | Skip documents |
| `$unwind` | Deconstruct array field |
| `$lookup` | Left-join with another collection |
| `$addFields` | Add computed fields |
| `$count` | Count documents into a field |

---

## Grammar Files

| File | Path | Covers |
|------|------|--------|
| `nosql-query.ebnf` | `src/main/resources/grammar/` | Filter operators, dot-notation, value comparisons |
| `nosql-operations.ebnf` | `src/main/resources/grammar/` | CRUD operations, update operators, aggregation pipeline |

---

## Usage Example

```java
// Create a database and collection
var db = new InMemoryNoSqlDatabase();
var users = db.getCollection("users");

// Insert documents
users.insertOne(Document.of("name", "Alice", "age", 30, "dept", "Engineering"));
users.insertOne(Document.of("name", "Bob",   "age", 25, "dept", "Sales"));
users.insertMany(List.of(
    Document.of("name", "Carol", "age", 35, "dept", "Engineering"),
    Document.of("name", "Dave",  "age", 28, "dept", "Sales")
));

// Query with operators
var seniors = users.find(Document.of("age", Document.of("$gt", 27))).toList();

// Update
users.updateMany(
    Document.of("dept", "Engineering"),
    Document.of("$set", Document.of("level", "senior"))
);

// Aggregation pipeline
var stats = users.aggregate(List.of(
    Document.of("$match",  Document.of("dept", "Engineering")),
    Document.of("$group",  Document.of("_id", "$dept",
                                        "avgAge", Document.of("$avg", "$age"))),
    Document.of("$sort",   Document.of("avgAge", -1))
)).toList();

// Count
long count = users.count(Document.of("dept", "Sales"));
```

---

## Dependencies

- `pex-base` only — no SQL dependencies, no external libraries

## SPI Registration

`NoSqlPlugin` is registered in:

```
src/main/resources/META-INF/services/ssg.pex.spi.PexPlugin
```

Content: `ssg.pex.nosql.NoSqlPlugin`

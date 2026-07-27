# PEX NoSQL Feature Compliance Report

Updated on each commit. Tests: `NoSqlFeatureComplianceTest`, `MongoDbFeatureTest`, `CassandraFeatureTest`

Run command:
```bash
./gradlew :pex-nosql:pex-nosql-dialects:test
```

## Dialect Variants Under Test

| Dialect | Wrapper | Notes |
|---------|---------|-------|
| MONGODB | `MongoDbDatabase` | Document-oriented; schemaless; fluent query/update/pipeline API |
| CASSANDRA | `CassandraDatabase` + `CassandraSession` | Wide-column; CQL string-based; keyspace + table model |

---

## Feature Support Matrix

| Feature | MONGODB | CASSANDRA |
|---------|:-------:|:---------:|
| **CRUD** | | |
| Insert one document | ✓ | ✓ |
| Insert many documents | ✓ | ✓ |
| Find all | ✓ | ✓ |
| Find with filter | ✓ | ✓ |
| Find one | ✓ | ✓ |
| Update one | ✓ | ✓ |
| Update many | ✓ | ✓ |
| Replace one | ✓ | partial |
| Delete one | ✓ | ✓ |
| Delete many | ✓ | ✓ |
| Distinct values | ✓ | ✓ |
| Count (total) | ✓ | ✓ |
| Count (filtered) | ✓ | ✓ |
| **Query Operators** | | |
| `$eq` | ✓ | ✓ (CQL `=`) |
| `$ne` | ✓ | ✓ (CQL `!=`) |
| `$gt` | ✓ | ✓ (CQL `>`) |
| `$gte` | ✓ | ✓ (CQL `>=`) |
| `$lt` | ✓ | ✓ (CQL `<`) |
| `$lte` | ✓ | ✓ (CQL `<=`) |
| `$in` | ✓ | ✓ (CQL `IN`) |
| `$nin` | ✓ | ✗ |
| `$exists` | ✓ | ✗ |
| `$regex` | ✓ | ✗ |
| `$and` | ✓ | ✓ (CQL `AND`) |
| `$or` | ✓ | ✗ |
| `$not` | ✓ | ✗ |
| Dot notation (nested) | ✓ | ✗ |
| **Update Operators** | | |
| `$set` | ✓ | ✓ (CQL `SET`) |
| `$unset` | ✓ | ✓ (CQL `DELETE col FROM`) |
| `$inc` | ✓ | partial (via `$set`) |
| `$push` | ✓ | ✗ |
| `$pull` | ✓ | ✗ |
| `$addToSet` | ✓ | ✗ |
| `$rename` | ✓ | ✗ |
| **Aggregation Stages** | | |
| `$match` | ✓ | ✗ (use WHERE in CQL) |
| `$project` | ✓ | ✓ (SELECT col1, col2) |
| `$sort` | ✓ | partial (via FindResult) |
| `$limit` | ✓ | ✓ (CQL LIMIT) |
| `$skip` | ✓ | ✗ |
| `$group` | ✓ | ✗ |
| `$unwind` | ✓ | ✗ |
| `$lookup` | ✓ | ✗ |
| `$addFields` | ✓ | ✗ |
| `$count` | ✓ | ✓ (SELECT COUNT(*)) |
| **Indexes** | | |
| Unique index enforcement | ✓ | ✓ (via `CREATE INDEX ... UNIQUE`) |
| Non-unique index | ✓ | ✓ (via `CREATE INDEX`) |
| Sparse index (metadata) | ✓ | ✗ |
| _id always unique | ✓ | ✓ (PRIMARY KEY) |
| **Other** | | |
| TTL (document expiry) | ✓ (programmatic) | ✓ (USING TTL n) |
| Nested document queries | ✓ | ✗ |
| Array operations | ✓ | ✗ |
| Bulk insert | ✓ | ✓ (prepared statement loop) |
| DISTINCT values | ✓ | ✓ (SELECT DISTINCT) |
| COUNT(*) | ✓ | ✓ |
| Prepared statements | ✗ | ✓ |
| Batch execution | ✗ | ✓ (BEGIN BATCH / APPLY BATCH) |
| Schema enforcement | ✗ (schemaless) | ✓ (DDL required) |

---

## Behavioral Differences: MongoDB vs Cassandra

| Aspect | MongoDB | Cassandra |
|--------|---------|-----------|
| **Schema** | Schemaless — documents can have any fields | Schema required — `CREATE TABLE` must be executed before `INSERT` |
| **API style** | Java fluent API (`MongoQuery`, `MongoUpdate`, `MongoPipeline`) | CQL string execution via `CassandraSession.execute(String)` |
| **_id generation** | UUID string auto-generated if absent | No automatic _id; `uuid()` function in CQL generates a UUID |
| **Table model** | Single flat collection namespace | Keyspace + table hierarchy (`keyspace.table`) |
| **TTL semantics** | Programmatic via `InMemoryCollection.setTtl(id, ms)` | Declarative via `USING TTL n` (seconds) in INSERT/UPDATE |
| **Partition key requirement** | No concept of partition key | First PRIMARY KEY component = partition key (required for writes) |
| **Nested documents** | Natively supported (Document extends Map) | Not supported natively — flat row model |
| **Array operations** | `$push`, `$pull`, `$addToSet`, `$unwind` | Not supported — use lists only in application layer |
| **Aggregation** | Full pipeline API (`$group`, `$lookup`, etc.) | COUNT(*) only via SELECT; no grouping |
| **Transactions** | No transaction support in this in-memory impl | Simulated BATCH (not atomic) |
| **Prepared statements** | Not applicable (Java API) | Full prepared statement support with `?` binding |

---

## Test Results Summary

| Test Class | Test Count | Description |
|------------|-----------|-------------|
| `NoSqlFeatureComplianceTest` | ~110 | Full compliance matrix: CRUD, operators, aggregation, indexes, TTL, dialects |
| `MongoDbFeatureTest` | ~37 | MongoDB-specific: MongoQuery, MongoUpdate, MongoPipeline |
| `CassandraFeatureTest` | ~31 | CQL DDL, DML, prepared statements, batches, TTL, COUNT, DISTINCT |
| `NoSqlBenchmarkTest` | 6 | Throughput benchmarks: bulk insert 10k, filter, aggregation, sort |

Run with:
```bash
# All NoSQL tests
./gradlew :pex-nosql:pex-nosql-dialects:test

# Specific test class
./gradlew :pex-nosql:pex-nosql-dialects:test --tests "ssg.pex.nosql.NoSqlFeatureComplianceTest"
```

---

## Grammar Files

Each pex-nosql module ships EBNF grammar files alongside its implementation. These are the authoritative specification of the supported syntax; parsers must be kept in sync with them.

| File | Module | Description |
|------|--------|-------------|
| [`nosql-query.ebnf`](../pex-nosql/pex-nosql-core/src/main/resources/grammar/nosql-query.ebnf) | pex-nosql-core | Filter document operators: `$eq`, `$ne`, `$gt/gte/lt/lte`, `$in/$nin`, `$exists`, `$regex`, `$and/$or/$not`, dot-notation field paths |
| [`nosql-operations.ebnf`](../pex-nosql/pex-nosql-core/src/main/resources/grammar/nosql-operations.ebnf) | pex-nosql-core | CRUD operations, update operators (`$set/$unset/$inc/$push/$pull/$addToSet/$rename`), aggregation pipeline stages (`$match/$project/$sort/$limit/$skip/$group/$unwind/$lookup/$addFields/$count`) |
| [`mongodb.ebnf`](../pex-nosql/pex-nosql-dialects/src/main/resources/grammar/mongodb.ebnf) | pex-nosql-dialects | MongoDB dialect: `MongoQuery` fluent builder DSL, `MongoUpdate` fluent builder, `MongoPipeline` builder, `MongoCollection` API |
| [`cassandra.ebnf`](../pex-nosql/pex-nosql-dialects/src/main/resources/grammar/cassandra.ebnf) | pex-nosql-dialects | CQL grammar: `CREATE KEYSPACE/TABLE`, `INSERT/SELECT/UPDATE/DELETE`, `BEGIN BATCH … APPLY BATCH`, `USING TTL`, `CREATE INDEX`, all CQL types |

HTML railroad diagrams are auto-generated by running `docs/grammar/GenerateGrammarDocs.java` (targets: `nosql-query.html`, `nosql-operations.html`, `nosql-mongodb.html`, `nosql-cassandra.html`).

> **Rule**: whenever a new query operator, CQL statement, or MongoQuery/MongoUpdate method is added to the implementation, the corresponding `.ebnf` file **must** be updated in the same commit.

---

## Change History

| Date | Version | Change |
|------|---------|--------|
| 2026-06-28 | 1.0 | Initial pex-nosql module: MongoDB and Cassandra in-memory dialects |

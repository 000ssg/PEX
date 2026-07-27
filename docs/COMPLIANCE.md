# PEX SQL Feature Compliance Report

Updated on each commit. Tests: `SqlFeatureComplianceTest`

Run command:
```bash
mvn test -pl pex-sql/pex-sql-dialects -Dtest=SqlFeatureComplianceTest
```

## Dialect Variants Under Test

| Dialect | Wrapper | Notes |
|---------|---------|-------|
| CORE | `InMemoryDatabase` | No dialect layer; plain standard SQL only |
| POSTGRESQL | `DialectDatabase(POSTGRESQL)` | RETURNING, ON CONFLICT, ILIKE, `::` cast |
| MYSQL | `DialectDatabase(MYSQL)` | ON DUPLICATE KEY, REPLACE INTO, SHOW TABLES/DATABASES, DESCRIBE |
| ORACLE | `DialectDatabase(ORACLE)` | MERGE (via pex-sql-olap), NVL, ROWNUM, DUAL, sequences, GTT |
| MSSQL | `DialectDatabase(MSSQL)` | TOP N, `#temp`, ISNULL, DECLARE/@variables, PRINT |

## Feature Support Matrix

| Feature | CORE | POSTGRESQL | MYSQL | ORACLE | MSSQL |
|---------|:----:|:----------:|:-----:|:------:|:-----:|
| **DDL** | | | | | |
| CREATE TABLE | ✓ | ✓ | ✓ | ✓ | ✓ |
| DROP TABLE | ✓ | ✓ | ✓ | ✓ | ✓ |
| CREATE TEMP TABLE | ✓ (TEMP) | ✓ (TEMP) | ✓ (TEMP) | ✓ (GLOBAL TEMPORARY) | ✓ (#prefix) |
| **DML** | | | | | |
| INSERT | ✓ | ✓ | ✓ | ✓ | ✓ |
| UPDATE | ✓ | ✓ | ✓ | ✓ | ✓ |
| DELETE | ✓ | ✓ | ✓ | ✓ | ✓ |
| executeBatch(Stream) | ✓ | ✓ | ✓ | ✓ | ✓ |
| **SELECT** | | | | | |
| SELECT WHERE | ✓ | ✓ | ✓ | ✓ | ✓ |
| INNER JOIN (equi) | ✓ | ✓ | ✓ | ✓ | ✓ |
| GROUP BY | ✓ | ✓ | ✓ | ✓ | ✓ |
| ORDER BY | ✓ | ✓ | ✓ | ✓ | ✓ |
| HAVING (syntax) | ✓ | ✓ | ✓ | ✓ | ✓ |
| LIMIT / TOP / ROWNUM | ✓ (LIMIT) | ✓ (LIMIT) | ✓ (LIMIT) | ✓ (ROWNUM) | ✓ (TOP N) |
| **Dialect-specific DML** | | | | | |
| ON CONFLICT DO NOTHING | ✗ | ✓ | ✗ | ✗ | ✗ |
| ON CONFLICT DO UPDATE | ✗ | ✓ | ✗ | ✗ | ✗ |
| ON DUPLICATE KEY UPDATE | ✗ | ✗ | ✓ | ✗ | ✗ |
| REPLACE INTO | ✗ | ✗ | ✓ | ✗ | ✗ |
| INSERT … RETURNING | ✗ | ✓ | ✗ | ✗ | ✗ |
| MERGE INTO | ✗ | ✗ | ✗ | ✓ (pex-sql-olap) | ✓ (pex-sql-olap) |
| **Dialect functions** | | | | | |
| NVL() | ✗ | ✗ | ✗ | ✓ | ✗ |
| ISNULL() | ✗ | ✗ | ✗ | ✗ | ✓ |
| COALESCE() | ✓ | ✓ | ✓ | ✓ | ✓ |
| ILIKE | ✗ | ✓ | ✗ | ✗ | ✗ |
| `::` cast syntax | ✗ | ✓ | ✗ | ✗ | ✗ |
| SELECT FROM DUAL | ✗ | ✗ | ✗ | ✓ | ✗ |
| SHOW TABLES / DATABASES | ✗ | ✗ | ✓ | ✗ | ✗ |
| DESCRIBE / DESC | ✗ | ✗ | ✓ | ✗ | ✗ |
| **Optimizations** | | | | | |
| LRU parse cache | ✓ | ✓ | ✓ | ✓ | ✓ |
| Hash-join equi-join | ✓ | ✓ | ✓ | ✓ | ✓ |
| WHERE predicate filter | ✓ | ✓ | ✓ | ✓ | ✓ |

## Dialect Syntax Differences

| Feature | Detail |
|---------|--------|
| Upsert syntax | PostgreSQL: `ON CONFLICT … DO UPDATE/NOTHING`; MySQL: `ON DUPLICATE KEY UPDATE` / `REPLACE INTO`; Oracle/MSSQL: `MERGE INTO` (via pex-sql-olap) |
| NULL coalescing | Standard: `COALESCE`; Oracle: `NVL` / `NVL2`; MSSQL: `ISNULL` |
| Row limiting | Standard: `LIMIT n`; Oracle: `WHERE ROWNUM <= n`; MSSQL: `SELECT TOP n` |
| Temp tables | Standard: `CREATE TEMP TABLE`; Oracle: `CREATE GLOBAL TEMPORARY TABLE … ON COMMIT`; MSSQL: `CREATE TABLE #name` |
| Case-insensitive LIKE | PostgreSQL: `ILIKE`; others: `UPPER(col) LIKE UPPER(pattern)` |
| RETURNING clause | PostgreSQL only; other dialects receive INSERT count only |
| DUAL pseudo-table | Oracle only; other dialects must omit the FROM clause for scalar expressions |
| Type casting | PostgreSQL: `expr::TYPE`; standard: `CAST(expr AS TYPE)` |

## Known Limitations

| Limitation | Scope |
|------------|-------|
| `HAVING` with aggregate re-evaluation | HAVING clause is parsed and executed, but `HAVING SUM(col) > n` may not correctly filter because the aggregate value is not available as a column in the post-GROUP BY filter step. Use HAVING with literal comparisons (`HAVING 1 = 1`) or subqueries. |
| `MERGE INTO` | Available only through `OlapDatabase` (pex-sql-olap), not directly via `DialectDatabase`. |
| Window functions (`ROW_NUMBER OVER …`) | Available only through `OlapDatabase` (pex-sql-olap), not via `DialectDatabase`. |
| CTEs (`WITH … AS`) | Available only through `OlapDatabase` (pex-sql-olap), not via `DialectDatabase`. |
| `ROLLUP` / `CUBE` | Available only through `OlapDatabase` (pex-sql-olap). |

## Test Results (last run)

```
Tests run: 117, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Test class: `ssg.pex.sql.dialect.SqlFeatureComplianceTest`  
Location: `pex-sql/pex-sql-dialects/src/test/java/ssg/pex/sql/dialect/SqlFeatureComplianceTest.java`

### Test Distribution

| Nested Class | Tests | Description |
|-------------|------:|-------------|
| `DdlFeatureTest` | 15 | CREATE TABLE, DROP TABLE, CREATE TEMP TABLE across all 5 dialects |
| `DmlFeatureTest` | 20 | INSERT, UPDATE, DELETE, executeBatch(Stream) across all 5 dialects |
| `SelectFeatureTest` | 30 | WHERE, GROUP BY, ORDER BY, LIMIT/TOP/ROWNUM, JOIN, hash-join across all 5 dialects |
| `DialectDmlTest` | 12 | Dialect-specific: PostgreSQL RETURNING/ON CONFLICT/ILIKE, MySQL ON DUPLICATE KEY/REPLACE INTO, Oracle NVL/ROWNUM/DUAL, MSSQL TOP/#temp/ISNULL |
| `UnsupportedFeatureTest` | 5 | CORE: ON CONFLICT rejects, CORE: REPLACE INTO rejects, MSSQL: ON CONFLICT rejects, Oracle: ON CONFLICT rejects, Oracle: REPLACE INTO rejects |
| `OlapFeatureTest` | 20 | GROUP BY/SUM, GROUP BY/COUNT, HAVING (syntax), multi-column GROUP BY across all 5 dialects |
| `OptimizationCorrectnessTest` | 15 | Hash-join correctness, LRU parse cache consistency, WHERE filter on joined column across all 5 dialects |
| **Total** | **117** | |

## Grammar Files

SQL syntax is specified in EBNF grammar files co-located with each module's source. Parsers must match these files exactly.

| File | Module | Covers |
|------|--------|--------|
| [`ddl.ebnf`](../pex-sql/pex-sql-core/src/main/resources/grammar/ddl.ebnf) | pex-sql-core | `CREATE [TEMP/TEMPORARY/GLOBAL TEMPORARY] TABLE`, `CREATE INDEX/VIEW/TRIGGER/PROCEDURE`, `ALTER TABLE`, `DROP TABLE` |
| [`dml.ebnf`](../pex-sql/pex-sql-core/src/main/resources/grammar/dml.ebnf) | pex-sql-core | `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CALL`, joins, GROUP BY, ORDER BY, LIMIT |
| [`sql-expressions.ebnf`](../pex-sql/pex-sql-core/src/main/resources/grammar/sql-expressions.ebnf) | pex-sql-core | Expressions, operators, function calls, subqueries |
| [`transactions.ebnf`](../pex-sql/pex-sql-core/src/main/resources/grammar/transactions.ebnf) | pex-sql-core | `BEGIN/COMMIT/ROLLBACK`, `SAVEPOINT` |
| [`oracle.ebnf`](../pex-sql/pex-sql-dialects/src/main/resources/grammar/oracle.ebnf) | pex-sql-dialects | `CONNECT BY`, `ROWNUM`, `DUAL`, sequences, `MINUS` |
| [`mssql.ebnf`](../pex-sql/pex-sql-dialects/src/main/resources/grammar/mssql.ebnf) | pex-sql-dialects | `TOP N`, `#temp` tables, `ISNULL`, `DECLARE/@variables` |
| [`mysql.ebnf`](../pex-sql/pex-sql-dialects/src/main/resources/grammar/mysql.ebnf) | pex-sql-dialects | `REPLACE INTO`, `ON DUPLICATE KEY UPDATE`, `SHOW`, `DESCRIBE` |
| [`postgresql.ebnf`](../pex-sql/pex-sql-dialects/src/main/resources/grammar/postgresql.ebnf) | pex-sql-dialects | `ON CONFLICT`, `RETURNING`, `::` cast, `ILIKE`, `DISTINCT ON`, `DO $$` blocks, arrays |
| [`nosql-query.ebnf`](../pex-nosql/pex-nosql-core/src/main/resources/grammar/nosql-query.ebnf) | pex-nosql-core | Filter operators (15), dot-notation, field paths, comparison |
| [`nosql-operations.ebnf`](../pex-nosql/pex-nosql-core/src/main/resources/grammar/nosql-operations.ebnf) | pex-nosql-core | CRUD, update operators (7), aggregation pipeline (10 stages) |
| [`mongodb.ebnf`](../pex-nosql/pex-nosql-dialects/src/main/resources/grammar/mongodb.ebnf) | pex-nosql-dialects | `MongoQuery`/`MongoUpdate`/`MongoPipeline` DSL |
| [`cassandra.ebnf`](../pex-nosql/pex-nosql-dialects/src/main/resources/grammar/cassandra.ebnf) | pex-nosql-dialects | Full CQL: DDL, DML, BATCH, DCL, TTL, types |

ANTLR4 equivalents live in `grammar/antlr/pex/SqlDdl.g4` and `grammar/antlr/pex/SqlDml.g4`. HTML railroad diagrams are generated by `docs/grammar/GenerateGrammarDocs.java`. NoSQL grammar files are defined in `pex-nosql` and have no ANTLR4 equivalents (NoSQL uses structural document APIs, not text parsing).

> **Rule**: whenever supported SQL or NoSQL syntax changes in any module, the matching `.ebnf` file must be updated in the same commit.

## Change History

| Commit | Change | Compliance Impact |
|--------|--------|-------------------|
| *(this commit)* | Add `SqlFeatureComplianceTest` + `docs/COMPLIANCE.md` | Initial compliance baseline: 117 tests, all 5 dialects |
| c603a75 | `executeBatch(Stream)`, hash-join, parse cache | All dialects: ✓ |
| 236998a | `CREATE TEMP TABLE` syntax | All dialects: ✓ |

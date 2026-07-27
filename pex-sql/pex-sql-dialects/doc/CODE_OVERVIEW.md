# pex-sql-dialects — Code Overview

> Part of the PEX multi-module project. See [pex-sql overview](../docs/CODE_OVERVIEW.md) and [top-level CODE_OVERVIEW.md](../../docs/CODE_OVERVIEW.md).

---

## Purpose

`pex-sql-dialects` provides vendor-specific SQL execution and four specialized storage engines:

- **Oracle dialect** — CONNECT BY, ROWNUM, NVL, DECODE, TO_CHAR, REGEXP_LIKE, hierarchical queries, dual table, anonymous blocks
- **MSSQL dialect** — TOP, CROSS APPLY, ISNULL, CONVERT, DATEDIFF, STUFF, FORMAT, OFFSET/FETCH
- **MySQL dialect** — REPLACE INTO, ON DUPLICATE KEY UPDATE, IF, IFNULL, LOCATE, DATE_FORMAT, LPAD/RPAD
- **PostgreSQL dialect** — ON CONFLICT (upsert), RETURNING, `::` type cast, ILIKE, DISTINCT ON, GENERATE_SERIES, ARRAY, DO $$ blocks, 25+ functions
- **Special engines**: `ColumnarEngine` (vectorized aggregation), `TimeSeriesEngine` (SAMPLE BY, FILL), `FullTextEngine` (MATCH/AGAINST, CONTAINS), `SpatialEngine` (ST_Distance, ST_Contains, ST_Intersects)

---

## Package Structure

```
ssg.pex.sql.dialects
├── DialectsPlugin              SPI PexPlugin (load order 230)
├── DialectDatabase             facade selecting parser/executor/functions by DialectType enum
├── DialectFunctionRegistry     name→NativeFunction map; functionNames() for external inspection
├── oracle/
│   ├── OracleParser, OracleExecutor, OracleFunctions, OracleNodes
├── mssql/
│   ├── MssqlParser, MssqlExecutor, MssqlFunctions, MssqlNodes
├── mysql/
│   ├── MysqlParser, MysqlExecutor, MysqlFunctions, MysqlNodes
├── postgresql/
│   ├── PostgresqlParser, PostgresqlExecutor, PostgresqlFunctions, PostgresqlNodes
└── engines/
    ├── ColumnarEngine          vectorized GROUP BY / aggregation
    ├── TimeSeriesEngine        SAMPLE BY, FILL, time-bucket queries
    ├── FullTextEngine          MATCH/AGAINST, CONTAINS/FREETEXT
    └── SpatialEngine           ST_* geometric functions
```

---

## Dialect Pattern

Each dialect follows the same four-class pattern:
1. **Parser** — regex-based heuristic detection of dialect-specific syntax; delegates to `SqlParser` for shared SQL and handles dialect extensions
2. **Executor** — overrides/augments `InMemoryDatabase` execution for dialect-specific statements (e.g., `executeUpsert` for MySQL, `applyOracleFunctions` for Oracle)
3. **Functions** — `DialectFunctionRegistry` populated with dialect-specific native functions
4. **Nodes** — additional AST node records for dialect-specific constructs (e.g., `PostgresqlNodes.ArrayLiteral`, `OracleNodes.ConnectByClause`)

`DialectDatabase` is the consumer-facing entry point: given a `DialectType` (ORACLE, MSSQL, MYSQL, POSTGRESQL, or ANSI), it selects and initialises the correct parser, executor, and function registry.

---

## Known Issues

### Dialect Detection by Regex

Parsers detect dialect syntax by searching for keywords/operators in the SQL string (e.g., `"::"` for PostgreSQL, `"ILIKE"` for PostgreSQL/MySQL). This can fail for:
- SQL strings that contain dialect keywords in string literals or comments
- Queries mixing constructs from multiple dialects

**Proposal**: Introduce explicit `DialectHint` parameter on `DialectDatabase.execute()`.

### OracleExecutor Post-Processing Overhead

`applyOracleFunctions()` re-executes `SELECT *` after the main query completes, then evaluates Oracle function expressions row-by-row. For large result sets this is O(N) extra work. See [top-level inconsistencies](../../docs/CODE_OVERVIEW.md#inconsistencies-inefficiencies-and-ambiguities).

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| OracleDialectTest | 55 | CONNECT BY, ROWNUM, NVL, DECODE, DUAL, anonymous blocks |
| MssqlDialectTest | 48 | TOP, CROSS APPLY, ISNULL, CONVERT, STUFF |
| MysqlDialectTest | 45 | REPLACE, ON DUPLICATE KEY, IF/IFNULL |
| PostgresqlDialectTest | 63 | ON CONFLICT, RETURNING, ::, ILIKE, ARRAY, GENERATE_SERIES |
| SpecialEngineTest | 25 | ColumnarEngine, TimeSeriesEngine, FullTextEngine, SpatialEngine |
| RealWorldDialectTest | 20 | cross-dialect, realistic queries |
| **Total** | **256** | |

# pex-sql-core — Code Overview

> Part of the PEX multi-module project. See [pex-sql overview](../docs/CODE_OVERVIEW.md) and [top-level CODE_OVERVIEW.md](../../docs/CODE_OVERVIEW.md).

---

## Purpose

`pex-sql-core` provides the complete SQL parsing and execution stack:

- **`SqlTokenizer`** — lexical analysis, handling keywords, identifiers, literals, operators
- **`SqlParser`** — recursive descent parser producing a sealed `SqlNode` AST
- **`InMemoryDatabase`** — the central DBMS facade with LRU parse cache and `executeBatch` support
- **`QueryExecutor`** — full SELECT pipeline with lazy stream processing and predicate push-down
- **`DmlExecutor`** — INSERT/UPDATE/DELETE with constraint enforcement
- **`DdlExecutor`** — CREATE/DROP/ALTER TABLE, INDEX, VIEW, PROCEDURE, TRIGGER
- **`JoinEngine`** — INNER (hash-join for equi, nested-loop for non-equi), LEFT, RIGHT, FULL, CROSS
- **`AggregateEngine`** — COUNT, SUM, AVG, MIN, MAX, GROUP_CONCAT
- **`SortEngine`** — ORDER BY with multi-column, ASC/DESC
- **`ExpressionEvaluator`** — WHERE/HAVING/CHECK expression evaluation
- **`TransactionManager`** — BEGIN/COMMIT/ROLLBACK/SAVEPOINT with change log for undo
- **`SqlFunctions`** — 19 built-in SQL functions (UPPER, LOWER, TRIM, SUBSTR, LENGTH, CONCAT, REPLACE, ABS, ROUND, CEIL, FLOOR, MOD, POWER, COALESCE, NULLIF, CAST, NOW, CURRENT_DATE, CURRENT_TIME)

---

## Package Structure

```
ssg.pex.sql
├── SqlPlugin                    SPI PexPlugin (load order 200)
├── grammar/
│   ├── SqlTokenizer
│   ├── SqlParser
│   └── SqlDialects              grammar variation constants
├── ast/                         SqlNode sealed hierarchy
│   ├── SqlNode, SqlExpression, SqlSupport
│   ├── SelectNode, InsertNode, UpdateNode, DeleteNode
│   ├── CreateTableNode, AlterTableNode, DropTableNode
│   ├── CreateIndexNode, CreateViewNode
│   ├── CreateProcedureNode, CreateTriggerNode
│   ├── CallNode, TransactionNode
│   └── (expression nodes: BinaryExpr, ColumnRef, FunctionCall, Literal, ...)
├── dbms/
│   ├── InMemoryDatabase         main facade + LRU cache + executeBatch
│   ├── Schema, Table, Column, Row, Index, View, StoredProcedure, Trigger
│   └── executor/
│       ├── QueryExecutor        SELECT pipeline (lazy streams)
│       ├── DmlExecutor          INSERT/UPDATE/DELETE
│       ├── DdlExecutor          CREATE/DROP/ALTER
│       ├── JoinEngine           INNER(hash)/LEFT/RIGHT/FULL/CROSS
│       ├── AggregateEngine      COUNT/SUM/AVG/MIN/MAX/GROUP_CONCAT
│       ├── SortEngine           ORDER BY
│       └── ExpressionEvaluator  WHERE/HAVING/CHECK
├── dbms/result/
│   ├── QueryResult              rows + column names + count
│   └── DmlResult                affected row count
├── dbms/transaction/
│   ├── TransactionManager
│   ├── Transaction, ChangeLog, IsolationLevel
├── function/
│   └── SqlFunctions
└── handler/
    └── SqlNodeHandler
```

---

## Key Design Decisions

### Lazy Stream Pipeline (QueryExecutor)

`resolveFrom()` returns a `Stream<Row>` using `flatMap` for cross-joins. WHERE is `.filter()`, LIMIT (when no ORDER BY/GROUP BY/DISTINCT) is `.limit()`. A single `collect()` at the end materialises only surviving rows. This prevents OOM on large Cartesian products.

ORDER BY / GROUP BY / DISTINCT collect the full stream first (non-streaming by nature).

### Hash-Join for INNER Equi-Joins

`JoinEngine.innerJoin()` calls `detectEquiJoin()` on the ON expression. If it finds `colA = colB` (simple column equality), it builds a `HashMap<Object, List<Row>>` over the right table, then probes with left keys. NULL keys are excluded (SQL standard). Non-equi and outer joins use nested-loop.

### LRU Parse Cache

`InMemoryDatabase` maintains a 1024-entry `LinkedHashMap` (access-order, removeEldestEntry when size > 1024) wrapped in `Collections.synchronizedMap`. `execute(String sql)` checks the cache before parsing. `executeBatch()` uses the same cache so the template is parsed once regardless of how many rows the stream contains.

### Partial Predicate Push-Down

At each cross-join step in `resolveFrom()`, `extractApplicableConjuncts()` flattens the WHERE AND-tree and filters out conjuncts that reference columns not yet in scope. This reduces intermediate row counts for 3+-table joins.

### Alias Resolution in Projection

`resolveSelectValue()` first tries to resolve a SELECT item by expression text, then falls back to the item's alias. This is required because `executeGroupBy()` produces output columns named by alias (e.g., `employee_count` for `COUNT(*) AS employee_count`).

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| SqlTokenizerTest | 35 | keywords, identifiers, string literals, operators, edge cases |
| SqlParserTest | 65 | all DML/DDL statement types, subqueries, expressions |
| InMemoryDatabaseTest | 80 | full CRUD scenarios, DDL, views, procedures |
| QueryExecutorTest | 85 | SELECT pipeline, joins, aggregation, subqueries, CASE |
| DmlExecutorTest | 55 | INSERT/UPDATE/DELETE, PK/FK/UNIQUE/NOT NULL/CHECK constraints |
| DdlExecutorTest | 40 | CREATE/DROP/ALTER TABLE, INDEX, VIEW, PROCEDURE, TRIGGER |
| JoinEngineTest | 35 | all join types, hash vs nested-loop, NULL handling |
| AggregateEngineTest | 30 | all aggregate functions, GROUP BY, HAVING |
| ExpressionEvaluatorTest | 40 | WHERE/HAVING/CHECK evaluation |
| TransactionTest | 30 | BEGIN/COMMIT/ROLLBACK/SAVEPOINT |
| SqlEngineOptimizationsTest | 16 | executeBatch, hash-join, LRU cache, predicate push-down |
| ComplexSqlTest, RealWorldSqlTest | 18 | complex real-world queries |
| **Total** | **509** | |

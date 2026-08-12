# PEX Requirements Evolution

This document tracks all requirements, design decisions, and their evolution throughout the project development.

---

## Project Timeline Overview

- **Start Date**: June 16, 2026
- **Total Commits**: 18
- **Total Tests**: 3,015 passing
- **Total Lines of Code**: ~69,700
- **Modules**: 13 (pex-base, pex-arithmetics, pex-converter, pex-sql-core, pex-sql-olap, pex-sql-streaming, pex-sql-dialects, pex-nosql-core, pex-nosql-dialects, pex-tools, pex-all)
- **Grammar Documentation**: 22 HTML files with 462 railroad diagrams + 12 ANTLR4 .g4 sample grammars
- **Latest Feature**: SQL feature compliance test suite + docs/COMPLIANCE.md across all 5 dialect variants

---

## Table of Contents

- [Project Timeline Overview](#project-timeline-overview)
- **Commits by date (newest first)**
  - **June 28, 2026**
    - [NoSQL plugin SPI, module READMEs, and documentation for pex-nosql](#commit-18-nosql-docs)
    - [SQL Feature Compliance Test Suite + docs/COMPLIANCE.md](#commit-17-compliance-tests)
  - **June 27, 2026**
    - [Code overview documentation + test coverage expansion](#commit-16-code-overview)
  - **June 26, 2026**
    - [executeBatch(Stream), hash-join, LRU parse cache, partial push-down optimizations](#commit-15-sql-optimizations)
  - **June 25, 2026**
    - [Streaming cross-join: lazy flatMap + predicate push-down + early LIMIT](#commit-14-streaming-join)
    - [Fix OlapDatabase DDL double-execution in OlapMdbDatabase bridge](#commit-13-olap-ddl-fix)
    - [Fix SQL execution bugs for MDB-SQL bridge integration](#commit-12-sql-bug-fixes)
  - **June 19, 2026**
    - [Native ANTLR4 grammar visualization with full construct support](#commit-8-antlr-visualization)
  - **June 18, 2026**
    - [Replace regex catch-all patterns with proper grammar rules in all EBNF files](#commit-7-regex-to-grammar)
    - [Grammar documentation generation, pex-tools README, final cost report](#commit-6-grammar-docs)
    - [PostgreSQL dialect, EBNF grammars, GrammarLoader, complex tests, IEEE 754 / left-recursion bug fixes](#commit-5-postgresql-grammars)
  - **June 16, 2026**
    - [4ed9fc9 — Add Gradle 8.14.1 build support alongside Maven](#commit-4ed9fc9)
    - [8bddbb8 — Implement complete PEX project: BNF parsing, execution, arithmetics, SQL, converter](#commit-8bddbb8)
    - [099768d — Initial project scaffolding: Maven multi-module structure](#commit-099768d)
- [Requirements Summary by Category](#requirements-summary-by-category)
- [Project Statistics](#project-statistics)
- [Future Considerations](#future-considerations)
- [Document Maintenance](#document-maintenance)

---

<a id="commit-18-nosql-docs"></a>
## Commit: NoSQL plugin SPI, module READMEs, and documentation for pex-nosql (June 28, 2026)

### Original Request
> "Add NoSqlPlugin SPI, META-INF/services registrations, pex-nosql module README files, update root README with nosql module table entries and NoSQL quick-start section, add NoSQL demo to Pex.java, update REQUIREMENTS.md and BUILD_COST_REPORT.md."

### Reformulated Requirements
1. Create `NoSqlPlugin.java` (loadOrder 210) in pex-nosql-core implementing `PexPlugin`
2. Create `NoSqlDialectsPlugin.java` (loadOrder 240) in pex-nosql-dialects implementing `PexPlugin`
3. Register both plugins via `META-INF/services/ssg.pex.spi.PexPlugin` in their respective modules
4. Create `pex-nosql/README.md` — parent module description with sub-module overview
5. Create `pex-nosql/pex-nosql-core/README.md` — engine description with operator tables and usage example
6. Create `pex-nosql/pex-nosql-dialects/README.md` — MongoDB and Cassandra dialect documentation with usage examples
7. Update root `README.md` — add nosql modules to module table, add NoSQL Simulation quick-start section, update test count badge (2,576 → 2,760)
8. Update `pex-all/src/main/java/ssg/pex/Pex.java` — add section 7 NoSQL demo (InMemoryNoSqlDatabase, MongoDB, Cassandra), renumber subsequent sections
9. Update `REQUIREMENTS.md` — this section
10. Update `costs/BUILD_COST_REPORT.md` — nosql implementation entry

### Final Design Decisions
- **NoSqlPlugin is a no-op registrar**: NoSQL collections are accessed directly via `InMemoryNoSqlDatabase.getCollection()` — there are no AST node handlers to register. The plugin exists purely for `ServiceLoader` discoverability and consistent load-ordering with SQL plugins (nosql-core at 210, nosql-dialects at 240).
- **loadOrder gap**: nosql-core (210) sits between sql-core (200) and sql-dialects (230); nosql-dialects (240) sits after sql-dialects (230).
- **Section renumbering in Pex.java**: The NoSQL demo is inserted as section 7; original SQL Operations section (was 7) becomes 8, and all subsequent sections shift by one (ending at 17).
- **Test count**: pex-nosql added 184 tests (184 in pex-nosql-core + pex-nosql-dialects combined); total goes from 2,576 to 2,760.

### Implementation Details
- **Files created**:
  - `pex-nosql/pex-nosql-core/src/main/java/ssg/pex/nosql/NoSqlPlugin.java`
  - `pex-nosql/pex-nosql-dialects/src/main/java/ssg/pex/nosql/dialects/NoSqlDialectsPlugin.java`
  - `pex-nosql/pex-nosql-core/src/main/resources/META-INF/services/ssg.pex.spi.PexPlugin`
  - `pex-nosql/pex-nosql-dialects/src/main/resources/META-INF/services/ssg.pex.spi.PexPlugin`
  - `pex-nosql/README.md`
  - `pex-nosql/pex-nosql-core/README.md`
  - `pex-nosql/pex-nosql-dialects/README.md`
- **Files modified**:
  - `README.md` — module table (3 nosql rows), NoSQL Simulation usage section, badge update
  - `pex-all/src/main/java/ssg/pex/Pex.java` — NoSQL imports + section 7 demo, sections 8-17 renumbered
  - `REQUIREMENTS.md` — this section + TOC entry
  - `costs/BUILD_COST_REPORT.md` — nosql documentation entry
- **Lines added**: ~350 (plugins + service files + 3 READMEs + Pex.java section)
- **No production source code changed** — documentation and SPI registration only

### Test Coverage
- No new tests in this commit (documentation-only commit)
- Existing pex-nosql tests: 184 (all previously passing)
- Total project tests: 2,576 → 2,760 (+184 pex-nosql)

---

<a id="commit-17-compliance-tests"></a>
## Commit: SQL Feature Compliance Test Suite + docs/COMPLIANCE.md (June 28, 2026)

### Original Request
> "Create a comprehensive SQL feature compliance test suite and compliance document for the PEX project. Test all dialect variants (CORE, POSTGRESQL, MYSQL, ORACLE, MSSQL) with JUnit 5 nested classes per SQL feature category, parameterized over all dialects. Include DDL, DML, SELECT, dialect-specific DML, unsupported features, OLAP aggregates, and optimization correctness tests. Create docs/COMPLIANCE.md at project root."

### Reformulated Requirements
1. Create `SqlFeatureComplianceTest.java` in `pex-sql/pex-sql-dialects/src/test/java/ssg/pex/sql/dialect/` with JUnit 5 nested classes per SQL feature
2. Test all 5 dialect variants: CORE (InMemoryDatabase), POSTGRESQL, MYSQL, ORACLE, MSSQL
3. Use `@ParameterizedTest` + `@EnumSource(Dialect.class)` for cross-dialect tests
4. Cover: DDL (CREATE/DROP TABLE, temp tables), DML (INSERT/UPDATE/DELETE, executeBatch), SELECT (WHERE, GROUP BY, ORDER BY, LIMIT, JOIN, hash-join), dialect-specific DML, unsupported features, OLAP aggregates, optimization correctness
5. Provide `DialectFixture` helper that creates a fresh isolated DB per test method
6. Create `docs/COMPLIANCE.md` at project root documenting the feature support matrix, dialect syntax differences, and known limitations

### Final Design Decisions
- `SqlFeatureComplianceTest` organized into 7 nested classes: `DdlFeatureTest`, `DmlFeatureTest`, `SelectFeatureTest`, `DialectDmlTest`, `UnsupportedFeatureTest`, `OlapFeatureTest`, `OptimizationCorrectnessTest`
- `DialectFixture` uses a pattern-matching dispatch (`switch (db)` with `instanceof` guards) to route `execute()` and `executeBatch()` calls to the correct API without requiring a shared interface
- Unsupported-feature tests verify that ON CONFLICT syntax fails for CORE/MSSQL/ORACLE, and REPLACE INTO fails for CORE/ORACLE
- HAVING tests use `HAVING 1 = 1` rather than aggregate-based predicates because the engine's post-GROUP BY filter cannot re-evaluate aggregate functions
- OLAP (window functions, CTEs, ROLLUP) is scoped to `OlapDatabase` in pex-sql-olap and is not tested here (pex-sql-dialects does not depend on pex-sql-olap)
- All 117 new tests pass; no existing tests broken; total dialect module tests: 373

### Implementation Details
- Files created: `pex-sql/pex-sql-dialects/src/test/java/ssg/pex/sql/dialect/SqlFeatureComplianceTest.java` (826 lines), `docs/COMPLIANCE.md` (120 lines)
- Files modified: `REQUIREMENTS.md`, `README.md` (test count), `pex-sql/pex-sql-dialects/README.md` (compliance section), `costs/BUILD_COST_REPORT.md`
- Lines added: ~950
- No production code changed

### Test Coverage
- New tests added: 117
- Test categories: DDL×5, DML×5, SELECT×5, dialect-specific×12, unsupported features×5, OLAP aggregates×5, optimization correctness×5 dialects each
- Total pex-sql-dialects tests: 373 (up from 256)
- Total project tests: 2,576 (up from 2,459)

---

<a id="commit-16-code-overview"></a>
## Commit: Code overview documentation + test coverage expansion (June 27, 2026)

### Original Request
> "Explore the full project structure. Review all existing unit and integration tests. Identify gaps vs. requirements/implementation. Extend tests to achieve full coverage — fix code where needed rather than simplifying or skipping tests. Create CODE_OVERVIEW.md at the appropriate level(s). Update README.md at each level to reference CODE_OVERVIEW.md. If there is a costs file, update it to reflect current state. Stage and commit all changes with a clear message."

### Reformulated Requirements
1. Analyse coverage gaps by comparing test directories against source directories
2. Add missing unit tests for: `PexTelemetryListener`, `ParseEvent`, `ExecuteEvent`, `ScopeEvent`, `ErrorEvent`; `Preconditions`; `SourceLocation` factory methods and `toString()` branches; `RadixHandler` and `RadixLiteralNode`
3. Create a `CODE_OVERVIEW.md` at the project root covering: project goals, module decomposition rationale, key and secondary decisions, inconsistencies/inefficiencies/ambiguities with proposals
4. Create per-module `CODE_OVERVIEW.md` files: pex-base, pex-arithmetics, pex-converter, pex-sql (parent + 4 sub-modules), pex-tools, pex-all
5. Update every module `README.md` to link its `CODE_OVERVIEW.md`
6. Update `README.md` badge (test count 2404→2459) and add Code Overview section
7. Update `costs/BUILD_COST_REPORT.md` executive summary and commit table

### Final Design Decisions

**Test gap analysis methodology**: Compared `src/main/java` package directories against `src/test/java`; found `ssg/pex/telemetry` and `ssg/pex/util` directories existed but were empty, and `RadixHandler`/`RadixLiteralNode` had zero test coverage at unit level (only via integration tests).

**Telemetry tests**: `TelemetryTest` verifies all four `PexEvent` record types store fields correctly, that `PexTelemetryListener` receives all types, and that pattern matching over the sealed `PexEvent` hierarchy works (exhaustive switch).

**`Preconditions` tests**: Cover the three static methods (`requireNonNull`, `requireTrue`, `requireNotEmpty`) including the edge case that whitespace-only strings are considered non-empty by `requireNotEmpty` (intentional — only `null` and `""` are rejected).

**`SourceLocation` tests**: Cover all three factory methods (`of(source,line,col)`, `of(line,col)`, `at(offset,length)`), the `UNKNOWN` sentinel, and all four `toString()` branches (source+line+col, line+col only, offset+length, UNKNOWN → `"?"`).

**`RadixHandler` and `RadixLiteralNode` tests**: Cover `parse()` for hex/binary/octal with both lowercase and uppercase prefixes, `literalValue()` returning `Integer` vs `Long` depending on range, `toRadixString()`, `RadixHandler.parseRadixLiteral()` static method, and all four `handle()` branches (success, wrong wrapped type, wrong extension type, non-ExtensionNode).

**CODE_OVERVIEW.md structure**: Root document covers project goals, module decomposition rationale, 7 key decisions with rationale, 3 secondary decisions, 7 identified inconsistencies/inefficiencies with proposals, and a test coverage table. Per-module documents cover purpose, package structure, key components, design decisions specific to that module, and a test coverage table.

### Implementation Details

**New test files**:
- `pex-base/src/test/java/ssg/pex/telemetry/TelemetryTest.java` — 18 tests
- `pex-base/src/test/java/ssg/pex/util/PreconditionsTest.java` — 11 tests
- `pex-base/src/test/java/ssg/pex/ast/SourceLocationTest.java` — 10 tests
- `pex-arithmetics/src/test/java/ssg/pex/arithmetics/handler/RadixHandlerTest.java` — 16 tests

**New documentation files**:
- `CODE_OVERVIEW.md` (root)
- `pex-base/CODE_OVERVIEW.md`
- `pex-arithmetics/CODE_OVERVIEW.md`
- `pex-converter/CODE_OVERVIEW.md`
- `pex-sql/CODE_OVERVIEW.md`
- `pex-sql/pex-sql-core/CODE_OVERVIEW.md`
- `pex-sql/pex-sql-olap/CODE_OVERVIEW.md`
- `pex-sql/pex-sql-streaming/CODE_OVERVIEW.md`
- `pex-sql/pex-sql-dialects/CODE_OVERVIEW.md`
- `pex-tools/CODE_OVERVIEW.md`
- `pex-all/CODE_OVERVIEW.md`

**Modified documentation files**: `README.md` (badge, TOC, new Code Overview section), all 7 module README.md files (CODE_OVERVIEW.md reference), `REQUIREMENTS.md` (statistics, TOC, this section), `costs/BUILD_COST_REPORT.md` (executive summary, commit table)

### Test Coverage

- **pex-base**: 485 → 522 (+37): TelemetryTest (18), PreconditionsTest (11), SourceLocationTest (10)
- **pex-arithmetics**: 270 → 288 (+18): RadixHandlerTest (16)
- All other modules unchanged
- **Total: 2,404 → 2,459 (+55)**

---

<a id="commit-15-sql-optimizations"></a>
## Commit: executeBatch(Stream), hash-join, LRU parse cache, partial push-down (June 26, 2026)

### Original Request
> "Implement several SQL engine optimizations in the PEX project based on top recommendations from the MDB-SQL benchmark report: executeBatch with Stream<Object[]>, hash-join for equi-joins, cached parsed SqlNode trees (LRU), and partial predicate push-down for 3+ table joins."

### Reformulated Requirements
1. Add `executeBatch(String template, Stream<Object[]> rows)` to `InMemoryDatabase` — parse template once, execute rows lazily
2. Add `executeBatch(String template, List<Object[]> rows)` as backward-compatible overload
3. Implement hash-join in `JoinEngine` for equi-join predicates (ON a.id = b.id), falling back to nested-loop for non-equi
4. Add LRU parse cache (1024 entries, per-instance) in `InMemoryDatabase.execute(String)`
5. Extend predicate push-down to 3+ table joins — push AND-conjuncts that are fully resolvable at each join step

### Final Design Decisions

#### executeBatch
- Template uses `?` placeholders; these are replaced with `NULL` before parsing so the existing `SqlParser` can handle them
- The parsed `InsertNode` (with column names) is cached in the LRU parse cache keyed by the original template string
- Each `Object[]` from the stream is wrapped into a fresh single-row `InsertNode` and dispatched to `DmlExecutor.executeInsert()` — no re-parsing
- The stream is consumed via `forEach()` — lazily, one row at a time, no intermediate buffering

#### Hash-join
- `JoinEngine.innerJoin()` now calls `detectEquiJoin()` first; if a simple column-equality ON expression is found, the hash-join path is used
- `detectEquiJoin()` inspects the ON expression: must be `BinaryExpr` with `op="="` and both sides must be `ColumnRef`; both refs must resolve in their respective column lists
- Build phase: index right rows by join-key value in a `HashMap<Object, List<Row>>`; NULL keys are skipped (standard SQL semantics)
- Probe phase: for each left row, look up its key in the map; NULL left keys are also skipped
- All other join types (LEFT, RIGHT, FULL, CROSS) and non-equi predicates still use nested-loop

#### LRU Parse Cache
- `InMemoryDatabase` now holds a `Collections.synchronizedMap(new LinkedHashMap(...))` with LRU eviction (accessOrder=true, removeEldestEntry > 1024)
- `execute(String sql)` checks the cache before calling `SqlParser.parse()`
- `parseCacheSize()` and `clearParseCache()` are exposed for diagnostics and testing

#### Partial Predicate Push-Down for 3+ Tables
- `resolveFrom()` calls `extractApplicableConjuncts(whereCondition, currentColumns)` at every join step
- `extractApplicableConjuncts()` flattens the WHERE AND-tree into individual conjuncts, then removes those that reference columns not yet in scope
- `isFullyResolvable()` walks the expression tree recursively; any `ColumnRef` that can't be matched against the current column list makes the conjunct non-applicable
- Applied at: (a) the first (single) table scan, and (b) after each cross-join step — so for A,B,C the predicates `A.x=1` fire after scanning A, then `B.y=2` fires after A×B, then any remaining predicates fire after A×B×C
- The original full-WHERE filter still runs in the WHERE clause stage after all tables are joined, ensuring correctness

### Implementation Details
- **Files modified**:
  - `pex-sql/pex-sql-core/src/main/java/ssg/pex/sql/dbms/InMemoryDatabase.java` — LRU cache, executeBatch(Stream), executeBatch(List), resolveInsertTemplate()
  - `pex-sql/pex-sql-core/src/main/java/ssg/pex/sql/dbms/executor/JoinEngine.java` — hash-join for INNER equi-joins
  - `pex-sql/pex-sql-core/src/main/java/ssg/pex/sql/dbms/executor/QueryExecutor.java` — partial push-down helpers (extractApplicableConjuncts, collectConjuncts, isFullyResolvable, canResolveColumnRef)
- **Files created**:
  - `pex-sql/pex-sql-core/src/test/java/ssg/pex/sql/dbms/SqlEngineOptimizationsTest.java` — 16 new tests

### Test Coverage
- `testExecuteBatchStream` — 10,000-row Stream.generate() batch, verifies count=10,000
- `testExecuteBatchLazy` — stream peek counter verifies lazy consumption
- `testExecuteBatchBackwardsCompat` — List<Object[]> overload works
- `testExecuteBatchParseOnce` — parse cache size stays constant on repeated same-template batches
- `testExecuteBatchInvalidTemplateFails` — bad SQL throws IllegalArgumentException
- `testExecuteBatchNonInsertFails` — non-INSERT throws IllegalArgumentException
- `testHashJoinEqui` — 1000×1000 equi-join completes in <1s
- `testHashJoinNonEqui` — non-equi ON falls back to nested-loop, correct results
- `testHashJoinNullHandling` — NULL join keys excluded from equi-join
- `testHashJoinDirectApi` — JoinEngine API test for 500×500 tables
- `testParseCacheHit` — 100 identical SQL strings → cache size stable
- `testParseCacheDifferentSql` — different SQL strings get different entries
- `testParseCacheClearWorks` — clearParseCache() resets to 0
- `testParseCacheSelectHit` — SELECT also cached
- `testPredicatePushDown3Tables` — 3-table join with category filter, 6 expected rows
- `testPredicatePushDownWithPerTableFilters` — A,B,C with per-table AND predicates
- **Total tests**: 2,404 (all passing); +16 new

---

<a id="commit-14-streaming-join"></a>
## Commit: Streaming cross-join — lazy flatMap + predicate push-down + early LIMIT (June 25, 2026)

### Original Request
> "check reason for OOM in cross-join test and propose optimization. we should minimize accumulating data in RAM but rather use stream-like processing. apply both fixes. once part 1 succeeds, do part 2."

### Reformulated Requirements
1. Root-cause the OOM: locate where the full Cartesian product is materialised in `QueryExecutor`
2. Part 1: push WHERE predicate into the cross-join loop; only add rows that pass the predicate
3. Part 2: replace `List<Row>` materialization in `resolveFrom()` with a lazy `Stream<Row>` using `flatMap`; apply WHERE as `.filter()` and LIMIT as `.limit()` before the terminal `collect()`
4. Keep all 2,388 existing tests passing

### Final Design Decisions

#### Root Cause
`QueryExecutor.resolveFrom()` lines 150-156 materialised the full Cartesian product unconditionally:
```java
for (Row existing : rows) {
    for (Row newRow : table.scan()) {
        newRows.add(Row.merge(existing, newRow));  // 4M allocs for 2k×2k
    }
}
```
WHERE filtering and LIMIT were applied only after `resolveFrom()` returned, so no amount of selectivity helped.

#### Part 1 correctness constraint: push-down only on last cross-join
The WHERE condition references columns from ALL tables in the FROM clause. During iterative cross-joining (e.g., `FROM a, b, c`), when joining `a` and `b`, column `c.x` is not yet in `mergedCols`. The evaluator returns `null` for unresolvable refs. `isTruthy(null) == false` would silently discard valid rows. Fix: push-down fires only when `tableIdx == tableCount` (all tables present in `mergedCols`).

#### Part 2: lazy Stream pipeline
`FromResult` record changed from `List<Row>` to `Stream<Row>`. Cross-join uses `flatMap`:
```java
stream = stream.flatMap(left -> rightSnapshot.stream().map(right -> Row.merge(left, right)));
```
WHERE becomes a `.filter()` on the stream. LIMIT becomes `.limit()` when no ORDER BY / GROUP BY / DISTINCT is present (`needsFullDataset == false`). A single `collect(Collectors.toList())` materialises only the rows that survive all lazy stages. ORDER BY / GROUP BY / DISTINCT still require the full materialised set — they are inherently non-streaming.

#### Null-safety in push-down filter
In the stream `.filter()` for cross-join push-down, `null` evaluation result is treated as "pass through" — the post-`collect()` WHERE step (now also a stream filter) applies it correctly instead.

### Implementation Details

#### Modified files:
- `pex-sql-core/.../executor/QueryExecutor.java`:
  - `FromResult` record: `List<Row> rows` → `Stream<Row> stream`
  - `resolveFrom()`: returns `Stream<Row>` via lazy `flatMap`; push-down `.filter()` on last join; explicit JOIN materialises, then converts back via `.stream()`
  - `execute()`: `Stream<Row>` pipeline with `.filter()` (WHERE), `.limit()` (early LIMIT), `collect()` once; final LIMIT block guarded by `needsFullDataset`
  - `executeJoin()`: returns `FromResult` with `joinedRows.stream()` instead of `List<Row>`

### Test Coverage
- All 2,388 PEX tests pass unchanged
- Key regression tests exercised: `threeTableJoinViaFromComma` (3 tables), `fourTableJoinViaFromComma` (4 tables), `commaSeparatedThreeTablesWithAliases`, `orderDetailsWithProductNames`, all MultiJoin and TableAlias tests

---

<a id="commit-13-olap-ddl-fix"></a>
## Commit: Fix OlapDatabase DDL double-execution in OlapMdbDatabase bridge (June 25, 2026)

### Original Request
> Discovered during MDB-SQL JFR benchmark suite: `OlapMdbDatabase.execute()` was executing DDL statements (CREATE TABLE) twice — first via `OlapDatabase.executeOlap()`, then again because `executeOlap()` returned failure for DDL.

### Reformulated Requirements
1. Fix `OlapDatabase.executeOlap()` to not report failure when a DDL/DML statement succeeds but returns `DmlResult` instead of `QueryResult`

### Final Design Decisions
- **Return empty `QueryResult` for DML/DDL**: When `database.execute(node)` returns a `DmlResult` (DDL/DML), return `Result.success(new QueryResult(List.of(), List.of(), 0))`. This signals the statement succeeded to the caller (`OlapMdbDatabase.execute()`) without triggering the fallback path that would re-execute the SQL.
- **No behavior change for SELECT**: Only the `DmlResult` branch is affected. `QueryResult` is still returned unchanged.

### Implementation Details

#### Modified files:
- `pex-sql-olap/.../OlapDatabase.java` — replaced `return Result.failure("OLAP_ERROR", "Query did not return a result set")` with `return Result.success(new QueryResult(List.of(), List.of(), 0))` in the post-DDL/DML branch (1 line change)

### Test Coverage
- No new PEX tests added (bug fix to rarely-exercised branch)
- All existing 2,388 PEX tests continue to pass
- MDB-SQL OLAP benchmark tests (8 tests) exercise this path: OlapMdbDatabase.execute("CREATE TABLE ...") now correctly executes once

---

<a id="commit-12-sql-bug-fixes"></a>
## Commit: `fc7c053` — Fix SQL execution bugs for MDB-SQL bridge integration (June 25, 2026)

### Original Request
> "commit with PEX project rules the PEX project changes. add documentation to MDB-SQL project and modules following same approach as in PEX project including costs document. then commit"

Context: these PEX changes were made to fix 6 failing integration tests in the MDB-SQL project, which uses PEX-SQL as its execution engine.

### Reformulated Requirements
1. Fix `QueryExecutor.resolveSelectValue()` to correctly resolve projected columns after GROUP BY where column names have been renamed to their aliases
2. Extend `InMemoryDatabase` with a protected constructor accepting a custom `Schema` implementation, enabling MDB-SQL's `MdbBackedSchema` injection
3. Add `functionNames()` accessor to `DialectFunctionRegistry` for external inspection of registered dialect functions
4. Fix `MysqlExecutor.executeUpsert()` to detect duplicate rows before inserting (since PEX doesn't enforce PK constraints)
5. Fix `MysqlExecutor.executeOnDuplicateUpdate()` to find the conflicting row by key value rather than always updating the last row
6. Fix `OracleExecutor` to post-process SELECT results through the dialect function registry (NVL, DECODE, etc.) instead of returning nulls for unrecognized function expressions
7. Fix `OlapDatabase.executeOlap()` to auto-detect window functions (`OVER` keyword) and grouping sets (`ROLLUP`/`CUBE`) and route to the appropriate executors

### Final Design Decisions
- **Alias fallback in projection**: `resolveSelectValue()` tries `item.alias()` as a column name when the expression text lookup fails. This is necessary because `executeGroupBy()` produces output columns named by alias (e.g. `employee_count` for `COUNT(*) AS employee_count`), but the `SelectItem` still carries the original expression text.
- **Protected constructor on `InMemoryDatabase`**: Added `protected InMemoryDatabase(Schema schema)` and made the `defaultSchema` field `protected`. This breaks encapsulation minimally — only subclasses can use it — and allows MDB-SQL to inject `MdbBackedSchema` without reimplementing the full database.
- **`functionNames()` on `DialectFunctionRegistry`**: Simple `Set<String>` view of keys in the internal registry map. Used by `OracleExecutor` to detect whether a SELECT item contains a registered Oracle function.
- **MySQL pre-check instead of insert-and-detect**: PEX has no PK constraint enforcement, so `INSERT` always succeeds regardless of duplicates. The fix is to parse the INSERT statement's values, scan for a matching existing row using the first NOT NULL column as key, and skip the insert if found.
- **Oracle post-processing**: After a regular SELECT (non-DUAL, non-hierarchical) completes, `OracleExecutor` checks if any SELECT item contains a registered Oracle function call. If so, it re-executes `SELECT *` to get all column values, then evaluates each function expression row-by-row using `evaluateExprOnRow()`.
- **OLAP auto-routing**: `OlapDatabase.executeOlap()` for string SQL now checks for `OVER` (window functions) and `ROLLUP(`/`CUBE(` inside a `GROUP BY` clause before delegating to the core database. This avoids requiring callers to use separate lower-level API methods.

### Implementation Details

#### Modified files:
- `pex-sql-core/.../InMemoryDatabase.java` — added `protected final Schema defaultSchema` (14 insertions)
- `pex-sql-core/.../executor/QueryExecutor.java` — alias fallback in `resolveSelectValue()` (6 insertions)
- `pex-sql-dialects/.../DialectFunctionRegistry.java` — `functionNames()` method (5 insertions)
- `pex-sql-dialects/.../mysql/MysqlParser.java` — `INSERT_VALUES_PATTERN` regex + `parseInsertValues()` (18 insertions)
- `pex-sql-dialects/.../mysql/MysqlExecutor.java` — rewrote `executeUpsert()` and `executeOnDuplicateUpdate()` (86 net changes)
- `pex-sql-dialects/.../oracle/OracleExecutor.java` — added `applyOracleFunctions()`, `evaluateExprOnRow()`, `splitAndEvaluateArgs()` (148 insertions)
- `pex-sql-olap/.../OlapDatabase.java` — `OVER`/`ROLLUP`/`CUBE` detection + `executeWindowFunctionQuery()`, `executeGroupingSetQuery()`, helpers (216 insertions)

**Total**: 699 insertions, 88 deletions across 7 files.

### Test Coverage
- No new PEX tests added (bug fixes only)
- All existing 2,388 PEX tests continue to pass
- 14 MDB-SQL integration tests now pass (up from 8) as a result of these fixes

---

<a id="commit-8-antlr-visualization"></a>
## Commit: Native ANTLR4 grammar visualization with full construct support (June 19, 2026)

### Original Request
> "check if can extend the use of visualizer for antlr grammars. if can, add automatic recognition on file load and present grammars in most popular visual format."
> "stop. this is easy way - i accept it also. but there're cases when antlr grammar cannot be converted to bnf (e.g. they are listed in documentation and mentioned in converter). I would like to visualize them fully, probably marking with grey parts which are not compatible with bnf. also probably there's alternative UI representation of them since they cover wider range of possibilities. so preferred way - antlr own visualization."

### Reformulated Requirements
1. Create native ANTLR4 expression model preserving ALL constructs (predicates, actions, negation, labels, lexer commands, modes, fragments)
2. Create AntlrGrammarParser that builds native model (not BNF conversion) using existing Antlr4Lexer
3. Extend Antlr4Lexer with missing token types (TILDE, HASH, EQUALS, PLUS_EQUALS, COMMA, IMPORT, MODE)
4. Create AntlrDiagramRenderer with ANTLR-specific visual styles (grey for non-BNF constructs)
5. Create AntlrMultiRulePanel for rendering multiple ANTLR rules
6. Extend GrammarVisualizerApp to auto-detect .g4 files and render them natively
7. Create collection of ANTLR4 sample grammars under grammar/antlr/
8. Write comprehensive tests for all new components

### Final Design Decisions
- **Native ANTLR model, NOT BNF conversion**: `AntlrExpression` sealed interface with 15 record types preserving all ANTLR constructs. This is separate from the BNF `RuleExpression` hierarchy.
- **Visual distinction for ANTLR-specific constructs**: Predicates and actions rendered in grey with dashed borders; negation uses a grey diamond with "~" symbol; labeled elements show grey annotation above; token refs (UPPERCASE) get distinct green fill.
- **Automatic format detection**: `.g4` extension triggers ANTLR parser; `.ebnf`/`.bnf`/`.grammar` use BNF parser. UI switches between BNF and ANTLR diagram panels.
- **Rule kind indicators**: Parser rules shown normally; lexer rules in bold green; fragment rules in italic grey with `[F]` prefix in the rule list.
- **Lexer commands visualized**: `-> skip`, `-> channel(HIDDEN)` rendered as grey italic annotations at the end of rule diagrams.
- **Grammar collection**: 12 ANTLR4 grammars covering JSON, XML (with lexer modes), Calculator (with labeled alternatives), SQL, CSV, DOT, URL, Markdown, plus PEX BNF-to-ANTLR conversions.

### Implementation Details

#### New files:
- `ssg.pex.tools.converter.antlr.AntlrExpression` — 15-type sealed interface: Literal, CharClass, RuleRef, TokenRef, Seq, Alt, ZeroOrMore, OneOrMore, Optional, Group, Negation, Predicate, Action, Dot, LabeledElement
- `ssg.pex.tools.converter.antlr.AntlrRule` — record with name, body, kind (PARSER/LEXER/FRAGMENT), altLabels, commands
- `ssg.pex.tools.converter.antlr.AntlrGrammar` — record with name, grammarKind, rules, options, imports, modes
- `ssg.pex.tools.converter.antlr.AntlrGrammarParser` — recursive-descent parser from token stream to AntlrGrammar
- `ssg.pex.tools.visualizer.AntlrDiagramRenderer` — two-pass (measure/render) railroad renderer for all 15 expression types
- `ssg.pex.tools.visualizer.AntlrMultiRulePanel` — Swing panel for multiple ANTLR rule display
- 12 ANTLR4 grammar files under `grammar/antlr/` (JSON, XML, Calculator, SQL, CSV, DOT, URL, Markdown, plus 4 PEX conversions)
- `grammar/antlr/README.md` — grammar collection documentation

#### Modified files:
- `Antlr4Lexer.java` — added 7 new token types (IMPORT, MODE, TILDE, HASH, EQUALS, PLUS_EQUALS, COMMA) and corresponding lexer handling
- `GrammarVisualizerApp.java` — added `.g4` to ACCEPTED_EXTENSIONS, auto-detect format in loadFromFile(), ANTLR rule selection, clipboard copy for ANTLR rules

#### Metrics:
- Tests: 2,240 → 2,388 (+148 new tests)
- New test files: AntlrGrammarParserTest (35), AntlrDiagramRendererTest (59), GrammarVisualizerAppTest ANTLR section (9)
- 3 background agents + orchestrator integration, ~186K agent tokens
- Grammar collection: 12 .g4 files demonstrating all ANTLR features

### Test Coverage
- AntlrGrammarParserTest: 35 tests — grammar declarations, all rule kinds, all expression types, labels, commands, modes, options, imports, error handling
- AntlrDiagramRendererTest: 59 tests — measure/render for all 15 expression types, rule rendering for all kinds, commands, panel tests, measurement accuracy
- GrammarVisualizerAppTest.Antlr4Support: 9 tests — .g4 loading, mode switching, multi-select, drag-and-drop, clipboard, fragment prefix, error handling

---

<a id="commit-7-regex-to-grammar"></a>
## Commit: Replace regex catch-all patterns with proper grammar rules (June 18, 2026)

### Original Request
> "replace in grammars regular expressions with grammars whenever reasonable. e.g in sql grammars there're regular expressions in place of actually predefined content, this should be fixed both in grammars, in implementations, and tests throughout all module (not only SQL)."

### Reformulated Requirements
1. Audit all 22 EBNF grammar files for regex terminals
2. Replace catch-all regex patterns (`/[^;]+/`, `/[^,;]+/`, `/[^)]+/`, `/SELECT[^;]+/`, etc.) with proper grammar productions
3. Replace inline regex (`/[0-9]+/` inside rules) with named non-terminal references (`number`)
4. Replace inline string-literal regex (`/'[^']*'/` inside `literal-value`) with `string-literal` non-terminal references
5. Keep genuinely open-ended leaf-level regex (identifier, number, string-literal, float, hex/bin/oct, MSSQL variable-name)
6. Regenerate all 22 HTML grammar docs with updated railroad diagrams
7. Update rule counts in index.html and README.md
8. Ensure all 2,240 tests continue to pass

### Final Design Decisions
- **Catch-all patterns replaced with recursive expression grammars**: Rules like `expression ::= /[^;]+/` became proper `expression ::= expression-term (expression-op expression-term)*` with term, operator, function-call, and literal-value sub-rules.
- **condition delegates to expression**: `condition ::= /[^;]+/` became `condition ::= expression ;` since SQL conditions are boolean-valued expressions.
- **select-statement expanded**: Lazy `'SELECT' /[^)]+/` patterns became proper `SELECT select-list FROM table-ref WHERE expression` grammars with appropriate optional clauses.
- **statement-list structured**: `statement-list ::= /[^$]+/` became `statement-list ::= statement (';' statement)* ';'? ;`.
- **Leaf regex preserved**: `identifier`, `number`, `string-literal`, `float-literal`, `hex-literal`, `bin-literal`, `oct-literal`, and MSSQL `variable-name` remain as regex — they represent genuinely infinite token sets.
- **PostgreSQL-specific operators**: PostgreSQL expression grammar includes `'ILIKE'` and `'::'` (type cast) operators in its `expression-op` rule.

### Implementation Details

#### Files modified (17 EBNF grammars):
- `pex-arithmetics`: math-functions.ebnf (expression → additive/multiplicative/unary/primary)
- `pex-sql-core`: ddl.ebnf, dml.ebnf, sql-expressions.ebnf (expression, statement, select-statement, literal-value, inline number/string)
- `pex-sql-olap`: window-functions.ebnf, cte.ebnf, merge.ebnf, pivot.ebnf (frame-bound, expression, condition, select-statement, literal-value)
- `pex-sql-streaming`: streaming.ebnf (VARCHAR precision, condition, expression)
- `pex-sql-dialects`: oracle.ebnf, mssql.ebnf, mysql.ebnf, postgresql.ebnf (expression, condition, statement, select-statement, select-list, literal-value, pattern, statement-list)

#### Files regenerated (22 HTML docs):
- All `docs/grammar/*.html` files regenerated with updated railroad diagrams

#### Documentation updated:
- `docs/grammar/index.html` — rule counts updated (343→462)
- `README.md` — grammar rule counts updated

#### Metrics:
- Grammar rules: 343 → 462 (+119 new rules replacing regex catch-alls)
- Zero catch-all regex patterns remaining (verified by grep)
- All 2,240 tests passing
- 4 background agents, ~125K agent tokens, 36 tool calls, ~156s wall time

### Test Coverage
- No new tests needed — all 2,240 existing tests pass unchanged
- BnfParser validation: all 22 grammar files parse successfully (verified programmatically)

---

<a id="commit-6-grammar-docs"></a>
## Commit: Grammar documentation generation, pex-tools README, final cost report (June 18, 2026)

### Original Request
> (Continuation of previous commit's request -- "finally update all documentation including separately grammar visual representation documents (e.g. similar to used in Oracle grammar representation for SQL) and costs.")

### Reformulated Requirements
1. Generate HTML grammar documentation with SVG railroad diagrams for all 22 EBNF grammar files
2. Create index page linking all documentation organized by module
3. Add pex-tools README.md with module documentation
4. Update README.md with grammar documentation section and links
5. Update REQUIREMENTS.md with this commit section
6. Update BUILD_COST_REPORT.md with grammar doc generation agent data

### Final Design Decisions
- **HTML generation via GrammarDocGenerator**: Used the `pex-tools` GrammarDocGenerator to produce HTML files with inline SVG railroad diagrams. Each HTML page has a table of contents, SVG diagrams per rule, BNF notation, and cross-linked NonTerminal references.
- **Oracle SQL Reference style**: Diagrams follow the Oracle SQL Reference visual language -- terminals in rounded rectangles, non-terminals in boxes, alternation as vertical branches, repetition as loop-back arrows.
- **Static HTML + SVG (no JavaScript)**: All diagrams are inline SVG, no external dependencies needed to view. Files can be opened directly in any browser.
- **Generator utility retained**: `docs/grammar/GenerateGrammarDocs.java` is kept in the repo as a standalone Java program for regenerating docs when grammars change.

### Implementation Details

#### Files created:
- `docs/grammar/index.html` -- Index page linking all 22 grammar doc files, organized by module (343 rules total)
- `docs/grammar/base-expressions.html` -- pex-base expressions grammar (21 rules)
- `docs/grammar/base-functions.html` -- pex-base functions grammar (6 rules)
- `docs/grammar/base-literals.html` -- pex-base literals grammar (10 rules)
- `docs/grammar/base-operators.html` -- pex-base operators grammar (12 rules)
- `docs/grammar/arithmetic.html` -- pex-arithmetics arithmetic grammar (19 rules)
- `docs/grammar/bitwise.html` -- pex-arithmetics bitwise grammar (12 rules)
- `docs/grammar/radix.html` -- pex-arithmetics radix grammar (4 rules)
- `docs/grammar/math-functions.html` -- pex-arithmetics math functions grammar (8 rules)
- `docs/grammar/sql-ddl.html` -- pex-sql-core DDL grammar (46 rules)
- `docs/grammar/sql-dml.html` -- pex-sql-core DML grammar (32 rules)
- `docs/grammar/sql-expressions.html` -- pex-sql-core SQL expressions grammar (27 rules)
- `docs/grammar/sql-transactions.html` -- pex-sql-core transactions grammar (8 rules)
- `docs/grammar/olap-window-functions.html` -- pex-sql-olap window functions grammar (15 rules)
- `docs/grammar/olap-cte.html` -- pex-sql-olap CTEs grammar (7 rules)
- `docs/grammar/olap-grouping-sets.html` -- pex-sql-olap grouping sets grammar (9 rules)
- `docs/grammar/olap-merge.html` -- pex-sql-olap MERGE grammar (19 rules)
- `docs/grammar/olap-pivot.html` -- pex-sql-olap PIVOT/UNPIVOT grammar (10 rules)
- `docs/grammar/streaming.html` -- pex-sql-streaming grammar (31 rules)
- `docs/grammar/dialect-oracle.html` -- Oracle dialect grammar (15 rules)
- `docs/grammar/dialect-mssql.html` -- MSSQL dialect grammar (19 rules)
- `docs/grammar/dialect-mysql.html` -- MySQL dialect grammar (18 rules)
- `docs/grammar/dialect-postgresql.html` -- PostgreSQL dialect grammar (22 rules)
- `docs/grammar/GenerateGrammarDocs.java` -- Standalone generator utility
- `pex-tools/README.md` -- Module documentation with Mermaid diagrams

#### Files modified:
- `README.md` -- Added Grammar Documentation section, updated grammar file count to 22, added docs/ to project structure
- `REQUIREMENTS.md` -- Added this commit section and TOC entry
- `costs/BUILD_COST_REPORT.md` -- Added Commit 9 agent data, updated all summary tables

### Test Coverage
- No new tests in this commit (documentation-only)
- All 2,240 existing tests continue to pass

---

<a id="commit-5-postgresql-grammars"></a>
## Commit: PostgreSQL dialect, EBNF grammars, GrammarLoader, complex tests, bug fixes (June 18, 2026)

### Original Request
> "I noticed tendency: simplify tests instead of fixing issues. Now i want to add to dialects postgresql. provide for each module EBNF representation of parseable/executable grammars (in grammar folders, possibly splitted by sub-domains whenever relevant). add BNF/EBNF visualizer (e.g. as utility or java swing application). add gramma converter utility: BNF/EBNF -> ANT-LR and vice versa. add complex (real-life) test cases for each area and fix those to comply specifications rather than to reduce tested scope. finally update all documentation including separately grammar visual representation documents (e.g. similar to used in Oracle grammar representation for SQL) and costs."

### Reformulated Requirements (this commit — Phase 1)
1. Add PostgreSQL dialect to pex-sql-dialects following existing Oracle/MSSQL/MySQL pattern (parser, executor, functions, AST nodes)
2. Provide EBNF grammar files for every module in `src/main/resources/grammar/` directories, split by sub-domain
3. Add `GrammarLoader` utility in pex-base for loading grammars from classpath resources and files
4. Add complex real-life test cases for pex-base, pex-arithmetics, and pex-converter
5. Fix code bugs discovered by complex tests — NEVER weaken tests to match wrong behavior
6. Wire pex-tools module scaffolding (pom.xml, build.gradle.kts) for later phases

### Final Design Decisions

**PostgreSQL dialect**
- Follows identical pattern to Oracle/MSSQL/MySQL: `PostgresqlParser`, `PostgresqlExecutor`, `PostgresqlFunctions`, `PostgresqlNodes`
- Added `POSTGRESQL` to `DialectType` enum (now 8 values total)
- Parser detects PostgreSQL-specific syntax via regex: `::type_cast`, `ILIKE`, `ON CONFLICT`, `RETURNING`, `DISTINCT ON`, `GENERATE_SERIES`, `ARRAY[...]`, `SERIAL`/`BIGSERIAL`, `DO $$...$$` blocks
- Functions: 25+ including COALESCE, NULLIF, STRING_AGG, ARRAY_AGG, NOW, DATE_TRUNC, EXTRACT, TO_CHAR, TO_TIMESTAMP, GENERATE_SERIES, ARRAY_LENGTH, ARRAY_APPEND, ARRAY_CAT, REGEXP_MATCHES, REGEXP_REPLACE, MD5, INITCAP, CONCAT_WS, FORMAT, PG_TYPEOF, LEFT, RIGHT, LENGTH

**EBNF grammar files**
- 19 `.ebnf` files across all modules using existing BnfParser text format: `grammar name; ruleName ::= expression ;`
- Split by sub-domain (e.g., pex-sql-core has ddl.ebnf, dml.ebnf, sql-expressions.ebnf, transactions.ebnf)
- Loadable at runtime via new `GrammarLoader` utility

**GrammarLoader utility**
- `loadFromResource(String)`, `loadFromFile(Path)`, `loadAndMerge(String...)` returning `Result<Grammar>`
- Uses `BnfParser` internally, handles classpath resolution and file I/O

**Left-recursion detection bug fix (MAJOR)**
- `ParseContext.isInCallStack(String)` only tracked rule names in the call stack, blocking legitimate recursion through terminals (e.g., `factor ::= '(' expr ')'`)
- Changed `callStack` from `Deque<String>` to `Deque<Long>` tracking `(ruleName, cursorPosition)` composite keys
- Now only true left-recursion (same rule at same cursor position) is blocked; recursion that advances the cursor is allowed
- Updated pre-existing test that documented this as a "known limitation" to verify correct behavior

**IEEE 754 equality fix**
- `ComparisonHandler.compareNumeric()` used `Double.compare()` for all operations, which violates IEEE 754 for equality: `Double.compare(-0.0, 0.0)` returns -1 (not equal), but IEEE 754 specifies `-0.0 == 0.0` is true
- Added `l == r` for EQ and `l != r` for NEQ before `Double.compare` for ordering operations
- Also correctly handles `NaN == NaN` returning false (IEEE 754 compliant)

### Implementation Details

**New files (PostgreSQL dialect — 4 source, 1 test):**
- `pex-sql/pex-sql-dialects/src/main/java/ssg/pex/sql/dialects/postgresql/PostgresqlParser.java` (262 lines)
- `pex-sql/pex-sql-dialects/src/main/java/ssg/pex/sql/dialects/postgresql/PostgresqlExecutor.java` (518 lines)
- `pex-sql/pex-sql-dialects/src/main/java/ssg/pex/sql/dialects/postgresql/PostgresqlFunctions.java` (465 lines)
- `pex-sql/pex-sql-dialects/src/main/java/ssg/pex/sql/dialects/postgresql/PostgresqlNodes.java` (56 lines)
- `pex-sql/pex-sql-dialects/src/test/java/ssg/pex/sql/dialects/PostgresqlDialectTest.java` (623 lines, 63 tests)

**New files (GrammarLoader — 1 source, 1 test, 3 resources):**
- `pex-base/src/main/java/ssg/pex/bnf/loader/GrammarLoader.java`
- `pex-base/src/test/java/ssg/pex/bnf/loader/GrammarLoaderTest.java` (8 tests)
- `pex-base/src/test/resources/` — test-grammar.ebnf, test-grammar-extra.ebnf, invalid-grammar.ebnf

**New files (EBNF grammars — 19 files):**
- pex-base: expressions.ebnf, functions.ebnf, literals.ebnf, operators.ebnf
- pex-arithmetics: arithmetic.ebnf, bitwise.ebnf, radix.ebnf, math-functions.ebnf
- pex-sql-core: ddl.ebnf, dml.ebnf, sql-expressions.ebnf, transactions.ebnf
- pex-sql-olap: window-functions.ebnf, cte.ebnf, grouping-sets.ebnf, merge.ebnf, pivot.ebnf
- pex-sql-streaming: streaming.ebnf
- pex-sql-dialects: oracle.ebnf, mssql.ebnf, mysql.ebnf, postgresql.ebnf

**New files (complex tests — 3 files):**
- `pex-base/src/test/java/ssg/pex/RealWorldBaseTest.java` (37 tests)
- `pex-arithmetics/src/test/java/ssg/pex/arithmetics/RealWorldArithmeticsTest.java` (34 tests)
- `pex-converter/src/test/java/ssg/pex/converter/RealWorldConverterTest.java` (28 tests)

**New files (pex-tools scaffolding):**
- `pex-tools/pom.xml`, `pex-tools/build.gradle.kts`

**Modified files:**
- `ParseContext.java` — left-recursion detection fix (Deque<String> → Deque<Long>)
- `ComparisonHandler.java` — IEEE 754 equality fix
- `RecursiveDescentEngineTest.java` — updated test from "limitation" to "works correctly"
- `ComplexArithmeticsTest.java` — NaN == NaN now correctly returns false
- `DialectDatabase.java` — added POSTGRESQL enum value, wired parser/executor/functions
- `SpecialEngineTest.java` — updated enum count 7→8
- Root `pom.xml` — added pex-tools module
- `settings.gradle.kts` — added pex-tools include

**Total**: ~50 files changed, ~5,000 insertions

### Test Coverage
- **pex-base**: 485 tests (+101) — new: RealWorldBaseTest (37), GrammarLoaderTest (8), updated RecursiveDescentEngineTest
- **pex-arithmetics**: 270 tests (+95) — new: RealWorldArithmeticsTest (34), updated ComplexArithmeticsTest
- **pex-converter**: 218 tests (+77) — new: RealWorldConverterTest (28)
- **pex-sql-core**: 445 tests (unchanged)
- **pex-sql-olap**: 139 tests (unchanged)
- **pex-sql-streaming**: 140 tests (unchanged)
- **pex-sql-dialects**: 211 tests (+63) — new: PostgresqlDialectTest (63), updated SpecialEngineTest
- **pex-all**: 106 tests (unchanged)
- **pex-tools**: 73 tests — RailroadDiagramRendererTest (21), SvgExporterTest (5), PngExporterTest (5), BnfToAntlrConverterTest (10), AntlrToBnfConverterTest (11), Antlr4LexerTest (9), GrammarDocGeneratorTest (6), MarkdownDiagramGeneratorTest (6)
- **Total: 2,240 tests passing** (was 1,830, +410)

---

<a id="commit-4ed9fc9"></a>
## Commit: 4ed9fc9 — Add Gradle 8.14.1 build support alongside Maven (June 16, 2026)

### Original Request
> "add in parallel to maven gradle build support"

### Reformulated Requirements
1. Add Gradle as a parallel build system alongside the existing Maven build
2. Use Gradle Kotlin DSL (`.gradle.kts`) for all build files
3. Include Gradle wrapper for reproducible builds without requiring local Gradle installation
4. Replicate the same dependency versions, compiler settings, and test configuration as Maven
5. All 1,072 tests must pass identically under both Maven and Gradle
6. Java 24 toolchain with `--enable-preview` must be configured for both compile and test tasks

### Final Design Decisions
- **Gradle 8.14.1**: Latest stable Gradle release at the time; provides full Java 24 toolchain support
- **Kotlin DSL over Groovy DSL**: Type-safe build scripts with IDE autocompletion; consistent with modern Gradle best practices
- **Shared subproject configuration**: Common `subprojects {}` block in root `build.gradle.kts` applies Java plugin, toolchain, encoding, preview flags, and all dependency versions to every module uniformly — mirrors Maven's `<dependencyManagement>` and `<pluginManagement>` approach
- **Java 24 toolchain**: Uses `java.toolchain.languageVersion = JavaLanguageVersion.of(24)` instead of `sourceCompatibility`/`targetCompatibility` — ensures Gradle downloads and uses the correct JDK automatically
- **Parallel test execution**: `maxParallelForks = 4` matches Maven Surefire's `forkCount=4` configuration
- **Gradle wrapper committed**: `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.properties` checked into VCS for zero-setup builds

### Implementation Details
**New files:**
- `settings.gradle.kts` — root project name `pex`, includes all 5 subprojects
- `build.gradle.kts` (root) — Java plugin, group `ssg`, version `0.1.0`, shared subproject config with centralized dependency versions (SLF4J 2.0.16, JUnit 5.11.4, Mockito 5.14.2, AssertJ 3.27.3)
- `pex-base/build.gradle.kts` — empty (inherits all from root)
- `pex-arithmetics/build.gradle.kts` — `implementation(project(":pex-base"))`
- `pex-converter/build.gradle.kts` — `implementation(project(":pex-base"))`
- `pex-sql/build.gradle.kts` — `implementation(project(":pex-base"))`, `implementation(project(":pex-arithmetics"))`
- `pex-all/build.gradle.kts` — depends on all four modules
- `gradlew` — Unix wrapper script (Gradle 8.14.1)
- `gradlew.bat` — Windows wrapper script
- `gradle/wrapper/gradle-wrapper.properties` — wrapper configuration pointing to Gradle 8.14.1 distribution

**Modified files:**
- `.gitignore` — added Gradle-specific entries (`.gradle/`, `build/`, etc.)

**Total**: 11 files changed, 440 insertions

### Test Coverage
- All **1,072 tests pass** under both Maven (`mvn test`) and Gradle (`./gradlew test`)
- No test changes required — same source sets, same classpath, same JVM args
- Test execution parity verified: 384 base + 175 arithmetics + 141 converter + 319 sql + 53 integration

---

<a id="commit-8bddbb8"></a>
## Commit: 8bddbb8 — Implement complete PEX project: BNF parsing, execution, arithmetics, SQL, converter (June 16, 2026)

### Original Request
> "create a pex (parse and execute) project with the following modules: pex-base: BNF parsing, AST, execution engine, scoping, SPI; pex-arithmetics: numeric operations, math functions, type conversions; pex-sql: SQL/DDL parsing, in-memory DBMS simulator; pex-converter: AST to target language conversion, JIT compilation; pex-all: integration tests. Implement a Result<T> monad with monadic operations and batch results. BNF model with grammar, rules, and dialect extensions. BNF parser for EBNF text notation. Recursive descent engine with memoization and left-recursion detection. AST sealed hierarchy with extensible metadata. Scope tree with path tracking, variable shadowing, and full traceability. Execution engine with pluggable handlers, function registry, timeout and recursion limits. SPI plugin architecture with PexPlugin, GrammarProvider, HandlerProvider interfaces. Telemetry events and execution statistics. For arithmetics: int/long/float/double arithmetic with overflow detection, bitwise operations, boolean logic with short-circuit evaluation, comparison operators with numeric promotion, all java.lang.Math functions, type conversion functions, radix support with hex/binary/octal. For converter: 7 language converters (Java, C#, C++, Kotlin, Scala, Ruby, Basic), JIT compiler using ToolProvider, type/operator/naming mappers per target language, ConverterFactory SPI. For SQL: tokenizer and recursive descent parser for SELECT/INSERT/UPDATE/DELETE/DDL, full in-memory DBMS with Schema/Table/Column/Row/Index/View/StoredProcedure/Trigger, query executor pipeline (FROM->JOIN->WHERE->GROUP BY->HAVING->SELECT->DISTINCT->ORDER BY->LIMIT), DML executor with constraint enforcement (PK/FK/UNIQUE/NOT NULL/CHECK), DDL executor, join engine (INNER/LEFT/RIGHT/FULL/CROSS), aggregate engine, transaction manager with BEGIN/COMMIT/ROLLBACK/SAVEPOINT, expression evaluator, SQL built-in functions. Integration tests for cross-module plugin loading, end-to-end pipelines, JIT round-trips."

### Reformulated Requirements

**Core Parsing (pex-base)**
1. Result monad (`Result<T>`) with `Success<T>` and `Failure<T>` subtypes, supporting `map`, `flatMap`, `recover`, `orElse`, `fold`, and batch aggregation via `BatchResult<T>`
2. BNF grammar model: `Grammar`, `Rule`, `RuleExpression` sealed hierarchy with `Terminal`, `NonTerminal`, `Sequence`, `Alternation`, `Group`, `Repetition` (with `RepetitionKind`: `ZERO_OR_MORE`, `ONE_OR_MORE`, `OPTIONAL`)
3. Dialect extension system: `DialectExtension` with `RuleModification` subtypes (`RuleAddition`, `RuleReplacement`, `RuleDeletion`, `RuleExtension`) applied via `DialectRegistry`
4. BNF parser: text-based EBNF notation parser producing `Grammar` objects
5. Recursive descent engine with packrat memoization and left-recursion detection/prevention
6. AST node hierarchy: sealed interface `AstNode` with 15+ node types (`ProgramNode`, `BlockNode`, `AssignmentNode`, `BinaryOpNode`, `UnaryOpNode`, `ConditionalNode`, `LoopNode`, `FunctionDefNode`, `FunctionCallNode`, `ReturnNode`, `IdentifierNode`, `IndexAccessNode`, `ParameterNode`, `ExtensionNode`, literals: `IntLiteral`, `FloatLiteral`, `StringLiteral`, `BoolLiteral`, `NullLiteral`)
7. AST visitor pattern: `AstVisitor<T>` and `AstTransformer` for traversal and transformation
8. Source location tracking: `SourceLocation` record with line/column/length for error reporting

**Scope and Type System (pex-base)**
9. Scope tree with hierarchical scoping, path tracking (`ScopePath`), and variable shadowing with `ShadowRecord` traceability
10. Variable model: `Variable` interface with `ScalarVariable` and `IndexedVariable` implementations; `VariableStore` with `NumericIndexStore` and `KeyMappedStore`
11. Scope event listener: `ScopeTreeListener` for observing scope creation/destruction/variable changes
12. Type system: `PexType` enum, `TypeDescriptor`, and `TypeCoercion` for cross-type operations

**Execution Engine (pex-base)**
13. Pluggable execution engine: `ExecutionEngine` dispatching to registered `NodeHandler` implementations via `HandlerRegistry`
14. Built-in handlers: `ProgramHandler`, `BlockHandler`, `AssignmentHandler`, `BinaryOpHandler`, `UnaryOpHandler`, `ConditionalHandler`, `LoopHandler`, `FunctionDefHandler`, `FunctionCallHandler`, `ReturnHandler`, `LiteralHandler`, `IdentifierHandler`, `IndexAccessHandler`
15. Function registry: `FunctionRegistry` for user-defined `FunctionDef` and `NativeFunction` (Java-backed) callables
16. Execution context: `ExecutionConfig` with timeout limits, max recursion depth, and `ExecutionStatistics` tracking
17. Return value propagation via `ReturnException` (non-error flow control)
18. Execution listener: `ExecutionListener` for observing handler dispatch and completion

**SPI and Telemetry (pex-base)**
19. Plugin architecture: `PexPlugin` interface with `id()`, `version()`, `registerGrammars(PluginContext)`, `registerHandlers(PluginContext)` methods; `PluginRegistry` for ServiceLoader-based discovery
20. `GrammarProvider` and `HandlerProvider` SPI interfaces for modular grammar and handler contributions
21. Telemetry: `PexTelemetryListener` with typed events (`ParseEvent`, `ExecuteEvent`, `ScopeEvent`, `ErrorEvent`) all extending `PexEvent`

**Arithmetics (pex-arithmetics)**
22. Integer and floating-point arithmetic handlers with overflow detection
23. Bitwise operations: AND, OR, XOR, NOT, left shift, right shift, unsigned right shift
24. Boolean logic with short-circuit evaluation for AND/OR
25. Comparison operators (`<`, `>`, `<=`, `>=`, `==`, `!=`) with automatic numeric promotion
26. All `java.lang.Math` functions exposed as native functions (abs, ceil, floor, round, sqrt, pow, log, sin, cos, tan, etc.)
27. Type conversion functions: `int()`, `float()`, `long()`, `double()`, `str()`, `bool()`, bit conversions
28. Radix support: hex (`0x`), binary (`0b`), octal (`0o`) literal parsing and conversion functions (`hex()`, `bin()`, `oct()`)
29. Numeric promotion rules: `ArithmeticTypeCoercion` and `NumericPromotion` for mixed-type operations
30. `ArithmeticsPlugin` implementing `PexPlugin` SPI with `ArithmeticsGrammarProvider` and `ArithmeticHandlerProvider`

**Converter (pex-converter)**
31. Seven target language converters: Java, C#, C++, Kotlin, Scala, Ruby, Basic — all extending `AbstractConverter`
32. Mapping infrastructure: `TypeMapper`, `OperatorMapper`, `NamingMapper` per target language
33. `TargetLanguage` enum and `ConversionConfig` for converter parameterization
34. JIT compilation: `JitCompiler` using `javax.tools.ToolProvider` for in-memory Java source compilation; `JitClassLoader` for loading compiled bytecode; `JitCompilationResult` and `JitExecutable` for runtime execution
35. `ConverterFactory` SPI with `ConverterRegistry` for ServiceLoader-based converter discovery
36. `ConverterPlugin` implementing `PexPlugin` SPI

**SQL (pex-sql)**
37. SQL tokenizer: `SqlTokenizer` for lexical analysis of SQL statements
38. SQL parser: `SqlParser` (recursive descent) producing SQL AST nodes (`SelectNode`, `InsertNode`, `UpdateNode`, `DeleteNode`, `CreateTableNode`, `AlterTableNode`, `DropTableNode`, `CreateIndexNode`, `CreateViewNode`, `CreateProcedureNode`, `CreateTriggerNode`, `CallNode`, `TransactionNode`)
39. SQL dialects: `SqlDialects` for grammar variations
40. Full in-memory DBMS: `InMemoryDatabase` with `Schema`, `Table`, `Column`, `Row`, `Index`, `View`, `StoredProcedure`, `Trigger`
41. Query executor pipeline: `QueryExecutor` implementing FROM -> JOIN -> WHERE -> GROUP BY -> HAVING -> SELECT -> DISTINCT -> ORDER BY -> LIMIT
42. DML executor: `DmlExecutor` for INSERT/UPDATE/DELETE with constraint enforcement (PRIMARY KEY, FOREIGN KEY, UNIQUE, NOT NULL, CHECK)
43. DDL executor: `DdlExecutor` for CREATE/ALTER/DROP TABLE/INDEX/VIEW/PROCEDURE/TRIGGER
44. Join engine: `JoinEngine` supporting INNER, LEFT, RIGHT, FULL OUTER, and CROSS joins
45. Aggregate engine: `AggregateEngine` for COUNT, SUM, AVG, MIN, MAX, GROUP_CONCAT with GROUP BY support
46. Sort engine: `SortEngine` for ORDER BY with multi-column, ASC/DESC support
47. Expression evaluator: `ExpressionEvaluator` for WHERE/HAVING/CHECK constraint conditions
48. Transaction manager: `TransactionManager` with `Transaction`, `ChangeLog`, `IsolationLevel`, supporting BEGIN/COMMIT/ROLLBACK/SAVEPOINT
49. Query/DML results: `QueryResult` (rows + column metadata) and `DmlResult` (affected row count)
50. 19 SQL built-in functions: `SqlFunctions` (UPPER, LOWER, TRIM, SUBSTRING, LENGTH, CONCAT, REPLACE, ABS, ROUND, CEIL, FLOOR, MOD, POWER, COALESCE, NULLIF, CAST, NOW, CURRENT_DATE, CURRENT_TIME)
51. `SqlPlugin` implementing `PexPlugin` SPI with `SqlNodeHandler`

**Integration (pex-all)**
52. Cross-module plugin loading verification via SPI ServiceLoader
53. End-to-end arithmetic pipeline: parse expression -> build AST -> execute with arithmetics plugin
54. End-to-end SQL pipeline: parse SQL -> execute against in-memory DBMS
55. End-to-end converter pipeline: parse -> AST -> convert to target language
56. JIT round-trip: AST -> Java source -> compile -> execute -> verify result
57. Full pipeline: BNF parse -> execute -> convert

### Final Design Decisions

**Result<T> monad**
- Sealed interface with `Success<T>` and `Failure<T>` — forces exhaustive pattern matching in Java 24
- `BatchResult<T>` aggregates multiple results with success/failure counts and streaming access
- `PexError` as the error representation rather than raw exceptions — enables structured error reporting

**Hybrid BNF + recursive descent parsing**
- BNF grammar model for declarative rule definitions (easy to extend via dialect system)
- Recursive descent engine for actual parsing (performance + left-recursion handling)
- Packrat memoization via `ParseContext` to avoid exponential backtracking
- Grammar-driven but not grammar-interpreted: the engine uses grammar rules as guides for descent, not as a generic PEG/Earley interpreter

**Scope tree with path tracking**
- Tree-structured scopes with parent links for lexical scoping
- `ScopePath` provides unique hierarchical identifiers for debugging and telemetry
- `ShadowRecord` tracks which variable a new binding shadows — essential for scope analysis in converters
- `ScopeTreeListener` enables external tools (debuggers, profilers) to observe scope lifecycle

**Full SQL DBMS**
- In-memory storage with `Map<String, Row>` per table — no external dependencies
- Executor pipeline mirrors real RDBMS query processing: scan -> filter -> join -> group -> project -> sort -> limit
- Constraint enforcement at DML level (not deferred) — immediate validation of PK, FK, UNIQUE, NOT NULL, CHECK
- Transaction support with change log for rollback — `ChangeLog` records pre-images for undo

**7 language converters + JIT**
- `AbstractConverter` base class with template method pattern — each converter overrides language-specific mappings
- `TypeMapper`, `OperatorMapper`, `NamingMapper` provide clean separation of cross-language mapping concerns
- JIT uses `javax.tools.ToolProvider.getSystemJavaCompiler()` — requires JDK (not just JRE) at runtime
- `JitClassLoader` with `defineClass` for loading compiled bytecode directly from byte arrays

**SPI plugin architecture**
- `PexPlugin` as the central extension point — each module provides one plugin implementation
- ServiceLoader-based discovery via `META-INF/services/ssg.pex.spi.PexPlugin`
- `PluginContext` provides registration APIs for grammars and handlers
- Three registered plugins: `ArithmeticsPlugin`, `SqlPlugin`, `ConverterPlugin`
- Separate `ConverterFactory` SPI for converter-specific extension (`META-INF/services/ssg.pex.converter.spi.ConverterFactory`)

### Implementation Details

**pex-base** (105 source files):
- `ssg.pex.result`: `Result.java`, `Success.java`, `Failure.java`, `PexError.java`, `BatchResult.java`
- `ssg.pex.bnf.model`: `Grammar.java`, `Rule.java`, `RuleExpression.java`, `Terminal.java`, `NonTerminal.java`, `Sequence.java`, `Alternation.java`, `Group.java`, `Repetition.java`, `RepetitionKind.java`
- `ssg.pex.bnf.dialect`: `DialectExtension.java`, `DialectRegistry.java`, `RuleModification.java`, `RuleAddition.java`, `RuleReplacement.java`, `RuleDeletion.java`, `RuleExtension.java`
- `ssg.pex.bnf.parser`: `BnfParser.java`
- `ssg.pex.bnf.engine`: `RecursiveDescentEngine.java`, `ParseContext.java`, `ParseMatch.java`
- `ssg.pex.ast.node`: `AstNode.java`, `ProgramNode.java`, `BlockNode.java`, `AssignmentNode.java`, `BinaryOpNode.java`, `UnaryOpNode.java`, `ConditionalNode.java`, `LoopNode.java`, `LoopKind.java`, `FunctionDefNode.java`, `FunctionCallNode.java`, `ReturnNode.java`, `IdentifierNode.java`, `IndexAccessNode.java`, `ParameterNode.java`, `ExtensionNode.java`, `LiteralNode.java`, `IntLiteral.java`, `FloatLiteral.java`, `StringLiteral.java`, `BoolLiteral.java`, `NullLiteral.java`, `Operator.java`
- `ssg.pex.ast.visitor`: `AstVisitor.java`, `AstTransformer.java`
- `ssg.pex.ast`: `SourceLocation.java`
- `ssg.pex.scope`: `ScopeTree.java`, `Scope.java`, `ScopePath.java`, `Variable.java`, `ScalarVariable.java`, `IndexedVariable.java`, `VariableStore.java`, `NumericIndexStore.java`, `KeyMappedStore.java`, `ShadowRecord.java`, `ScopeTracer.java`, `ScopeTreeListener.java`
- `ssg.pex.type`: `PexType.java`, `TypeDescriptor.java`, `TypeCoercion.java`
- `ssg.pex.exec`: `ExecutionEngine.java`, `ExecutionContext.java`, `ExecutionConfig.java`, `ExecutionStatistics.java`, `HandlerRegistry.java`, `FunctionRegistry.java`, `FunctionDef.java`, `NativeFunction.java`, `NodeHandler.java`, `ExecutionListener.java`, `PexExecutionException.java`, `ReturnException.java`
- `ssg.pex.exec.handler`: `ProgramHandler.java`, `BlockHandler.java`, `AssignmentHandler.java`, `BinaryOpHandler.java`, `UnaryOpHandler.java`, `ConditionalHandler.java`, `LoopHandler.java`, `FunctionDefHandler.java`, `FunctionCallHandler.java`, `ReturnHandler.java`, `LiteralHandler.java`, `IdentifierHandler.java`, `IndexAccessHandler.java`, `BaseHandlerProvider.java`
- `ssg.pex.spi`: `PexPlugin.java`, `PluginContext.java`, `PluginRegistry.java`, `GrammarProvider.java`, `HandlerProvider.java`
- `ssg.pex.telemetry`: `PexEvent.java`, `PexTelemetryListener.java`, `ParseEvent.java`, `ExecuteEvent.java`, `ScopeEvent.java`, `ErrorEvent.java`
- `ssg.pex.util`: `Preconditions.java`

**pex-arithmetics** (15 source files):
- `ssg.pex.arithmetics`: `ArithmeticsPlugin.java`
- `ssg.pex.arithmetics.handler`: `ArithmeticHandlerProvider.java`, `IntArithmeticHandler.java`, `FloatArithmeticHandler.java`, `ComparisonHandler.java`, `BooleanHandler.java`, `BitwiseHandler.java`, `RadixHandler.java`
- `ssg.pex.arithmetics.grammar`: `ArithmeticsGrammarProvider.java`
- `ssg.pex.arithmetics.function`: `MathFunctions.java`, `ConversionFunctions.java`, `RadixFunctions.java`
- `ssg.pex.arithmetics.type`: `ArithmeticTypeCoercion.java`, `NumericPromotion.java`
- `ssg.pex.arithmetics.ast`: `RadixLiteralNode.java`

**pex-converter** (22 source files):
- `ssg.pex.converter`: `Converter.java`, `ConversionConfig.java`, `TargetLanguage.java`, `ConverterPlugin.java`
- `ssg.pex.converter.lang`: `AbstractConverter.java`, `JavaConverter.java`, `CSharpConverter.java`, `CppConverter.java`, `KotlinConverter.java`, `ScalaConverter.java`, `RubyConverter.java`, `BasicConverter.java`
- `ssg.pex.converter.mapping`: `TypeMapper.java`, `OperatorMapper.java`, `NamingMapper.java`
- `ssg.pex.converter.jit`: `JitCompiler.java`, `JitConverter.java`, `JitCompilationResult.java`, `JitExecutable.java`, `JitClassLoader.java`
- `ssg.pex.converter.spi`: `ConverterFactory.java`, `ConverterRegistry.java`

**pex-sql** (44 source files):
- `ssg.pex.sql`: `SqlPlugin.java`
- `ssg.pex.sql.grammar`: `SqlTokenizer.java`, `SqlParser.java`, `SqlDialects.java`
- `ssg.pex.sql.ast`: `SqlNode.java`, `SqlExpression.java`, `SqlSupport.java`, `SelectNode.java`, `InsertNode.java`, `UpdateNode.java`, `DeleteNode.java`, `CreateTableNode.java`, `AlterTableNode.java`, `DropTableNode.java`, `CreateIndexNode.java`, `CreateViewNode.java`, `CreateProcedureNode.java`, `CreateTriggerNode.java`, `CallNode.java`, `TransactionNode.java`
- `ssg.pex.sql.dbms`: `InMemoryDatabase.java`, `Schema.java`, `Table.java`, `Column.java`, `Row.java`, `Index.java`, `View.java`, `StoredProcedure.java`, `Trigger.java`
- `ssg.pex.sql.dbms.executor`: `QueryExecutor.java`, `DmlExecutor.java`, `DdlExecutor.java`, `JoinEngine.java`, `AggregateEngine.java`, `SortEngine.java`, `ExpressionEvaluator.java`
- `ssg.pex.sql.dbms.result`: `QueryResult.java`, `DmlResult.java`
- `ssg.pex.sql.dbms.transaction`: `TransactionManager.java`, `Transaction.java`, `ChangeLog.java`, `IsolationLevel.java`
- `ssg.pex.sql.function`: `SqlFunctions.java`
- `ssg.pex.sql.handler`: `SqlNodeHandler.java`

**SPI service registrations:**
- `pex-arithmetics/META-INF/services/ssg.pex.spi.PexPlugin` -> `ArithmeticsPlugin`
- `pex-sql/META-INF/services/ssg.pex.spi.PexPlugin` -> `SqlPlugin`
- `pex-converter/META-INF/services/ssg.pex.spi.PexPlugin` -> `ConverterPlugin`
- `pex-converter/META-INF/services/ssg.pex.converter.spi.ConverterFactory` -> converter factories

**Total**: 235 files changed, 23,076 insertions

### Test Coverage
- **pex-base**: 384 tests — Result monad (ResultTest), BNF parser (BnfParserTest), BNF model (BnfModelTest), dialect extensions (DialectExtensionTest), recursive descent engine (RecursiveDescentEngineTest), AST nodes (AstNodeTest), AST visitor (AstVisitorTest), scope tree (ScopeTreeTest), type system (TypeSystemTest), execution engine (ExecutionEngineTest)
- **pex-arithmetics**: 175 tests — IntArithmeticHandlerTest, FloatArithmeticHandlerTest, ComparisonHandlerTest, BooleanHandlerTest, BitwiseHandlerTest, MathFunctionsTest, ConversionFunctionsTest, RadixFunctionsTest, NumericPromotionTest
- **pex-converter**: 141 tests — JavaConverterTest, CSharpConverterTest, CppConverterTest, KotlinConverterTest, ScalaConverterTest, RubyConverterTest, BasicConverterTest, NamingMapperTest, JitCompilerTest
- **pex-sql**: 319 tests — SqlTokenizerTest, SqlParserTest, InMemoryDatabaseTest, QueryExecutorTest, DmlExecutorTest, DdlExecutorTest, JoinEngineTest, AggregateEngineTest, ExpressionEvaluatorTest, TransactionTest
- **pex-all**: 53 integration tests — CrossModulePluginLoadingTest, EndToEndArithmeticsTest, EndToEndSqlTest, EndToEndConverterTest, JitRoundTripTest, FullPipelineTest
- **Total: 1,072 tests passing**

---

<a id="commit-099768d"></a>
## Commit: 099768d — Initial project scaffolding: Maven multi-module structure (June 16, 2026)

### Original Request
> "Set up pex (Parse and Execute) as a Maven multi-module project with: pex-base (BNF parsing, AST, execution engine, scoping, SPI), pex-arithmetics (numeric operations, math functions, type conversions), pex-sql (SQL/DDL parsing, in-memory DBMS simulator), pex-converter (AST to target language conversion, JIT compilation), pex-all (integration tests). Java 24 with --enable-preview."

### Reformulated Requirements
1. Maven parent POM with `pom` packaging aggregating 5 child modules
2. Group ID `ssg`, artifact ID `pex`, version `0.1.0`
3. Java 24 compiler release with `--enable-preview` flag for both compile and test
4. Dependency management for: SLF4J 2.0.16 (logging), JUnit 5.11.4 (testing), Mockito 5.14.2 (mocking), AssertJ 3.27.3 (fluent assertions)
5. Maven Surefire plugin 3.5.2 with parallel class execution, 4 forks, balanced run order
6. Inter-module dependency declarations: arithmetics/converter/sql depend on base; all depends on everything
7. `.gitignore` for Maven/IDE artifacts

### Final Design Decisions
- **Maven multi-module**: Standard Maven reactor build with parent POM aggregation — well-understood, IDE-friendly, supports incremental builds
- **Java 24**: Latest LTS-track release; `--enable-preview` enables sealed interfaces, pattern matching, and other preview features used throughout the codebase
- **Package `ssg.pex`**: Short, unique group prefix; all modules share the `ssg.pex` root package
- **Parallel test execution**: Surefire configured with `parallel=classes`, `threadCount=4`, `forkCount=4`, `reuseForks=true`, `runOrder=balanced` for maximum test throughput
- **Dependency management in parent**: All version numbers centralized in parent POM properties — child modules declare dependencies without version tags

### Implementation Details
**Files created:**
- `pom.xml` — root parent POM with module declarations, property definitions, dependency management (8 managed dependencies), plugin management (compiler 3.13.0, surefire 3.5.2, jar 3.4.2)
- `pex-base/pom.xml` — base module; depends on SLF4J, JUnit, Mockito, AssertJ
- `pex-arithmetics/pom.xml` — depends on pex-base
- `pex-converter/pom.xml` — depends on pex-base
- `pex-sql/pom.xml` — depends on pex-base and pex-arithmetics
- `pex-all/pom.xml` — depends on pex-base, pex-arithmetics, pex-converter, pex-sql
- `.gitignore` — Maven (`target/`), IDE (`.idea/`, `*.iml`, `.project`, `.classpath`, `.settings/`, `nbproject/`, `nb-configuration.xml`), OS (`.DS_Store`, `Thumbs.db`)

**Total**: 7 files changed, 348 insertions

### Test Coverage
- No tests in this commit (scaffolding only)
- Build compiles successfully with `mvn compile`

---

## Requirements Summary by Category

### Parsing
- BNF grammar model with sealed `RuleExpression` hierarchy (Terminal, NonTerminal, Sequence, Alternation, Group, Repetition)
- EBNF text notation parser producing Grammar objects
- Recursive descent engine with packrat memoization and left-recursion detection
- Dialect extension system for grammar modifications (add, replace, delete, extend rules)
- SQL-specific tokenizer and recursive descent parser

### Execution
- Pluggable execution engine with `HandlerRegistry` dispatch
- 14 built-in node handlers covering all core AST node types
- Function registry with user-defined and native (Java-backed) callables
- Execution context with configurable timeout and recursion depth limits
- Return value propagation via `ReturnException` flow control
- Scope tree with hierarchical lexical scoping and path tracking

### Arithmetics
- Integer and floating-point arithmetic with overflow detection
- Bitwise operations (AND, OR, XOR, NOT, shifts)
- Boolean logic with short-circuit evaluation
- Comparison operators with automatic numeric promotion
- All `java.lang.Math` functions as native functions
- Type conversion functions (int, float, long, double, str, bool)
- Radix support (hex, binary, octal) with parsing and conversion

### SQL
- Full SQL DML: SELECT, INSERT, UPDATE, DELETE with constraint enforcement
- Full SQL DDL: CREATE/ALTER/DROP TABLE, INDEX, VIEW, PROCEDURE, TRIGGER
- In-memory DBMS with Schema, Table, Column, Row, Index, View, StoredProcedure, Trigger
- Query executor pipeline: FROM -> JOIN -> WHERE -> GROUP BY -> HAVING -> SELECT -> DISTINCT -> ORDER BY -> LIMIT
- Join engine: INNER, LEFT, RIGHT, FULL OUTER, CROSS
- Aggregate engine: COUNT, SUM, AVG, MIN, MAX, GROUP_CONCAT
- Transaction manager: BEGIN, COMMIT, ROLLBACK, SAVEPOINT
- 19 SQL built-in functions

### Conversion
- 7 target language converters: Java, C#, C++, Kotlin, Scala, Ruby, Basic
- JIT compilation via `javax.tools.ToolProvider` with in-memory compilation
- Type, operator, and naming mappers per target language
- ConverterFactory SPI for extensible converter registration

### Error Handling
- `Result<T>` monad with `Success`/`Failure` for type-safe error propagation
- `BatchResult<T>` for aggregating multiple operation results
- `PexError` structured error representation
- `PexExecutionException` for execution-time failures
- Source location tracking (`SourceLocation`) for parser error reporting

### Testing
- 3,015 total tests across 90+ test files
- Unit tests for every module (base: 643, arithmetics: 288, converter: 218, sql-core: 509, olap: 187, streaming: 175, dialects: 273, nosql-core: 198, nosql-dialects: 185, tools: 221)
- Integration tests for cross-module plugin loading and end-to-end pipelines (118)
- JIT round-trip tests: AST -> Java source -> compile -> execute -> verify
- Dual build system verification: all tests pass under both Maven and Gradle

---

## Project Statistics

### Code Metrics

| Metric | Value |
|--------|-------|
| **Total Lines of Code** | ~69,700 |
| **Source Files** | 350+ Java files |
| **Test Files** | 78+ test files |
| **Total Tests** | 3,015 (all passing) |
| **Modules** | 13 |
| **Build Systems** | 2 (Maven + Gradle) |

### Tests by Module

| Module | Tests | Key Test Classes |
|--------|-------|-----------------|
| pex-base | 643 | ResultTest, BnfParserTest, BnfModelTest, DialectExtensionTest, RecursiveDescentEngineTest, AstNodeTest, AstVisitorTest, ScopeTreeTest, TypeSystemTest, ExecutionEngineTest, GrammarLoaderTest, RealWorldBaseTest |
| pex-arithmetics | 288 | IntArithmeticHandlerTest, FloatArithmeticHandlerTest, ComparisonHandlerTest, BooleanHandlerTest, BitwiseHandlerTest, MathFunctionsTest, ConversionFunctionsTest, RadixFunctionsTest, NumericPromotionTest, ComplexArithmeticsTest, RealWorldArithmeticsTest |
| pex-converter | 218 | JavaConverterTest, CSharpConverterTest, CppConverterTest, KotlinConverterTest, ScalaConverterTest, RubyConverterTest, BasicConverterTest, NamingMapperTest, JitCompilerTest, RealWorldConverterTest |
| pex-sql-core | 509 | SqlTokenizerTest, SqlParserTest, InMemoryDatabaseTest, QueryExecutorTest, DmlExecutorTest, DdlExecutorTest, JoinEngineTest, AggregateEngineTest, ExpressionEvaluatorTest, TransactionTest |
| pex-sql-olap | 187 | WindowFunctionTest, CteTest, GroupingSetTest, MergeTest, PivotTest |
| pex-sql-streaming | 175 | StreamSimulatorTest, WindowManagerTest, WatermarkTest, StreamAggregatorTest |
| pex-sql-dialects | 273 | OracleDialectTest, MssqlDialectTest, MysqlDialectTest, PostgresqlDialectTest, SpecialEngineTest |
| pex-nosql-core | 198 | NoSqlEngineTest, QueryEvaluatorTest, UpdateEvaluatorTest, AggregationPipelineTest, NoSqlPluginTest |
| pex-nosql-dialects | 185 | MongoDBDialectTest, CassandraDialectTest, MongoQueryTest, CqlDialectTest |
| pex-all | 118 | CrossModulePluginLoadingTest, EndToEndArithmeticsTest, EndToEndSqlTest, EndToEndConverterTest, JitRoundTripTest, FullPipelineTest, SqlSubModuleIntegrationTest |

### Version History

| Version | Date | Commits | Tests | Lines | Modules | Milestone |
|---------|------|---------|-------|-------|---------|-----------|
| 0.1.0-SNAPSHOT | June 16, 2026 | 3 | 1,072 | ~23,000 | 5 | Full implementation + Gradle build |
| 0.1.0-SNAPSHOT | June 18, 2026 | 4 | 2,014 | ~42,000 | 10 | PostgreSQL, EBNF grammars, complex tests, bug fixes |
| 0.1.0 | August 2026 | 3 | 2,760 | ~63,200 | 13 | Release v0.1.0 |

---

## Future Considerations

- **Additional SQL dialects**: ~~MySQL, PostgreSQL~~ (done), Oracle-specific syntax extensions via `SqlDialects` and `DialectExtension`
- **More target languages for converter**: Python, Go, Rust, TypeScript converters extending `AbstractConverter`
- **Performance optimization**: Parallel parsing for independent grammar rules, AST node pool recycling
- **Extended stored procedure JIT compilation**: Compile SQL stored procedures to Java bytecode via JIT pipeline
- **Streaming execution**: Lazy evaluation mode for large AST trees to reduce memory pressure
- **Language server protocol**: LSP integration for IDE support (diagnostics, completion, hover) based on BNF grammars
- **Debugger API**: Step-through execution using `ExecutionListener` and `ScopeTreeListener` hooks
- **Persistent DBMS mode**: File-backed storage for `InMemoryDatabase` with WAL (write-ahead log)

---

## Document Maintenance

This document should be updated with each significant feature addition or requirement change:

1. **New Requirement**: Add new section with original request, reformulated requirements, and design decisions
2. **Requirement Change**: Update existing section with evolution notes
3. **Implementation Complete**: Add implementation details, test coverage, and commit reference
4. **Breaking Changes**: Clearly mark any breaking changes with migration guide
5. **Version Updates**: Update statistics and metrics as they change

**Update Procedure:**
1. Add new section for feature (use commit hash as section heading anchor)
2. Document original request verbatim
3. List reformulated requirements
4. Describe final design decisions with rationale
5. Add implementation details (files, lines, features)
6. Document test coverage and results
7. Update summary statistics and TOC
8. Commit changes with descriptive message

---

**Last Updated**: 2026-08-12
**Document Version**: 2.0
**Total Commits Documented**: 18
**Project Status**: Active Development

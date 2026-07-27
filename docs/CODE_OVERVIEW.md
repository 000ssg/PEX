# PEX — Code Overview

> Cross-reference: module-level overviews are linked in each module's own CODE_OVERVIEW.md.
> See: [pex-base](pex-base/CODE_OVERVIEW.md) | [pex-arithmetics](pex-arithmetics/CODE_OVERVIEW.md) | [pex-converter](pex-converter/CODE_OVERVIEW.md) | [pex-sql](pex-sql/CODE_OVERVIEW.md) | [pex-tools](pex-tools/CODE_OVERVIEW.md) | [pex-all](pex-all/CODE_OVERVIEW.md)

---

## Table of Contents

- [Project Goals](#project-goals)
- [Repository Structure](#repository-structure)
- [Module Decomposition and Rationale](#module-decomposition-and-rationale)
- [Key Architectural Decisions](#key-architectural-decisions)
- [Secondary Design Decisions](#secondary-design-decisions)
- [Inconsistencies, Inefficiencies, and Ambiguities](#inconsistencies-inefficiencies-and-ambiguities)
- [Test Coverage Summary](#test-coverage-summary)
- [Build System](#build-system)

---

## Project Goals

PEX (Parse and Execute) is a Java 24 toolkit for:

1. **Defining grammars** in EBNF notation and parsing input against them at runtime.
2. **Constructing and executing ASTs** through a pluggable handler/function registry.
3. **Evaluating arithmetic expressions** with full IEEE 754 compliance and numeric promotion.
4. **Simulating an in-memory SQL RDBMS** with DML, DDL, transactions, OLAP, streaming, and SQL dialects.
5. **Converting ASTs** to seven target programming languages, including in-memory JIT compilation.
6. **Documenting and visualizing grammars** via railroad diagrams and ANTLR4 native rendering.

The project serves two overlapping purposes: as a production-ready parsing/execution library (for embedding in larger systems, such as MDB-SQL), and as a self-contained reference implementation demonstrating extensible interpreter patterns.

---

## Repository Structure

```
pex/                           # Root project (Maven + Gradle dual build)
├── pex-base/                  # Core: BNF parsing, AST, execution engine, SPI, scoping
├── pex-arithmetics/           # Arithmetic, boolean, bitwise, math functions, radix
├── pex-converter/             # AST → target language conversion + JIT
├── pex-sql/                   # SQL parent module (no source, aggregates sub-modules)
│   ├── pex-sql-core/          # SQL parser, in-memory DBMS, DML/DDL/query executors
│   ├── pex-sql-olap/          # Window functions, CTEs, CUBE/ROLLUP, MERGE, PIVOT
│   ├── pex-sql-streaming/     # Streaming SQL with tumbling/hopping/sliding/session windows
│   └── pex-sql-dialects/      # Oracle, MSSQL, MySQL, PostgreSQL + special engines
├── pex-tools/                 # Visualizer, BNF↔ANTLR converter, HTML doc generator
├── pex-all/                   # Integration tests + Pex.java demonstration
├── grammar/                   # ANTLR4 sample grammars (12 .g4 files)
├── docs/grammar/              # Generated HTML grammar docs (22 files, 462 railroad diagrams)
└── costs/                     # Build cost and effort report
```

**Total**: ~350 Java source files, ~60 400 lines of code, 2 459 tests.

---

## Module Decomposition and Rationale

### pex-base — The Extension Point

Contains everything that other modules depend on without creating circular references: the `Result<T>` monad, BNF grammar model, parser, recursive-descent engine, AST node hierarchy, scope tree, execution engine, and the SPI plugin interfaces.

**Why a single base module?** All downstream modules share the same types (`AstNode`, `Result`, `Scope`, `NativeFunction`). Splitting base into smaller pieces (e.g., separating AST from execution) would not eliminate dependencies — execution depends on AST, and both depend on Result. The boundary is kept at the SPI layer: modules contribute handlers and grammar fragments, not base types.

### pex-arithmetics

Adds integer/float arithmetic, bitwise, boolean, comparison, math functions, type conversions, and radix literals. Packaged separately so the SQL and converter modules need not depend on arithmetic if they do not use it.

### pex-converter

Provides AST-to-source-code translation for seven languages and a JIT pipeline. Kept isolated because JIT requires the JDK's `javax.tools.ToolProvider`, which is not available in JRE-only deployments. The `ConverterFactory` SPI allows external converters without recompiling PEX.

### pex-sql (multi-sub-module)

Split into four sub-modules to allow consumers to take only what they need:

- **pex-sql-core** — the always-required SQL parser and DBMS engine. All other SQL sub-modules depend on it.
- **pex-sql-olap** — OLAP extensions (window functions, CTEs, CUBE/ROLLUP, MERGE, PIVOT) that are not universally needed.
- **pex-sql-streaming** — streaming SQL (CREATE STREAM, windows, watermarks) — separate runtime model from batch SQL.
- **pex-sql-dialects** — vendor-specific SQL (Oracle, MSSQL, MySQL, PostgreSQL) and special engines (columnar, time-series, full-text, spatial). Consumers that only need ANSI SQL need not include this module.

The alternative — a single `pex-sql` module — would have bundled all dialects and OLAP features unconditionally, significantly increasing compile-time classpath and jar size for consumers that only need basic SQL.

### pex-tools

Developer tooling: railroad diagram renderer, SVG/PNG exporter, BNF↔ANTLR4 converter, HTML grammar documentation generator, and a Swing visualizer app. Kept separate because these have Swing/AWT dependencies not appropriate for a library or server environment.

### pex-all

No production code; contains only integration tests and `Pex.java` (a demonstration class). This module depends on all other modules, making it the natural place for cross-module tests.

---

## Key Architectural Decisions

### 1. Sealed Interfaces + Records Everywhere

`Result<T>`, `RuleExpression`, `AstNode`, `PexEvent`, `AntlrExpression` — all use Java 24 sealed interfaces with record implementations. This enables exhaustive `switch` expressions at call sites, eliminates boilerplate equals/hashCode, and makes the type hierarchy explicit and closed (extending requires modifying the `permits` clause).

**Trade-off**: Sealed hierarchies cannot be extended by external code. This is deliberate — the extension point is the SPI (`PexPlugin`, `ConverterFactory`), not the AST. `ExtensionNode` wraps arbitrary payloads to allow plugin-defined node types to coexist in a sealed hierarchy.

### 2. Result<T> Monad — No Exceptions Across API Boundaries

All public API methods return `Result<T>`. Exceptions are only thrown when `ExecutionConfig.ErrorMode.EXCEPTION` is explicitly configured, or for programming errors (null arguments). This design enables callers to compose operations with `map`/`flatMap` and collect batch results without try-catch blocks.

**Implication**: Internal handlers use `Result.success()`/`Result.failure()` extensively. Tests must verify both success and failure paths.

### 3. SPI Plugin Architecture

Three production modules register `PexPlugin` implementations via `META-INF/services/ssg.pex.spi.PexPlugin`:
- `ArithmeticsPlugin` (load order 100)
- `SqlPlugin` (200), `OlapPlugin` (210), `StreamingPlugin` (220), `DialectsPlugin` (230)
- `ConverterPlugin` (500)

`ExecutionEngine.builder().loadPlugins().build()` auto-discovers all plugins from the classpath, making integration zero-configuration. **Trade-off**: `PluginRegistry` is a singleton with a mutable `plugins` field. In a classpath-isolated environment (OSGi, module layers) this can behave unexpectedly.

### 4. Hybrid BNF Grammar + Recursive Descent

Grammar rules are declared declaratively in EBNF text (`BnfParser`) but executed by a hand-written recursive-descent engine (`RecursiveDescentEngine`). Packrat memoization in `ParseContext` prevents exponential backtracking. Left-recursion is detected by tracking `(ruleName, cursorPosition)` composite keys — only true left-recursion (same rule at the same position) is blocked; recursion that advances the cursor is allowed.

**Bug fixed during development**: the original implementation tracked only rule names, blocking legitimate recursion through terminals (`factor ::= '(' expr ')'`). This was corrected in commit 5.

### 5. Lazy Stream Pipeline in SQL QueryExecutor

Cross-joins in `QueryExecutor.resolveFrom()` are implemented as a lazy `Stream<Row>` pipeline using `flatMap`, with WHERE as `.filter()` and LIMIT as `.limit()` applied before `collect()`. This prevents OOM for large Cartesian products when a WHERE predicate is selective. ORDER BY, GROUP BY, and DISTINCT still require full materialisation (they are inherently non-streaming).

### 6. Hash-Join for Equi-Joins

`JoinEngine` detects equi-join predicates (`ON a.id = b.id`) and uses a hash-join (build right, probe left). NULL join keys are skipped per SQL standard. Non-equi predicates and outer joins fall back to nested-loop. This was added in commit 15 after benchmarking.

### 7. LRU Parse Cache in InMemoryDatabase

`InMemoryDatabase.execute(String)` maintains a 1024-entry LRU cache of parsed `SqlNode` trees (keyed by SQL text). This is effective for workloads with repeated parameterised queries. The `executeBatch()` methods parse the template once and replay the parsed structure.

---

## Secondary Design Decisions

### IEEE 754 Equality Compliance

`ComparisonHandler.compareNumeric()` uses `l == r` for `EQ` / `l != r` for `NEQ` before falling back to `Double.compare()` for ordering. This ensures `-0.0 == 0.0` returns `true` and `NaN == NaN` returns `false`, as required by IEEE 754. `Double.compare(-0.0, 0.0)` returns -1, violating this contract if used naively.

### Predicate Push-Down in Multi-Table FROM

`QueryExecutor` extracts AND-conjuncts from the WHERE clause that are fully resolvable against the column list available at each cross-join step. Conjuncts referencing columns from not-yet-joined tables are deferred to the final WHERE pass. This reduces intermediate row counts in 3+ table joins.

### Grammar EBNF Files as First-Class Artifacts

22 `.ebnf` files across all modules serve both documentation and runtime purposes — they can be loaded and validated via `GrammarLoader`. HTML railroad diagrams (462 rules) are generated from them. Keeping them in `src/main/resources/grammar/` means they are included in the module jar.

### Dialect-Specific SQL Parsing via Pattern Matching

Each dialect parser (`OracleParser`, `MysqlParser`, `PostgresqlParser`, etc.) uses regex-based heuristics to detect dialect-specific syntax before delegating to the core SQL parser. This is pragmatic but not exhaustive — it can misfire on edge cases (see [Inconsistencies](#inconsistencies-inefficiencies-and-ambiguities) below).

---

## Inconsistencies, Inefficiencies, and Ambiguities

### 1. Regex-Based Dialect Detection is Fragile

**Problem**: Dialect parsers detect SQL variants by matching patterns like `"::"`, `"ILIKE"`, `"ON CONFLICT"` in the SQL string before parsing. This can produce false positives (e.g., a column comment containing `"::"`) and false negatives (e.g., mixed-dialect queries).

**Proposal**: Introduce a `DialectHint` enum that callers pass explicitly, eliminating regex detection. The current detection can remain as a fallback for backward compatibility.

### 2. PluginRegistry is a Singleton with Mutable State

**Problem**: `PluginRegistry.getInstance()` returns a global singleton. The `plugins` field is lazily initialised and then mutated by `reload()`. In test environments, multiple tests that call `reload()` share state, which can cause ordering issues.

**Proposal**: Make `PluginRegistry` an instance class (non-singleton) and expose a `PluginRegistry.forClassLoader(ClassLoader)` factory. The `ExecutionEngine.Builder` can hold its own registry instance.

### 3. REQUIREMENTS.md Statistics Are Stale

**Problem**: The `Project Statistics` section in `REQUIREMENTS.md` shows totals from commit 7 (2,240 tests, ~42,000 lines). The actual current totals are 2,459 tests and ~60,400 lines.

**Proposal**: Update the statistics table on every commit as part of the mandatory commit checklist. (Done in this commit — see [Test Coverage Summary](#test-coverage-summary) below.)

### 4. OracleExecutor Post-Processing is Quadratic

**Problem**: `OracleExecutor.applyOracleFunctions()` executes a `SELECT *` to get all rows, then re-evaluates function expressions row-by-row. For a SELECT that already returned N rows, this is O(N) extra database calls plus O(N·F) expression evaluations (F = number of Oracle function calls in SELECT list). This is acceptable for small result sets but degrades for large ones.

**Proposal**: Integrate Oracle function evaluation directly into the `QueryExecutor` projection step (in `resolveSelectValue()`), eliminating the post-processing pass.

### 5. `InMemoryDatabase` Protected Constructor Breaks Encapsulation Minimally but Noticeably

**Problem**: The protected constructor `InMemoryDatabase(Schema schema)` was added to allow `MdbBackedSchema` injection. This pattern is documented but is unusual — it tightly couples `InMemoryDatabase` to its subclasses.

**Proposal**: Replace with a factory method `InMemoryDatabase.withSchema(Schema schema)` or a builder parameter, making the injection explicit and not inheritance-based.

### 6. No Test Coverage for `ArithmeticsGrammarProvider` and `ArithmeticHandlerProvider`

**Problem**: These two SPI classes are tested indirectly through integration tests, but have no dedicated unit tests verifying that they register the expected grammar rules and handlers.

**Proposal**: Add unit tests verifying the counts and names of registered rules and handlers. (Partially addressed in this commit — `RadixHandler` now has unit tests; the provider classes themselves remain integration-tested only.)

### 7. SQL Streaming Module Has No ANTLR Grammar File

**Problem**: All other modules have corresponding ANTLR4 `.g4` sample files under `grammar/antlr/`, but pex-sql-streaming is missing one.

**Proposal**: Add `grammar/antlr/streaming/Streaming.g4` mirroring the EBNF file in `pex-sql-streaming/src/main/resources/grammar/streaming.ebnf`.

---

## Test Coverage Summary

| Module | Tests | Notable Uncovered Areas |
|--------|-------|------------------------|
| pex-base | 522 | `ArithmeticsPlugin` SPI wiring (integration-tested only) |
| pex-arithmetics | 288 | `ArithmeticsGrammarProvider`, `ArithmeticHandlerProvider` (integration-tested only) |
| pex-converter | 218 | `ConverterPlugin` SPI wiring (integration-tested only) |
| pex-sql-core | 509 | — |
| pex-sql-olap | 167 | — |
| pex-sql-streaming | 160 | — |
| pex-sql-dialects | 256 | — |
| pex-tools | 221 | — |
| pex-all (integration) | 118 | — |
| **Total** | **2 459** | |

New tests added in this session (+55):
- `TelemetryTest` (18 tests) — `ParseEvent`, `ExecuteEvent`, `ScopeEvent`, `ErrorEvent`, `PexTelemetryListener`
- `PreconditionsTest` (11 tests) — `requireNonNull`, `requireTrue`, `requireNotEmpty`
- `SourceLocationTest` (10 tests) — all factory methods, `toString()` branches, equality
- `RadixHandlerTest` (16 tests) — `RadixLiteralNode.parse()`, `literalValue()`, `toRadixString()`, `RadixHandler.handle()`, `RadixHandler.parseRadixLiteral()`

---

## Build System

PEX uses both Maven 3.9+ and Gradle 8.14+ with identical configuration:

- **Java 24** with `--enable-preview` (required for sealed interfaces with exhaustive switching in preview form)
- **Parallel test execution**: 4 forks (Maven Surefire `forkCount=4`; Gradle `maxParallelForks=4`)
- Dependencies: JUnit 5.11.4, AssertJ 3.27.3, Mockito 5.14.2, SLF4J 2.0.16

```bash
# Maven
mvn test                        # all modules
mvn test -pl pex-base           # single module

# Gradle
./gradlew test                  # all modules
./gradlew :pex-base:test        # single module
```

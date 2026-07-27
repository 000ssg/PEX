# pex-sql — Code Overview

> Part of the PEX multi-module project. See [top-level CODE_OVERVIEW.md](../docs/CODE_OVERVIEW.md) for project-wide context.
> Sub-module overviews: [pex-sql-core](pex-sql-core/CODE_OVERVIEW.md) | [pex-sql-olap](pex-sql-olap/CODE_OVERVIEW.md) | [pex-sql-streaming](pex-sql-streaming/CODE_OVERVIEW.md) | [pex-sql-dialects](pex-sql-dialects/CODE_OVERVIEW.md)

---

## Purpose

`pex-sql` is the parent module for the SQL simulation stack. It contains no source code itself — it aggregates four sub-modules and manages their shared Maven/Gradle build configuration.

---

## Sub-Module Summary

| Sub-module | Description | Tests |
|------------|-------------|-------|
| [pex-sql-core](pex-sql-core/CODE_OVERVIEW.md) | SQL tokenizer, parser, in-memory DBMS, DML/DDL/query executors, transactions | 509 |
| [pex-sql-olap](pex-sql-olap/CODE_OVERVIEW.md) | Window functions, CTEs, CUBE/ROLLUP, MERGE, PIVOT | 167 |
| [pex-sql-streaming](pex-sql-streaming/CODE_OVERVIEW.md) | CREATE STREAM, tumbling/hopping/sliding/session windows, watermarks | 160 |
| [pex-sql-dialects](pex-sql-dialects/CODE_OVERVIEW.md) | Oracle, MSSQL, MySQL, PostgreSQL, special engines (columnar, time-series, full-text, spatial) | 256 |
| **Total** | | **1 092** |

---

## Dependency Hierarchy

```
pex-sql-dialects ──► pex-sql-core ──► pex-arithmetics ──► pex-base
pex-sql-olap ────► pex-sql-core
pex-sql-streaming ► pex-sql-core
```

All sub-modules depend on `pex-sql-core`. `pex-sql-dialects` also depends on `pex-sql-olap` (for `OlapDatabase` integration). Sub-modules are independent of each other except through `pex-sql-core`.

---

## Shared SQL Design Principles

1. **All SQL statements go through `SqlParser`** — a hand-written recursive descent parser producing a sealed `SqlNode` hierarchy.
2. **Execution pipelines are separate from parsing** — `QueryExecutor`, `DmlExecutor`, `DdlExecutor` are distinct classes with single responsibilities.
3. **No external SQL library dependencies** — the entire stack is implemented from scratch in PEX to remain dependency-free.
4. **`InMemoryDatabase` is the integration point** — all sub-modules either extend it (`OlapDatabase`) or use it as a backing store.

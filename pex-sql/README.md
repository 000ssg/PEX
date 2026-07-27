# pex-sql

Parent module for the PEX SQL module family. Provides SQL parsing, an in-memory
DBMS, OLAP analytics, streaming SQL, and multi-dialect support through four
specialized sub-modules.

For detailed design documentation see [CODE_OVERVIEW.md](doc/CODE_OVERVIEW.md).

## Dependencies

- `pex-base`
- `pex-converter`

## Module Structure

```mermaid
flowchart TD
    SQL[pex-sql<br/>Parent Module]
    CORE[pex-sql-core<br/>SQL Parser + In-Memory DBMS]
    OLAP[pex-sql-olap<br/>Window Functions + CTEs + MERGE]
    STREAM[pex-sql-streaming<br/>Streaming SQL Engine]
    DIALECT[pex-sql-dialects<br/>Oracle + MSSQL + MySQL + Engines]

    SQL --> CORE
    SQL --> OLAP
    SQL --> STREAM
    SQL --> DIALECT
```

## Sub-Module Overview

| Sub-Module | Artifact | Description |
|------------|----------|-------------|
| **pex-sql-core** | `ssg:pex-sql-core` | Hand-written SQL parser, sealed `SqlNode` AST, full in-memory DBMS with schema, DML/DDL, joins, aggregations, transactions, triggers, stored procedures; `executeBatch(Stream)`, hash-join, LRU parse cache, partial predicate push-down |
| **pex-sql-olap** | `ssg:pex-sql-olap` | OLAP extensions: 14 window functions, CTEs (recursive and non-recursive), CUBE/ROLLUP/GROUPING SETS, MERGE, PIVOT/UNPIVOT |
| **pex-sql-streaming** | `ssg:pex-sql-streaming` | Streaming SQL engine with CREATE STREAM, tumbling/hopping/sliding/session windows, watermarks, emit strategies, stream joins |
| **pex-sql-dialects** | `ssg:pex-sql-dialects` | Dialect-specific parsers and executors for Oracle, MSSQL, MySQL plus columnar, time-series, full-text, and spatial engines |

## Dependency Diagram

```mermaid
flowchart BT
    BASE[pex-base]
    CONV[pex-converter]
    CORE[pex-sql-core]
    OLAP[pex-sql-olap]
    STREAM[pex-sql-streaming]
    DIALECT[pex-sql-dialects]

    CORE --> BASE
    CORE --> CONV
    OLAP --> CORE
    STREAM --> CORE
    DIALECT --> CORE

    style BASE fill:#efe,stroke:#333
    style CORE fill:#e3f2fd,stroke:#333
    style OLAP fill:#fff3e0,stroke:#333
    style STREAM fill:#fce4ec,stroke:#333
    style DIALECT fill:#f3e5f5,stroke:#333
```

## Test Coverage

| Sub-Module | Tests |
|------------|------:|
| pex-sql-core | 509 |
| pex-sql-olap | 167 |
| pex-sql-streaming | 160 |
| pex-sql-dialects | 256 |
| **Total** | **1,092** |

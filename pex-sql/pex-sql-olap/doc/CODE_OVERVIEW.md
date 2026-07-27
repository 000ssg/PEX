# pex-sql-olap — Code Overview

> Part of the PEX multi-module project. See [pex-sql overview](../docs/CODE_OVERVIEW.md) and [top-level CODE_OVERVIEW.md](../../docs/CODE_OVERVIEW.md).

---

## Purpose

`pex-sql-olap` adds OLAP (Online Analytical Processing) capabilities on top of `pex-sql-core`:

- **Window functions** — ROW_NUMBER, RANK, DENSE_RANK, NTILE, LAG, LEAD, FIRST_VALUE, LAST_VALUE, NTH_VALUE, PERCENT_RANK, CUME_DIST, PERCENTILE_CONT, PERCENTILE_DISC, and aggregate-over-window (SUM/COUNT/AVG/MIN/MAX OVER ...)
- **Common Table Expressions (CTEs)** — both recursive and non-recursive WITH clauses
- **CUBE / ROLLUP / GROUPING SETS** — multi-dimensional aggregation
- **MERGE** — upsert with WHEN MATCHED / WHEN NOT MATCHED clauses
- **PIVOT / UNPIVOT** — row-to-column and column-to-row transformations
- **`OlapDatabase`** — extends `InMemoryDatabase` with auto-routing for OVER/ROLLUP/CUBE detection

---

## Package Structure

```
ssg.pex.sql.olap
├── OlapPlugin              SPI PexPlugin (load order 210)
├── OlapDatabase            extends InMemoryDatabase; auto-detects OLAP SQL
├── parser/
│   └── OlapSqlParser       parses OVER, WITH, GROUPING SETS, MERGE, PIVOT
├── ast/
│   ├── OlapNode            sealed base for OLAP-specific AST nodes
│   ├── WindowFunctionCall  func + OverClause
│   ├── OverClause          PARTITION BY + ORDER BY + FrameSpec
│   ├── FrameSpec           ROWS/RANGE BETWEEN frame-bound AND frame-bound
│   ├── FrameBound          UNBOUNDED PRECEDING/FOLLOWING, CURRENT ROW, N PRECEDING/FOLLOWING
│   ├── WithClause          list of CteDefinition
│   ├── CteDefinition       name + query
│   ├── GroupingSetSpec     CUBE/ROLLUP/GROUPING SETS
│   ├── MergeNode           target + source + actions
│   ├── MergeAction         WHEN MATCHED THEN UPDATE/DELETE, WHEN NOT MATCHED THEN INSERT
│   ├── PivotClause         column + aggregates + pivot values
│   └── UnpivotClause       value column + pivot columns
└── executor/
    ├── WindowFunctionExecutor   computes window frames, ranks, lag/lead
    ├── CteExecutor              materialises CTE results + recursive iteration
    ├── GroupingSetExecutor      CUBE/ROLLUP expansion + GROUPING() function
    ├── MergeExecutor            source scan → match test → apply actions
    └── PivotExecutor            PIVOT/UNPIVOT transformation
```

---

## Key Design Decision: OlapDatabase Auto-Routing

`OlapDatabase.executeOlap(String sql)` detects OLAP constructs via string pattern matching before delegating:
- `OVER` keyword → `executeWindowFunctionQuery()`
- `ROLLUP(` or `CUBE(` in GROUP BY → `executeGroupingSetQuery()`
- Otherwise → core `InMemoryDatabase.execute()`

This allows callers to use `OlapDatabase` as a drop-in replacement for `InMemoryDatabase` without needing to choose a specific executor upfront.

**Known issue**: DDL statements (`CREATE TABLE`, etc.) in `OlapDatabase.executeOlap()` were previously double-executed because `executeOlap()` returned failure when the result was a `DmlResult`. Fixed in commit 13 by returning an empty `QueryResult` for DDL/DML.

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| OlapTest | 115 | WindowFunctionTest, CteTest, GroupingSetTest, MergeTest, PivotTest nested |
| RealWorldOlapTest | 52 | complex analytical queries, multi-CTE, recursive, ROLLUP+PIVOT |
| **Total** | **167** | |

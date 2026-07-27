# pex-all — Code Overview

> Part of the PEX multi-module project. See [top-level CODE_OVERVIEW.md](../docs/CODE_OVERVIEW.md) for project-wide context.

---

## Purpose

`pex-all` serves two roles:

1. **Integration tests** — cross-module end-to-end tests that span pex-base, pex-arithmetics, pex-converter, and all pex-sql sub-modules.
2. **`Pex.java`** — a demonstration class showing the full PEX API in one place (BNF parsing, AST execution, arithmetic, SQL, OLAP, streaming, dialects, conversion, JIT, plugins).

`pex-all` has no production code beyond `Pex.java`. It depends on all other modules.

---

## Integration Test Classes

| Test Class | Count | What It Tests |
|------------|-------|---------------|
| CrossModulePluginLoadingTest | 8 | ServiceLoader discovers all 6 plugins; load-order sorting |
| EndToEndArithmeticsTest | 12 | BNF grammar → parse → execute arithmetic expressions |
| EndToEndSqlTest | 13 | SQL CREATE/INSERT/SELECT via InMemoryDatabase |
| EndToEndConverterTest | 10 | AST → target language source, all 7 languages |
| JitRoundTripTest | 5 | AST → Java source → compile → load → execute → verify result |
| FullPipelineTest | 5 | BNF parse → AST → execute → convert → JIT |
| ComplexIntegrationTest | 31 | cross-module interactions, stress edge cases, full pipeline |
| RealWorldIntegrationTest | 16 | SQL streaming, OLAP, dialect cross-module, converter+SQL |
| SqlSubModulesIntegrationTest | 22 | OlapDatabase, streaming, dialect integration |
| **Total** | **118** | |

---

## Key Decision: No Production Code Beyond Pex.java

`pex-all` intentionally contains no reusable library code. Any functionality that needs to be shared across modules belongs in `pex-base` (core) or the appropriate domain module. This keeps the dependency graph a strict DAG and prevents circular dependencies.

`Pex.java` is a demonstration/documentation artefact, not a stable API. It changes with every new feature to demonstrate the latest capabilities.

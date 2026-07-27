# ANTLR4 Grammar Collection

Sample ANTLR4 (.g4) grammar files for use with the PEX ANTLR Grammar Visualizer.

## Grammar Index

| Directory | Grammar | Files | Source | Description | Notable ANTLR Features |
|-----------|---------|-------|--------|-------------|----------------------|
| `pex/` | PexExpressions | 1 | PEX conversion | Expression language with full operator precedence | `# AltLabel`, `op=`, labeled elements |
| `pex/` | PexLiterals | 1 | PEX conversion | Literal types (int, float, hex, bin, oct, string, bool, null) | `fragment` rules, character classes |
| `pex/` | SqlDml | 1 | PEX conversion | SQL DML (SELECT, INSERT, UPDATE, DELETE) | Case-insensitive keyword fragments, `left=`/`right=` labels |
| `pex/` | SqlDdl | 1 | PEX conversion | SQL DDL (CREATE, ALTER, DROP) | Complex rule hierarchies, many keyword tokens |
| `json/` | JSON | 1 | Public (RFC 8259) | JSON data interchange format | `fragment` (ESC, UNICODE_ESCAPE, SAFECODEPOINT), `-> skip` |
| `xml/` | XML | 1 | Public | Simplified XML | **Lexer modes**: `mode INSIDE`, `-> pushMode()`, `-> popMode` |
| `csv/` | CSV | 1 | Public (RFC 4180) | CSV tabular data | Parser/lexer separation, `fragment` for escaped quotes |
| `calculator/` | Calculator | 1 | Public | Arithmetic expressions with precedence | `# AltLabel`, `left=`/`right=`/`op=`, `args+=`, `<assoc=right>`, semantic predicates `{...}?` |
| `sql/` | SimpleSQL | 1 | Public | SQL SELECT queries | Large grammar, BETWEEN/IN/LIKE, case-insensitive keywords |
| `dot/` | DOT | 1 | Public | Graphviz DOT graph description | Graph language, edge operators `->` / `--`, subgraphs |
| `url/` | URL | 1 | Public (RFC 3986) | URI/URL parsing | `fragment` for character classes (OCTET, UNRESERVED, PCT_ENCODED) |
| `markdown/` | SimpleMarkdown | 1 | Public | Simplified Markdown | `-> channel(HIDDEN)`, nested inline formatting, `text+=` list labels |

## PEX Conversions vs Public Grammars

### PEX Conversions (`pex/`)
These grammars were hand-converted from the PEX project's BNF/EBNF grammar files to ANTLR4 format:
- `PexExpressions.g4` -- from `pex-base/src/main/resources/grammar/expressions.ebnf`
- `PexLiterals.g4` -- from `pex-base/src/main/resources/grammar/literals.ebnf`
- `SqlDml.g4` -- from `pex-sql/pex-sql-core/src/main/resources/grammar/dml.ebnf`
- `SqlDdl.g4` -- from `pex-sql/pex-sql-core/src/main/resources/grammar/ddl.ebnf`

### Public Grammars
Written from scratch based on well-known specifications (RFC 8259, RFC 3986, RFC 4180, Graphviz DOT spec, etc.). These demonstrate ANTLR4 features that cannot be expressed in BNF.

## ANTLR4 Features Demonstrated

| Feature | Grammars | Description |
|---------|----------|-------------|
| `-> skip` | All | Whitespace and comment skipping |
| `-> channel(HIDDEN)` | SimpleMarkdown | Preserving tokens on hidden channel |
| `# AltLabel` | PexExpressions, Calculator, SimpleSQL, JSON | Named alternatives for visitor/listener methods |
| `fragment` rules | All | Reusable character patterns (never produce tokens) |
| `mode` / `pushMode` / `popMode` | XML | Context-sensitive tokenization (inside vs outside tags) |
| `label=expr` | Calculator, SqlDml, SimpleSQL, DOT | Named rule elements for typed access |
| `label+=expr` | Calculator, SimpleMarkdown | List labels collecting multiple elements |
| `{predicate}?` | Calculator | Semantic predicates (identifier length check) |
| `[a-zA-Z]` | All | Character classes |
| `~[...]` | JSON, CSV, XML, Markdown | Character class negation |
| `<assoc=right>` | Calculator | Right-associative operators (power) |
| Case-insensitive keywords | SqlDml, SqlDdl, SimpleSQL, DOT | Fragment-per-letter pattern for SQL keywords |

## Viewing with the Visualizer

These grammars are loaded as sample files in the PEX ANTLR Grammar Visualizer (`pex-tools`). Open any `.g4` file to see:
- Railroad diagrams for each rule
- Syntax-highlighted grammar source
- Rule dependency graphs
- Parser vs lexer rule separation

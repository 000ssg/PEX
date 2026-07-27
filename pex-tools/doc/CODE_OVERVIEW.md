# pex-tools — Code Overview

> Part of the PEX multi-module project. See [top-level CODE_OVERVIEW.md](../docs/CODE_OVERVIEW.md) for project-wide context.

---

## Purpose

`pex-tools` provides developer tooling for PEX grammars:

- **Railroad diagram renderer** — converts BNF `Grammar` to SVG railroad diagrams (Oracle SQL Reference visual style)
- **SVG and PNG exporters** — save diagrams to files
- **`GrammarDocGenerator`** — generates HTML pages with inline SVG diagrams for all rules in a Grammar; produces the 22 HTML files in `docs/grammar/`
- **`MarkdownDiagramGenerator`** — Markdown-compatible ASCII or inline SVG table of grammar rules
- **`BnfToAntlrConverter`** — converts PEX EBNF `Grammar` objects to ANTLR4 `.g4` text
- **`AntlrToBnfConverter`** — converts ANTLR4 `.g4` text to PEX `Grammar` objects (best-effort; ANTLR-specific constructs like predicates and lexer commands are approximated)
- **`Antlr4Lexer`** — tokenises ANTLR4 `.g4` files for the converter and native parser
- **`AntlrGrammarParser`** — native ANTLR4 grammar parser preserving all constructs (predicates, actions, negation, labels, lexer commands, modes, fragments)
- **`AntlrDiagramRenderer`** — railroad renderer for the native ANTLR model, marking non-BNF constructs (predicates, actions) with grey dashed borders
- **`GrammarVisualizerApp`** — Swing application for interactive grammar exploration; auto-detects `.g4` vs `.ebnf`/`.bnf`

---

## Package Structure

```
ssg.pex.tools
├── converter/
│   ├── Antlr4Lexer                 tokenises ANTLR4 .g4 files
│   ├── BnfToAntlrConverter         Grammar → ANTLR4 .g4 text
│   ├── AntlrToBnfConverter         ANTLR4 .g4 text → Grammar
│   ├── ConversionResult            Result record (output, warnings)
│   └── antlr/
│       ├── AntlrExpression         sealed: Literal, CharClass, RuleRef, TokenRef, Seq, Alt,
│       │                           ZeroOrMore, OneOrMore, Optional, Group, Negation,
│       │                           Predicate, Action, Dot, LabeledElement (15 types)
│       ├── AntlrRule               name + body + kind (PARSER/LEXER/FRAGMENT) + altLabels + commands
│       ├── AntlrGrammar            name + grammarKind + rules + options + imports + modes
│       └── AntlrGrammarParser      recursive-descent from Antlr4Lexer token stream
├── docgen/
│   ├── GrammarDocGenerator         Grammar → HTML with inline SVG
│   └── MarkdownDiagramGenerator    Grammar → Markdown
└── visualizer/
    ├── RailroadDiagramRenderer     two-pass (measure/render) SVG for BNF Grammar
    ├── AntlrDiagramRenderer        two-pass SVG for native AntlrExpression
    ├── DiagramMetrics              font metrics and layout constants
    ├── DiagramStyle                color theme
    ├── RailroadPanel               Swing panel for single BNF rule
    ├── MultiRulePanel              Swing panel for multiple BNF rules
    ├── AntlrMultiRulePanel         Swing panel for ANTLR rules
    ├── SvgExporter                 render Grammar to .svg file
    ├── PngExporter                 render Grammar to .png file
    └── GrammarVisualizerApp        Swing application (auto-detects .g4 vs .ebnf)
```

---

## Key Design Decision: Native ANTLR4 Model

When the visualiser loads a `.g4` file, it does not convert to BNF first. Instead, `AntlrGrammarParser` builds a native `AntlrExpression` tree preserving all 15 expression types including predicates, actions, character classes, negation, labels, and lexer commands. `AntlrDiagramRenderer` then visualises them natively, using grey dashed borders for constructs that have no BNF equivalent.

This is intentional: BNF conversion would silently drop predicates and actions, giving a misleading diagram. The native representation shows the grammar as-written.

---

## Known Limitation: ANTLR4 → BNF Conversion is Lossy

`AntlrToBnfConverter` is best-effort. Constructs with no BNF equivalent (predicates, lexer commands, labeled alternatives, character class ranges outside simple ASCII) are either dropped or approximated. The converter produces a `ConversionResult` with a `warnings` list enumerating all dropped constructs. Users needing a faithful representation should use the native ANTLR visualiser instead.

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| Antlr4LexerTest | 9 | all token types including TILDE, HASH, IMPORT, MODE |
| BnfToAntlrConverterTest | 10 | grammar → .g4 round-trips |
| AntlrToBnfConverterTest | 14 | .g4 → Grammar, warning generation |
| AntlrGrammarParserTest | 35 | all expression types, modes, fragments, options, imports |
| RailroadDiagramRendererTest | 21 | measure/render for all BNF expression types |
| AntlrDiagramRendererTest | 59 | measure/render for all 15 ANTLR expression types |
| GrammarVisualizerAppTest | 31 | .ebnf loading, .g4 loading, multi-select, clipboard, drag-and-drop |
| MultiRulePanelTest | 8 | multi-rule panel layout |
| SvgExporterTest | 5 | SVG file output |
| PngExporterTest | 5 | PNG file output |
| GrammarDocGeneratorTest | 6 | HTML generation, cross-linked non-terminals |
| **Total** | **221** | (includes nested inner-class test groups) |

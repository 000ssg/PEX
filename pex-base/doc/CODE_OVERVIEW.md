# pex-base — Code Overview

> Part of the PEX multi-module project. See [top-level CODE_OVERVIEW.md](../docs/CODE_OVERVIEW.md) for project-wide context.

---

## Purpose

`pex-base` is the dependency-free foundation of PEX. Every other module depends on it. It provides:

- The **`Result<T>` monad** for type-safe error propagation
- The **BNF grammar model and parser** (EBNF text → `Grammar` object)
- The **`RecursiveDescentEngine`** with packrat memoization and left-recursion detection
- The **sealed AST node hierarchy** (`AstNode` and 20+ record subtypes)
- The **scope tree** with variable shadowing, path tracking, and listener hooks
- The **execution engine** with pluggable handlers, function registry, and timeout guards
- The **SPI plugin interfaces** (`PexPlugin`, `PluginContext`, `GrammarProvider`, `HandlerProvider`)
- The **telemetry event model** (`PexEvent` sealed hierarchy)
- The **`GrammarLoader`** utility for loading `.ebnf` files from classpath or filesystem

---

## Package Structure

```
ssg.pex
├── result/          Result<T>, Success, Failure, PexError, BatchResult
├── bnf/
│   ├── model/       Grammar, Rule, RuleExpression sealed hierarchy (Terminal, NonTerminal,
│   │                Sequence, Alternation, Group, Repetition, RepetitionKind)
│   ├── dialect/     DialectExtension, DialectRegistry, RuleModification subtypes
│   ├── parser/      BnfParser — EBNF text to Grammar
│   ├── engine/      RecursiveDescentEngine, ParseContext, ParseMatch
│   └── loader/      GrammarLoader
├── ast/
│   ├── SourceLocation
│   ├── node/        AstNode sealed hierarchy (20+ record types)
│   └── visitor/     AstVisitor<T>, AstTransformer
├── scope/           ScopeTree, Scope, ScopePath, Variable, ScalarVariable,
│                    IndexedVariable, VariableStore, NumericIndexStore, KeyMappedStore,
│                    ShadowRecord, ScopeTracer, ScopeTreeListener
├── type/            PexType, TypeDescriptor, TypeCoercion
├── exec/
│   ├── ExecutionEngine (Builder + AutoCloseable)
│   ├── ExecutionContext, ExecutionConfig, ExecutionStatistics
│   ├── HandlerRegistry, FunctionRegistry, NodeHandler, NativeFunction
│   ├── ExecutionListener, PexExecutionException, ReturnException, FunctionDef
│   └── handler/     ProgramHandler, BlockHandler, AssignmentHandler, BinaryOpHandler,
│                    UnaryOpHandler, ConditionalHandler, LoopHandler, FunctionDefHandler,
│                    FunctionCallHandler, ReturnHandler, LiteralHandler, IdentifierHandler,
│                    IndexAccessHandler, BaseHandlerProvider
├── spi/             PexPlugin, PluginContext, PluginRegistry, GrammarProvider, HandlerProvider
├── telemetry/       PexEvent (sealed), ParseEvent, ExecuteEvent, ScopeEvent, ErrorEvent,
│                    PexTelemetryListener
└── util/            Preconditions
```

---

## Key Components

### Result<T> Monad

Sealed interface with `Success<T>` and `Failure<T>`. Chain operations with `map`, `flatMap`, `recover`, `fold`. Aggregate multiple results with `BatchResult<T>` (via `Result.collect()`). `Result.of(Callable)` safely wraps any exception-throwing code.

**Error codes by convention**: `PARSE_` for grammar errors, `EXEC_` for execution errors, `SCOPE_` for scope resolution failures, `TYPE_` for type coercion failures.

### BNF Grammar Model

`Grammar` holds a list of `Rule` objects; each rule has a name and a `RuleExpression`. `RuleExpression` is a sealed hierarchy:

```
RuleExpression
├── Terminal        — literal string or regex pattern
├── NonTerminal     — reference to another rule by name
├── Sequence        — ordered list of expressions (all must match)
├── Alternation     — ordered alternatives (first match wins)
├── Group           — parenthesised expression (used for grouping)
└── Repetition      — zero-or-more (*), one-or-more (+), optional (?)
```

Dialect extensions (`DialectExtension`) modify a base grammar non-destructively: `RuleAddition`, `RuleReplacement`, `RuleExtension` (add alternatives to existing rule), `RuleDeletion`.

### RecursiveDescentEngine

Implements PEG-style parsing with memoization (`ParseContext` stores `ParseMatch` results keyed by `(ruleName, position)`). Left-recursion is detected by tracking `(ruleName, position)` pairs in the call stack — the same rule at the same input position is blocked, but recursion that advances the cursor is allowed.

### AST Node Hierarchy

All 20+ node types are records implementing the sealed `AstNode` interface:
- **Control flow**: `ProgramNode`, `BlockNode`, `ConditionalNode`, `LoopNode` (`LoopKind`: WHILE/FOR/DO_WHILE)
- **Variables/functions**: `AssignmentNode`, `IdentifierNode`, `IndexAccessNode`, `FunctionDefNode`, `FunctionCallNode`, `ParameterNode`, `ReturnNode`
- **Expressions**: `BinaryOpNode` (operator enum: ADD, SUB, MUL, DIV, MOD, ...), `UnaryOpNode`
- **Literals**: `IntLiteral`, `FloatLiteral`, `StringLiteral`, `BoolLiteral`, `NullLiteral`
- **Extension**: `ExtensionNode` — wraps arbitrary payloads from plugins (e.g., `RadixLiteralNode`)

All nodes carry `SourceLocation` and `Map<String, Object> metadata`.

### Scope Tree

`ScopeTree` maintains a tree of `Scope` objects with parent links for lexical scoping. `ScopePath` provides unique hierarchical identifiers (`root.function.loop`). Variable shadowing is recorded in `ShadowRecord` and collected by `ScopeTracer`. `ScopeTreeListener` observes scope enter/exit/shadow events.

`VariableStore` has two implementations: `NumericIndexStore` (array-like, auto-grows) and `KeyMappedStore` (string-keyed map). `IndexedVariable` holds a store; `ScalarVariable` holds a single value.

### Execution Engine

`ExecutionEngine` dispatches each `AstNode` to the registered `NodeHandler` via `HandlerRegistry`. Handlers are functional interfaces (`Result<Object> handle(Object node, ExecutionContext ctx)`). `ExecutionContext` carries the current scope, function registry, handler registry, config, and statistics.

`ExecutionConfig` controls: `maxRecursionDepth`, `timeoutMillis`, `ErrorMode` (RESULT vs EXCEPTION).

`ReturnException` is used for non-error flow control (implementing `return` statements).

---

## Key Design Decisions

1. **`ExtensionNode` bridges sealed hierarchy and plugins**: Plugin-defined node types wrap their data inside `ExtensionNode` with a string `extensionType`. Handlers pattern-match on that string. This allows closed-world sealed AST with open-world plugin payloads.

2. **Packrat memoization with position-aware left-recursion detection**: Changed from name-only tracking (which incorrectly blocked `factor ::= '(' expr ')'`) to `(name, position)` composite keys.

3. **`ScopeTreeListener` vs. telemetry**: `ScopeTreeListener` is synchronous and scope-lifecycle-specific; `PexTelemetryListener` is asynchronous-ready and covers parse, execute, scope, and error events. Both can be active simultaneously.

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| ResultTest | 65 | map, flatMap, recover, fold, zip, sequence, BatchResult |
| BnfParserTest | 52 | terminals, non-terminals, sequences, alternation, repetition, regex, comments |
| BnfModelTest | 30 | Grammar/Rule/RuleExpression construction and querying |
| DialectExtensionTest | 28 | RuleAddition, RuleReplacement, RuleExtension, RuleDeletion, DialectRegistry |
| RecursiveDescentEngineTest | 45 | memoization, left-recursion, backtracking, nested rules |
| GrammarLoaderTest | 8 | classpath loading, file loading, merging, invalid grammar |
| AstNodeTest | 36 | all node types, metadata, location, children |
| AstVisitorTest | 19 | traversal, transformation, visitor dispatch |
| ScopeTreeTest | 75 | nesting, shadowing, path tracking, NumericIndexStore, KeyMappedStore, ScopeTracer |
| TypeSystemTest | 44 | PexType coercions, TypeDescriptor |
| ExecutionEngineTest | 60 | handler dispatch, timeout, recursion limit, function registry, listeners |
| TelemetryTest | 18 | ParseEvent, ExecuteEvent, ScopeEvent, ErrorEvent, listener dispatch, pattern matching |
| PreconditionsTest | 11 | requireNonNull, requireTrue, requireNotEmpty |
| SourceLocationTest | 10 | all factory methods, toString() branches, equality |
| ComplexBaseTest, RealWorldBaseTest | 21 | cross-component scenarios |
| **Total** | **522** | |

---

## Known Limitations

- `PluginRegistry` is a JVM-global singleton; it can behave unexpectedly in classpath-isolated environments (OSGi, module layers). See the [top-level inconsistencies section](../docs/CODE_OVERVIEW.md#inconsistencies-inefficiencies-and-ambiguities).
- `BnfParser` does not support Unicode escape sequences in terminal strings (`'A'`).
- Regex terminals in EBNF are evaluated via `java.util.regex` at parse time — there is no pre-compilation cache.

# pex-arithmetics — Code Overview

> Part of the PEX multi-module project. See [top-level CODE_OVERVIEW.md](../docs/CODE_OVERVIEW.md) for project-wide context.

---

## Purpose

`pex-arithmetics` extends the pex-base execution engine with full numeric computation support:

- **Integer and floating-point arithmetic** with overflow detection
- **Bitwise operations** (AND, OR, XOR, NOT, shifts)
- **Boolean logic** with short-circuit evaluation
- **Comparison operators** with IEEE 754-compliant equality
- **All `java.lang.Math` functions** as native functions (25+)
- **Type conversion functions** (`int()`, `float()`, `long()`, `double()`, `str()`, `bool()`)
- **Radix literals and functions** — hex (`0xFF`), binary (`0b1010`), octal (`0o17`)
- **Numeric type promotion** — widening rules across int/long/float/double

---

## Package Structure

```
ssg.pex.arithmetics
├── ArithmeticsPlugin           SPI PexPlugin (load order 100)
├── handler/
│   ├── ArithmeticHandlerProvider   registers all handlers
│   ├── IntArithmeticHandler        integer operations
│   ├── FloatArithmeticHandler      float/double operations
│   ├── ComparisonHandler           <, >, <=, >=, ==, != with IEEE 754
│   ├── BooleanHandler              &&, ||, ! with short-circuit
│   ├── BitwiseHandler              &, |, ^, ~, <<, >>, >>>
│   └── RadixHandler                0x/0b/0o literal parsing
├── function/
│   ├── MathFunctions           java.lang.Math wrappers
│   ├── ConversionFunctions     int/float/long/double/str/bool/char
│   └── RadixFunctions          hex()/bin()/oct()
├── grammar/
│   └── ArithmeticsGrammarProvider  EBNF rules for arithmetic expressions
├── type/
│   ├── ArithmeticTypeCoercion  coerceToBoolean, coerceToNumber, isNumeric
│   └── NumericPromotion        widening: int → long → float → double
└── ast/
    └── RadixLiteralNode        record wrapping a radix-prefixed integer
```

---

## Key Components

### ArithmeticsPlugin

Registered via `META-INF/services/ssg.pex.spi.PexPlugin`. On `initialize(ctx)`, delegates to `ArithmeticsGrammarProvider` (contributes grammar rules for arithmetic expressions) and `ArithmeticHandlerProvider` (registers all seven handlers and 40+ native functions).

### Handler Design

All handlers extend nothing — they implement `NodeHandler` directly. Each handler is registered for `BinaryOpNode` or `UnaryOpNode` and dispatches on `Operator`:

- `IntArithmeticHandler`: ADD, SUB, MUL, DIV, MOD for int/long operands
- `FloatArithmeticHandler`: same operators for float/double
- `ComparisonHandler`: EQ, NEQ, LT, LTE, GT, GTE — uses `l == r` for equality (IEEE 754: -0.0 == 0.0, NaN != NaN)
- `BooleanHandler`: AND, OR, NOT — short-circuit evaluation where applicable
- `BitwiseHandler`: BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, SHL, SHR, USHR
- `RadixHandler`: handles `ExtensionNode` with `extensionType="radix_literal"` wrapping a `RadixLiteralNode`

`NumericPromotion` runs before each binary handler call to promote both operands to a common type.

### RadixLiteralNode

A standalone record (not `AstNode`) wrapping a `long value`, `int radix`, and `String originalText`. Cannot extend the sealed `AstNode` hierarchy; instead it is placed inside an `ExtensionNode`. The `parse(String)` factory method understands `0x`/`0X`, `0b`/`0B`, `0o`/`0O` prefixes.

`literalValue()` returns `Integer` if the value fits in int range, otherwise `Long`.

### IEEE 754 Fix

`ComparisonHandler.compareNumeric()` was fixed (commit 5) to use `l == r` for `EQ` and `l != r` for `NEQ` before falling back to `Double.compare()`. Without this, `-0.0 == 0.0` would incorrectly return false.

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| IntArithmeticHandlerTest | 35 | add/sub/mul/div/mod, overflow, type promotion |
| FloatArithmeticHandlerTest | 30 | float ops, NaN, infinity, promotion |
| ComparisonHandlerTest | 28 | all six comparison operators, IEEE 754 edge cases |
| BooleanHandlerTest | 25 | AND/OR short-circuit, NOT, truthy coercions |
| BitwiseHandlerTest | 22 | all bitwise ops, shift edge cases, unsigned shift |
| MathFunctionsTest | 40 | all Math.* wrappers |
| ConversionFunctionsTest | 28 | int/float/long/double/str/bool conversions |
| RadixFunctionsTest | 20 | hex()/bin()/oct() and round-trips |
| NumericPromotionTest | 30 | all widening paths |
| RadixHandlerTest | 16 | RadixLiteralNode.parse/literalValue/toRadixString, RadixHandler.handle |
| ComplexArithmeticsTest, RealWorldArithmeticsTest | 14 | cross-handler scenarios |
| **Total** | **288** | |

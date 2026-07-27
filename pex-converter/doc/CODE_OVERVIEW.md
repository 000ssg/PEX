# pex-converter — Code Overview

> Part of the PEX multi-module project. See [top-level CODE_OVERVIEW.md](../docs/CODE_OVERVIEW.md) for project-wide context.

---

## Purpose

`pex-converter` translates PEX ASTs to source code in seven target languages, and provides a JIT pipeline that compiles AST-derived Java source to bytecode and executes it in-process.

---

## Package Structure

```
ssg.pex.converter
├── Converter               interface: convert(AstNode, ConversionConfig) → Result<String>
├── ConversionConfig        record: targetLanguage, indent, options
├── TargetLanguage          enum: JAVA, CSHARP, CPP, KOTLIN, SCALA, RUBY, BASIC
├── ConverterPlugin         SPI PexPlugin (load order 500)
├── lang/
│   ├── AbstractConverter   template-method base class
│   ├── JavaConverter
│   ├── CSharpConverter
│   ├── CppConverter
│   ├── KotlinConverter
│   ├── ScalaConverter
│   ├── RubyConverter
│   └── BasicConverter
├── mapping/
│   ├── TypeMapper          PEX types → target type names
│   ├── OperatorMapper      PEX operators → target operator syntax
│   └── NamingMapper        camelCase ↔ snake_case ↔ PascalCase transforms
├── jit/
│   ├── JitCompiler         javax.tools.ToolProvider in-memory compilation
│   ├── JitConverter        compile + load shortcut
│   ├── JitCompilationResult Result<byte[]> + diagnostic messages
│   ├── JitExecutable       loaded class + invoke method
│   └── JitClassLoader      defineClass from byte[]
└── spi/
    ├── ConverterFactory    interface: language() + create(config)
    └── ConverterRegistry   ServiceLoader-based factory discovery
```

---

## Key Components

### AbstractConverter

All seven language converters extend `AbstractConverter`, which implements the visitor pattern over `AstNode`. Subclasses override language-specific rendering methods (e.g., `renderIfStatement`, `renderFunctionDef`, `mapType`). The base class provides traversal, indentation management, and delegation to `TypeMapper`, `OperatorMapper`, and `NamingMapper`.

### JIT Pipeline

1. `JavaConverter` converts an `AstNode` to a Java source string.
2. `JitCompiler.compile(className, source)` uses `javax.tools.JavaCompiler` to compile the source in-memory to bytecode (`byte[]`).
3. `JitClassLoader.defineClass(name, bytes)` loads the bytecode.
4. `JitExecutable.invoke(args)` reflectively calls the entry-point method.

**Limitation**: Requires a JDK (not just JRE) at runtime — `ToolProvider.getSystemJavaCompiler()` returns null in JRE-only deployments.

### ConverterFactory SPI

Registered via `META-INF/services/ssg.pex.converter.spi.ConverterFactory`. `ConverterRegistry.forLanguage(TargetLanguage)` returns the first matching factory. This allows third-party converters to be added to the classpath without modifying PEX.

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| JavaConverterTest | 25 | all AST node types to Java |
| CSharpConverterTest | 22 | C# syntax, nullable types |
| CppConverterTest | 20 | C++ syntax, pointers, headers |
| KotlinConverterTest | 20 | Kotlin idioms, val/var |
| ScalaConverterTest | 20 | Scala syntax, case classes |
| RubyConverterTest | 20 | Ruby syntax, method_missing |
| BasicConverterTest | 20 | BASIC syntax, line numbers |
| NamingMapperTest | 22 | camelCase/snake_case/PascalCase conversions |
| JitCompilerTest | 15 | compile, load, invoke, error cases |
| ComplexConverterTest, RealWorldConverterTest | 34 | multi-node, real-world scenarios |
| **Total** | **218** | |

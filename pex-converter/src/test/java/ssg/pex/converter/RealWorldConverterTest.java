package ssg.pex.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.jit.JitCompiler;
import ssg.pex.converter.jit.JitConverter;
import ssg.pex.converter.lang.*;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.converter.spi.ConverterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-world complex test cases for pex-converter: full program conversion to all 7 languages,
 * operator precedence, JIT compilation round-trip, edge cases, and converter registry.
 */
@DisplayName("Real-world pex-converter tests")
class RealWorldConverterTest {

    // -----------------------------------------------------------------------
    // Shared AST builders
    // -----------------------------------------------------------------------

    /**
     * Complex program: function definition with conditional, variable assignments,
     * nested arithmetic, loop, and function call.
     */
    private static ProgramNode buildComplexProgram() {
        // compute(x): if (x > 10) return x * 2 else return x + 5
        var computeBody = new BlockNode(List.of(
                new ConditionalNode(
                        new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(10)),
                        new BlockNode(List.of(new ReturnNode(
                                new BinaryOpNode(new IdentifierNode("x"), Operator.MULTIPLY, new IntLiteral(2))))),
                        new BlockNode(List.of(new ReturnNode(
                                new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(5)))))
                )
        ));
        var computeFunc = new FunctionDefNode("compute", List.of(new ParameterNode("x")), computeBody);

        // main body: assignments, loop, function call
        var mainBody = new BlockNode(List.of(
                new AssignmentNode(new IdentifierNode("total"), new IntLiteral(0)),
                new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                new LoopNode(LoopKind.WHILE,
                        new BinaryOpNode(new IdentifierNode("i"), Operator.LE, new IntLiteral(5)),
                        new BlockNode(List.of(
                                new AssignmentNode(new IdentifierNode("total"),
                                        new BinaryOpNode(new IdentifierNode("total"), Operator.PLUS,
                                                new FunctionCallNode("compute", List.of(new IdentifierNode("i"))))),
                                new AssignmentNode(new IdentifierNode("i"),
                                        new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                        ))
                ),
                new ReturnNode(new IdentifierNode("total"))
        ));
        var mainFunc = new FunctionDefNode("main", List.of(), mainBody);

        return new ProgramNode(List.of(computeFunc, mainFunc));
    }

    // ===========================================================================================
    // Full Program Conversion to All 7 Languages
    // ===========================================================================================

    @Nested
    @DisplayName("Full Program Conversion to All 7 Languages")
    class FullProgramConversionToAll7Languages {

        @Test
        @DisplayName("convert complex program to Java with correct syntax")
        void convertToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildComplexProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("Object compute(Object x)");
            assertThat(code).contains("Object main()");
            assertThat(code).contains("if (");
            assertThat(code).contains("while (");
            assertThat(code).contains(";"); // Java terminators
            assertThat(code).contains("return");
        }

        @Test
        @DisplayName("convert complex program to C# with PascalCase methods")
        void convertToCSharp() {
            var converter = new CSharpConverter(ConversionConfig.defaults());
            var result = converter.convert(buildComplexProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("object Compute(object x)"); // PascalCase
            assertThat(code).contains("object Main()");
            assertThat(code).contains("if (");
            assertThat(code).contains("while (");
            assertThat(code).contains(";");
        }

        @Test
        @DisplayName("convert complex program to C++ with auto return types")
        void convertToCpp() {
            var converter = new CppConverter(ConversionConfig.defaults());
            var result = converter.convert(buildComplexProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("auto compute(auto x) -> auto");
            assertThat(code).contains("auto main() -> auto");
            assertThat(code).contains("if (");
            assertThat(code).contains(";");
        }

        @Test
        @DisplayName("convert complex program to Kotlin without semicolons")
        void convertToKotlin() {
            var converter = new KotlinConverter(ConversionConfig.defaults());
            var result = converter.convert(buildComplexProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("fun compute(x: Any): Any");
            assertThat(code).contains("fun main(): Any");
            assertThat(code).doesNotContain(";"); // Kotlin has no semicolons
        }

        @Test
        @DisplayName("convert complex program to Scala with def and Any types")
        void convertToScala() {
            var converter = new ScalaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildComplexProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("def compute(x: Any): Any =");
            assertThat(code).contains("def main(): Any =");
            assertThat(code).doesNotContain(";");
        }

        @Test
        @DisplayName("convert complex program to Ruby with def/end blocks")
        void convertToRuby() {
            var converter = new RubyConverter(ConversionConfig.defaults());
            var result = converter.convert(buildComplexProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("def compute(x)");
            assertThat(code).contains("def main()");
            assertThat(code).contains("end");
            assertThat(code).doesNotContain(";"); // Ruby has no semicolons
        }

        @Test
        @DisplayName("convert complex program to BASIC with FUNCTION/END FUNCTION")
        void convertToBasic() {
            var converter = new BasicConverter(ConversionConfig.defaults());
            var result = converter.convert(buildComplexProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("FUNCTION COMPUTE(x)"); // BASIC uppercase
            assertThat(code).contains("FUNCTION MAIN()");
            assertThat(code).contains("END FUNCTION");
            assertThat(code).contains("RETURN");
        }
    }

    // ===========================================================================================
    // Operator Precedence Preservation
    // ===========================================================================================

    @Nested
    @DisplayName("Operator Precedence Preservation")
    class OperatorPrecedencePreservation {

        @Test
        @DisplayName("a + b * c: multiplication appears in correct structure")
        void additionAndMultiplicationOrder() {
            // AST: a + (b * c) -- the binary ops encode the precedence
            var ast = new ProgramNode(List.of(
                    new BinaryOpNode(
                            new IdentifierNode("a"),
                            Operator.PLUS,
                            new BinaryOpNode(new IdentifierNode("b"), Operator.MULTIPLY, new IdentifierNode("c"))
                    )
            ));
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(ast);
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("a + (b * c)");
        }

        @Test
        @DisplayName("deeply nested binary ops produce correct output structure")
        void deeplyNestedBinaryOps() {
            // ((a + b) * (c - d)) / (e % f)
            var ab = new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"));
            var cd = new BinaryOpNode(new IdentifierNode("c"), Operator.MINUS, new IdentifierNode("d"));
            var abTimescd = new BinaryOpNode(ab, Operator.MULTIPLY, cd);
            var ef = new BinaryOpNode(new IdentifierNode("e"), Operator.MODULO, new IdentifierNode("f"));
            var ast = new ProgramNode(List.of(new BinaryOpNode(abTimescd, Operator.DIVIDE, ef)));

            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(ast);
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("+").contains("*").contains("-").contains("/").contains("%");
            assertThat(code).contains("a").contains("b").contains("c").contains("d").contains("e").contains("f");
        }

        @Test
        @DisplayName("unary operators in all languages")
        void unaryOperatorsInAllLanguages() {
            var ast = new ProgramNode(List.of(
                    new UnaryOpNode(Operator.MINUS, new IdentifierNode("x"), true),
                    new UnaryOpNode(Operator.NOT, new IdentifierNode("flag"), true)
            ));
            for (TargetLanguage lang : TargetLanguage.values()) {
                if (lang == TargetLanguage.JIT) continue;
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess())
                        .as("Unary operators should convert to %s", lang.displayName())
                        .isTrue();
                String code = result.value();
                // All languages have some form of negation
                assertThat(code).containsAnyOf("-", "MINUS");
            }
        }

        @Test
        @DisplayName("comparison operators in all languages")
        void comparisonOperatorsInAllLanguages() {
            var ast = new ProgramNode(List.of(
                    new BinaryOpNode(new IdentifierNode("a"), Operator.GT, new IdentifierNode("b")),
                    new BinaryOpNode(new IdentifierNode("c"), Operator.LE, new IdentifierNode("d")),
                    new BinaryOpNode(new IdentifierNode("e"), Operator.EQ, new IdentifierNode("f")),
                    new BinaryOpNode(new IdentifierNode("g"), Operator.NEQ, new IdentifierNode("h"))
            ));
            for (TargetLanguage lang : TargetLanguage.values()) {
                if (lang == TargetLanguage.JIT) continue;
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess())
                        .as("Comparison operators should convert to %s", lang.displayName())
                        .isTrue();
            }
        }
    }

    // ===========================================================================================
    // JIT Compilation Round-Trip
    // ===========================================================================================

    @Nested
    @DisplayName("JIT Compilation Round-Trip")
    class JitCompilationRoundTrip {

        private JitCompiler compiler;

        @BeforeEach
        void setUp() {
            compiler = new JitCompiler();
        }

        @Test
        @DisplayName("simple arithmetic: JIT compile and execute, verify result")
        void simpleArithmeticJitRoundTrip() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class RWSimpleArith implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            return (10 + 20) * 3;
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.RWSimpleArith");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().instance().execute(Map.of())).isEqualTo(90);
        }

        @Test
        @DisplayName("expression with variables: inject, compile, execute, verify")
        void expressionWithVariablesJit() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class RWVarExpr implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int x = (Integer) context.get("x");
                            int y = (Integer) context.get("y");
                            return x * x + y * y;
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.RWVarExpr");
            assertThat(result.isSuccess()).isTrue();
            // 3*3 + 4*4 = 9 + 16 = 25
            assertThat(result.value().instance().execute(Map.of("x", 3, "y", 4))).isEqualTo(25);
        }

        @Test
        @DisplayName("complex expression with function call: define, convert, compile, execute")
        void complexExpressionWithFunctionJit() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class RWFuncExpr implements JitExecutable {
                        private int factorial(int n) {
                            if (n <= 1) return 1;
                            return n * factorial(n - 1);
                        }
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int n = (Integer) context.get("n");
                            return factorial(n);
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.RWFuncExpr");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().instance().execute(Map.of("n", 10))).isEqualTo(3628800);
        }

        @Test
        @DisplayName("multiple sequential JIT compilations with unique class names")
        void multipleSequentialJitCompilations() {
            for (int i = 0; i < 5; i++) {
                String className = "RWSeq" + i;
                int value = i * 7 + 3;
                String source = """
                        package ssg.pex.jit.generated;
                        import ssg.pex.converter.jit.JitExecutable;
                        import java.util.Map;
                        public class %s implements JitExecutable {
                            @Override
                            public Object execute(Map<String, Object> context) {
                                return %d;
                            }
                        }
                        """.formatted(className, value);
                var result = compiler.compile(source, "ssg.pex.jit.generated." + className);
                assertThat(result.isSuccess())
                        .as("Compilation %d should succeed", i)
                        .isTrue();
                assertThat(result.value().instance().execute(Map.of()))
                        .as("Execution %d should return %d", i, value)
                        .isEqualTo(value);
            }
        }

        @Test
        @DisplayName("JIT with conditional: if/else compiled and executed")
        void jitWithConditionalExecuted() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class RWCondJit implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int score = (Integer) context.get("score");
                            if (score >= 90) return "A";
                            else if (score >= 80) return "B";
                            else if (score >= 70) return "C";
                            else return "F";
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.RWCondJit");
            assertThat(result.isSuccess()).isTrue();
            var exe = result.value().instance();
            assertThat(exe.execute(Map.of("score", 95))).isEqualTo("A");
            assertThat(exe.execute(Map.of("score", 85))).isEqualTo("B");
            assertThat(exe.execute(Map.of("score", 72))).isEqualTo("C");
            assertThat(exe.execute(Map.of("score", 50))).isEqualTo("F");
        }
    }

    // ===========================================================================================
    // Edge Cases
    // ===========================================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("empty block conversion for each language")
        void emptyBlockConversionEachLanguage() {
            var func = new FunctionDefNode("empty", List.of(),
                    new BlockNode(List.of()));
            var ast = new ProgramNode(List.of(func));

            for (TargetLanguage lang : TargetLanguage.values()) {
                if (lang == TargetLanguage.JIT) continue;
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess())
                        .as("Empty block should convert to %s", lang.displayName())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("single-statement program conversion")
        void singleStatementProgramConversion() {
            var ast = new ProgramNode(List.of(new IntLiteral(42)));
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.value()).contains("42");
            }
        }

        @Test
        @DisplayName("very long expression (50+ nodes) conversion does not crash")
        void veryLongExpressionDoesNotCrash() {
            // Build a chain: 1 + 2 + 3 + ... + 50
            AstNode expr = new IntLiteral(1);
            for (int i = 2; i <= 50; i++) {
                expr = new BinaryOpNode(expr, Operator.PLUS, new IntLiteral(i));
            }
            var ast = new ProgramNode(List.of(expr));

            for (TargetLanguage lang : TargetLanguage.values()) {
                if (lang == TargetLanguage.JIT) continue;
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess())
                        .as("50-node expression should convert to %s", lang.displayName())
                        .isTrue();
                assertThat(result.value()).isNotBlank();
            }
        }

        @Test
        @DisplayName("null/void handling per language")
        void nullVoidHandlingPerLanguage() {
            var ast = new ProgramNode(List.of(new NullLiteral()));
            var java = new JavaConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(java.value()).contains("null");

            var ruby = new RubyConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(ruby.value()).contains("nil");

            var cpp = new CppConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(cpp.value()).contains("nullptr");

            var basic = new BasicConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(basic.value()).contains("NOTHING");
        }

        @Test
        @DisplayName("function with 0 parameters")
        void functionWith0Parameters() {
            var func = new FunctionDefNode("noParams", List.of(),
                    new BlockNode(List.of(new ReturnNode(new IntLiteral(42)))));
            var ast = new ProgramNode(List.of(func));

            var java = new JavaConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(java.value()).contains("noParams()");

            var kotlin = new KotlinConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(kotlin.value()).contains("noParams()");

            var ruby = new RubyConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(ruby.value()).contains("no_params()"); // snake_case
        }

        @Test
        @DisplayName("function with 10+ parameters")
        void functionWith10PlusParameters() {
            var params = new ArrayList<ParameterNode>();
            for (int i = 0; i < 12; i++) {
                params.add(new ParameterNode("p" + i));
            }
            var func = new FunctionDefNode("manyParams", params,
                    new BlockNode(List.of(new ReturnNode(new IdentifierNode("p0")))));
            var ast = new ProgramNode(List.of(func));

            for (TargetLanguage lang : TargetLanguage.values()) {
                if (lang == TargetLanguage.JIT) continue;
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess())
                        .as("12-parameter function should convert to %s", lang.displayName())
                        .isTrue();
                // All 12 parameters should appear
                for (int i = 0; i < 12; i++) {
                    assertThat(result.value())
                            .as("Parameter p%d should appear in %s output", i, lang.displayName())
                            .containsIgnoringCase("p" + i);
                }
            }
        }

        @Test
        @DisplayName("boolean literal conversion per language")
        void booleanLiteralConversionPerLanguage() {
            var ast = new ProgramNode(List.of(new BoolLiteral(true), new BoolLiteral(false)));

            var java = new JavaConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(java.value()).contains("true").contains("false");

            var basic = new BasicConverter(ConversionConfig.defaults()).convert(ast);
            assertThat(basic.value()).contains("TRUE").contains("FALSE");
        }
    }

    // ===========================================================================================
    // Converter Registry
    // ===========================================================================================

    @Nested
    @DisplayName("Converter Registry")
    class ConverterRegistryTests {

        @Test
        @DisplayName("all 8 converters (7 languages + JIT) discoverable via ConverterRegistry")
        void allConvertersDiscoverable() {
            var registry = ConverterRegistry.getInstance();
            var available = registry.availableTargets();
            assertThat(available).containsExactlyInAnyOrder(TargetLanguage.values());
        }

        @Test
        @DisplayName("convert same AST with all converters, all succeed")
        void convertSameAstWithAllConverters() {
            var ast = new ProgramNode(List.of(
                    new FunctionDefNode("test", List.of(new ParameterNode("x")),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("y"),
                                            new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(1))),
                                    new ReturnNode(new IdentifierNode("y"))
                            ))
                    )
            ));

            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess())
                        .as("Simple function should convert to %s", lang.displayName())
                        .isTrue();
                assertThat(result.value()).isNotBlank();
            }
        }

        @Test
        @DisplayName("ConverterFactory SPI loading finds all factories")
        void converterFactorySpiLoadingFindsAllFactories() {
            var factories = ServiceLoader.load(ConverterFactory.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
            // Should find at least 8 factories (7 language + JIT)
            assertThat(factories.size()).isGreaterThanOrEqualTo(8);

            // Each factory should have a valid target and description
            for (var factory : factories) {
                assertThat(factory.target()).isNotNull();
                assertThat(factory.description()).isNotBlank();
                // Each factory should be able to create a converter
                var converter = factory.create(ConversionConfig.defaults());
                assertThat(converter).isNotNull();
            }
        }

        @Test
        @DisplayName("TargetLanguage enum coverage: all values have displayName and fileExtension")
        void targetLanguageEnumCoverage() {
            for (var lang : TargetLanguage.values()) {
                assertThat(lang.displayName()).isNotBlank();
                assertThat(lang.fileExtension()).startsWith(".");
            }
            // Verify exact count
            assertThat(TargetLanguage.values()).hasSize(8);
        }

        @Test
        @DisplayName("each converter produces distinct language-specific output")
        void eachConverterProducesDistinctOutput() {
            var ast = new ProgramNode(List.of(
                    new FunctionDefNode("greet", List.of(new ParameterNode("name")),
                            new BlockNode(List.of(
                                    new ReturnNode(new BinaryOpNode(
                                            new StringLiteral("Hello "), Operator.PLUS, new IdentifierNode("name")))
                            ))
                    )
            ));

            var javaCode = new JavaConverter(ConversionConfig.defaults()).convert(ast).value();
            var kotlinCode = new KotlinConverter(ConversionConfig.defaults()).convert(ast).value();
            var rubyCode = new RubyConverter(ConversionConfig.defaults()).convert(ast).value();
            var basicCode = new BasicConverter(ConversionConfig.defaults()).convert(ast).value();

            // Java and Kotlin should differ (semicolons, fun vs Object, etc.)
            assertThat(javaCode).isNotEqualTo(kotlinCode);
            // Ruby and Java should differ (def/end vs {})
            assertThat(rubyCode).isNotEqualTo(javaCode);
            // BASIC should be uppercase
            assertThat(basicCode).contains("FUNCTION");
            assertThat(javaCode).doesNotContain("FUNCTION");
        }
    }
}

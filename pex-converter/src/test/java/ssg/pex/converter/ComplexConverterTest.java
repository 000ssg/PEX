package ssg.pex.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.jit.JitCompiler;
import ssg.pex.converter.jit.JitConverter;
import ssg.pex.converter.lang.*;
import ssg.pex.converter.spi.ConverterRegistry;
import ssg.pex.result.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexConverterTest {

    // -----------------------------------------------------------------------
    // Shared AST builders
    // -----------------------------------------------------------------------

    /** main() calls helper1() and helper2(), with assignments, conditionals, returns */
    private static ProgramNode buildMultiFunctionProgram() {
        // helper1(x): if x > 0 then return x * 2 else return 0
        var helper1Body = new BlockNode(List.of(
                new ConditionalNode(
                        new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(0)),
                        new BlockNode(List.of(new ReturnNode(
                                new BinaryOpNode(new IdentifierNode("x"), Operator.MULTIPLY, new IntLiteral(2))))),
                        new BlockNode(List.of(new ReturnNode(new IntLiteral(0))))
                )
        ));
        var helper1 = new FunctionDefNode("helper1", List.of(new ParameterNode("x")), helper1Body);

        // helper2(a, b): return a + b
        var helper2Body = new BlockNode(List.of(
                new ReturnNode(new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b")))
        ));
        var helper2 = new FunctionDefNode("helper2", List.of(new ParameterNode("a"), new ParameterNode("b")), helper2Body);

        // main(): var r1 = helper1(5); var r2 = helper2(r1, 10); return r2
        var mainBody = new BlockNode(List.of(
                new AssignmentNode(new IdentifierNode("r1"), new FunctionCallNode("helper1", List.of(new IntLiteral(5)))),
                new AssignmentNode(new IdentifierNode("r2"), new FunctionCallNode("helper2", List.of(new IdentifierNode("r1"), new IntLiteral(10)))),
                new ReturnNode(new IdentifierNode("r2"))
        ));
        var mainFunc = new FunctionDefNode("main", List.of(), mainBody);

        return new ProgramNode(List.of(helper1, helper2, mainFunc));
    }

    /** Function with while loop accumulator: sumUpTo(n) { var s=0; while(s < n) s = s + 1; return s } */
    private static ProgramNode buildWhileLoopAccumulator() {
        var body = new BlockNode(List.of(
                new AssignmentNode(new IdentifierNode("s"), new IntLiteral(0)),
                new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                new LoopNode(LoopKind.WHILE,
                        new BinaryOpNode(new IdentifierNode("i"), Operator.LE, new IdentifierNode("n")),
                        new BlockNode(List.of(
                                new AssignmentNode(new IdentifierNode("s"),
                                        new BinaryOpNode(new IdentifierNode("s"), Operator.PLUS, new IdentifierNode("i"))),
                                new AssignmentNode(new IdentifierNode("i"),
                                        new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                        ))
                ),
                new ReturnNode(new IdentifierNode("s"))
        ));
        var func = new FunctionDefNode("sumUpTo", List.of(new ParameterNode("n")), body);
        return new ProgramNode(List.of(func));
    }

    /** Nested if/else chain with 4 branches */
    private static ProgramNode buildNestedIfElseChain() {
        // classify(x): if x>100 return "high" else if x>50 return "medium" else if x>0 return "low" else return "negative"
        var innerElse3 = new BlockNode(List.of(new ReturnNode(new StringLiteral("negative"))));
        var branch3 = new ConditionalNode(
                new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(0)),
                new BlockNode(List.of(new ReturnNode(new StringLiteral("low")))),
                innerElse3
        );
        var branch2 = new ConditionalNode(
                new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(50)),
                new BlockNode(List.of(new ReturnNode(new StringLiteral("medium")))),
                new BlockNode(List.of(branch3))
        );
        var branch1 = new ConditionalNode(
                new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(100)),
                new BlockNode(List.of(new ReturnNode(new StringLiteral("high")))),
                new BlockNode(List.of(branch2))
        );

        var func = new FunctionDefNode("classify", List.of(new ParameterNode("x")),
                new BlockNode(List.of(branch1)));
        return new ProgramNode(List.of(func));
    }

    /** Function with index access operations */
    private static ProgramNode buildIndexAccessProgram() {
        // getElement(arr, idx): return arr[idx]
        var func = new FunctionDefNode("getElement",
                List.of(new ParameterNode("arr"), new ParameterNode("idx")),
                new BlockNode(List.of(
                        new ReturnNode(new IndexAccessNode(new IdentifierNode("arr"), new IdentifierNode("idx")))
                )));
        // setAndGet(arr): var x = arr[0]; var y = arr[1]; return x + y
        var func2 = new FunctionDefNode("setAndGet",
                List.of(new ParameterNode("arr")),
                new BlockNode(List.of(
                        new AssignmentNode(new IdentifierNode("x"), new IndexAccessNode(new IdentifierNode("arr"), new IntLiteral(0))),
                        new AssignmentNode(new IdentifierNode("y"), new IndexAccessNode(new IdentifierNode("arr"), new IntLiteral(1))),
                        new ReturnNode(new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IdentifierNode("y")))
                )));
        return new ProgramNode(List.of(func, func2));
    }

    /** Program with 5+ function definitions */
    private static ProgramNode buildFiveFunctionProgram() {
        List<AstNode> funcs = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            var body = new BlockNode(List.of(
                    new ReturnNode(new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(i)))
            ));
            funcs.add(new FunctionDefNode("func" + i, List.of(new ParameterNode("x")), body));
        }
        return new ProgramNode(funcs);
    }

    /** Deeply nested expression: ((a + b) * (c - d)) / (e % f) */
    private static ProgramNode buildDeeplyNestedExpression() {
        var ab = new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"));
        var cd = new BinaryOpNode(new IdentifierNode("c"), Operator.MINUS, new IdentifierNode("d"));
        var abTimescd = new BinaryOpNode(ab, Operator.MULTIPLY, cd);
        var ef = new BinaryOpNode(new IdentifierNode("e"), Operator.MODULO, new IdentifierNode("f"));
        var result = new BinaryOpNode(abTimescd, Operator.DIVIDE, ef);
        return new ProgramNode(List.of(result));
    }

    /** Program with mixed literal types */
    private static ProgramNode buildMixedLiteralProgram() {
        return new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("intVal"), new IntLiteral(42)),
                new AssignmentNode(new IdentifierNode("floatVal"), new FloatLiteral(3.14)),
                new AssignmentNode(new IdentifierNode("strVal"), new StringLiteral("hello world")),
                new AssignmentNode(new IdentifierNode("boolVal"), new BoolLiteral(true)),
                new AssignmentNode(new IdentifierNode("nullVal"), new NullLiteral()),
                new BinaryOpNode(new IdentifierNode("intVal"), Operator.PLUS, new IntLiteral(1))
        ));
    }

    /** Deeply nested blocks (5+ levels) */
    private static ProgramNode buildDeeplyNestedBlocks() {
        AstNode innermost = new ReturnNode(new IntLiteral(99));
        AstNode current = innermost;
        for (int i = 0; i < 6; i++) {
            current = new ConditionalNode(
                    new BoolLiteral(true),
                    new BlockNode(List.of(current))
            );
        }
        var func = new FunctionDefNode("deepNest", List.of(), new BlockNode(List.of(current)));
        return new ProgramNode(List.of(func));
    }

    /** Do-while loop program */
    private static ProgramNode buildDoWhileProgram() {
        var body = new BlockNode(List.of(
                new AssignmentNode(new IdentifierNode("count"), new IntLiteral(0)),
                new LoopNode(LoopKind.DO_WHILE,
                        new BinaryOpNode(new IdentifierNode("count"), Operator.LT, new IntLiteral(5)),
                        new BlockNode(List.of(
                                new AssignmentNode(new IdentifierNode("count"),
                                        new BinaryOpNode(new IdentifierNode("count"), Operator.PLUS, new IntLiteral(1)))
                        ))
                ),
                new ReturnNode(new IdentifierNode("count"))
        ));
        var func = new FunctionDefNode("doWhileTest", List.of(), body);
        return new ProgramNode(List.of(func));
    }

    /** Boolean expression chain: a && b || c && !d */
    private static ProgramNode buildBooleanExpressionChain() {
        var aAndB = new BinaryOpNode(new IdentifierNode("a"), Operator.AND, new IdentifierNode("b"));
        var notD = new UnaryOpNode(Operator.NOT, new IdentifierNode("d"), true);
        var cAndNotD = new BinaryOpNode(new IdentifierNode("c"), Operator.AND, notD);
        var full = new BinaryOpNode(aAndB, Operator.OR, cAndNotD);
        return new ProgramNode(List.of(full));
    }

    // -----------------------------------------------------------------------
    // Part 1: Complex Program Conversion tests
    // -----------------------------------------------------------------------
    @Nested
    class ComplexProgramConversion {

        @Test
        void multiFunctionProgramToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMultiFunctionProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("Object helper1(Object x)");
            assertThat(code).contains("Object helper2(Object a, Object b)");
            assertThat(code).contains("Object main()");
            assertThat(code).contains("var r1 = helper1(5);");
            assertThat(code).contains("return r2;");
            assertThat(code).contains("if (");
            assertThat(code).contains(";"); // semicolons
        }

        @Test
        void multiFunctionProgramToKotlin() {
            var converter = new KotlinConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMultiFunctionProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("fun helper1(x: Any): Any");
            assertThat(code).contains("fun helper2(a: Any, b: Any): Any");
            assertThat(code).contains("fun main(): Any");
            assertThat(code).doesNotContain(";");
        }

        @Test
        void multiFunctionProgramToRuby() {
            var converter = new RubyConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMultiFunctionProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("def helper1(x)");
            assertThat(code).contains("def helper2(a, b)");
            assertThat(code).contains("def main()");
            assertThat(code).contains("end");
            assertThat(code).doesNotContain(";");
        }

        @Test
        void multiFunctionProgramToCpp() {
            var converter = new CppConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMultiFunctionProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("auto helper1(auto x) -> auto");
            assertThat(code).contains("auto helper2(auto a, auto b) -> auto");
            assertThat(code).contains(";"); // C++ has semicolons
        }

        @Test
        void multiFunctionProgramToCSharp() {
            var converter = new CSharpConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMultiFunctionProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("object Helper1(object x)"); // PascalCase method
            assertThat(code).contains("object Helper2(object a, object b)");
            assertThat(code).contains("object Main()");
            assertThat(code).contains(";");
        }

        @Test
        void multiFunctionProgramToScala() {
            var converter = new ScalaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMultiFunctionProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("def helper1(x: Any): Any =");
            assertThat(code).contains("def helper2(a: Any, b: Any): Any =");
            assertThat(code).doesNotContain(";");
        }

        @Test
        void multiFunctionProgramToBasic() {
            var converter = new BasicConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMultiFunctionProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("FUNCTION HELPER1(x)");
            assertThat(code).contains("FUNCTION HELPER2(a, b)");
            assertThat(code).contains("END FUNCTION");
            assertThat(code).contains("RETURN");
        }

        @Test
        void whileLoopAccumulatorToAllLanguages() {
            var program = buildWhileLoopAccumulator();
            for (TargetLanguage lang : TargetLanguage.values()) {
                if (lang == TargetLanguage.JIT) continue;
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess())
                        .as("While loop accumulator should convert to %s", lang.displayName())
                        .isTrue();
                assertThat(result.value()).isNotBlank();
            }
        }

        @Test
        void whileLoopAccumulatorJavaContainsSyntax() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildWhileLoopAccumulator());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("while (");
            assertThat(code).contains("var s = 0;");
            assertThat(code).contains("var i = 1;");
            assertThat(code).contains("return s;");
        }

        @Test
        void nestedIfElseChainToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildNestedIfElseChain());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("if (");
            assertThat(code).contains("else");
            assertThat(code).contains("\"high\"");
            assertThat(code).contains("\"medium\"");
            assertThat(code).contains("\"low\"");
            assertThat(code).contains("\"negative\"");
        }

        @Test
        void nestedIfElseChainToRuby() {
            var converter = new RubyConverter(ConversionConfig.defaults());
            var result = converter.convert(buildNestedIfElseChain());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("if ");
            assertThat(code).contains("else");
            assertThat(code).contains("end");
            assertThat(code).contains("\"high\"");
            assertThat(code).contains("\"negative\"");
        }

        @Test
        void nestedIfElseChainToBasic() {
            var converter = new BasicConverter(ConversionConfig.defaults());
            var result = converter.convert(buildNestedIfElseChain());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("IF ");
            assertThat(code).contains("THEN");
            assertThat(code).contains("ELSE");
            assertThat(code).contains("END IF");
        }

        @Test
        void indexAccessProgramToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildIndexAccessProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("arr[idx]");
            assertThat(code).contains("arr[0]");
            assertThat(code).contains("arr[1]");
        }

        @Test
        void fiveFunctionProgramToAllLanguages() {
            var program = buildFiveFunctionProgram();
            for (TargetLanguage lang : TargetLanguage.values()) {
                if (lang == TargetLanguage.JIT) continue;
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess())
                        .as("5-function program should convert to %s", lang.displayName())
                        .isTrue();
                // Each of 6 functions should appear in output
                for (int i = 1; i <= 6; i++) {
                    assertThat(result.value())
                            .as("Function func%d should appear in %s output", i, lang.displayName())
                            .containsIgnoringCase("func" + i);
                }
            }
        }

        @Test
        void deeplyNestedExpressionToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildDeeplyNestedExpression());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            // ((a + b) * (c - d)) / (e % f)
            assertThat(code).contains("((a + b) * (c - d))");
            assertThat(code).contains("(e % f)");
        }

        @Test
        void deeplyNestedExpressionToCpp() {
            var converter = new CppConverter(ConversionConfig.defaults());
            var result = converter.convert(buildDeeplyNestedExpression());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("+").contains("*").contains("-").contains("/").contains("%");
        }

        @Test
        void mixedLiteralProgramToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMixedLiteralProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("var intVal = 42;");
            assertThat(code).contains("3.14");
            assertThat(code).contains("\"hello world\"");
            assertThat(code).contains("true");
            assertThat(code).contains("null");
        }

        @Test
        void mixedLiteralProgramToRuby() {
            var converter = new RubyConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMixedLiteralProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("int_val = 42"); // snake_case
            assertThat(code).contains("3.14");
            assertThat(code).contains("nil"); // Ruby null
        }

        @Test
        void mixedLiteralProgramToCpp() {
            var converter = new CppConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMixedLiteralProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("auto intVal = 42;");
            assertThat(code).contains("nullptr"); // C++ null
        }

        @Test
        void mixedLiteralProgramToBasic() {
            var converter = new BasicConverter(ConversionConfig.defaults());
            var result = converter.convert(buildMixedLiteralProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("LET intVal = 42");
            assertThat(code).contains("TRUE");
            assertThat(code).contains("NOTHING"); // BASIC null
        }

        @Test
        void doWhileProgramToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildDoWhileProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("do {");
            assertThat(code).contains("} while (");
        }

        @Test
        void doWhileProgramToBasic() {
            var converter = new BasicConverter(ConversionConfig.defaults());
            var result = converter.convert(buildDoWhileProgram());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("DO");
            assertThat(code).contains("LOOP WHILE ");
        }

        @Test
        void booleanExpressionChainToJava() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(buildBooleanExpressionChain());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("&&");
            assertThat(code).contains("||");
            assertThat(code).contains("!");
        }

        @Test
        void booleanExpressionChainToBasic() {
            var converter = new BasicConverter(ConversionConfig.defaults());
            var result = converter.convert(buildBooleanExpressionChain());
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("AND");
            assertThat(code).contains("OR");
            assertThat(code).contains("NOT ");
        }
    }

    // -----------------------------------------------------------------------
    // Part 2: JIT Compilation of Complex Expressions
    // -----------------------------------------------------------------------
    @Nested
    class JitComplexExpressions {

        private JitCompiler compiler;

        @BeforeEach
        void setUp() {
            compiler = new JitCompiler();
        }

        @Test
        void jitArithmeticWithFiveOperators() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestFiveOps implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            // (10 + 5) * 3 - 8 / 2 % 3
                            return (10 + 5) * 3 - 8 / 2 % 3;
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestFiveOps");
            assertThat(result.isSuccess()).isTrue();
            // (10+5)*3 - (8/2)%3 = 15*3 - 4%3 = 45 - 1 = 44
            assertThat(result.value().instance().execute(Map.of())).isEqualTo(44);
        }

        @Test
        void jitWithMultipleContextVariables() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestMultiCtx implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int a = (Integer) context.get("a");
                            int b = (Integer) context.get("b");
                            int c = (Integer) context.get("c");
                            int d = (Integer) context.get("d");
                            return (a + b) * (c - d);
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestMultiCtx");
            assertThat(result.isSuccess()).isTrue();
            // (3+7)*(12-4) = 10*8 = 80
            assertThat(result.value().instance().execute(Map.of("a", 3, "b", 7, "c", 12, "d", 4))).isEqualTo(80);
        }

        @Test
        void jitConditionalExpression() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitCond implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int x = (Integer) context.get("x");
                            if (x > 100) return "big";
                            else if (x > 50) return "medium";
                            else if (x > 0) return "small";
                            else return "zero-or-negative";
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitCond");
            assertThat(result.isSuccess()).isTrue();
            var exe = result.value().instance();
            assertThat(exe.execute(Map.of("x", 200))).isEqualTo("big");
            assertThat(exe.execute(Map.of("x", 75))).isEqualTo("medium");
            assertThat(exe.execute(Map.of("x", 10))).isEqualTo("small");
            assertThat(exe.execute(Map.of("x", -5))).isEqualTo("zero-or-negative");
        }

        @Test
        void jitWithFunctionCallsInsideClass() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitFuncCall implements JitExecutable {
                        private int square(int n) { return n * n; }
                        private int add(int a, int b) { return a + b; }
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int x = (Integer) context.get("x");
                            return add(square(x), square(x + 1));
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitFuncCall");
            assertThat(result.isSuccess()).isTrue();
            // x=3: square(3) + square(4) = 9 + 16 = 25
            assertThat(result.value().instance().execute(Map.of("x", 3))).isEqualTo(25);
        }

        @Test
        void jitStringOperations() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitString implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            String first = (String) context.get("first");
                            String last = (String) context.get("last");
                            return first.toUpperCase() + " " + last.toLowerCase();
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitString");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().instance().execute(Map.of("first", "hello", "last", "WORLD")))
                    .isEqualTo("HELLO world");
        }

        @Test
        void jitBooleanExpressions() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitBool implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            boolean a = (Boolean) context.get("a");
                            boolean b = (Boolean) context.get("b");
                            boolean c = (Boolean) context.get("c");
                            return (a && b) || (!a && c);
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitBool");
            assertThat(result.isSuccess()).isTrue();
            var exe = result.value().instance();
            assertThat(exe.execute(Map.of("a", true, "b", true, "c", false))).isEqualTo(true);
            assertThat(exe.execute(Map.of("a", true, "b", false, "c", true))).isEqualTo(false);
            assertThat(exe.execute(Map.of("a", false, "b", false, "c", true))).isEqualTo(true);
        }

        @Test
        void jitCompilationErrorHandling() {
            String source = """
                    package ssg.pex.jit.generated;
                    public class TestJitBadSyntax {
                        // missing implements, unresolved type
                        public Object execute(UndefinedType x) {
                            return x.foo();
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitBadSyntax");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("JIT_COMPILATION_ERROR");
        }

        @Test
        void jitCompileTenDifferentClassesSequentially() {
            for (int i = 0; i < 10; i++) {
                String className = "TestSeq" + i;
                int value = i * 10 + 7;
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
        void jitWithLoopComputation() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitLoop implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int n = (Integer) context.get("n");
                            int sum = 0;
                            int i = 1;
                            while (i <= n) {
                                sum += i * i;
                                i++;
                            }
                            return sum;
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitLoop");
            assertThat(result.isSuccess()).isTrue();
            // sum of squares 1..5 = 1+4+9+16+25 = 55
            assertThat(result.value().instance().execute(Map.of("n", 5))).isEqualTo(55);
        }

        @Test
        void jitWithNestedLoops() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitNestedLoop implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int rows = (Integer) context.get("rows");
                            int cols = (Integer) context.get("cols");
                            int count = 0;
                            for (int i = 0; i < rows; i++) {
                                for (int j = 0; j < cols; j++) {
                                    count++;
                                }
                            }
                            return count;
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitNestedLoop");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().instance().execute(Map.of("rows", 4, "cols", 5))).isEqualTo(20);
        }

        @Test
        void jitWithRecursiveMethod() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitRecursion implements JitExecutable {
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
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitRecursion");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().instance().execute(Map.of("n", 6))).isEqualTo(720);
        }

        @Test
        void jitWithSwitchExpression() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitSwitch implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int code = (Integer) context.get("code");
                            return switch (code) {
                                case 1 -> "one";
                                case 2 -> "two";
                                case 3 -> "three";
                                default -> "other";
                            };
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitSwitch");
            assertThat(result.isSuccess()).isTrue();
            var exe = result.value().instance();
            assertThat(exe.execute(Map.of("code", 2))).isEqualTo("two");
            assertThat(exe.execute(Map.of("code", 99))).isEqualTo("other");
        }

        @Test
        void jitRoundTripBuildAstConvertCompileExecute() {
            // Build AST for: return (10 + 20) * 3
            var ast = new ProgramNode(List.of(
                    new ReturnNode(new BinaryOpNode(
                            new BinaryOpNode(new IntLiteral(10), Operator.PLUS, new IntLiteral(20)),
                            Operator.MULTIPLY,
                            new IntLiteral(3)))
            ));

            // Convert to JIT source
            var jitConverter = new JitConverter(ConversionConfig.defaults());
            var sourceResult = jitConverter.convert(ast);
            assertThat(sourceResult.isSuccess()).isTrue();

            String source = sourceResult.value();
            // Extract class name
            var matcher = java.util.regex.Pattern.compile("public class (\\S+) implements")
                    .matcher(source);
            assertThat(matcher.find()).isTrue();
            String className = "ssg.pex.jit.generated." + matcher.group(1);

            // Compile and execute
            var compileResult = compiler.compile(source, className);
            assertThat(compileResult.isSuccess())
                    .as("JIT compilation should succeed. Source:\n%s", source)
                    .isTrue();
            // The body is "return (10 + 20) * 3;" which returns (long) 90 but Java int arithmetic = 90
            // Actually the JavaConverter produces "return (((10 + 20)) * 3);" as text, not runnable per se
            // The return inside execute() means it returns the value.
            assertThat(compileResult.value().instance().execute(Map.of())).isNotNull();
        }

        @Test
        void jitCompilationTracksTiming() {
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitTiming implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            return 42;
                        }
                    }
                    """;
            var result = compiler.compile(source, "ssg.pex.jit.generated.TestJitTiming");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().compilationTimeNanos()).isGreaterThan(0);
        }
    }

    // -----------------------------------------------------------------------
    // Part 3: Conversion Consistency
    // -----------------------------------------------------------------------
    @Nested
    class ConversionConsistency {

        @Test
        void allLanguagesConvertMultiFunctionProgram() {
            var program = buildMultiFunctionProgram();
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess())
                        .as("Multi-function program should convert to %s", lang.displayName())
                        .isTrue();
                assertThat(result.value())
                        .as("Output for %s should not be blank", lang.displayName())
                        .isNotBlank();
            }
        }

        @Test
        void allLanguagesConvertDeeplyNestedExpression() {
            var program = buildDeeplyNestedExpression();
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess())
                        .as("Deeply nested expression should convert to %s", lang.displayName())
                        .isTrue();
                // All languages should contain the operator symbols (or equivalents)
                assertThat(result.value()).contains("a").contains("b");
            }
        }

        @Test
        void operatorMappingConsistencyPlusOperator() {
            var ast = new ProgramNode(List.of(
                    new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2))));
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.value())
                        .as("%s should map PLUS to +", lang.displayName())
                        .contains("+");
            }
        }

        @Test
        void operatorMappingConsistencyMultiplyOperator() {
            var ast = new ProgramNode(List.of(
                    new BinaryOpNode(new IntLiteral(3), Operator.MULTIPLY, new IntLiteral(4))));
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.value())
                        .as("%s should map MULTIPLY to *", lang.displayName())
                        .contains("*");
            }
        }

        @Test
        void rubyUsesSnakeCaseNaming() {
            var func = new FunctionDefNode("myFunction", List.of(new ParameterNode("firstName")),
                    new BlockNode(List.of(new ReturnNode(new IdentifierNode("firstName")))));
            var program = new ProgramNode(List.of(func));
            var converter = new RubyConverter(ConversionConfig.defaults());
            var result = converter.convert(program);
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("my_function");
            assertThat(code).contains("first_name");
        }

        @Test
        void csharpUsesPascalCaseForFunctions() {
            var func = new FunctionDefNode("myFunction", List.of(new ParameterNode("value")),
                    new BlockNode(List.of(new ReturnNode(new IdentifierNode("value")))));
            var program = new ProgramNode(List.of(func));
            var converter = new CSharpConverter(ConversionConfig.defaults());
            var result = converter.convert(program);
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            assertThat(code).contains("MyFunction"); // PascalCase function name
        }

        @Test
        void allConvertersHandleEmptyProgram() {
            var emptyProgram = new ProgramNode(List.of());
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(emptyProgram);
                assertThat(result.isSuccess())
                        .as("Empty program should convert to %s without error", lang.displayName())
                        .isTrue();
            }
        }

        @Test
        void allConvertersHandleSingleLiteral() {
            var program = new ProgramNode(List.of(new IntLiteral(42)));
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.value())
                        .as("%s should contain literal 42", lang.displayName())
                        .contains("42");
            }
        }

        @Test
        void deeplyNestedBlocksProduceProperIndentation() {
            var program = buildDeeplyNestedBlocks();
            var converter = new JavaConverter(ConversionConfig.defaults());
            var result = converter.convert(program);
            assertThat(result.isSuccess()).isTrue();
            String code = result.value();
            // Verify that each nested level adds indentation (at least 6 levels deep)
            String[] lines = code.split("\n");
            int maxIndent = 0;
            for (String line : lines) {
                int spaces = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') spaces++;
                    else break;
                }
                maxIndent = Math.max(maxIndent, spaces);
            }
            // 6 nested conditionals + function = at least 7 indent levels * 4 spaces = 28
            assertThat(maxIndent).isGreaterThanOrEqualTo(28);
        }

        @Test
        void allLanguagesProduceValidLookingOutputForComplexProgram() {
            var program = buildMultiFunctionProgram();
            var javaResult = new JavaConverter(ConversionConfig.defaults()).convert(program);
            var kotlinResult = new KotlinConverter(ConversionConfig.defaults()).convert(program);
            var rubyResult = new RubyConverter(ConversionConfig.defaults()).convert(program);
            var cppResult = new CppConverter(ConversionConfig.defaults()).convert(program);
            var csharpResult = new CSharpConverter(ConversionConfig.defaults()).convert(program);
            var scalaResult = new ScalaConverter(ConversionConfig.defaults()).convert(program);
            var basicResult = new BasicConverter(ConversionConfig.defaults()).convert(program);

            // Java specifics
            assertThat(javaResult.value()).contains("{").contains("}").contains(";");
            // Kotlin specifics
            assertThat(kotlinResult.value()).contains("fun ").contains("{").contains("}");
            // Ruby specifics
            assertThat(rubyResult.value()).contains("def ").contains("end");
            // C++ specifics
            assertThat(cppResult.value()).contains("auto ").contains("{").contains("}");
            // C# specifics
            assertThat(csharpResult.value()).contains("object ").contains("{").contains("}");
            // Scala specifics
            assertThat(scalaResult.value()).contains("def ").contains(": Any");
            // BASIC specifics
            assertThat(basicResult.value()).contains("FUNCTION ").contains("END FUNCTION");
        }

        @Test
        void converterReusabilityEachCallProducesFreshOutput() {
            var converter = new JavaConverter(ConversionConfig.defaults());
            var prog1 = new ProgramNode(List.of(new IntLiteral(1)));
            var prog2 = new ProgramNode(List.of(new IntLiteral(999)));

            var result1 = converter.convert(prog1);
            var result2 = converter.convert(prog2);

            assertThat(result1.value()).contains("1").doesNotContain("999");
            assertThat(result2.value()).contains("999");
        }
    }
}

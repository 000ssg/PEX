package ssg.pex.converter.jit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JitCompilerTest {

    private JitCompiler compiler;
    private JitConverter jitConverter;

    @BeforeEach
    void setUp() {
        compiler = new JitCompiler();
        jitConverter = new JitConverter(ConversionConfig.defaults());
    }

    @Test
    void compilesSimpleClass() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestSimple implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return 42;
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestSimple");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance()).isNotNull();
        assertThat(result.value().instance().execute(Map.of())).isEqualTo(42);
    }

    @Test
    void compiledClassIsLoadable() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestLoadable implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return "hello";
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestLoadable");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().compiledClass()).isNotNull();
        assertThat(result.value().compiledClass().getName()).isEqualTo("ssg.pex.jit.generated.TestLoadable");
    }

    @Test
    void trackCompilationTime() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestTime implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return 1;
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestTime");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().compilationTimeNanos()).isGreaterThan(0);
    }

    @Test
    void handlesCompilationErrors() {
        String source = """
                package ssg.pex.jit.generated;
                public class TestBad implements {
                    // Missing interface, syntax error
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestBad");
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("JIT_COMPILATION_ERROR");
    }

    @Test
    void compilesWithContextVariables() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestContext implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return context.get("x");
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestContext");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance().execute(Map.of("x", 100))).isEqualTo(100);
    }

    @Test
    void compilesExpressionEvaluation() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestExpr implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        int a = (Integer) context.get("a");
                        int b = (Integer) context.get("b");
                        return a + b;
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestExpr");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance().execute(Map.of("a", 3, "b", 4))).isEqualTo(7);
    }

    @Test
    void compilesWithBooleanLogic() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestBool implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return (Boolean) context.get("flag") ? "yes" : "no";
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestBool");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance().execute(Map.of("flag", true))).isEqualTo("yes");
        assertThat(result.value().instance().execute(Map.of("flag", false))).isEqualTo("no");
    }

    @Test
    void compilesWithStringConcatenation() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestStringConcat implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return (String) context.get("greeting") + " World";
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestStringConcat");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance().execute(Map.of("greeting", "Hello"))).isEqualTo("Hello World");
    }

    @Test
    void compilesReturningNull() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestNull implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return null;
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestNull");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance().execute(Map.of())).isNull();
    }

    @Test
    void compilesWithConditionalLogic() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestCond implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        int x = (Integer) context.get("x");
                        if (x > 0) {
                            return "positive";
                        } else {
                            return "non-positive";
                        }
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestCond");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance().execute(Map.of("x", 5))).isEqualTo("positive");
        assertThat(result.value().instance().execute(Map.of("x", -1))).isEqualTo("non-positive");
    }

    @Test
    void compilesWithLoop() {
        String source = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestLoop implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        int n = (Integer) context.get("n");
                        int sum = 0;
                        for (int i = 1; i <= n; i++) {
                            sum += i;
                        }
                        return sum;
                    }
                }
                """;
        var result = compiler.compile(source, "ssg.pex.jit.generated.TestLoop");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().instance().execute(Map.of("n", 10))).isEqualTo(55);
    }

    @Test
    void multipleCompilationsProduceIndependentClasses() {
        String source1 = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestIndep1 implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return 1;
                    }
                }
                """;
        String source2 = """
                package ssg.pex.jit.generated;
                import ssg.pex.converter.jit.JitExecutable;
                import java.util.Map;
                public class TestIndep2 implements JitExecutable {
                    @Override
                    public Object execute(Map<String, Object> context) {
                        return 2;
                    }
                }
                """;
        var result1 = compiler.compile(source1, "ssg.pex.jit.generated.TestIndep1");
        var result2 = compiler.compile(source2, "ssg.pex.jit.generated.TestIndep2");
        assertThat(result1.isSuccess()).isTrue();
        assertThat(result2.isSuccess()).isTrue();
        assertThat(result1.value().instance().execute(Map.of())).isEqualTo(1);
        assertThat(result2.value().instance().execute(Map.of())).isEqualTo(2);
    }

    @Test
    void jitConverterGeneratesValidSource() {
        var ast = new ProgramNode(List.of(new IntLiteral(42)));
        var result = jitConverter.convert(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("implements JitExecutable");
        assertThat(result.value()).contains("public Object execute(Map<String, Object> context)");
    }

    @Test
    void jitConverterIncludesPackageDeclaration() {
        var ast = new ProgramNode(List.of(new IntLiteral(42)));
        var result = jitConverter.convert(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("package ssg.pex.jit.generated;");
    }

    @Test
    void jitConverterIncludesImports() {
        var ast = new ProgramNode(List.of(new IntLiteral(42)));
        var result = jitConverter.convert(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("import ssg.pex.converter.jit.JitExecutable;");
        assertThat(result.value()).contains("import java.util.Map;");
    }

    @Test
    void jitConverterHandlesBinaryExpressions() {
        var ast = new ProgramNode(List.of(
                new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2))
        ));
        var result = jitConverter.convert(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("(1 + 2)");
    }

    @Test
    void jitConverterClassName() {
        var ast = new ProgramNode(List.of(new IntLiteral(42)));
        var result = jitConverter.convert(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("public class JitExpr_");
    }

    @Test
    void classLoaderLoadsCustomBytes() {
        var loader = new JitClassLoader(getClass().getClassLoader());
        // Just verify it doesn't throw for basic operations
        assertThat(loader).isNotNull();
    }

    @Test
    void classLoaderThrowsForMissingClass() {
        var loader = new JitClassLoader(getClass().getClassLoader());
        try {
            loader.findClass("com.nonexistent.Class");
            assertThat(false).isTrue(); // should not reach
        } catch (ClassNotFoundException e) {
            assertThat(e.getMessage()).contains("com.nonexistent.Class");
        }
    }
}

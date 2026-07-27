package ssg.pex.converter.lang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaConverterTest {

    private JavaConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JavaConverter(ConversionConfig.defaults());
    }

    @Test
    void targetLanguageIsJava() {
        assertThat(converter.targetLanguage()).isEqualTo(TargetLanguage.JAVA);
    }

    @Test
    void convertsIntLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new IntLiteral(42))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("42");
    }

    @Test
    void convertsFloatLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new FloatLiteral(3.14))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("3.14");
    }

    @Test
    void convertsStringLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new StringLiteral("hello"))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("\"hello\"");
    }

    @Test
    void convertsBoolLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new BoolLiteral(true))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("true");
    }

    @Test
    void convertsNullLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new NullLiteral())));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("null");
    }

    @Test
    void convertsBinaryOp() {
        var node = new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2));
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("(1 + 2)");
    }

    @Test
    void convertsNestedBinaryOp() {
        var inner = new BinaryOpNode(new IntLiteral(2), Operator.MULTIPLY, new IntLiteral(3));
        var node = new BinaryOpNode(new IntLiteral(1), Operator.PLUS, inner);
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("(1 + (2 * 3))");
    }

    @Test
    void convertsUnaryOp() {
        var node = new UnaryOpNode(Operator.NOT, new BoolLiteral(true), true);
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("!true");
    }

    @Test
    void convertsFunctionDef() {
        var body = new BlockNode(List.of(new ReturnNode(new IntLiteral(42))));
        var func = new FunctionDefNode("add", List.of(
                new ParameterNode("a"),
                new ParameterNode("b")
        ), body);
        var result = converter.convert(new ProgramNode(List.of(func)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("Object add(Object a, Object b)");
        assertThat(result.value()).contains("return 42;");
    }

    @Test
    void convertsFunctionCall() {
        var call = new FunctionCallNode("add", List.of(new IntLiteral(1), new IntLiteral(2)));
        var result = converter.convert(new ProgramNode(List.of(call)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("add(1, 2)");
    }

    @Test
    void convertsAssignment() {
        var assign = new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10));
        var result = converter.convert(new ProgramNode(List.of(assign)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("var x = 10;");
    }

    @Test
    void convertsConditional() {
        var cond = new ConditionalNode(
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))),
                new BlockNode(List.of(new IntLiteral(2)))
        );
        var result = converter.convert(new ProgramNode(List.of(cond)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("if (true)");
        assertThat(result.value()).contains("else");
    }

    @Test
    void convertsConditionalWithoutElse() {
        var cond = new ConditionalNode(
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1)))
        );
        var result = converter.convert(new ProgramNode(List.of(cond)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("if (true)");
        assertThat(result.value()).doesNotContain("else");
    }

    @Test
    void convertsWhileLoop() {
        var loop = new LoopNode(LoopKind.WHILE,
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))));
        var result = converter.convert(new ProgramNode(List.of(loop)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("while (true)");
    }

    @Test
    void convertsDoWhileLoop() {
        var loop = new LoopNode(LoopKind.DO_WHILE,
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))));
        var result = converter.convert(new ProgramNode(List.of(loop)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("do {");
        assertThat(result.value()).contains("} while (true);");
    }

    @Test
    void convertsReturn() {
        var ret = new ReturnNode(new IntLiteral(42));
        var result = converter.convert(new ProgramNode(List.of(ret)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("return 42;");
    }

    @Test
    void convertsReturnVoid() {
        var ret = new ReturnNode();
        var result = converter.convert(new ProgramNode(List.of(ret)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("return;");
    }

    @Test
    void convertsIndexAccess() {
        var access = new IndexAccessNode(new IdentifierNode("arr"), new IntLiteral(0));
        var result = converter.convert(new ProgramNode(List.of(access)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("arr[0]");
    }

    @Test
    void convertsBlock() {
        var block = new BlockNode(List.of(
                new IntLiteral(1),
                new IntLiteral(2)
        ));
        var result = converter.convert(new ProgramNode(List.of(block)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("{");
        assertThat(result.value()).contains("}");
    }

    @Test
    void convertsIdentifier() {
        var result = converter.convert(new ProgramNode(List.of(new IdentifierNode("myVar"))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("myVar");
    }
}

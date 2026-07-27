package ssg.pex.converter.lang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScalaConverterTest {

    private ScalaConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ScalaConverter(ConversionConfig.defaults());
    }

    @Test
    void targetLanguageIsScala() {
        assertThat(converter.targetLanguage()).isEqualTo(TargetLanguage.SCALA);
    }

    @Test
    void convertsIntLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new IntLiteral(42))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("42");
    }

    @Test
    void convertsStringLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new StringLiteral("hello"))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("\"hello\"");
    }

    @Test
    void convertsFunctionDefWithDef() {
        var body = new BlockNode(List.of(new ReturnNode(new IntLiteral(1))));
        var func = new FunctionDefNode("myFunc", List.of(
                new ParameterNode("a"),
                new ParameterNode("b")
        ), body);
        var result = converter.convert(new ProgramNode(List.of(func)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("def myFunc(a: Any, b: Any): Any =");
    }

    @Test
    void convertsAssignmentWithVar() {
        var assign = new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10));
        var result = converter.convert(new ProgramNode(List.of(assign)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("var x = 10");
    }

    @Test
    void noSemicolonsOnStatements() {
        var assign = new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10));
        var result = converter.convert(new ProgramNode(List.of(assign)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).doesNotContain(";");
    }

    @Test
    void convertsBinaryOp() {
        var node = new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2));
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("(1 + 2)");
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
    void convertsWhileLoop() {
        var loop = new LoopNode(LoopKind.WHILE,
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))));
        var result = converter.convert(new ProgramNode(List.of(loop)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("while (true)");
    }

    @Test
    void convertsReturn() {
        var ret = new ReturnNode(new IntLiteral(42));
        var result = converter.convert(new ProgramNode(List.of(ret)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("return 42");
    }

    @Test
    void convertsFunctionCall() {
        var call = new FunctionCallNode("add", List.of(new IntLiteral(1), new IntLiteral(2)));
        var result = converter.convert(new ProgramNode(List.of(call)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("add(1, 2)");
    }

    @Test
    void convertsNullLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new NullLiteral())));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("null");
    }

    @Test
    void convertsBoolLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new BoolLiteral(true))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("true");
    }

    @Test
    void convertsFloatLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new FloatLiteral(3.14))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("3.14");
    }

    @Test
    void convertsIndexAccess() {
        var access = new IndexAccessNode(new IdentifierNode("arr"), new IntLiteral(0));
        var result = converter.convert(new ProgramNode(List.of(access)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("arr[0]");
    }

    @Test
    void convertsUnaryOp() {
        var node = new UnaryOpNode(Operator.MINUS, new IntLiteral(5), true);
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("-5");
    }
}

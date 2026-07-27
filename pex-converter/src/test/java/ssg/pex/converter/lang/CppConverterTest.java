package ssg.pex.converter.lang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CppConverterTest {

    private CppConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CppConverter(ConversionConfig.defaults());
    }

    @Test
    void targetLanguageIsCpp() {
        assertThat(converter.targetLanguage()).isEqualTo(TargetLanguage.CPP);
    }

    @Test
    void convertsIntLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new IntLiteral(42))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("42");
    }

    @Test
    void convertsStringLiteralAsStdString() {
        var result = converter.convert(new ProgramNode(List.of(new StringLiteral("hello"))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("std::string(\"hello\")");
    }

    @Test
    void convertsNullAsNullptr() {
        var result = converter.convert(new ProgramNode(List.of(new NullLiteral())));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("nullptr");
    }

    @Test
    void convertsFunctionDefWithAuto() {
        var body = new BlockNode(List.of(new ReturnNode(new IntLiteral(1))));
        var func = new FunctionDefNode("compute", List.of(
                new ParameterNode("x"),
                new ParameterNode("y")
        ), body);
        var result = converter.convert(new ProgramNode(List.of(func)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("auto compute(auto x, auto y) -> auto");
    }

    @Test
    void convertsAssignmentWithAuto() {
        var assign = new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10));
        var result = converter.convert(new ProgramNode(List.of(assign)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("auto x = 10;");
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
                new BlockNode(List.of(new IntLiteral(1)))
        );
        var result = converter.convert(new ProgramNode(List.of(cond)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("if (true)");
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
        assertThat(result.value()).contains("return 42;");
    }

    @Test
    void convertsFunctionCall() {
        var call = new FunctionCallNode("compute", List.of(new IntLiteral(1), new IntLiteral(2)));
        var result = converter.convert(new ProgramNode(List.of(call)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("compute(1, 2)");
    }

    @Test
    void convertsBoolLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new BoolLiteral(false))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("false");
    }

    @Test
    void convertsFloatLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new FloatLiteral(1.5))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("1.5");
    }

    @Test
    void convertsIndexAccess() {
        var access = new IndexAccessNode(new IdentifierNode("arr"), new IntLiteral(3));
        var result = converter.convert(new ProgramNode(List.of(access)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("arr[3]");
    }

    @Test
    void convertsShiftOperator() {
        var node = new BinaryOpNode(new IntLiteral(1), Operator.SHIFT_LEFT, new IntLiteral(2));
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("(1 << 2)");
    }
}

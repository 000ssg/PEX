package ssg.pex.converter.lang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RubyConverterTest {

    private RubyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new RubyConverter(ConversionConfig.defaults());
    }

    @Test
    void targetLanguageIsRuby() {
        assertThat(converter.targetLanguage()).isEqualTo(TargetLanguage.RUBY);
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
    void convertsFunctionDefWithSnakeCase() {
        var body = new BlockNode(List.of(new ReturnNode(new IntLiteral(1))));
        var func = new FunctionDefNode("myFunc", List.of(
                new ParameterNode("paramOne")
        ), body);
        var result = converter.convert(new ProgramNode(List.of(func)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("def my_func(param_one)");
        assertThat(result.value()).contains("end");
    }

    @Test
    void convertsAssignmentWithoutVarKeyword() {
        var assign = new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10));
        var result = converter.convert(new ProgramNode(List.of(assign)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("x = 10");
        assertThat(result.value()).doesNotContain("var ");
    }

    @Test
    void noSemicolonsOnStatements() {
        var assign = new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10));
        var result = converter.convert(new ProgramNode(List.of(assign)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).doesNotContain(";");
    }

    @Test
    void convertsNullAsNil() {
        var result = converter.convert(new ProgramNode(List.of(new NullLiteral())));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("nil");
    }

    @Test
    void convertsBinaryOp() {
        var node = new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2));
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("(1 + 2)");
    }

    @Test
    void convertsConditionalWithEnd() {
        var cond = new ConditionalNode(
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))),
                new BlockNode(List.of(new IntLiteral(2)))
        );
        var result = converter.convert(new ProgramNode(List.of(cond)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("if true");
        assertThat(result.value()).contains("else");
        assertThat(result.value()).contains("end");
    }

    @Test
    void convertsWhileLoopWithEnd() {
        var loop = new LoopNode(LoopKind.WHILE,
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))));
        var result = converter.convert(new ProgramNode(List.of(loop)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("while true");
        assertThat(result.value()).contains("end");
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
        var call = new FunctionCallNode("doWork", List.of(new IntLiteral(1)));
        var result = converter.convert(new ProgramNode(List.of(call)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("do_work(1)");
    }

    @Test
    void convertsBoolLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new BoolLiteral(true))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("true");
    }

    @Test
    void convertsLogicalOperators() {
        var node = new BinaryOpNode(new BoolLiteral(true), Operator.AND, new BoolLiteral(false));
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("&&");
    }

    @Test
    void convertsIndexAccess() {
        var access = new IndexAccessNode(new IdentifierNode("arr"), new IntLiteral(0));
        var result = converter.convert(new ProgramNode(List.of(access)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("arr[0]");
    }
}

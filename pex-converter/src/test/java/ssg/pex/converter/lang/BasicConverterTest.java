package ssg.pex.converter.lang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BasicConverterTest {

    private BasicConverter converter;

    @BeforeEach
    void setUp() {
        converter = new BasicConverter(ConversionConfig.defaults());
    }

    @Test
    void targetLanguageIsBasic() {
        assertThat(converter.targetLanguage()).isEqualTo(TargetLanguage.BASIC);
    }

    @Test
    void convertsIntLiteral() {
        var result = converter.convert(new ProgramNode(List.of(new IntLiteral(42))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("42");
    }

    @Test
    void convertsAssignmentWithLet() {
        var assign = new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10));
        var result = converter.convert(new ProgramNode(List.of(assign)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("LET x = 10");
    }

    @Test
    void convertsFunctionDefWithUppercaseKeywords() {
        var body = new BlockNode(List.of(new ReturnNode(new IntLiteral(1))));
        var func = new FunctionDefNode("add", List.of(
                new ParameterNode("a"),
                new ParameterNode("b")
        ), body);
        var result = converter.convert(new ProgramNode(List.of(func)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("FUNCTION ADD(a, b)");
        assertThat(result.value()).contains("END FUNCTION");
    }

    @Test
    void convertsConditionalWithIfThenEndIf() {
        var cond = new ConditionalNode(
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))),
                new BlockNode(List.of(new IntLiteral(2)))
        );
        var result = converter.convert(new ProgramNode(List.of(cond)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("IF");
        assertThat(result.value()).contains("THEN");
        assertThat(result.value()).contains("ELSE");
        assertThat(result.value()).contains("END IF");
    }

    @Test
    void convertsWhileLoopWithWend() {
        var loop = new LoopNode(LoopKind.WHILE,
                new BoolLiteral(true),
                new BlockNode(List.of(new IntLiteral(1))));
        var result = converter.convert(new ProgramNode(List.of(loop)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("WHILE");
        assertThat(result.value()).contains("WEND");
    }

    @Test
    void convertsNullAsNothing() {
        var result = converter.convert(new ProgramNode(List.of(new NullLiteral())));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("NOTHING");
    }

    @Test
    void convertsBoolAsUppercase() {
        var result = converter.convert(new ProgramNode(List.of(new BoolLiteral(true))));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("TRUE");
    }

    @Test
    void convertsReturnUppercase() {
        var ret = new ReturnNode(new IntLiteral(42));
        var result = converter.convert(new ProgramNode(List.of(ret)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("RETURN 42");
    }

    @Test
    void convertsLogicalOperatorsUppercase() {
        var node = new BinaryOpNode(new BoolLiteral(true), Operator.AND, new BoolLiteral(false));
        var result = converter.convert(new ProgramNode(List.of(node)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("AND");
    }
}

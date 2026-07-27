package ssg.pex.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.BoolLiteral;
import ssg.pex.ast.node.FloatLiteral;
import ssg.pex.ast.node.FunctionCallNode;
import ssg.pex.ast.node.IntLiteral;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.UnaryOpNode;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.exec.ExecutionEngine;
import ssg.pex.exec.handler.BaseHandlerProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndArithmeticsTest {

    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .loadPlugins()
                .build();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void simpleAddition() {
        // 2 + 3 = 5
        var ast = new BinaryOpNode(new IntLiteral(2), Operator.PLUS, new IntLiteral(3));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.value()).longValue()).isEqualTo(5L);
    }

    @Test
    void operatorPrecedenceMultiplicationBeforeAddition() {
        // 2 + 3 * 4 = 14  (build AST reflecting precedence)
        var mul = new BinaryOpNode(new IntLiteral(3), Operator.MULTIPLY, new IntLiteral(4));
        var add = new BinaryOpNode(new IntLiteral(2), Operator.PLUS, mul);
        var result = engine.execute(add);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.value()).longValue()).isEqualTo(14L);
    }

    @Test
    void parenthesesOverridePrecedence() {
        // (2 + 3) * 4 = 20
        var add = new BinaryOpNode(new IntLiteral(2), Operator.PLUS, new IntLiteral(3));
        var mul = new BinaryOpNode(add, Operator.MULTIPLY, new IntLiteral(4));
        var result = engine.execute(mul);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.value()).longValue()).isEqualTo(20L);
    }

    @Test
    void sqrtFunction() {
        // sqrt(16) = 4.0
        var ast = new FunctionCallNode("sqrt", List.of(new IntLiteral(16)));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.value()).isEqualTo(4.0);
    }

    @Test
    void nestedMathFunctions() {
        // abs(-42) = 42
        var ast = new FunctionCallNode("abs", List.of(
                new UnaryOpNode(Operator.MINUS, new IntLiteral(42), true)));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.value()).longValue()).isEqualTo(42L);
    }

    @Test
    void booleanLogicAndFalse() {
        // true && false = false
        var ast = new BinaryOpNode(new BoolLiteral(true), Operator.AND, new BoolLiteral(false));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(false);
    }

    @Test
    void booleanLogicOrTrue() {
        // false || true = true
        var ast = new BinaryOpNode(new BoolLiteral(false), Operator.OR, new BoolLiteral(true));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(true);
    }

    @Test
    void bitwiseAndOperation() {
        // 0b1100 & 0b1010 = 0b1000 = 8
        var ast = new BinaryOpNode(new IntLiteral(12), Operator.BIT_AND, new IntLiteral(10));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.value()).intValue()).isEqualTo(8);
    }

    @Test
    void comparisonLessThan() {
        // 3 < 5 = true
        var ast = new BinaryOpNode(new IntLiteral(3), Operator.LT, new IntLiteral(5));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(true);
    }

    @Test
    void floatingPointArithmetic() {
        // 1.5 + 2.5 = 4.0
        var ast = new BinaryOpNode(new FloatLiteral(1.5), Operator.PLUS, new FloatLiteral(2.5));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.value()).isEqualTo(4.0);
    }

    @Test
    void mathFunctionPow() {
        // pow(2, 10) = 1024.0
        var ast = new FunctionCallNode("pow", List.of(new IntLiteral(2), new IntLiteral(10)));
        var result = engine.execute(ast);
        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.value()).isEqualTo(1024.0);
    }

    @Test
    void arithmeticsGrammarCanParseExpression() {
        // Verify the grammar from the plugin can parse a simple expression
        var grammar = engine.grammar();
        assertThat(grammar).isNotNull();

        var rde = new RecursiveDescentEngine();
        var parseResult = rde.parse("42", grammar);
        assertThat(parseResult.isSuccess()).isTrue();
    }
}

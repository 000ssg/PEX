package ssg.pex.ast.node;

import org.junit.jupiter.api.Test;
import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AstNodeTest {

    private static final SourceLocation LOC = SourceLocation.of("test.pex", 1, 0);

    // --- IntLiteral ---

    @Test
    void intLiteral_convenienceConstructor() {
        var node = new IntLiteral(42);
        assertThat(node.value()).isEqualTo(42L);
        assertThat(node.location()).isEqualTo(SourceLocation.UNKNOWN);
        assertThat(node.metadata()).isEmpty();
        assertThat(node.children()).isEmpty();
    }

    @Test
    void intLiteral_fullConstructor() {
        var meta = Map.<String, Object>of("key", "val");
        var node = new IntLiteral(99, LOC, meta);
        assertThat(node.value()).isEqualTo(99L);
        assertThat(node.location()).isEqualTo(LOC);
        assertThat(node.metadata()).containsEntry("key", "val");
    }

    @Test
    void intLiteral_literalValue() {
        assertThat(new IntLiteral(7).literalValue()).isEqualTo(7L);
    }

    // --- FloatLiteral ---

    @Test
    void floatLiteral_convenienceConstructor() {
        var node = new FloatLiteral(3.14);
        assertThat(node.value()).isEqualTo(3.14);
        assertThat(node.children()).isEmpty();
    }

    @Test
    void floatLiteral_literalValue() {
        assertThat(new FloatLiteral(2.5).literalValue()).isEqualTo(2.5);
    }

    // --- StringLiteral ---

    @Test
    void stringLiteral_convenienceConstructor() {
        var node = new StringLiteral("hello");
        assertThat(node.value()).isEqualTo("hello");
        assertThat(node.children()).isEmpty();
    }

    @Test
    void stringLiteral_literalValue() {
        assertThat(new StringLiteral("abc").literalValue()).isEqualTo("abc");
    }

    // --- BoolLiteral ---

    @Test
    void boolLiteral_convenienceConstructor() {
        assertThat(new BoolLiteral(true).value()).isTrue();
        assertThat(new BoolLiteral(false).literalValue()).isEqualTo(false);
        assertThat(new BoolLiteral(true).children()).isEmpty();
    }

    // --- NullLiteral ---

    @Test
    void nullLiteral_convenienceConstructor() {
        var node = new NullLiteral();
        assertThat(node.location()).isEqualTo(SourceLocation.UNKNOWN);
        assertThat(node.literalValue()).isNull();
        assertThat(node.children()).isEmpty();
    }

    // --- IdentifierNode ---

    @Test
    void identifierNode_convenienceConstructor() {
        var node = new IdentifierNode("x");
        assertThat(node.name()).isEqualTo("x");
        assertThat(node.children()).isEmpty();
        assertThat(node.metadata()).isEmpty();
    }

    // --- BinaryOpNode ---

    @Test
    void binaryOpNode_childrenReturnsLeftAndRight() {
        var left = new IntLiteral(1);
        var right = new IntLiteral(2);
        var node = new BinaryOpNode(left, Operator.PLUS, right);
        assertThat(node.children()).containsExactly(left, right);
        assertThat(node.op()).isEqualTo(Operator.PLUS);
    }

    // --- UnaryOpNode ---

    @Test
    void unaryOpNode_childrenContainsOperand() {
        var operand = new IntLiteral(5);
        var node = new UnaryOpNode(Operator.MINUS, operand, true);
        assertThat(node.children()).containsExactly(operand);
        assertThat(node.prefix()).isTrue();
    }

    // --- AssignmentNode ---

    @Test
    void assignmentNode_children() {
        var target = new IdentifierNode("x");
        var value = new IntLiteral(10);
        var node = new AssignmentNode(target, value);
        assertThat(node.children()).containsExactly(target, value);
    }

    // --- BlockNode ---

    @Test
    void blockNode_children() {
        var s1 = new IntLiteral(1);
        var s2 = new IntLiteral(2);
        var node = new BlockNode(List.of(s1, s2));
        assertThat(node.children()).containsExactly(s1, s2);
    }

    // --- ConditionalNode with else ---

    @Test
    void conditionalNode_withElse_children() {
        var cond = new BoolLiteral(true);
        var then = new IntLiteral(1);
        var els = new IntLiteral(2);
        var node = new ConditionalNode(cond, then, els);
        assertThat(node.children()).containsExactly(cond, then, els);
    }

    @Test
    void conditionalNode_withoutElse_children() {
        var cond = new BoolLiteral(true);
        var then = new IntLiteral(1);
        var node = new ConditionalNode(cond, then);
        assertThat(node.children()).containsExactly(cond, then);
        assertThat(node.elseBranch()).isNull();
    }

    // --- ReturnNode ---

    @Test
    void returnNode_withValue_children() {
        var val = new IntLiteral(42);
        var node = new ReturnNode(val);
        assertThat(node.children()).containsExactly(val);
    }

    @Test
    void returnNode_withoutValue_children() {
        var node = new ReturnNode();
        assertThat(node.children()).isEmpty();
        assertThat(node.value()).isNull();
    }

    // --- FunctionDefNode ---

    @Test
    void functionDefNode_childrenIncludesParamsAndBody() {
        var p1 = new ParameterNode("a", "int");
        var p2 = new ParameterNode("b");
        var body = new IntLiteral(0);
        var node = new FunctionDefNode("add", List.of(p1, p2), body);
        assertThat(node.children()).containsExactly(p1, p2, body);
        assertThat(node.name()).isEqualTo("add");
    }

    // --- FunctionCallNode ---

    @Test
    void functionCallNode_childrenAreArguments() {
        var a1 = new IntLiteral(1);
        var a2 = new IntLiteral(2);
        var node = new FunctionCallNode("add", List.of(a1, a2));
        assertThat(node.children()).containsExactly(a1, a2);
        assertThat(node.name()).isEqualTo("add");
    }

    // --- IndexAccessNode ---

    @Test
    void indexAccessNode_children() {
        var target = new IdentifierNode("arr");
        var index = new IntLiteral(0);
        var node = new IndexAccessNode(target, index);
        assertThat(node.children()).containsExactly(target, index);
    }

    // --- LoopNode ---

    @Test
    void loopNode_children() {
        var cond = new BoolLiteral(true);
        var body = new IntLiteral(0);
        var node = new LoopNode(LoopKind.WHILE, cond, body);
        assertThat(node.children()).containsExactly(cond, body);
        assertThat(node.kind()).isEqualTo(LoopKind.WHILE);
    }

    // --- ProgramNode ---

    @Test
    void programNode_childrenAreStatements() {
        var s1 = new IntLiteral(1);
        var s2 = new StringLiteral("two");
        var node = new ProgramNode(List.of(s1, s2));
        assertThat(node.children()).containsExactly(s1, s2);
    }

    // --- ExtensionNode ---

    @Test
    void extensionNode_childrenEmpty() {
        var node = new ExtensionNode("custom", "payload");
        assertThat(node.children()).isEmpty();
        assertThat(node.extensionType()).isEqualTo("custom");
        assertThat(node.wrappedNode()).isEqualTo("payload");
    }

    // --- Metadata immutability from convenience constructor ---

    @Test
    void convenienceConstructor_metadataIsImmutable() {
        var node = new IntLiteral(1);
        assertThatThrownBy(() -> node.metadata().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Operator enum ---

    @Test
    void operator_fromSymbol_found() {
        assertThat(Operator.fromSymbol("+")).contains(Operator.PLUS);
        assertThat(Operator.fromSymbol("==")).contains(Operator.EQ);
        assertThat(Operator.fromSymbol(">>>")).contains(Operator.UNSIGNED_SHIFT_RIGHT);
    }

    @Test
    void operator_fromSymbol_notFound() {
        assertThat(Operator.fromSymbol("??")).isEmpty();
    }

    @Test
    void operator_precedence_multiplyHigherThanPlus() {
        assertThat(Operator.MULTIPLY.precedence()).isGreaterThan(Operator.PLUS.precedence());
    }

    @Test
    void operator_precedence_comparisonHigherThanLogical() {
        assertThat(Operator.LT.precedence()).isGreaterThan(Operator.AND.precedence());
        assertThat(Operator.AND.precedence()).isGreaterThan(Operator.OR.precedence());
    }

    @Test
    void operator_isUnary() {
        assertThat(Operator.NOT.isUnary()).isTrue();
        assertThat(Operator.BIT_NOT.isUnary()).isTrue();
        assertThat(Operator.MINUS.isUnary()).isTrue();
        assertThat(Operator.PLUS.isUnary()).isTrue();
        assertThat(Operator.MULTIPLY.isUnary()).isFalse();
    }

    @Test
    void operator_isBinary() {
        assertThat(Operator.PLUS.isBinary()).isTrue();
        assertThat(Operator.AND.isBinary()).isTrue();
        assertThat(Operator.NOT.isBinary()).isFalse();
        assertThat(Operator.BIT_NOT.isBinary()).isFalse();
    }

    @Test
    void operator_isArithmetic() {
        assertThat(Operator.PLUS.isArithmetic()).isTrue();
        assertThat(Operator.MODULO.isArithmetic()).isTrue();
        assertThat(Operator.EQ.isArithmetic()).isFalse();
    }

    @Test
    void operator_isComparison() {
        assertThat(Operator.EQ.isComparison()).isTrue();
        assertThat(Operator.GE.isComparison()).isTrue();
        assertThat(Operator.AND.isComparison()).isFalse();
    }

    @Test
    void operator_isLogical() {
        assertThat(Operator.AND.isLogical()).isTrue();
        assertThat(Operator.OR.isLogical()).isTrue();
        assertThat(Operator.NOT.isLogical()).isTrue();
        assertThat(Operator.BIT_AND.isLogical()).isFalse();
    }

    @Test
    void operator_isBitwise() {
        assertThat(Operator.BIT_AND.isBitwise()).isTrue();
        assertThat(Operator.SHIFT_LEFT.isBitwise()).isTrue();
        assertThat(Operator.UNSIGNED_SHIFT_RIGHT.isBitwise()).isTrue();
        assertThat(Operator.AND.isBitwise()).isFalse();
    }

    @Test
    void operator_toString_returnsSymbol() {
        assertThat(Operator.PLUS.toString()).isEqualTo("+");
        assertThat(Operator.ASSIGN.toString()).isEqualTo("=");
    }
}

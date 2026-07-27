package ssg.pex.ast.visitor;

import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.result.Result;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AstVisitorTest {

    // --- AstTransformer default behavior (identity) ---

    @Test
    void defaultTransformer_returnsIntLiteralUnchanged() {
        var transformer = new IdentityTransformer();
        var node = new IntLiteral(42);
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(node);
    }

    @Test
    void defaultTransformer_returnsStringLiteralUnchanged() {
        var transformer = new IdentityTransformer();
        var node = new StringLiteral("hello");
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(node);
    }

    @Test
    void defaultTransformer_returnsBoolLiteralUnchanged() {
        var transformer = new IdentityTransformer();
        var node = new BoolLiteral(true);
        assertThat(transformer.transform(node).value()).isEqualTo(node);
    }

    @Test
    void defaultTransformer_returnsNullLiteralUnchanged() {
        var transformer = new IdentityTransformer();
        var node = new NullLiteral();
        assertThat(transformer.transform(node).value()).isEqualTo(node);
    }

    @Test
    void defaultTransformer_returnsIdentifierUnchanged() {
        var transformer = new IdentityTransformer();
        var node = new IdentifierNode("x");
        assertThat(transformer.transform(node).value()).isEqualTo(node);
    }

    @Test
    void defaultTransformer_returnsParameterUnchanged() {
        var transformer = new IdentityTransformer();
        var node = new ParameterNode("x", "int");
        assertThat(transformer.transform(node).value()).isEqualTo(node);
    }

    @Test
    void defaultTransformer_returnsExtensionUnchanged() {
        var transformer = new IdentityTransformer();
        var node = new ExtensionNode("custom", "data");
        assertThat(transformer.transform(node).value()).isEqualTo(node);
    }

    // --- Dispatch to correct visit method ---

    @Test
    void transform_dispatchesBinaryOp() {
        var transformer = new IdentityTransformer();
        var left = new IntLiteral(1);
        var right = new IntLiteral(2);
        var node = new BinaryOpNode(left, Operator.PLUS, right);
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        var out = (BinaryOpNode) result.value();
        assertThat(out.op()).isEqualTo(Operator.PLUS);
    }

    @Test
    void transform_dispatchesUnaryOp() {
        var transformer = new IdentityTransformer();
        var operand = new IntLiteral(5);
        var node = new UnaryOpNode(Operator.MINUS, operand, true);
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isInstanceOf(UnaryOpNode.class);
    }

    @Test
    void transform_dispatchesConditionalWithElse() {
        var transformer = new IdentityTransformer();
        var node = new ConditionalNode(new BoolLiteral(true), new IntLiteral(1), new IntLiteral(2));
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        var cond = (ConditionalNode) result.value();
        assertThat(cond.elseBranch()).isNotNull();
    }

    @Test
    void transform_dispatchesConditionalWithoutElse() {
        var transformer = new IdentityTransformer();
        var node = new ConditionalNode(new BoolLiteral(true), new IntLiteral(1));
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        var cond = (ConditionalNode) result.value();
        assertThat(cond.elseBranch()).isNull();
    }

    @Test
    void transform_dispatchesReturnWithValue() {
        var transformer = new IdentityTransformer();
        var node = new ReturnNode(new IntLiteral(42));
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((ReturnNode) result.value()).value()).isNotNull();
    }

    @Test
    void transform_dispatchesReturnWithoutValue() {
        var transformer = new IdentityTransformer();
        var node = new ReturnNode();
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(node);
    }

    // --- AstTransformer that modifies specific nodes ---

    @Test
    void customTransformer_doublesIntLiterals() {
        var transformer = new DoublingTransformer();
        var node = new IntLiteral(5);
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((IntLiteral) result.value()).value()).isEqualTo(10L);
    }

    @Test
    void customTransformer_doublesInsideBinaryOp() {
        var transformer = new DoublingTransformer();
        var node = new BinaryOpNode(new IntLiteral(3), Operator.PLUS, new IntLiteral(4));
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        var bin = (BinaryOpNode) result.value();
        assertThat(((IntLiteral) bin.left()).value()).isEqualTo(6L);
        assertThat(((IntLiteral) bin.right()).value()).isEqualTo(8L);
    }

    @Test
    void customTransformer_doublesInNestedTree() {
        var transformer = new DoublingTransformer();
        // (1 + 2) + 3
        var inner = new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2));
        var outer = new BinaryOpNode(inner, Operator.PLUS, new IntLiteral(3));
        var result = transformer.transform(outer);
        assertThat(result.isSuccess()).isTrue();
        var outerBin = (BinaryOpNode) result.value();
        var innerBin = (BinaryOpNode) outerBin.left();
        assertThat(((IntLiteral) innerBin.left()).value()).isEqualTo(2L);
        assertThat(((IntLiteral) innerBin.right()).value()).isEqualTo(4L);
        assertThat(((IntLiteral) outerBin.right()).value()).isEqualTo(6L);
    }

    @Test
    void customTransformer_preservesUnmodifiedSubtrees() {
        var transformer = new DoublingTransformer();
        var strNode = new StringLiteral("unchanged");
        var node = new BinaryOpNode(new IntLiteral(1), Operator.PLUS, strNode);
        // BinaryOp creates a new node, but the string child should be the same instance
        var result = transformer.transform(node);
        assertThat(result.isSuccess()).isTrue();
        var bin = (BinaryOpNode) result.value();
        assertThat(bin.right()).isSameAs(strNode);
    }

    @Test
    void transform_programWithMultipleStatements() {
        var transformer = new DoublingTransformer();
        var prog = new ProgramNode(List.of(new IntLiteral(10), new IntLiteral(20)));
        var result = transformer.transform(prog);
        assertThat(result.isSuccess()).isTrue();
        var out = (ProgramNode) result.value();
        assertThat(out.statements()).hasSize(2);
        assertThat(((IntLiteral) out.statements().get(0)).value()).isEqualTo(20L);
        assertThat(((IntLiteral) out.statements().get(1)).value()).isEqualTo(40L);
    }

    @Test
    void transform_functionDef_transformsBody() {
        var transformer = new DoublingTransformer();
        var funcDef = new FunctionDefNode("f", List.of(new ParameterNode("a")), new IntLiteral(5));
        var result = transformer.transform(funcDef);
        assertThat(result.isSuccess()).isTrue();
        var out = (FunctionDefNode) result.value();
        assertThat(((IntLiteral) out.body()).value()).isEqualTo(10L);
    }

    // --- Helper transformers ---

    /** Does nothing -- tests default identity behavior. */
    private static class IdentityTransformer extends AstTransformer {}

    /** Doubles all IntLiteral values, leaves everything else alone. */
    private static class DoublingTransformer extends AstTransformer {
        @Override
        public Result<AstNode> visitIntLiteral(IntLiteral node) {
            return Result.success(new IntLiteral(node.value() * 2, node.location(), node.metadata()));
        }
    }
}

package ssg.pex.arithmetics.handler;

import ssg.pex.arithmetics.type.ArithmeticTypeCoercion;
import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.UnaryOpNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

/**
 * Handles boolean/logical operations: &amp;&amp;, ||, !.
 * Short-circuit evaluation for &amp;&amp; and ||.
 */
public final class BooleanHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (node instanceof BinaryOpNode bin) {
            return handleBinary(bin, ctx);
        }
        if (node instanceof UnaryOpNode unary) {
            return handleUnary(unary, ctx);
        }
        return Result.failure("HANDLER_ERROR", "BooleanHandler expects BinaryOpNode or UnaryOpNode");
    }

    private Result<Object> handleBinary(BinaryOpNode bin, ExecutionContext ctx) {
        // Short-circuit evaluation: evaluate left first
        var leftResult = ctx.execute(bin.left());
        if (leftResult.isFailure()) return Result.failure(leftResult.error());

        var leftBool = ArithmeticTypeCoercion.coerceToBoolean(leftResult.value());
        if (leftBool.isFailure()) return Result.failure(leftBool.error());

        return switch (bin.op()) {
            case AND -> {
                // Short-circuit: if left is false, don't evaluate right
                if (!leftBool.value()) {
                    yield Result.success(false);
                }
                var rightResult = ctx.execute(bin.right());
                if (rightResult.isFailure()) yield Result.failure(rightResult.error());
                var rightBool = ArithmeticTypeCoercion.coerceToBoolean(rightResult.value());
                if (rightBool.isFailure()) yield Result.failure(rightBool.error());
                yield Result.success(rightBool.value());
            }
            case OR -> {
                // Short-circuit: if left is true, don't evaluate right
                if (leftBool.value()) {
                    yield Result.success(true);
                }
                var rightResult = ctx.execute(bin.right());
                if (rightResult.isFailure()) yield Result.failure(rightResult.error());
                var rightBool = ArithmeticTypeCoercion.coerceToBoolean(rightResult.value());
                if (rightBool.isFailure()) yield Result.failure(rightBool.error());
                yield Result.success(rightBool.value());
            }
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported boolean operator: " + bin.op().symbol());
        };
    }

    private Result<Object> handleUnary(UnaryOpNode unary, ExecutionContext ctx) {
        if (unary.op() != Operator.NOT) {
            return Result.failure("UNSUPPORTED_OP",
                    "BooleanHandler does not support unary operator: " + unary.op().symbol());
        }

        var operandResult = ctx.execute(unary.operand());
        if (operandResult.isFailure()) return Result.failure(operandResult.error());

        var boolResult = ArithmeticTypeCoercion.coerceToBoolean(operandResult.value());
        if (boolResult.isFailure()) return Result.failure(boolResult.error());

        return Result.success(!boolResult.value());
    }
}

package ssg.pex.arithmetics.handler;

import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

/**
 * Handles float/double arithmetic: +, -, *, /, %.
 * NaN and Infinity propagation.
 */
public final class FloatArithmeticHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof BinaryOpNode bin)) {
            return Result.failure("HANDLER_ERROR", "FloatArithmeticHandler expects BinaryOpNode");
        }

        var leftResult = ctx.execute(bin.left());
        if (leftResult.isFailure()) return Result.failure(leftResult.error());
        var rightResult = ctx.execute(bin.right());
        if (rightResult.isFailure()) return Result.failure(rightResult.error());

        Object left = leftResult.value();
        Object right = rightResult.value();

        if (left instanceof Number leftNum && right instanceof Number rightNum) {
            if (left instanceof Float || right instanceof Float) {
                if (!(left instanceof Double) && !(right instanceof Double)) {
                    return computeFloat(leftNum.floatValue(), rightNum.floatValue(), bin.op());
                }
            }
            return computeDouble(leftNum.doubleValue(), rightNum.doubleValue(), bin.op());
        }

        return Result.failure("TYPE_ERROR",
                "FloatArithmeticHandler requires numeric operands, got " +
                typeName(left) + " and " + typeName(right));
    }

    /**
     * Perform float arithmetic with NaN/Infinity propagation.
     */
    public static Result<Object> computeFloat(float left, float right, Operator op) {
        return switch (op) {
            case PLUS -> Result.success(left + right);
            case MINUS -> Result.success(left - right);
            case MULTIPLY -> Result.success(left * right);
            case DIVIDE -> Result.success(left / right);  // IEEE 754: produces Infinity or NaN
            case MODULO -> Result.success(left % right);
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported operator for float arithmetic: " + op.symbol());
        };
    }

    /**
     * Perform double arithmetic with NaN/Infinity propagation.
     */
    public static Result<Object> computeDouble(double left, double right, Operator op) {
        return switch (op) {
            case PLUS -> Result.success(left + right);
            case MINUS -> Result.success(left - right);
            case MULTIPLY -> Result.success(left * right);
            case DIVIDE -> Result.success(left / right);  // IEEE 754: produces Infinity or NaN
            case MODULO -> Result.success(left % right);
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported operator for double arithmetic: " + op.symbol());
        };
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}

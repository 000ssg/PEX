package ssg.pex.arithmetics.handler;

import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.UnaryOpNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

/**
 * Handles bitwise operations: &amp;, |, ^, ~, &lt;&lt;, &gt;&gt;, &gt;&gt;&gt;.
 * Only works on integer types (int, long). Returns error if applied to floats.
 */
public final class BitwiseHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (node instanceof BinaryOpNode bin) {
            return handleBinary(bin, ctx);
        }
        if (node instanceof UnaryOpNode unary) {
            return handleUnary(unary, ctx);
        }
        return Result.failure("HANDLER_ERROR", "BitwiseHandler expects BinaryOpNode or UnaryOpNode");
    }

    private Result<Object> handleBinary(BinaryOpNode bin, ExecutionContext ctx) {
        var leftResult = ctx.execute(bin.left());
        if (leftResult.isFailure()) return Result.failure(leftResult.error());
        var rightResult = ctx.execute(bin.right());
        if (rightResult.isFailure()) return Result.failure(rightResult.error());

        Object left = leftResult.value();
        Object right = rightResult.value();

        if (left instanceof Float || left instanceof Double ||
            right instanceof Float || right instanceof Double) {
            return Result.failure("TYPE_ERROR", "Bitwise operations require integer types, got floating-point");
        }

        if (!(left instanceof Number leftNum) || !(right instanceof Number rightNum)) {
            return Result.failure("TYPE_ERROR",
                    "Bitwise operations require integer operands, got " +
                    typeName(left) + " and " + typeName(right));
        }

        boolean useLong = left instanceof Long || right instanceof Long;
        if (useLong) {
            return computeLong(leftNum.longValue(), rightNum.longValue(), bin.op());
        }
        return computeInt(leftNum.intValue(), rightNum.intValue(), bin.op());
    }

    private Result<Object> handleUnary(UnaryOpNode unary, ExecutionContext ctx) {
        if (unary.op() != Operator.BIT_NOT) {
            return Result.failure("UNSUPPORTED_OP",
                    "BitwiseHandler does not support unary operator: " + unary.op().symbol());
        }

        var operandResult = ctx.execute(unary.operand());
        if (operandResult.isFailure()) return Result.failure(operandResult.error());
        Object operand = operandResult.value();

        if (operand instanceof Float || operand instanceof Double) {
            return Result.failure("TYPE_ERROR", "Bitwise NOT requires integer type, got floating-point");
        }
        if (operand instanceof Long l) {
            return Result.success(~l);
        }
        if (operand instanceof Integer i) {
            return Result.success(~i);
        }
        return Result.failure("TYPE_ERROR",
                "Bitwise NOT requires integer operand, got " + typeName(operand));
    }

    /**
     * Perform bitwise operation on ints.
     */
    public static Result<Object> computeInt(int left, int right, Operator op) {
        return switch (op) {
            case BIT_AND -> Result.success(left & right);
            case BIT_OR -> Result.success(left | right);
            case BIT_XOR -> Result.success(left ^ right);
            case SHIFT_LEFT -> Result.success(left << right);
            case SHIFT_RIGHT -> Result.success(left >> right);
            case UNSIGNED_SHIFT_RIGHT -> Result.success(left >>> right);
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported bitwise operator: " + op.symbol());
        };
    }

    /**
     * Perform bitwise operation on longs.
     */
    public static Result<Object> computeLong(long left, long right, Operator op) {
        return switch (op) {
            case BIT_AND -> Result.success(left & right);
            case BIT_OR -> Result.success(left | right);
            case BIT_XOR -> Result.success(left ^ right);
            case SHIFT_LEFT -> Result.success(left << right);
            case SHIFT_RIGHT -> Result.success(left >> right);
            case UNSIGNED_SHIFT_RIGHT -> Result.success(left >>> right);
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported bitwise operator: " + op.symbol());
        };
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}

package ssg.pex.arithmetics.handler;

import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

/**
 * Handles integer arithmetic: +, -, *, /, %.
 * Overflow detection for int operations (promotes to long).
 * Division by zero returns error Result (not exception).
 */
public final class IntArithmeticHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof BinaryOpNode bin)) {
            return Result.failure("HANDLER_ERROR", "IntArithmeticHandler expects BinaryOpNode");
        }

        var leftResult = ctx.execute(bin.left());
        if (leftResult.isFailure()) return Result.failure(leftResult.error());
        var rightResult = ctx.execute(bin.right());
        if (rightResult.isFailure()) return Result.failure(rightResult.error());

        Object left = leftResult.value();
        Object right = rightResult.value();

        if (left instanceof Integer li && right instanceof Integer ri) {
            return computeInt(li, ri, bin.op());
        }
        if ((left instanceof Integer || left instanceof Long) &&
            (right instanceof Integer || right instanceof Long)) {
            long ll = ((Number) left).longValue();
            long rl = ((Number) right).longValue();
            return computeLong(ll, rl, bin.op());
        }

        return Result.failure("TYPE_ERROR",
                "IntArithmeticHandler requires integer operands, got " +
                typeName(left) + " and " + typeName(right));
    }

    /**
     * Perform integer arithmetic with overflow detection.
     * If the result overflows int range, promotes to long.
     */
    public static Result<Object> computeInt(int left, int right, Operator op) {
        return switch (op) {
            case PLUS -> {
                long result = (long) left + right;
                yield safeResult(result);
            }
            case MINUS -> {
                long result = (long) left - right;
                yield safeResult(result);
            }
            case MULTIPLY -> {
                long result = (long) left * right;
                yield safeResult(result);
            }
            case DIVIDE -> {
                if (right == 0) {
                    yield Result.failure("DIVISION_BY_ZERO", "Division by zero");
                }
                yield Result.success(left / right);
            }
            case MODULO -> {
                if (right == 0) {
                    yield Result.failure("DIVISION_BY_ZERO", "Modulo by zero");
                }
                yield Result.success(left % right);
            }
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported operator for int arithmetic: " + op.symbol());
        };
    }

    /**
     * Perform long arithmetic.
     */
    public static Result<Object> computeLong(long left, long right, Operator op) {
        return switch (op) {
            case PLUS -> {
                try {
                    yield Result.success(Math.addExact(left, right));
                } catch (ArithmeticException e) {
                    yield Result.failure("OVERFLOW", "Long addition overflow");
                }
            }
            case MINUS -> {
                try {
                    yield Result.success(Math.subtractExact(left, right));
                } catch (ArithmeticException e) {
                    yield Result.failure("OVERFLOW", "Long subtraction overflow");
                }
            }
            case MULTIPLY -> {
                try {
                    yield Result.success(Math.multiplyExact(left, right));
                } catch (ArithmeticException e) {
                    yield Result.failure("OVERFLOW", "Long multiplication overflow");
                }
            }
            case DIVIDE -> {
                if (right == 0L) {
                    yield Result.failure("DIVISION_BY_ZERO", "Division by zero");
                }
                yield Result.success(left / right);
            }
            case MODULO -> {
                if (right == 0L) {
                    yield Result.failure("DIVISION_BY_ZERO", "Modulo by zero");
                }
                yield Result.success(left % right);
            }
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported operator for long arithmetic: " + op.symbol());
        };
    }

    private static Result<Object> safeResult(long result) {
        if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
            return Result.success((int) result);
        }
        return Result.success(result);
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}

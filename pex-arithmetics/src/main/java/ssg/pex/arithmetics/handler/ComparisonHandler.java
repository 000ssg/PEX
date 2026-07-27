package ssg.pex.arithmetics.handler;

import ssg.pex.arithmetics.type.NumericPromotion;
import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

import java.util.Objects;

/**
 * Handles comparison operations: ==, !=, &lt;, &gt;, &lt;=, &gt;=.
 * Cross-type comparison with numeric promotion.
 */
public final class ComparisonHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof BinaryOpNode bin)) {
            return Result.failure("HANDLER_ERROR", "ComparisonHandler expects BinaryOpNode");
        }

        var leftResult = ctx.execute(bin.left());
        if (leftResult.isFailure()) return Result.failure(leftResult.error());
        var rightResult = ctx.execute(bin.right());
        if (rightResult.isFailure()) return Result.failure(rightResult.error());

        Object left = leftResult.value();
        Object right = rightResult.value();

        return compare(left, right, bin.op());
    }

    /**
     * Compare two values with the given operator.
     */
    public static Result<Object> compare(Object left, Object right, Operator op) {
        // Handle null comparisons
        if (left == null || right == null) {
            return switch (op) {
                case EQ -> Result.success(left == right);
                case NEQ -> Result.success(left != right);
                default -> Result.failure("TYPE_ERROR",
                        "Cannot apply " + op.symbol() + " to null");
            };
        }

        // Numeric comparison with promotion
        if (left instanceof Number leftNum && right instanceof Number rightNum) {
            return compareNumeric(leftNum, rightNum, op);
        }

        // Boolean comparison
        if (left instanceof Boolean lb && right instanceof Boolean rb) {
            return switch (op) {
                case EQ -> Result.success(lb.equals(rb));
                case NEQ -> Result.success(!lb.equals(rb));
                default -> Result.failure("TYPE_ERROR",
                        "Cannot apply " + op.symbol() + " to boolean values");
            };
        }

        // String comparison
        if (left instanceof String ls && right instanceof String rs) {
            return compareStrings(ls, rs, op);
        }

        // Cross-type equality
        if (op == Operator.EQ) {
            return Result.success(Objects.equals(left, right));
        }
        if (op == Operator.NEQ) {
            return Result.success(!Objects.equals(left, right));
        }

        return Result.failure("TYPE_ERROR",
                "Cannot compare " + left.getClass().getSimpleName() +
                " and " + right.getClass().getSimpleName() + " with " + op.symbol());
    }

    private static Result<Object> compareNumeric(Number left, Number right, Operator op) {
        var pair = NumericPromotion.promotePair(left, right);
        double l = pair.left().doubleValue();
        double r = pair.right().doubleValue();
        // Use == for equality to respect IEEE 754 semantics (-0.0 == 0.0 is true),
        // but use Double.compare for ordering (where -0.0 < 0.0)
        if (op == Operator.EQ) return Result.success(l == r);
        if (op == Operator.NEQ) return Result.success(l != r);
        int cmp = Double.compare(l, r);

        return switch (op) {
            case EQ -> Result.success(cmp == 0);
            case NEQ -> Result.success(cmp != 0);
            case LT -> Result.success(cmp < 0);
            case GT -> Result.success(cmp > 0);
            case LE -> Result.success(cmp <= 0);
            case GE -> Result.success(cmp >= 0);
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported comparison operator: " + op.symbol());
        };
    }

    private static Result<Object> compareStrings(String left, String right, Operator op) {
        int cmp = left.compareTo(right);
        return switch (op) {
            case EQ -> Result.success(cmp == 0);
            case NEQ -> Result.success(cmp != 0);
            case LT -> Result.success(cmp < 0);
            case GT -> Result.success(cmp > 0);
            case LE -> Result.success(cmp <= 0);
            case GE -> Result.success(cmp >= 0);
            default -> Result.failure("UNSUPPORTED_OP",
                    "Unsupported comparison operator: " + op.symbol());
        };
    }
}

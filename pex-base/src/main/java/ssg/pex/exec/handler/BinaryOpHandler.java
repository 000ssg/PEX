package ssg.pex.exec.handler;

import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeCoercion;

public final class BinaryOpHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof BinaryOpNode binOp)) {
            return Result.failure("HANDLER_ERROR", "Expected BinaryOpNode but got: " + node.getClass().getName());
        }

        var leftResult = ctx.execute(binOp.left());
        if (leftResult.isFailure()) return Result.failure(leftResult.error());

        // Short-circuit for logical operators
        if (binOp.op() == Operator.AND) {
            if (!isTruthy(leftResult.value())) return Result.success(false);
            var rightResult = ctx.execute(binOp.right());
            if (rightResult.isFailure()) return Result.failure(rightResult.error());
            return Result.success(isTruthy(rightResult.value()));
        }
        if (binOp.op() == Operator.OR) {
            if (isTruthy(leftResult.value())) return Result.success(true);
            var rightResult = ctx.execute(binOp.right());
            if (rightResult.isFailure()) return Result.failure(rightResult.error());
            return Result.success(isTruthy(rightResult.value()));
        }

        var rightResult = ctx.execute(binOp.right());
        if (rightResult.isFailure()) return Result.failure(rightResult.error());

        var left = leftResult.value();
        var right = rightResult.value();

        return applyOperator(binOp.op(), left, right);
    }

    private Result<Object> applyOperator(Operator op, Object left, Object right) {
        // String concatenation
        if (op == Operator.PLUS && (left instanceof String || right instanceof String)) {
            return Result.success(String.valueOf(left) + String.valueOf(right));
        }

        if (op.isComparison()) {
            return applyComparison(op, left, right);
        }

        if (op.isArithmetic()) {
            return applyArithmetic(op, left, right);
        }

        if (op.isBitwise()) {
            return applyBitwise(op, left, right);
        }

        return Result.failure("UNSUPPORTED_OPERATOR", "Unsupported binary operator: " + op.symbol());
    }

    private Result<Object> applyArithmetic(Operator op, Object left, Object right) {
        var leftType = TypeCoercion.inferType(left);
        var rightType = TypeCoercion.inferType(right);

        if (!leftType.isNumeric() || !rightType.isNumeric()) {
            return Result.failure("TYPE_ERROR",
                    "Arithmetic operator " + op.symbol() + " requires numeric operands, got " +
                    leftType.displayName() + " and " + rightType.displayName());
        }

        var targetType = TypeCoercion.widenNumeric(leftType, rightType);

        var leftCoerced = TypeCoercion.coerce(left, targetType);
        var rightCoerced = TypeCoercion.coerce(right, targetType);
        if (leftCoerced.isFailure()) return Result.failure(leftCoerced.error());
        if (rightCoerced.isFailure()) return Result.failure(rightCoerced.error());

        return computeArithmetic(op, leftCoerced.value(), rightCoerced.value(), targetType);
    }

    private Result<Object> computeArithmetic(Operator op, Object left, Object right, PexType type) {
        return switch (type) {
            case INT -> {
                int a = (Integer) left, b = (Integer) right;
                yield switch (op) {
                    case PLUS -> Result.success((Object) (a + b));
                    case MINUS -> Result.success((Object) (a - b));
                    case MULTIPLY -> Result.success((Object) (a * b));
                    case DIVIDE -> b == 0
                            ? Result.failure("DIVISION_BY_ZERO", "Division by zero")
                            : Result.success((Object) (a / b));
                    case MODULO -> b == 0
                            ? Result.failure("DIVISION_BY_ZERO", "Modulo by zero")
                            : Result.success((Object) (a % b));
                    default -> Result.failure("UNSUPPORTED_OPERATOR", "Unsupported: " + op);
                };
            }
            case LONG -> {
                long a = (Long) left, b = (Long) right;
                yield switch (op) {
                    case PLUS -> Result.success((Object) (a + b));
                    case MINUS -> Result.success((Object) (a - b));
                    case MULTIPLY -> Result.success((Object) (a * b));
                    case DIVIDE -> b == 0L
                            ? Result.failure("DIVISION_BY_ZERO", "Division by zero")
                            : Result.success((Object) (a / b));
                    case MODULO -> b == 0L
                            ? Result.failure("DIVISION_BY_ZERO", "Modulo by zero")
                            : Result.success((Object) (a % b));
                    default -> Result.failure("UNSUPPORTED_OPERATOR", "Unsupported: " + op);
                };
            }
            case FLOAT -> {
                float a = (Float) left, b = (Float) right;
                yield switch (op) {
                    case PLUS -> Result.success((Object) (a + b));
                    case MINUS -> Result.success((Object) (a - b));
                    case MULTIPLY -> Result.success((Object) (a * b));
                    case DIVIDE -> Result.success((Object) (a / b));
                    case MODULO -> Result.success((Object) (a % b));
                    default -> Result.failure("UNSUPPORTED_OPERATOR", "Unsupported: " + op);
                };
            }
            case DOUBLE -> {
                double a = (Double) left, b = (Double) right;
                yield switch (op) {
                    case PLUS -> Result.success((Object) (a + b));
                    case MINUS -> Result.success((Object) (a - b));
                    case MULTIPLY -> Result.success((Object) (a * b));
                    case DIVIDE -> Result.success((Object) (a / b));
                    case MODULO -> Result.success((Object) (a % b));
                    default -> Result.failure("UNSUPPORTED_OPERATOR", "Unsupported: " + op);
                };
            }
            default -> Result.failure("TYPE_ERROR", "Cannot perform arithmetic on " + type.displayName());
        };
    }

    @SuppressWarnings("unchecked")
    private Result<Object> applyComparison(Operator op, Object left, Object right) {
        // Null comparisons
        if (left == null || right == null) {
            return switch (op) {
                case EQ -> Result.success(left == right);
                case NEQ -> Result.success(left != right);
                default -> Result.failure("TYPE_ERROR", "Cannot compare null with " + op.symbol());
            };
        }

        // Equality for any type
        if (op == Operator.EQ) return Result.success(left.equals(right));
        if (op == Operator.NEQ) return Result.success(!left.equals(right));

        // Ordering comparisons require Comparable
        if (left instanceof Comparable<?> && left.getClass().isInstance(right)) {
            int cmp = ((Comparable<Object>) left).compareTo(right);
            return switch (op) {
                case LT -> Result.success(cmp < 0);
                case GT -> Result.success(cmp > 0);
                case LE -> Result.success(cmp <= 0);
                case GE -> Result.success(cmp >= 0);
                default -> Result.failure("UNSUPPORTED_OPERATOR", "Unsupported comparison: " + op);
            };
        }

        // Try numeric coercion for mixed types
        var leftType = TypeCoercion.inferType(left);
        var rightType = TypeCoercion.inferType(right);
        if (leftType.isNumeric() && rightType.isNumeric()) {
            var targetType = TypeCoercion.widenNumeric(leftType, rightType);
            var leftCoerced = TypeCoercion.coerce(left, targetType);
            var rightCoerced = TypeCoercion.coerce(right, targetType);
            if (leftCoerced.isSuccess() && rightCoerced.isSuccess()) {
                int cmp = ((Comparable<Object>) leftCoerced.value()).compareTo(rightCoerced.value());
                return switch (op) {
                    case LT -> Result.success(cmp < 0);
                    case GT -> Result.success(cmp > 0);
                    case LE -> Result.success(cmp <= 0);
                    case GE -> Result.success(cmp >= 0);
                    default -> Result.failure("UNSUPPORTED_OPERATOR", "Unsupported: " + op);
                };
            }
        }

        return Result.failure("TYPE_ERROR",
                "Cannot compare " + leftType.displayName() + " with " + rightType.displayName() + " using " + op.symbol());
    }

    private Result<Object> applyBitwise(Operator op, Object left, Object right) {
        var leftType = TypeCoercion.inferType(left);
        var rightType = TypeCoercion.inferType(right);

        if (!leftType.isInteger() || !rightType.isInteger()) {
            return Result.failure("TYPE_ERROR",
                    "Bitwise operator " + op.symbol() + " requires integer operands");
        }

        long a = ((Number) left).longValue();
        long b = ((Number) right).longValue();

        long result = switch (op) {
            case BIT_AND -> a & b;
            case BIT_OR -> a | b;
            case BIT_XOR -> a ^ b;
            case SHIFT_LEFT -> a << b;
            case SHIFT_RIGHT -> a >> b;
            case UNSIGNED_SHIFT_RIGHT -> a >>> b;
            default -> throw new IllegalArgumentException("Not a bitwise operator: " + op);
        };

        if (leftType == PexType.INT && rightType == PexType.INT) {
            return Result.success((int) result);
        }
        return Result.success(result);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }
}

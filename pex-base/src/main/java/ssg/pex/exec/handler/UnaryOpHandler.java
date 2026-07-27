package ssg.pex.exec.handler;

import ssg.pex.ast.node.UnaryOpNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeCoercion;

public final class UnaryOpHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof UnaryOpNode unary)) {
            return Result.failure("HANDLER_ERROR", "Expected UnaryOpNode but got: " + node.getClass().getName());
        }

        var operandResult = ctx.execute(unary.operand());
        if (operandResult.isFailure()) return Result.failure(operandResult.error());

        var operand = operandResult.value();

        return switch (unary.op()) {
            case MINUS -> applyNegate(operand);
            case PLUS -> applyPositive(operand);
            case NOT -> Result.success(!isTruthy(operand));
            case BIT_NOT -> applyBitwiseNot(operand);
            default -> Result.failure("UNSUPPORTED_OPERATOR", "Unsupported unary operator: " + unary.op().symbol());
        };
    }

    private Result<Object> applyNegate(Object operand) {
        var type = TypeCoercion.inferType(operand);
        return switch (type) {
            case INT -> Result.success((Object) (-(Integer) operand));
            case LONG -> Result.success((Object) (-(Long) operand));
            case FLOAT -> Result.success((Object) (-(Float) operand));
            case DOUBLE -> Result.success((Object) (-(Double) operand));
            default -> Result.failure("TYPE_ERROR", "Cannot negate " + type.displayName());
        };
    }

    private Result<Object> applyPositive(Object operand) {
        var type = TypeCoercion.inferType(operand);
        if (type.isNumeric()) {
            return Result.success(operand);
        }
        return Result.failure("TYPE_ERROR", "Unary '+' requires numeric operand, got " + type.displayName());
    }

    private Result<Object> applyBitwiseNot(Object operand) {
        var type = TypeCoercion.inferType(operand);
        return switch (type) {
            case INT -> Result.success((Object) (~(Integer) operand));
            case LONG -> Result.success((Object) (~(Long) operand));
            default -> Result.failure("TYPE_ERROR", "Bitwise NOT requires integer operand, got " + type.displayName());
        };
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }
}

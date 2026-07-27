package ssg.pex.arithmetics.handler;

import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.ExtensionNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.UnaryOpNode;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class that creates all arithmetic node handlers.
 * Returns a map of AST node class to handler, where a dispatching handler
 * routes based on operator type.
 */
public final class ArithmeticHandlerProvider {

    private ArithmeticHandlerProvider() {}

    /**
     * Creates the full set of arithmetic handlers keyed by AST node type.
     */
    public static Map<Class<?>, NodeHandler> createHandlers() {
        var handlers = new LinkedHashMap<Class<?>, NodeHandler>();

        var intHandler = new IntArithmeticHandler();
        var floatHandler = new FloatArithmeticHandler();
        var bitwiseHandler = new BitwiseHandler();
        var boolHandler = new BooleanHandler();
        var comparisonHandler = new ComparisonHandler();
        var radixHandler = new RadixHandler();

        // Dispatch BinaryOpNode based on operator
        NodeHandler binaryDispatcher = (node, ctx) -> {
            if (!(node instanceof BinaryOpNode bin)) {
                return Result.failure("HANDLER_ERROR", "Expected BinaryOpNode");
            }
            Operator op = bin.op();

            if (op.isComparison()) {
                return comparisonHandler.handle(node, ctx);
            }
            if (op.isLogical()) {
                return boolHandler.handle(node, ctx);
            }
            if (op.isBitwise()) {
                return bitwiseHandler.handle(node, ctx);
            }
            if (op.isArithmetic()) {
                return dispatchArithmetic(node, ctx, intHandler, floatHandler);
            }
            return Result.failure("UNSUPPORTED_OP", "Unsupported binary operator: " + op.symbol());
        };

        // Dispatch UnaryOpNode based on operator
        NodeHandler unaryDispatcher = (node, ctx) -> {
            if (!(node instanceof UnaryOpNode unary)) {
                return Result.failure("HANDLER_ERROR", "Expected UnaryOpNode");
            }
            Operator op = unary.op();

            if (op == Operator.NOT) {
                return boolHandler.handle(node, ctx);
            }
            if (op == Operator.BIT_NOT) {
                return bitwiseHandler.handle(node, ctx);
            }
            if (op == Operator.MINUS || op == Operator.PLUS) {
                return handleUnaryArithmetic(unary, ctx);
            }
            return Result.failure("UNSUPPORTED_OP", "Unsupported unary operator: " + op.symbol());
        };

        handlers.put(BinaryOpNode.class, binaryDispatcher);
        handlers.put(UnaryOpNode.class, unaryDispatcher);
        handlers.put(ExtensionNode.class, radixHandler);

        return Map.copyOf(handlers);
    }

    private static Result<Object> dispatchArithmetic(
            Object node, ssg.pex.exec.ExecutionContext ctx,
            IntArithmeticHandler intHandler, FloatArithmeticHandler floatHandler) {

        if (!(node instanceof BinaryOpNode bin)) {
            return Result.failure("HANDLER_ERROR", "Expected BinaryOpNode");
        }

        var leftResult = ctx.execute(bin.left());
        if (leftResult.isFailure()) return Result.failure(leftResult.error());
        var rightResult = ctx.execute(bin.right());
        if (rightResult.isFailure()) return Result.failure(rightResult.error());

        Object left = leftResult.value();
        Object right = rightResult.value();

        // If either operand is floating-point, use float handler
        if (left instanceof Float || left instanceof Double ||
            right instanceof Float || right instanceof Double) {
            return floatArithmetic(left, right, bin.op());
        }

        // Integer arithmetic
        if (left instanceof Integer li && right instanceof Integer ri) {
            return IntArithmeticHandler.computeInt(li, ri, bin.op());
        }
        if ((left instanceof Integer || left instanceof Long) &&
            (right instanceof Integer || right instanceof Long)) {
            return IntArithmeticHandler.computeLong(
                    ((Number) left).longValue(), ((Number) right).longValue(), bin.op());
        }

        return Result.failure("TYPE_ERROR",
                "Cannot perform arithmetic on " + typeName(left) + " and " + typeName(right));
    }

    private static Result<Object> floatArithmetic(Object left, Object right, Operator op) {
        if (!(left instanceof Number ln) || !(right instanceof Number rn)) {
            return Result.failure("TYPE_ERROR",
                    "Cannot perform float arithmetic on " + typeName(left) + " and " + typeName(right));
        }

        // Use float if both are float or narrower (and neither is double)
        if (!(left instanceof Double) && !(right instanceof Double) &&
            (left instanceof Float || right instanceof Float)) {
            return FloatArithmeticHandler.computeFloat(ln.floatValue(), rn.floatValue(), op);
        }
        return FloatArithmeticHandler.computeDouble(ln.doubleValue(), rn.doubleValue(), op);
    }

    private static Result<Object> handleUnaryArithmetic(UnaryOpNode unary, ssg.pex.exec.ExecutionContext ctx) {
        var operandResult = ctx.execute(unary.operand());
        if (operandResult.isFailure()) return Result.failure(operandResult.error());
        Object operand = operandResult.value();

        if (unary.op() == Operator.PLUS) {
            if (operand instanceof Number) {
                return Result.success(operand);
            }
            return Result.failure("TYPE_ERROR", "Unary + requires numeric operand");
        }

        // Unary minus
        if (operand instanceof Integer i) return Result.success(-i);
        if (operand instanceof Long l) return Result.success(-l);
        if (operand instanceof Float f) return Result.success(-f);
        if (operand instanceof Double d) return Result.success(-d);

        return Result.failure("TYPE_ERROR",
                "Unary - requires numeric operand, got " + typeName(operand));
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}

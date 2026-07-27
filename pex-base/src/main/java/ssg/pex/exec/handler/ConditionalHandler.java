package ssg.pex.exec.handler;

import ssg.pex.ast.node.ConditionalNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

public final class ConditionalHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof ConditionalNode cond)) {
            return Result.failure("HANDLER_ERROR", "Expected ConditionalNode but got: " + node.getClass().getName());
        }

        var condResult = ctx.execute(cond.condition());
        if (condResult.isFailure()) return Result.failure(condResult.error());

        if (isTruthy(condResult.value())) {
            return ctx.execute(cond.thenBranch());
        } else if (cond.elseBranch() != null) {
            return ctx.execute(cond.elseBranch());
        }
        return Result.success(null);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }
}

package ssg.pex.exec.handler;

import ssg.pex.ast.node.LoopNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

public final class LoopHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof LoopNode loop)) {
            return Result.failure("HANDLER_ERROR", "Expected LoopNode but got: " + node.getClass().getName());
        }

        int maxIterations = ctx.config().maxLoopIterations();
        Object lastValue = null;

        return switch (loop.kind()) {
            case WHILE -> executeWhile(loop, ctx, maxIterations);
            case DO_WHILE -> executeDoWhile(loop, ctx, maxIterations);
            case FOR -> executeWhile(loop, ctx, maxIterations); // FOR uses same logic as WHILE for now
        };
    }

    private Result<Object> executeWhile(LoopNode loop, ExecutionContext ctx, int maxIterations) {
        Object lastValue = null;
        for (int i = 0; i < maxIterations; i++) {
            ctx.checkTimeout();

            var condResult = ctx.execute(loop.condition());
            if (condResult.isFailure()) return Result.failure(condResult.error());
            if (!isTruthy(condResult.value())) break;

            var bodyResult = ctx.execute(loop.body());
            if (bodyResult.isFailure()) return bodyResult;
            lastValue = bodyResult.value();
        }
        return Result.success(lastValue);
    }

    private Result<Object> executeDoWhile(LoopNode loop, ExecutionContext ctx, int maxIterations) {
        Object lastValue = null;
        for (int i = 0; i < maxIterations; i++) {
            ctx.checkTimeout();

            var bodyResult = ctx.execute(loop.body());
            if (bodyResult.isFailure()) return bodyResult;
            lastValue = bodyResult.value();

            var condResult = ctx.execute(loop.condition());
            if (condResult.isFailure()) return Result.failure(condResult.error());
            if (!isTruthy(condResult.value())) break;
        }
        return Result.success(lastValue);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }
}

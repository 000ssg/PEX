package ssg.pex.exec.handler;

import ssg.pex.ast.node.BlockNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

public final class BlockHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof BlockNode block)) {
            return Result.failure("HANDLER_ERROR", "Expected BlockNode but got: " + node.getClass().getName());
        }

        ctx.scopeTree().enterScope("block");
        ctx.statistics().incrementScopeEntries();
        for (var listener : ctx.listeners()) {
            listener.onScopeEnter("block", ctx);
        }

        try {
            Object lastValue = null;
            for (var stmt : block.statements()) {
                var result = ctx.execute(stmt);
                if (result.isFailure()) return result;
                lastValue = result.value();
            }
            return Result.success(lastValue);
        } finally {
            ctx.scopeTree().exitScope();
            ctx.statistics().incrementScopeExits();
            for (var listener : ctx.listeners()) {
                listener.onScopeExit("block", ctx);
            }
        }
    }
}

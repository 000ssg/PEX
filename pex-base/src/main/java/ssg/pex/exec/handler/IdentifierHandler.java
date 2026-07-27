package ssg.pex.exec.handler;

import ssg.pex.ast.node.IdentifierNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

public final class IdentifierHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (node instanceof IdentifierNode id) {
            var resolved = ctx.scopeTree().resolveVariable(id.name());
            if (resolved.isFailure()) {
                return Result.failure(resolved.error());
            }
            return Result.success(resolved.value().currentValue());
        }
        return Result.failure("HANDLER_ERROR", "Expected IdentifierNode but got: " + node.getClass().getName());
    }
}

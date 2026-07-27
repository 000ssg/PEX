package ssg.pex.exec.handler;

import ssg.pex.ast.node.LiteralNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

public final class LiteralHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (node instanceof LiteralNode literal) {
            return Result.success(literal.literalValue());
        }
        return Result.failure("HANDLER_ERROR", "Expected LiteralNode but got: " + node.getClass().getName());
    }
}

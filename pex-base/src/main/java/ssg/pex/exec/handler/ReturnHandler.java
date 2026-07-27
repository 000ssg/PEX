package ssg.pex.exec.handler;

import ssg.pex.ast.node.ReturnNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.exec.ReturnException;
import ssg.pex.result.Result;

public final class ReturnHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof ReturnNode ret)) {
            return Result.failure("HANDLER_ERROR", "Expected ReturnNode but got: " + node.getClass().getName());
        }

        Object value = null;
        if (ret.value() != null) {
            var valueResult = ctx.execute(ret.value());
            if (valueResult.isFailure()) return valueResult;
            value = valueResult.value();
        }

        throw new ReturnException(value);
    }
}

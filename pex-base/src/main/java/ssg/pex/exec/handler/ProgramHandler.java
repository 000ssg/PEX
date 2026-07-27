package ssg.pex.exec.handler;

import ssg.pex.ast.node.ProgramNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

public final class ProgramHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof ProgramNode program)) {
            return Result.failure("HANDLER_ERROR", "Expected ProgramNode but got: " + node.getClass().getName());
        }

        Object lastValue = null;
        for (var stmt : program.statements()) {
            var result = ctx.execute(stmt);
            if (result.isFailure()) return result;
            lastValue = result.value();
        }
        return Result.success(lastValue);
    }
}

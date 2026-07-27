package ssg.pex.exec.handler;

import ssg.pex.ast.node.FunctionDefNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.FunctionDef;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

public final class FunctionDefHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof FunctionDefNode funcDef)) {
            return Result.failure("HANDLER_ERROR", "Expected FunctionDefNode but got: " + node.getClass().getName());
        }

        var paramNames = funcDef.params().stream()
                .map(p -> p.name())
                .toList();

        var def = new FunctionDef(funcDef.name(), paramNames, funcDef.body(), null);
        ctx.functions().register(funcDef.name(), def);

        return Result.success(null);
    }
}

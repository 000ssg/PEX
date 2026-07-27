package ssg.pex.exec.handler;

import ssg.pex.ast.node.AssignmentNode;
import ssg.pex.ast.node.IdentifierNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;
import ssg.pex.scope.ScalarVariable;
import ssg.pex.type.TypeCoercion;
import ssg.pex.type.TypeDescriptor;

public final class AssignmentHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof AssignmentNode assign)) {
            return Result.failure("HANDLER_ERROR", "Expected AssignmentNode but got: " + node.getClass().getName());
        }

        var valueResult = ctx.execute(assign.value());
        if (valueResult.isFailure()) return Result.failure(valueResult.error());
        var value = valueResult.value();

        if (assign.target() instanceof IdentifierNode id) {
            var existing = ctx.scopeTree().resolveVariable(id.name());
            if (existing.isSuccess()) {
                var updateResult = ctx.scopeTree().updateVariable(id.name(), value);
                if (updateResult.isFailure()) return Result.failure(updateResult.error());
            } else {
                var pexType = TypeCoercion.inferType(value);
                var typeDesc = TypeDescriptor.of(pexType);
                var variable = new ScalarVariable(id.name(), value, typeDesc, true);
                var defineResult = ctx.scopeTree().defineVariable(id.name(), variable);
                if (defineResult.isFailure()) return Result.failure(defineResult.error());
            }
            return Result.success(value);
        }

        return Result.failure("ASSIGNMENT_ERROR",
                "Unsupported assignment target: " + assign.target().getClass().getSimpleName());
    }
}

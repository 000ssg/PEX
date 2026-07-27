package ssg.pex.exec.handler;

import ssg.pex.ast.node.FunctionCallNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.exec.ReturnException;
import ssg.pex.result.Result;
import ssg.pex.scope.ScalarVariable;
import ssg.pex.type.TypeCoercion;
import ssg.pex.type.TypeDescriptor;

import java.util.ArrayList;

public final class FunctionCallHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof FunctionCallNode call)) {
            return Result.failure("HANDLER_ERROR", "Expected FunctionCallNode but got: " + node.getClass().getName());
        }

        var funcOpt = ctx.functions().lookup(call.name());
        if (funcOpt.isEmpty()) {
            return Result.failure("UNDEFINED_FUNCTION", "Function '" + call.name() + "' is not defined");
        }
        var funcDef = funcOpt.get();

        // Evaluate arguments
        var args = new ArrayList<Object>(call.arguments().size());
        for (var argNode : call.arguments()) {
            var argResult = ctx.execute(argNode);
            if (argResult.isFailure()) return Result.failure(argResult.error());
            args.add(argResult.value());
        }

        ctx.statistics().incrementFunctionCalls();
        for (var listener : ctx.listeners()) {
            listener.onFunctionCall(call.name(), args, ctx);
        }

        // Native function
        if (funcDef.isNative()) {
            return funcDef.nativeImpl().invoke(args, ctx);
        }

        // User-defined function
        ctx.enterRecursion();
        ctx.scopeTree().enterScope("fn:" + call.name());
        ctx.statistics().incrementScopeEntries();

        try {
            // Bind parameters
            var paramNames = funcDef.paramNames();
            for (int i = 0; i < paramNames.size(); i++) {
                var paramName = paramNames.get(i);
                var value = i < args.size() ? args.get(i) : null;
                var pexType = TypeCoercion.inferType(value);
                var variable = new ScalarVariable(paramName, value, TypeDescriptor.of(pexType), true);
                ctx.scopeTree().defineVariable(paramName, variable);
            }

            // Execute body
            var bodyResult = ctx.execute(funcDef.body());
            return bodyResult;
        } catch (ReturnException re) {
            return Result.success(re.value());
        } finally {
            ctx.scopeTree().exitScope();
            ctx.statistics().incrementScopeExits();
            ctx.exitRecursion();
        }
    }
}

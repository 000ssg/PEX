package ssg.pex.exec.handler;

import ssg.pex.ast.node.IndexAccessNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;
import ssg.pex.scope.VariableStore;

import java.util.List;
import java.util.Map;

public final class IndexAccessHandler implements NodeHandler {

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (!(node instanceof IndexAccessNode access)) {
            return Result.failure("HANDLER_ERROR", "Expected IndexAccessNode but got: " + node.getClass().getName());
        }

        var targetResult = ctx.execute(access.target());
        if (targetResult.isFailure()) return Result.failure(targetResult.error());

        var indexResult = ctx.execute(access.index());
        if (indexResult.isFailure()) return Result.failure(indexResult.error());

        var target = targetResult.value();
        var index = indexResult.value();

        if (target instanceof VariableStore store) {
            return Result.success(store.get(index));
        }

        if (target instanceof List<?> list) {
            if (!(index instanceof Number num)) {
                return Result.failure("TYPE_ERROR", "List index must be numeric, got: " + index.getClass().getSimpleName());
            }
            int i = num.intValue();
            if (i < 0 || i >= list.size()) {
                return Result.failure("INDEX_OUT_OF_BOUNDS",
                        "Index " + i + " out of bounds for list of size " + list.size());
            }
            return Result.success(list.get(i));
        }

        if (target instanceof Map<?, ?> map) {
            return Result.success(map.get(index));
        }

        if (target instanceof String str) {
            if (!(index instanceof Number num)) {
                return Result.failure("TYPE_ERROR", "String index must be numeric");
            }
            int i = num.intValue();
            if (i < 0 || i >= str.length()) {
                return Result.failure("INDEX_OUT_OF_BOUNDS",
                        "Index " + i + " out of bounds for string of length " + str.length());
            }
            return Result.success(String.valueOf(str.charAt(i)));
        }

        return Result.failure("TYPE_ERROR",
                "Cannot index into " + (target == null ? "null" : target.getClass().getSimpleName()));
    }
}

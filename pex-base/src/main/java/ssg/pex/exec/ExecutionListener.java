package ssg.pex.exec;

import ssg.pex.result.PexError;
import ssg.pex.result.Result;

import java.util.List;

public interface ExecutionListener {

    void onNodeEnter(Object node, ExecutionContext ctx);

    void onNodeExit(Object node, ExecutionContext ctx, Result<Object> result);

    void onScopeEnter(String scopeName, ExecutionContext ctx);

    void onScopeExit(String scopeName, ExecutionContext ctx);

    void onFunctionCall(String functionName, List<Object> args, ExecutionContext ctx);

    void onError(PexError error, ExecutionContext ctx);
}

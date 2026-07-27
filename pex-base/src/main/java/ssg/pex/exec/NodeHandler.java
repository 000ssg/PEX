package ssg.pex.exec;

import ssg.pex.result.Result;

@FunctionalInterface
public interface NodeHandler {

    Result<Object> handle(Object node, ExecutionContext ctx);
}

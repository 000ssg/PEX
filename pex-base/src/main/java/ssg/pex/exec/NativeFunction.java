package ssg.pex.exec;

import ssg.pex.result.Result;

import java.util.List;

@FunctionalInterface
public interface NativeFunction {

    Result<Object> invoke(List<Object> args, ExecutionContext ctx);
}

package ssg.pex.converter.jit;

import java.util.Map;

public interface JitExecutable {

    Object execute(Map<String, Object> context);
}

package ssg.pex.exec;

import ssg.pex.ast.node.AstNode;

import java.util.List;

public record FunctionDef(String name, List<String> paramNames, AstNode body, NativeFunction nativeImpl) {

    public FunctionDef {
        paramNames = List.copyOf(paramNames);
    }

    public boolean isNative() {
        return nativeImpl != null;
    }
}

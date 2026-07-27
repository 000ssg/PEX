package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record FunctionDefNode(String name, List<ParameterNode> params, AstNode body, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public FunctionDefNode(String name, List<ParameterNode> params, AstNode body) {
        this(name, List.copyOf(params), body, SourceLocation.UNKNOWN, Map.of());
    }

    public FunctionDefNode {
        params = List.copyOf(params);
    }

    @Override
    public List<AstNode> children() {
        var result = new ArrayList<AstNode>(params.size() + 1);
        result.addAll(params);
        result.add(body);
        return List.copyOf(result);
    }
}

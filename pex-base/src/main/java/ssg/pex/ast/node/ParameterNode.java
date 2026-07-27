package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record ParameterNode(String name, String typeName, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public ParameterNode(String name, String typeName) {
        this(name, typeName, SourceLocation.UNKNOWN, Map.of());
    }

    public ParameterNode(String name) {
        this(name, null, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}

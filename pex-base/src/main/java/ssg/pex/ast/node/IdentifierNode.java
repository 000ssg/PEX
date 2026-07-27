package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record IdentifierNode(String name, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public IdentifierNode(String name) {
        this(name, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}

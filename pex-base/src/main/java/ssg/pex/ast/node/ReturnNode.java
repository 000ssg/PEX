package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record ReturnNode(AstNode value, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public ReturnNode(AstNode value) {
        this(value, SourceLocation.UNKNOWN, Map.of());
    }

    public ReturnNode() {
        this(null, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return value != null ? List.of(value) : List.of();
    }
}

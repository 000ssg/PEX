package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record LoopNode(LoopKind kind, AstNode condition, AstNode body, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public LoopNode(LoopKind kind, AstNode condition, AstNode body) {
        this(kind, condition, body, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of(condition, body);
    }
}

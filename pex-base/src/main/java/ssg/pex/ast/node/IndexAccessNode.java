package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record IndexAccessNode(AstNode target, AstNode index, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public IndexAccessNode(AstNode target, AstNode index) {
        this(target, index, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of(target, index);
    }
}

package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record AssignmentNode(AstNode target, AstNode value, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public AssignmentNode(AstNode target, AstNode value) {
        this(target, value, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of(target, value);
    }
}

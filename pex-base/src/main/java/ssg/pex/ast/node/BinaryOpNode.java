package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record BinaryOpNode(AstNode left, Operator op, AstNode right, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public BinaryOpNode(AstNode left, Operator op, AstNode right) {
        this(left, op, right, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of(left, right);
    }
}

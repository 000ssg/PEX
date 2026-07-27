package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record UnaryOpNode(Operator op, AstNode operand, boolean prefix, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public UnaryOpNode(Operator op, AstNode operand, boolean prefix) {
        this(op, operand, prefix, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of(operand);
    }
}

package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record StringLiteral(String value, SourceLocation location, Map<String, Object> metadata) implements LiteralNode {

    public StringLiteral(String value) {
        this(value, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public Object literalValue() {
        return value;
    }
}

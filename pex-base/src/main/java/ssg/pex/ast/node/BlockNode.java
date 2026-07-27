package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record BlockNode(List<AstNode> statements, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public BlockNode(List<AstNode> statements) {
        this(List.copyOf(statements), SourceLocation.UNKNOWN, Map.of());
    }

    public BlockNode {
        statements = List.copyOf(statements);
    }

    @Override
    public List<AstNode> children() {
        return statements;
    }
}

package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record ProgramNode(List<AstNode> statements, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public ProgramNode(List<AstNode> statements) {
        this(List.copyOf(statements), SourceLocation.UNKNOWN, Map.of());
    }

    public ProgramNode {
        statements = List.copyOf(statements);
    }

    @Override
    public List<AstNode> children() {
        return statements;
    }
}

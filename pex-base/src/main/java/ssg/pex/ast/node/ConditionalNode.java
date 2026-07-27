package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ConditionalNode(AstNode condition, AstNode thenBranch, AstNode elseBranch, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public ConditionalNode(AstNode condition, AstNode thenBranch, AstNode elseBranch) {
        this(condition, thenBranch, elseBranch, SourceLocation.UNKNOWN, Map.of());
    }

    public ConditionalNode(AstNode condition, AstNode thenBranch) {
        this(condition, thenBranch, null, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        if (elseBranch == null) {
            return List.of(condition, thenBranch);
        }
        return List.of(condition, thenBranch, elseBranch);
    }
}

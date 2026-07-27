package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record FunctionCallNode(String name, List<AstNode> arguments, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public FunctionCallNode(String name, List<AstNode> arguments) {
        this(name, List.copyOf(arguments), SourceLocation.UNKNOWN, Map.of());
    }

    public FunctionCallNode {
        arguments = List.copyOf(arguments);
    }

    @Override
    public List<AstNode> children() {
        return arguments;
    }
}

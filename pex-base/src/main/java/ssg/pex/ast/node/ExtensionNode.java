package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public record ExtensionNode(String extensionType, Object wrappedNode, SourceLocation location, Map<String, Object> metadata) implements AstNode {

    public ExtensionNode(String extensionType, Object wrappedNode) {
        this(extensionType, wrappedNode, SourceLocation.UNKNOWN, Map.of());
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}

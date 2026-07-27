package ssg.pex.ast.node;

import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.Map;

public sealed interface AstNode permits
        ProgramNode,
        LiteralNode,
        IdentifierNode,
        BinaryOpNode,
        UnaryOpNode,
        FunctionCallNode,
        FunctionDefNode,
        ParameterNode,
        AssignmentNode,
        BlockNode,
        IndexAccessNode,
        ConditionalNode,
        LoopNode,
        ReturnNode,
        ExtensionNode {

    SourceLocation location();

    Map<String, Object> metadata();

    List<AstNode> children();
}

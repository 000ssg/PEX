package ssg.pex.ast.visitor;

import ssg.pex.ast.node.*;
import ssg.pex.result.Result;

public interface AstVisitor<T> {

    Result<T> visitProgram(ProgramNode node);

    Result<T> visitIntLiteral(IntLiteral node);

    Result<T> visitFloatLiteral(FloatLiteral node);

    Result<T> visitStringLiteral(StringLiteral node);

    Result<T> visitBoolLiteral(BoolLiteral node);

    Result<T> visitNullLiteral(NullLiteral node);

    Result<T> visitIdentifier(IdentifierNode node);

    Result<T> visitBinaryOp(BinaryOpNode node);

    Result<T> visitUnaryOp(UnaryOpNode node);

    Result<T> visitFunctionCall(FunctionCallNode node);

    Result<T> visitFunctionDef(FunctionDefNode node);

    Result<T> visitParameter(ParameterNode node);

    Result<T> visitAssignment(AssignmentNode node);

    Result<T> visitBlock(BlockNode node);

    Result<T> visitIndexAccess(IndexAccessNode node);

    Result<T> visitConditional(ConditionalNode node);

    Result<T> visitLoop(LoopNode node);

    Result<T> visitReturn(ReturnNode node);

    Result<T> visitExtension(ExtensionNode node);
}

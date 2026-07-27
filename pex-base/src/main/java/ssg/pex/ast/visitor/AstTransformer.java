package ssg.pex.ast.visitor;

import ssg.pex.ast.node.*;
import ssg.pex.result.Result;

import java.util.ArrayList;
import java.util.List;

public abstract class AstTransformer implements AstVisitor<AstNode> {

    public Result<AstNode> transform(AstNode node) {
        return switch (node) {
            case ProgramNode n -> visitProgram(n);
            case IntLiteral n -> visitIntLiteral(n);
            case FloatLiteral n -> visitFloatLiteral(n);
            case StringLiteral n -> visitStringLiteral(n);
            case BoolLiteral n -> visitBoolLiteral(n);
            case NullLiteral n -> visitNullLiteral(n);
            case IdentifierNode n -> visitIdentifier(n);
            case BinaryOpNode n -> visitBinaryOp(n);
            case UnaryOpNode n -> visitUnaryOp(n);
            case FunctionCallNode n -> visitFunctionCall(n);
            case FunctionDefNode n -> visitFunctionDef(n);
            case ParameterNode n -> visitParameter(n);
            case AssignmentNode n -> visitAssignment(n);
            case BlockNode n -> visitBlock(n);
            case IndexAccessNode n -> visitIndexAccess(n);
            case ConditionalNode n -> visitConditional(n);
            case LoopNode n -> visitLoop(n);
            case ReturnNode n -> visitReturn(n);
            case ExtensionNode n -> visitExtension(n);
        };
    }

    @Override
    public Result<AstNode> visitProgram(ProgramNode node) {
        return transformChildren(node.statements()).map(stmts ->
                new ProgramNode(stmts, node.location(), node.metadata()));
    }

    @Override
    public Result<AstNode> visitIntLiteral(IntLiteral node) {
        return Result.success(node);
    }

    @Override
    public Result<AstNode> visitFloatLiteral(FloatLiteral node) {
        return Result.success(node);
    }

    @Override
    public Result<AstNode> visitStringLiteral(StringLiteral node) {
        return Result.success(node);
    }

    @Override
    public Result<AstNode> visitBoolLiteral(BoolLiteral node) {
        return Result.success(node);
    }

    @Override
    public Result<AstNode> visitNullLiteral(NullLiteral node) {
        return Result.success(node);
    }

    @Override
    public Result<AstNode> visitIdentifier(IdentifierNode node) {
        return Result.success(node);
    }

    @Override
    public Result<AstNode> visitBinaryOp(BinaryOpNode node) {
        return transform(node.left()).flatMap(left ->
                transform(node.right()).map(right ->
                        new BinaryOpNode(left, node.op(), right, node.location(), node.metadata())));
    }

    @Override
    public Result<AstNode> visitUnaryOp(UnaryOpNode node) {
        return transform(node.operand()).map(operand ->
                new UnaryOpNode(node.op(), operand, node.prefix(), node.location(), node.metadata()));
    }

    @Override
    public Result<AstNode> visitFunctionCall(FunctionCallNode node) {
        return transformChildren(node.arguments()).map(args ->
                new FunctionCallNode(node.name(), args, node.location(), node.metadata()));
    }

    @Override
    public Result<AstNode> visitFunctionDef(FunctionDefNode node) {
        return transformParameters(node.params()).flatMap(params ->
                transform(node.body()).map(body ->
                        new FunctionDefNode(node.name(), params, body, node.location(), node.metadata())));
    }

    @Override
    public Result<AstNode> visitParameter(ParameterNode node) {
        return Result.success(node);
    }

    @Override
    public Result<AstNode> visitAssignment(AssignmentNode node) {
        return transform(node.target()).flatMap(target ->
                transform(node.value()).map(value ->
                        new AssignmentNode(target, value, node.location(), node.metadata())));
    }

    @Override
    public Result<AstNode> visitBlock(BlockNode node) {
        return transformChildren(node.statements()).map(stmts ->
                new BlockNode(stmts, node.location(), node.metadata()));
    }

    @Override
    public Result<AstNode> visitIndexAccess(IndexAccessNode node) {
        return transform(node.target()).flatMap(target ->
                transform(node.index()).map(index ->
                        new IndexAccessNode(target, index, node.location(), node.metadata())));
    }

    @Override
    public Result<AstNode> visitConditional(ConditionalNode node) {
        return transform(node.condition()).flatMap(condition ->
                transform(node.thenBranch()).flatMap(thenBranch -> {
                    if (node.elseBranch() == null) {
                        return Result.success(new ConditionalNode(condition, thenBranch, null, node.location(), node.metadata()));
                    }
                    return transform(node.elseBranch()).map(elseBranch ->
                            new ConditionalNode(condition, thenBranch, elseBranch, node.location(), node.metadata()));
                }));
    }

    @Override
    public Result<AstNode> visitLoop(LoopNode node) {
        return transform(node.condition()).flatMap(condition ->
                transform(node.body()).map(body ->
                        new LoopNode(node.kind(), condition, body, node.location(), node.metadata())));
    }

    @Override
    public Result<AstNode> visitReturn(ReturnNode node) {
        if (node.value() == null) {
            return Result.success(node);
        }
        return transform(node.value()).map(value ->
                new ReturnNode(value, node.location(), node.metadata()));
    }

    @Override
    public Result<AstNode> visitExtension(ExtensionNode node) {
        return Result.success(node);
    }

    protected Result<List<AstNode>> transformChildren(List<AstNode> children) {
        var results = new ArrayList<AstNode>(children.size());
        for (var child : children) {
            var result = transform(child);
            if (result.isFailure()) {
                return Result.failure(result.error());
            }
            results.add(result.value());
        }
        return Result.success(List.copyOf(results));
    }

    protected Result<List<ParameterNode>> transformParameters(List<ParameterNode> params) {
        var results = new ArrayList<ParameterNode>(params.size());
        for (var param : params) {
            var result = visitParameter(param);
            if (result.isFailure()) {
                return Result.failure(result.error());
            }
            if (result.value() instanceof ParameterNode p) {
                results.add(p);
            } else {
                return Result.failure("TRANSFORM_ERROR", "Parameter transformation must return a ParameterNode");
            }
        }
        return Result.success(List.copyOf(results));
    }
}

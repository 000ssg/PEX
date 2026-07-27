package ssg.pex.converter.lang;

import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.Converter;
import ssg.pex.converter.mapping.NamingMapper;
import ssg.pex.converter.mapping.OperatorMapper;
import ssg.pex.converter.mapping.TypeMapper;
import ssg.pex.result.Result;

import java.util.List;

public abstract class AbstractConverter implements Converter {

    protected final ConversionConfig config;
    protected final TypeMapper typeMapper;
    protected final OperatorMapper operatorMapper;
    protected final StringBuilder sb;
    protected int indentLevel;

    protected AbstractConverter(ConversionConfig config) {
        this.config = config;
        this.typeMapper = createTypeMapper();
        this.operatorMapper = createOperatorMapper();
        this.sb = new StringBuilder();
        this.indentLevel = 0;
    }

    protected abstract TypeMapper createTypeMapper();

    protected abstract OperatorMapper createOperatorMapper();

    protected abstract String statementTerminator();

    protected abstract String blockOpen();

    protected abstract String blockClose();

    protected abstract void convertFunctionDef(FunctionDefNode node);

    protected abstract void convertVariableDeclaration(String name, String valueExpr);

    @Override
    public Result<String> convert(AstNode root) {
        try {
            sb.setLength(0);
            indentLevel = 0;
            convertNode(root);
            return Result.success(sb.toString());
        } catch (Exception e) {
            return Result.failure("CONVERSION_ERROR", e.getMessage(), e);
        }
    }

    protected void convertNode(AstNode node) {
        switch (node) {
            case ProgramNode n -> convertProgram(n);
            case IntLiteral n -> convertIntLiteral(n);
            case FloatLiteral n -> convertFloatLiteral(n);
            case StringLiteral n -> convertStringLiteral(n);
            case BoolLiteral n -> convertBoolLiteral(n);
            case NullLiteral n -> convertNullLiteral(n);
            case IdentifierNode n -> convertIdentifier(n);
            case BinaryOpNode n -> convertBinaryOp(n);
            case UnaryOpNode n -> convertUnaryOp(n);
            case AssignmentNode n -> convertAssignment(n);
            case BlockNode n -> convertBlock(n);
            case ConditionalNode n -> convertConditional(n);
            case LoopNode n -> convertLoop(n);
            case FunctionDefNode n -> convertFunctionDef(n);
            case FunctionCallNode n -> convertFunctionCall(n);
            case ParameterNode n -> convertParameter(n);
            case ReturnNode n -> convertReturn(n);
            case IndexAccessNode n -> convertIndexAccess(n);
            case ExtensionNode n -> convertExtension(n);
        }
    }

    protected void convertProgram(ProgramNode node) {
        for (int i = 0; i < node.statements().size(); i++) {
            if (i > 0) newline();
            convertStatement(node.statements().get(i));
        }
    }

    protected void convertStatement(AstNode node) {
        indent();
        convertNode(node);
        if (needsTerminator(node)) {
            sb.append(statementTerminator());
        }
    }

    protected boolean needsTerminator(AstNode node) {
        return switch (node) {
            case BlockNode ignored -> false;
            case ConditionalNode ignored -> false;
            case LoopNode ignored -> false;
            case FunctionDefNode ignored -> false;
            default -> !statementTerminator().isEmpty();
        };
    }

    protected void convertIntLiteral(IntLiteral node) {
        sb.append(node.value());
    }

    protected void convertFloatLiteral(FloatLiteral node) {
        sb.append(node.value());
    }

    protected void convertStringLiteral(StringLiteral node) {
        sb.append('"').append(escapeString(node.value())).append('"');
    }

    protected void convertBoolLiteral(BoolLiteral node) {
        sb.append(node.value());
    }

    protected void convertNullLiteral(NullLiteral node) {
        sb.append("null");
    }

    protected void convertIdentifier(IdentifierNode node) {
        sb.append(convertName(node.name()));
    }

    protected void convertBinaryOp(BinaryOpNode node) {
        sb.append('(');
        convertNode(node.left());
        sb.append(' ').append(operatorMapper.mapOperator(node.op())).append(' ');
        convertNode(node.right());
        sb.append(')');
    }

    protected void convertUnaryOp(UnaryOpNode node) {
        if (node.prefix()) {
            sb.append(operatorMapper.mapOperator(node.op()));
            convertNode(node.operand());
        } else {
            convertNode(node.operand());
            sb.append(operatorMapper.mapOperator(node.op()));
        }
    }

    protected void convertAssignment(AssignmentNode node) {
        if (node.target() instanceof IdentifierNode id) {
            String valueExpr = expressionToString(node.value());
            convertVariableDeclaration(convertName(id.name()), valueExpr);
        } else {
            convertNode(node.target());
            sb.append(" = ");
            convertNode(node.value());
        }
    }

    protected void convertBlock(BlockNode node) {
        sb.append(blockOpen());
        newline();
        indentLevel++;
        for (int i = 0; i < node.statements().size(); i++) {
            if (i > 0) newline();
            convertStatement(node.statements().get(i));
        }
        indentLevel--;
        newline();
        indent();
        sb.append(blockClose());
    }

    protected void convertConditional(ConditionalNode node) {
        sb.append("if (");
        convertNode(node.condition());
        sb.append(") ");
        convertBody(node.thenBranch());
        if (node.elseBranch() != null) {
            sb.append(" else ");
            convertBody(node.elseBranch());
        }
    }

    protected void convertLoop(LoopNode node) {
        switch (node.kind()) {
            case WHILE -> {
                sb.append("while (");
                convertNode(node.condition());
                sb.append(") ");
                convertBody(node.body());
            }
            case DO_WHILE -> {
                sb.append("do ");
                convertBody(node.body());
                sb.append(" while (");
                convertNode(node.condition());
                sb.append(")").append(statementTerminator());
            }
            case FOR -> {
                sb.append("for (");
                convertNode(node.condition());
                sb.append(") ");
                convertBody(node.body());
            }
        }
    }

    protected void convertFunctionCall(FunctionCallNode node) {
        sb.append(convertName(node.name())).append('(');
        convertArgList(node.arguments());
        sb.append(')');
    }

    protected void convertParameter(ParameterNode node) {
        sb.append(convertName(node.name()));
    }

    protected void convertReturn(ReturnNode node) {
        sb.append("return");
        if (node.value() != null) {
            sb.append(' ');
            convertNode(node.value());
        }
    }

    protected void convertIndexAccess(IndexAccessNode node) {
        convertNode(node.target());
        sb.append('[');
        convertNode(node.index());
        sb.append(']');
    }

    protected void convertExtension(ExtensionNode node) {
        sb.append("/* extension: ").append(node.extensionType()).append(" */");
    }

    protected void convertBody(AstNode node) {
        if (node instanceof BlockNode block) {
            convertBlock(block);
        } else {
            sb.append(blockOpen());
            newline();
            indentLevel++;
            convertStatement(node);
            indentLevel--;
            newline();
            indent();
            sb.append(blockClose());
        }
    }

    protected void convertArgList(List<AstNode> args) {
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            convertNode(args.get(i));
        }
    }

    protected void indent() {
        sb.append(config.indentation().repeat(indentLevel));
    }

    protected void newline() {
        sb.append(config.lineEnding());
    }

    protected String convertName(String name) {
        return NamingMapper.convert(name, config.namingConvention());
    }

    protected String expressionToString(AstNode node) {
        int start = sb.length();
        convertNode(node);
        String result = sb.substring(start);
        sb.setLength(start);
        return result;
    }

    protected static String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

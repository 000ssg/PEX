package ssg.pex.converter.lang;

import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.mapping.OperatorMapper;
import ssg.pex.converter.mapping.TypeMapper;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.type.PexType;

public class BasicConverter extends AbstractConverter {

    public BasicConverter(ConversionConfig config) {
        super(config);
    }

    @Override
    public TargetLanguage targetLanguage() {
        return TargetLanguage.BASIC;
    }

    @Override
    protected TypeMapper createTypeMapper() {
        return new BasicTypeMapper();
    }

    @Override
    protected OperatorMapper createOperatorMapper() {
        return new BasicOperatorMapper();
    }

    @Override
    protected String statementTerminator() {
        return "";
    }

    @Override
    protected String blockOpen() {
        return "";
    }

    @Override
    protected String blockClose() {
        return "";
    }

    @Override
    protected void convertFunctionDef(FunctionDefNode node) {
        sb.append("FUNCTION ").append(convertName(node.name()).toUpperCase()).append('(');
        for (int i = 0; i < node.params().size(); i++) {
            if (i > 0) sb.append(", ");
            ParameterNode param = node.params().get(i);
            sb.append(convertName(param.name()));
        }
        sb.append(')');
        newline();
        indentLevel++;
        if (node.body() instanceof BlockNode block) {
            for (int i = 0; i < block.statements().size(); i++) {
                if (i > 0) newline();
                convertStatement(block.statements().get(i));
            }
        } else {
            convertStatement(node.body());
        }
        indentLevel--;
        newline();
        indent();
        sb.append("END FUNCTION");
    }

    @Override
    protected void convertVariableDeclaration(String name, String valueExpr) {
        sb.append("LET ").append(name).append(" = ").append(valueExpr);
    }

    @Override
    protected void convertConditional(ConditionalNode node) {
        sb.append("IF ");
        convertNode(node.condition());
        sb.append(" THEN");
        newline();
        indentLevel++;
        if (node.thenBranch() instanceof BlockNode block) {
            for (int i = 0; i < block.statements().size(); i++) {
                if (i > 0) newline();
                convertStatement(block.statements().get(i));
            }
        } else {
            convertStatement(node.thenBranch());
        }
        indentLevel--;
        if (node.elseBranch() != null) {
            newline();
            indent();
            sb.append("ELSE");
            newline();
            indentLevel++;
            if (node.elseBranch() instanceof BlockNode block) {
                for (int i = 0; i < block.statements().size(); i++) {
                    if (i > 0) newline();
                    convertStatement(block.statements().get(i));
                }
            } else {
                convertStatement(node.elseBranch());
            }
            indentLevel--;
        }
        newline();
        indent();
        sb.append("END IF");
    }

    @Override
    protected void convertLoop(LoopNode node) {
        switch (node.kind()) {
            case WHILE -> {
                sb.append("WHILE ");
                convertNode(node.condition());
                newline();
                indentLevel++;
                if (node.body() instanceof BlockNode block) {
                    for (int i = 0; i < block.statements().size(); i++) {
                        if (i > 0) newline();
                        convertStatement(block.statements().get(i));
                    }
                } else {
                    convertStatement(node.body());
                }
                indentLevel--;
                newline();
                indent();
                sb.append("WEND");
            }
            case DO_WHILE -> {
                sb.append("DO");
                newline();
                indentLevel++;
                if (node.body() instanceof BlockNode block) {
                    for (int i = 0; i < block.statements().size(); i++) {
                        if (i > 0) newline();
                        convertStatement(block.statements().get(i));
                    }
                } else {
                    convertStatement(node.body());
                }
                indentLevel--;
                newline();
                indent();
                sb.append("LOOP WHILE ");
                convertNode(node.condition());
            }
            case FOR -> {
                sb.append("FOR ");
                convertNode(node.condition());
                newline();
                indentLevel++;
                if (node.body() instanceof BlockNode block) {
                    for (int i = 0; i < block.statements().size(); i++) {
                        if (i > 0) newline();
                        convertStatement(block.statements().get(i));
                    }
                } else {
                    convertStatement(node.body());
                }
                indentLevel--;
                newline();
                indent();
                sb.append("NEXT");
            }
        }
    }

    @Override
    protected void convertReturn(ReturnNode node) {
        sb.append("RETURN");
        if (node.value() != null) {
            sb.append(' ');
            convertNode(node.value());
        }
    }

    @Override
    protected void convertBlock(BlockNode node) {
        newline();
        indentLevel++;
        for (int i = 0; i < node.statements().size(); i++) {
            if (i > 0) newline();
            convertStatement(node.statements().get(i));
        }
        indentLevel--;
    }

    @Override
    protected void convertNullLiteral(NullLiteral node) {
        sb.append("NOTHING");
    }

    @Override
    protected void convertBoolLiteral(BoolLiteral node) {
        sb.append(node.value() ? "TRUE" : "FALSE");
    }

    static class BasicTypeMapper extends TypeMapper {
        @Override
        public String mapType(PexType type) {
            return switch (type) {
                case INT, LONG -> "INTEGER";
                case FLOAT, DOUBLE -> "DOUBLE";
                case STRING -> "STRING";
                case BOOL -> "BOOLEAN";
                case NULL, VOID -> "VOID";
                case ARRAY -> "ARRAY";
                case MAP -> "OBJECT";
                case FUNCTION -> "FUNCTION";
            };
        }
    }

    static class BasicOperatorMapper extends OperatorMapper {
        @Override
        public String mapOperator(Operator op) {
            return switch (op) {
                case AND -> "AND";
                case OR -> "OR";
                case NOT -> "NOT ";
                case EQ -> "=";
                case NEQ -> "<>";
                default -> op.symbol();
            };
        }
    }

    public static class Factory implements ConverterFactory {
        @Override
        public TargetLanguage target() {
            return TargetLanguage.BASIC;
        }

        @Override
        public String description() {
            return "Converts PEX AST to BASIC source code";
        }

        @Override
        public BasicConverter create(ConversionConfig config) {
            return new BasicConverter(config);
        }
    }
}

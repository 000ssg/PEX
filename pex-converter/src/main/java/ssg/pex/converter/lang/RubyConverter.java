package ssg.pex.converter.lang;

import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.mapping.NamingMapper;
import ssg.pex.converter.mapping.OperatorMapper;
import ssg.pex.converter.mapping.TypeMapper;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.type.PexType;

public class RubyConverter extends AbstractConverter {

    public RubyConverter(ConversionConfig config) {
        super(config);
    }

    @Override
    public TargetLanguage targetLanguage() {
        return TargetLanguage.RUBY;
    }

    @Override
    protected TypeMapper createTypeMapper() {
        return new RubyTypeMapper();
    }

    @Override
    protected OperatorMapper createOperatorMapper() {
        return new RubyOperatorMapper();
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
        return "end";
    }

    @Override
    protected String convertName(String name) {
        return NamingMapper.toSnakeCase(name);
    }

    @Override
    protected void convertFunctionDef(FunctionDefNode node) {
        sb.append("def ").append(convertName(node.name())).append('(');
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
        sb.append("end");
    }

    @Override
    protected void convertVariableDeclaration(String name, String valueExpr) {
        sb.append(name).append(" = ").append(valueExpr);
    }

    @Override
    protected void convertConditional(ConditionalNode node) {
        sb.append("if ");
        convertNode(node.condition());
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
            sb.append("else");
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
        sb.append("end");
    }

    @Override
    protected void convertLoop(LoopNode node) {
        switch (node.kind()) {
            case WHILE -> {
                sb.append("while ");
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
                sb.append("end");
            }
            case DO_WHILE -> {
                sb.append("begin");
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
                sb.append("end while ");
                convertNode(node.condition());
            }
            case FOR -> {
                sb.append("for ");
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
                sb.append("end");
            }
        }
    }

    @Override
    protected void convertBlock(BlockNode node) {
        // Ruby blocks in general context just output statements
        newline();
        indentLevel++;
        for (int i = 0; i < node.statements().size(); i++) {
            if (i > 0) newline();
            convertStatement(node.statements().get(i));
        }
        indentLevel--;
        newline();
        indent();
        sb.append("end");
    }

    @Override
    protected void convertNullLiteral(NullLiteral node) {
        sb.append("nil");
    }

    @Override
    protected void convertBoolLiteral(BoolLiteral node) {
        sb.append(node.value());
    }

    static class RubyTypeMapper extends TypeMapper {
        @Override
        public String mapType(PexType type) {
            // Ruby is dynamically typed
            return switch (type) {
                case INT, LONG -> "Integer";
                case FLOAT, DOUBLE -> "Float";
                case STRING -> "String";
                case BOOL -> "Boolean";
                case NULL, VOID -> "NilClass";
                case ARRAY -> "Array";
                case MAP -> "Hash";
                case FUNCTION -> "Proc";
            };
        }
    }

    static class RubyOperatorMapper extends OperatorMapper {
        @Override
        public String mapOperator(Operator op) {
            return switch (op) {
                case AND -> "&&";
                case OR -> "||";
                case NOT -> "!";
                default -> op.symbol();
            };
        }
    }

    public static class Factory implements ConverterFactory {
        @Override
        public TargetLanguage target() {
            return TargetLanguage.RUBY;
        }

        @Override
        public String description() {
            return "Converts PEX AST to Ruby source code";
        }

        @Override
        public RubyConverter create(ConversionConfig config) {
            return new RubyConverter(config);
        }
    }
}

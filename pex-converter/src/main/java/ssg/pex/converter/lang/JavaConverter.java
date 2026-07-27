package ssg.pex.converter.lang;

import ssg.pex.ast.node.FunctionDefNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.ParameterNode;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.mapping.OperatorMapper;
import ssg.pex.converter.mapping.TypeMapper;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.type.PexType;

public class JavaConverter extends AbstractConverter {

    public JavaConverter(ConversionConfig config) {
        super(config);
    }

    @Override
    public TargetLanguage targetLanguage() {
        return TargetLanguage.JAVA;
    }

    @Override
    protected TypeMapper createTypeMapper() {
        return new JavaTypeMapper();
    }

    @Override
    protected OperatorMapper createOperatorMapper() {
        return new JavaOperatorMapper();
    }

    @Override
    protected String statementTerminator() {
        return ";";
    }

    @Override
    protected String blockOpen() {
        return "{";
    }

    @Override
    protected String blockClose() {
        return "}";
    }

    @Override
    protected void convertFunctionDef(FunctionDefNode node) {
        sb.append("Object ").append(convertName(node.name())).append('(');
        for (int i = 0; i < node.params().size(); i++) {
            if (i > 0) sb.append(", ");
            ParameterNode param = node.params().get(i);
            sb.append("Object ").append(convertName(param.name()));
        }
        sb.append(") ");
        convertBody(node.body());
    }

    @Override
    protected void convertVariableDeclaration(String name, String valueExpr) {
        sb.append("var ").append(name).append(" = ").append(valueExpr);
    }

    static class JavaTypeMapper extends TypeMapper {
        @Override
        public String mapType(PexType type) {
            return switch (type) {
                case INT -> "int";
                case LONG -> "long";
                case FLOAT -> "float";
                case DOUBLE -> "double";
                case STRING -> "String";
                case BOOL -> "boolean";
                case NULL, VOID -> "void";
                case ARRAY -> "Object[]";
                case MAP -> "Map<String, Object>";
                case FUNCTION -> "Object";
            };
        }
    }

    static class JavaOperatorMapper extends OperatorMapper {
        @Override
        public String mapOperator(Operator op) {
            return op.symbol();
        }
    }

    public static class Factory implements ConverterFactory {
        @Override
        public TargetLanguage target() {
            return TargetLanguage.JAVA;
        }

        @Override
        public String description() {
            return "Converts PEX AST to Java source code";
        }

        @Override
        public JavaConverter create(ConversionConfig config) {
            return new JavaConverter(config);
        }
    }
}

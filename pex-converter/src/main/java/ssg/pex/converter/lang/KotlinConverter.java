package ssg.pex.converter.lang;

import ssg.pex.ast.node.*;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.mapping.OperatorMapper;
import ssg.pex.converter.mapping.TypeMapper;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.type.PexType;

public class KotlinConverter extends AbstractConverter {

    public KotlinConverter(ConversionConfig config) {
        super(config);
    }

    @Override
    public TargetLanguage targetLanguage() {
        return TargetLanguage.KOTLIN;
    }

    @Override
    protected TypeMapper createTypeMapper() {
        return new KotlinTypeMapper();
    }

    @Override
    protected OperatorMapper createOperatorMapper() {
        return new KotlinOperatorMapper();
    }

    @Override
    protected String statementTerminator() {
        return "";
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
        sb.append("fun ").append(convertName(node.name())).append('(');
        for (int i = 0; i < node.params().size(); i++) {
            if (i > 0) sb.append(", ");
            ParameterNode param = node.params().get(i);
            sb.append(convertName(param.name())).append(": Any");
        }
        sb.append("): Any ");
        convertBody(node.body());
    }

    @Override
    protected void convertVariableDeclaration(String name, String valueExpr) {
        sb.append("var ").append(name).append(" = ").append(valueExpr);
    }

    @Override
    protected void convertNullLiteral(NullLiteral node) {
        sb.append("null");
    }

    static class KotlinTypeMapper extends TypeMapper {
        @Override
        public String mapType(PexType type) {
            return switch (type) {
                case INT -> "Int";
                case LONG -> "Long";
                case FLOAT -> "Float";
                case DOUBLE -> "Double";
                case STRING -> "String";
                case BOOL -> "Boolean";
                case NULL, VOID -> "Unit";
                case ARRAY -> "Array<Any>";
                case MAP -> "Map<String, Any>";
                case FUNCTION -> "Any";
            };
        }
    }

    static class KotlinOperatorMapper extends OperatorMapper {
        @Override
        public String mapOperator(Operator op) {
            return op.symbol();
        }
    }

    public static class Factory implements ConverterFactory {
        @Override
        public TargetLanguage target() {
            return TargetLanguage.KOTLIN;
        }

        @Override
        public String description() {
            return "Converts PEX AST to Kotlin source code";
        }

        @Override
        public KotlinConverter create(ConversionConfig config) {
            return new KotlinConverter(config);
        }
    }
}

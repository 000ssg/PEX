package ssg.pex.converter.lang;

import ssg.pex.ast.node.BoolLiteral;
import ssg.pex.ast.node.FunctionDefNode;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.ParameterNode;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.mapping.NamingMapper;
import ssg.pex.converter.mapping.OperatorMapper;
import ssg.pex.converter.mapping.TypeMapper;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.type.PexType;

public class CSharpConverter extends AbstractConverter {

    public CSharpConverter(ConversionConfig config) {
        super(config);
    }

    @Override
    public TargetLanguage targetLanguage() {
        return TargetLanguage.CSHARP;
    }

    @Override
    protected TypeMapper createTypeMapper() {
        return new CSharpTypeMapper();
    }

    @Override
    protected OperatorMapper createOperatorMapper() {
        return new CSharpOperatorMapper();
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
        String methodName = NamingMapper.toPascalCase(node.name());
        sb.append("object ").append(methodName).append('(');
        for (int i = 0; i < node.params().size(); i++) {
            if (i > 0) sb.append(", ");
            ParameterNode param = node.params().get(i);
            sb.append("object ").append(convertName(param.name()));
        }
        sb.append(") ");
        convertBody(node.body());
    }

    @Override
    protected void convertVariableDeclaration(String name, String valueExpr) {
        sb.append("var ").append(name).append(" = ").append(valueExpr);
    }

    @Override
    protected void convertNullLiteral(ssg.pex.ast.node.NullLiteral node) {
        sb.append("null");
    }

    @Override
    protected void convertBoolLiteral(BoolLiteral node) {
        sb.append(node.value() ? "true" : "false");
    }

    static class CSharpTypeMapper extends TypeMapper {
        @Override
        public String mapType(PexType type) {
            return switch (type) {
                case INT -> "int";
                case LONG -> "long";
                case FLOAT -> "float";
                case DOUBLE -> "double";
                case STRING -> "string";
                case BOOL -> "bool";
                case NULL, VOID -> "void";
                case ARRAY -> "object[]";
                case MAP -> "Dictionary<string, object>";
                case FUNCTION -> "object";
            };
        }
    }

    static class CSharpOperatorMapper extends OperatorMapper {
        @Override
        public String mapOperator(Operator op) {
            return op.symbol();
        }
    }

    public static class Factory implements ConverterFactory {
        @Override
        public TargetLanguage target() {
            return TargetLanguage.CSHARP;
        }

        @Override
        public String description() {
            return "Converts PEX AST to C# source code";
        }

        @Override
        public CSharpConverter create(ConversionConfig config) {
            return new CSharpConverter(config);
        }
    }
}

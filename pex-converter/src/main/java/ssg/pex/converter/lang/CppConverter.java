package ssg.pex.converter.lang;

import ssg.pex.ast.node.FunctionDefNode;
import ssg.pex.ast.node.NullLiteral;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.ParameterNode;
import ssg.pex.ast.node.StringLiteral;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.mapping.OperatorMapper;
import ssg.pex.converter.mapping.TypeMapper;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.type.PexType;

public class CppConverter extends AbstractConverter {

    public CppConverter(ConversionConfig config) {
        super(config);
    }

    @Override
    public TargetLanguage targetLanguage() {
        return TargetLanguage.CPP;
    }

    @Override
    protected TypeMapper createTypeMapper() {
        return new CppTypeMapper();
    }

    @Override
    protected OperatorMapper createOperatorMapper() {
        return new CppOperatorMapper();
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
        sb.append("auto ").append(convertName(node.name())).append('(');
        for (int i = 0; i < node.params().size(); i++) {
            if (i > 0) sb.append(", ");
            ParameterNode param = node.params().get(i);
            sb.append("auto ").append(convertName(param.name()));
        }
        sb.append(") -> auto ");
        convertBody(node.body());
    }

    @Override
    protected void convertVariableDeclaration(String name, String valueExpr) {
        sb.append("auto ").append(name).append(" = ").append(valueExpr);
    }

    @Override
    protected void convertStringLiteral(StringLiteral node) {
        sb.append("std::string(\"").append(escapeString(node.value())).append("\")");
    }

    @Override
    protected void convertNullLiteral(NullLiteral node) {
        sb.append("nullptr");
    }

    static class CppTypeMapper extends TypeMapper {
        @Override
        public String mapType(PexType type) {
            return switch (type) {
                case INT -> "int";
                case LONG -> "long long";
                case FLOAT -> "float";
                case DOUBLE -> "double";
                case STRING -> "std::string";
                case BOOL -> "bool";
                case NULL, VOID -> "void";
                case ARRAY -> "std::vector<std::any>";
                case MAP -> "std::unordered_map<std::string, std::any>";
                case FUNCTION -> "std::function<std::any(std::any)>";
            };
        }
    }

    static class CppOperatorMapper extends OperatorMapper {
        @Override
        public String mapOperator(Operator op) {
            return op.symbol();
        }
    }

    public static class Factory implements ConverterFactory {
        @Override
        public TargetLanguage target() {
            return TargetLanguage.CPP;
        }

        @Override
        public String description() {
            return "Converts PEX AST to C++ source code";
        }

        @Override
        public CppConverter create(ConversionConfig config) {
            return new CppConverter(config);
        }
    }
}

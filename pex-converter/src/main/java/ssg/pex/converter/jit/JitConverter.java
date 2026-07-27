package ssg.pex.converter.jit;

import ssg.pex.ast.node.AstNode;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.Converter;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.lang.JavaConverter;
import ssg.pex.converter.spi.ConverterFactory;
import ssg.pex.result.Result;

public class JitConverter implements Converter {

    private final ConversionConfig config;
    private final JavaConverter javaConverter;

    public JitConverter(ConversionConfig config) {
        this.config = config;
        this.javaConverter = new JavaConverter(config);
    }

    @Override
    public TargetLanguage targetLanguage() {
        return TargetLanguage.JIT;
    }

    @Override
    public Result<String> convert(AstNode root) {
        String className = "JitExpr_" + Integer.toHexString(root.hashCode());
        String fqn = "ssg.pex.jit.generated." + className;

        Result<String> bodyResult = javaConverter.convert(root);
        if (bodyResult.isFailure()) {
            return bodyResult;
        }

        String body = bodyResult.value();
        String indentation = config.indentation();

        var sb = new StringBuilder();
        sb.append("package ssg.pex.jit.generated;").append(config.lineEnding());
        sb.append(config.lineEnding());
        sb.append("import ssg.pex.converter.jit.JitExecutable;").append(config.lineEnding());
        sb.append("import java.util.Map;").append(config.lineEnding());
        sb.append(config.lineEnding());
        sb.append("public class ").append(className).append(" implements JitExecutable {").append(config.lineEnding());
        sb.append(config.lineEnding());
        sb.append(indentation).append("@Override").append(config.lineEnding());
        sb.append(indentation).append("public Object execute(Map<String, Object> context) {").append(config.lineEnding());

        // Indent the body
        String[] lines = body.split("\\n");
        for (String line : lines) {
            if (!line.isEmpty()) {
                sb.append(indentation).append(indentation).append(line);
            }
            sb.append(config.lineEnding());
        }

        sb.append(indentation).append("}").append(config.lineEnding());
        sb.append("}").append(config.lineEnding());

        return Result.success(sb.toString());
    }

    public static class Factory implements ConverterFactory {
        @Override
        public TargetLanguage target() {
            return TargetLanguage.JIT;
        }

        @Override
        public String description() {
            return "Generates JIT-compilable Java class implementing JitExecutable";
        }

        @Override
        public JitConverter create(ConversionConfig config) {
            return new JitConverter(config);
        }
    }
}

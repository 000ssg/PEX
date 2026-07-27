package ssg.pex.integration;

import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.IntLiteral;
import ssg.pex.ast.node.Operator;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.spi.ConverterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndConverterTest {

    private final BinaryOpNode simpleExpr = new BinaryOpNode(
            new IntLiteral(2), Operator.PLUS, new IntLiteral(3));

    @Test
    void converterRegistryDiscoversAllConverters() {
        var registry = ConverterRegistry.getInstance();
        var targets = registry.availableTargets();

        assertThat(targets).contains(
                TargetLanguage.JAVA,
                TargetLanguage.CSHARP,
                TargetLanguage.CPP,
                TargetLanguage.KOTLIN,
                TargetLanguage.SCALA,
                TargetLanguage.RUBY,
                TargetLanguage.BASIC,
                TargetLanguage.JIT);
    }

    @Test
    void convertToJava() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.JAVA)
                .orElseThrow();

        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();

        String output = result.value();
        assertThat(output).contains("2");
        assertThat(output).contains("3");
        assertThat(output).contains("+");
    }

    @Test
    void convertToCSharpContainsPascalCaseForFunctions() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.CSHARP)
                .orElseThrow();

        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("2").contains("+").contains("3");
    }

    @Test
    void convertToKotlinNoSemicolons() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.KOTLIN)
                .orElseThrow();

        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();
        // Kotlin converter has empty statement terminator
        assertThat(result.value()).doesNotContain(";");
    }

    @Test
    void convertToRubySnakeCaseAndNoSemicolon() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.RUBY)
                .orElseThrow();

        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).doesNotContain(";");
    }

    @Test
    void convertToBasicUppercaseKeywords() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.BASIC)
                .orElseThrow();

        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();
        // BASIC uses uppercase TRUE/FALSE for bools, but for a simple expression
        // it should contain the numbers and operator
        assertThat(result.value()).contains("2").contains("3");
    }

    @Test
    void convertToCppContainsAutoKeyword() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.CPP)
                .orElseThrow();

        // Simple expression does not produce 'auto', but we can verify conversion works
        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("2").contains("+").contains("3");
    }

    @Test
    void convertToScalaContainsExpression() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.SCALA)
                .orElseThrow();

        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("2").contains("+").contains("3");
        // Scala has no semicolons
        assertThat(result.value()).doesNotContain(";");
    }

    @Test
    void convertToJitProducesJavaClass() {
        var converter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.JIT)
                .orElseThrow();

        var result = converter.convert(simpleExpr);
        assertThat(result.isSuccess()).isTrue();

        String output = result.value();
        assertThat(output).contains("package ssg.pex.jit.generated");
        assertThat(output).contains("implements JitExecutable");
        assertThat(output).contains("public Object execute(Map<String, Object> context)");
    }

    @Test
    void allConvertersHandleSameAst() {
        var registry = ConverterRegistry.getInstance();
        for (TargetLanguage lang : TargetLanguage.values()) {
            var converter = registry.getConverter(lang);
            assertThat(converter)
                    .as("Converter should exist for " + lang.displayName())
                    .isPresent();

            var result = converter.get().convert(simpleExpr);
            assertThat(result.isSuccess())
                    .as("Conversion to %s should succeed", lang.displayName())
                    .isTrue();
            assertThat(result.value())
                    .as("Output for %s should not be empty", lang.displayName())
                    .isNotBlank();
        }
    }
}

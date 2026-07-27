package ssg.pex.tools.visualizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class PngExporterTest {

    private PngExporter exporter;
    private DiagramStyle style;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        exporter = new PngExporter();
        style = DiagramStyle.defaultStyle();
    }

    @Test
    void testExportSingleRuleCreatesFile() {
        var rule = new Rule("test", new Sequence(List.of(
                Terminal.literal("a"), new NonTerminal("b")
        )));
        var outputFile = tempDir.resolve("test.png");

        var result = exporter.exportRule(rule, style, outputFile);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.exists(outputFile)).isTrue();
        assertThat(outputFile.toFile().length()).isGreaterThan(0);
    }

    @Test
    void testExportSingleRuleProducesValidImage() throws Exception {
        var rule = new Rule("valid", Terminal.literal("hello"));
        var outputFile = tempDir.resolve("valid.png");

        var result = exporter.exportRule(rule, style, outputFile);

        assertThat(result.isSuccess()).isTrue();
        var image = ImageIO.read(outputFile.toFile());
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);
    }

    @Test
    void testExportGrammarCreatesOneFilePerRule() {
        var rules = new LinkedHashMap<String, Rule>();
        rules.put("first", new Rule("first", Terminal.literal("1")));
        rules.put("second", new Rule("second", Terminal.literal("2")));
        rules.put("third", new Rule("third", new NonTerminal("first")));
        var grammar = new Grammar("multi", rules, "first");

        var outputSubDir = tempDir.resolve("grammar-out");
        var result = exporter.exportGrammar(grammar, style, outputSubDir);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.exists(outputSubDir.resolve("first.png"))).isTrue();
        assertThat(Files.exists(outputSubDir.resolve("second.png"))).isTrue();
        assertThat(Files.exists(outputSubDir.resolve("third.png"))).isTrue();
    }

    @Test
    void testExportGrammarPngsAreValidImages() throws Exception {
        var rules = new LinkedHashMap<String, Rule>();
        rules.put("alpha", new Rule("alpha", Terminal.literal("a")));
        rules.put("beta", new Rule("beta", Terminal.literal("b")));
        var grammar = new Grammar("imgs", rules, "alpha");

        var outputSubDir = tempDir.resolve("img-out");
        exporter.exportGrammar(grammar, style, outputSubDir);

        for (var name : grammar.rules().keySet()) {
            var file = outputSubDir.resolve(name + ".png");
            var image = ImageIO.read(file.toFile());
            assertThat(image).isNotNull();
        }
    }

    @Test
    void testExportCreatesParentDirectories() {
        var rule = new Rule("deep", Terminal.literal("nested"));
        var outputFile = tempDir.resolve("a/b/c/deep.png");

        var result = exporter.exportRule(rule, style, outputFile);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.exists(outputFile)).isTrue();
    }
}

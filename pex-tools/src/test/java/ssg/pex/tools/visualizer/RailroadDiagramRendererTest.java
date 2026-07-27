package ssg.pex.tools.visualizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class RailroadDiagramRendererTest {

    private RailroadDiagramRenderer renderer;
    private DiagramStyle style;
    private Graphics2D graphics;

    @BeforeEach
    void setUp() {
        renderer = new RailroadDiagramRenderer();
        style = DiagramStyle.defaultStyle();
        var img = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_ARGB);
        graphics = img.createGraphics();
        graphics.setFont(style.textFont());
    }

    @Test
    void testMeasureTerminalLiteral() {
        var terminal = Terminal.literal("hello");
        var metrics = renderer.measure(terminal, graphics, style);

        assertThat(metrics.width()).isGreaterThan(0);
        assertThat(metrics.height()).isGreaterThan(0);
    }

    @Test
    void testRenderTerminalLiteral() {
        var terminal = Terminal.literal("hello");
        var metrics = renderer.measure(terminal, graphics, style);
        int railY = renderer.render(terminal, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
        assertThat(metrics.width()).isGreaterThan(0);
    }

    @Test
    void testMeasureTerminalRegex() {
        var terminal = Terminal.regex("[a-z]+");
        var metrics = renderer.measure(terminal, graphics, style);

        assertThat(metrics.width()).isGreaterThan(0);
        assertThat(metrics.height()).isGreaterThan(0);
    }

    @Test
    void testRenderTerminalRegex() {
        var terminal = Terminal.regex("[a-z]+");
        int railY = renderer.render(terminal, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
    }

    @Test
    void testMeasureNonTerminal() {
        var nt = new NonTerminal("expression");
        var metrics = renderer.measure(nt, graphics, style);

        assertThat(metrics.width()).isGreaterThan(0);
        assertThat(metrics.height()).isGreaterThan(0);
    }

    @Test
    void testRenderNonTerminal() {
        var nt = new NonTerminal("expression");
        int railY = renderer.render(nt, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
    }

    @Test
    void testMeasureSequenceWidthGreaterThanSumOfIndividual() {
        var t1 = Terminal.literal("a");
        var t2 = Terminal.literal("b");
        var t3 = Terminal.literal("c");
        var seq = new Sequence(List.of(t1, t2, t3));

        var m1 = renderer.measure(t1, graphics, style);
        var m2 = renderer.measure(t2, graphics, style);
        var m3 = renderer.measure(t3, graphics, style);
        var seqMetrics = renderer.measure(seq, graphics, style);

        // Sequence width must be greater than sum of individual widths (due to arrow gaps)
        int sumWidths = m1.width() + m2.width() + m3.width();
        assertThat(seqMetrics.width()).isGreaterThan(sumWidths);
    }

    @Test
    void testRenderSequence() {
        var seq = new Sequence(List.of(
                Terminal.literal("a"),
                new NonTerminal("expr"),
                Terminal.literal("b")
        ));
        int railY = renderer.render(seq, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
    }

    @Test
    void testMeasureAlternationHeightGreaterThanMaxIndividual() {
        var a1 = Terminal.literal("alpha");
        var a2 = Terminal.literal("beta");
        var a3 = Terminal.literal("gamma");
        var alt = new Alternation(List.of(a1, a2, a3));

        var m1 = renderer.measure(a1, graphics, style);
        var altMetrics = renderer.measure(alt, graphics, style);

        // Alternation height must be greater than any single alternative
        assertThat(altMetrics.height()).isGreaterThan(m1.height());
    }

    @Test
    void testRenderAlternation() {
        var alt = new Alternation(List.of(
                Terminal.literal("x"),
                Terminal.literal("y"),
                new NonTerminal("z")
        ));
        int railY = renderer.render(alt, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
    }

    @Test
    void testMeasureRepetitionZeroOrMore() {
        var rep = new Repetition(Terminal.literal("item"), RepetitionKind.ZERO_OR_MORE);
        var metrics = renderer.measure(rep, graphics, style);

        assertThat(metrics.width()).isGreaterThan(0);
        assertThat(metrics.height()).isGreaterThan(0);
    }

    @Test
    void testRenderRepetitionZeroOrMore() {
        var rep = new Repetition(Terminal.literal("item"), RepetitionKind.ZERO_OR_MORE);
        int railY = renderer.render(rep, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
    }

    @Test
    void testMeasureRepetitionOneOrMore() {
        var rep = new Repetition(new NonTerminal("digit"), RepetitionKind.ONE_OR_MORE);
        var metrics = renderer.measure(rep, graphics, style);

        assertThat(metrics.width()).isGreaterThan(0);
        assertThat(metrics.height()).isGreaterThan(0);
    }

    @Test
    void testRenderRepetitionOneOrMore() {
        var rep = new Repetition(new NonTerminal("digit"), RepetitionKind.ONE_OR_MORE);
        int railY = renderer.render(rep, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
    }

    @Test
    void testMeasureRepetitionOptional() {
        var rep = new Repetition(Terminal.literal("sign"), RepetitionKind.OPTIONAL);
        var metrics = renderer.measure(rep, graphics, style);

        assertThat(metrics.width()).isGreaterThan(0);
        assertThat(metrics.height()).isGreaterThan(0);
    }

    @Test
    void testRenderRepetitionOptional() {
        var rep = new Repetition(Terminal.literal("sign"), RepetitionKind.OPTIONAL);
        int railY = renderer.render(rep, graphics, 10, 10, style);

        assertThat(railY).isGreaterThan(0);
    }

    @Test
    void testMeasureGroupSameAsInner() {
        var inner = Terminal.literal("content");
        var group = new Group(inner);

        var innerMetrics = renderer.measure(inner, graphics, style);
        var groupMetrics = renderer.measure(group, graphics, style);

        assertThat(groupMetrics.width()).isEqualTo(innerMetrics.width());
        assertThat(groupMetrics.height()).isEqualTo(innerMetrics.height());
    }

    @Test
    void testRenderFullGrammarNoExceptions() {
        var rules = new LinkedHashMap<String, Rule>();
        rules.put("expr", new Rule("expr", new Alternation(List.of(
                new NonTerminal("term"),
                new Sequence(List.of(new NonTerminal("expr"), Terminal.literal("+"), new NonTerminal("term")))
        ))));
        rules.put("term", new Rule("term", new Alternation(List.of(
                new NonTerminal("factor"),
                new Sequence(List.of(new NonTerminal("term"), Terminal.literal("*"), new NonTerminal("factor")))
        ))));
        rules.put("factor", new Rule("factor", new Alternation(List.of(
                new NonTerminal("number"),
                new Sequence(List.of(Terminal.literal("("), new NonTerminal("expr"), Terminal.literal(")")))
        ))));
        rules.put("number", new Rule("number", new Repetition(
                Terminal.regex("[0-9]"), RepetitionKind.ONE_OR_MORE)));
        rules.put("sign", new Rule("sign", new Repetition(
                new Alternation(List.of(Terminal.literal("+"), Terminal.literal("-"))),
                RepetitionKind.OPTIONAL)));

        var grammar = new Grammar("arithmetic", rules, "expr");

        // Render all rules without exceptions
        for (var rule : grammar.rules().values()) {
            var metrics = renderer.measureRule(rule, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
            renderer.renderRule(rule, graphics, 0, 0, style);
        }
    }

    @Test
    void testExportToPngFileExists() throws Exception {
        var rule = new Rule("test", new Sequence(List.of(
                Terminal.literal("a"), new NonTerminal("b"), Terminal.literal("c")
        )));

        var tmpDir = Files.createTempDirectory("pex-test-png");
        var outputFile = tmpDir.resolve("test.png");

        var exporter = new PngExporter();
        var result = exporter.exportRule(rule, style, outputFile);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.exists(outputFile)).isTrue();
        assertThat(Files.size(outputFile)).isGreaterThan(0);

        // Verify it is a valid image
        var image = ImageIO.read(outputFile.toFile());
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(0);

        // Cleanup
        Files.deleteIfExists(outputFile);
        Files.deleteIfExists(tmpDir);
    }

    @Test
    void testRailroadPanelPreferredSize() {
        var rule = new Rule("test", Terminal.literal("hello"));
        var panel = new RailroadPanel(rule, style);

        var prefSize = panel.getPreferredSize();
        assertThat(prefSize.width).isGreaterThan(0);
        assertThat(prefSize.height).isGreaterThan(0);
    }

    @Test
    void testMeasureRuleIncludesLabel() {
        var rule = new Rule("myRuleName", Terminal.literal("x"));
        var ruleMetrics = renderer.measureRule(rule, graphics, style);
        var bodyMetrics = renderer.measure(rule.body(), graphics, style);

        // Rule metrics should be wider than body alone (includes label)
        assertThat(ruleMetrics.width()).isGreaterThan(bodyMetrics.width());
    }
}

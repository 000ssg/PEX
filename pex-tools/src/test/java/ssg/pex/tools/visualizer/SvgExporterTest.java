package ssg.pex.tools.visualizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

class SvgExporterTest {

    private SvgExporter exporter;
    private DiagramStyle style;

    @BeforeEach
    void setUp() {
        exporter = new SvgExporter();
        style = DiagramStyle.defaultStyle();
    }

    @Test
    void testExportSingleRuleContainsSvgElements() {
        var rule = new Rule("greeting", new Sequence(List.of(
                Terminal.literal("hello"),
                new NonTerminal("name")
        )));

        var result = exporter.exportRule(rule, style);

        assertThat(result.isSuccess()).isTrue();
        var svg = result.value();
        assertThat(svg).contains("<svg");
        assertThat(svg).contains("<rect");
        assertThat(svg).contains("<text");
        assertThat(svg).contains("</svg>");
    }

    @Test
    void testExportGrammarContainsAllRuleNames() {
        var rules = new LinkedHashMap<String, Rule>();
        rules.put("alpha", new Rule("alpha", Terminal.literal("a")));
        rules.put("beta", new Rule("beta", Terminal.literal("b")));
        rules.put("gamma", new Rule("gamma", new NonTerminal("alpha")));
        var grammar = new Grammar("test", rules, "alpha");

        var result = exporter.exportGrammar(grammar, style);

        assertThat(result.isSuccess()).isTrue();
        var svg = result.value();
        assertThat(svg).contains("alpha");
        assertThat(svg).contains("beta");
        assertThat(svg).contains("gamma");
    }

    @Test
    void testSvgOutputIsValidXml() throws Exception {
        var rule = new Rule("valid", new Alternation(List.of(
                Terminal.literal("x"),
                Terminal.literal("y")
        )));

        var result = exporter.exportRule(rule, style);

        assertThat(result.isSuccess()).isTrue();
        var svg = result.value();

        // Parse as XML to verify validity
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
        assertThat(doc).isNotNull();
        assertThat(doc.getDocumentElement().getTagName()).isEqualTo("svg");
    }

    @Test
    void testExportRuleWithRegexTerminal() {
        var rule = new Rule("pattern", Terminal.regex("[0-9]+"));

        var result = exporter.exportRule(rule, style);

        assertThat(result.isSuccess()).isTrue();
        var svg = result.value();
        assertThat(svg).contains("stroke-dasharray");
        assertThat(svg).contains("/[0-9]+/");
    }

    @Test
    void testExportGrammarSvgHeader() {
        var rules = new LinkedHashMap<String, Rule>();
        rules.put("start", new Rule("start", Terminal.literal("go")));
        var grammar = new Grammar("simple", rules, "start");

        var result = exporter.exportGrammar(grammar, style);

        assertThat(result.isSuccess()).isTrue();
        var svg = result.value();
        assertThat(svg).startsWith("<?xml version=\"1.0\"");
        assertThat(svg).contains("xmlns=\"http://www.w3.org/2000/svg\"");
    }
}

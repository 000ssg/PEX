package ssg.pex.tools.docgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.tools.visualizer.DiagramStyle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarDocGeneratorTest {

    private final GrammarDocGenerator generator = new GrammarDocGenerator();
    private final DiagramStyle style = DiagramStyle.defaultStyle();

    @Test
    void testGenerateHtmlContainsSvgAndRuleNames() {
        var grammar = buildGrammar("expr",
                new Rule("expr", new Alternation(List.of(
                        new NonTerminal("term"), Terminal.literal("+")))),
                new Rule("term", Terminal.literal("x")));

        var result = generator.generateHtml(grammar, style);

        assertThat(result.isSuccess()).isTrue();
        var html = result.value();
        assertThat(html).contains("<svg");
        assertThat(html).contains("</svg>");
        assertThat(html).contains("expr");
        assertThat(html).contains("term");
    }

    @Test
    void testHtmlIsWellFormed() {
        var grammar = buildGrammar("test",
                new Rule("start", Terminal.literal("hello")));

        var result = generator.generateHtml(grammar, style);

        assertThat(result.isSuccess()).isTrue();
        var html = result.value();
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<html");
        assertThat(html).contains("<head>");
        assertThat(html).contains("<body>");
        assertThat(html).contains("</html>");
    }

    @Test
    void testHtmlContainsTableOfContents() {
        var grammar = buildGrammar("test",
                new Rule("alpha", Terminal.literal("a")),
                new Rule("beta", Terminal.literal("b")));

        var result = generator.generateHtml(grammar, style);

        assertThat(result.isSuccess()).isTrue();
        var html = result.value();
        assertThat(html).contains("Table of Contents");
        assertThat(html).contains("href=\"#rule-alpha\"");
        assertThat(html).contains("href=\"#rule-beta\"");
    }

    @Test
    void testNonTerminalLinksPresent() {
        var grammar = buildGrammar("test",
                new Rule("start", new NonTerminal("target")),
                new Rule("target", Terminal.literal("x")));

        var result = generator.generateHtml(grammar, style);

        assertThat(result.isSuccess()).isTrue();
        var html = result.value();
        // SVG should contain a link to the target rule
        assertThat(html).contains("href=\"#rule-target\"");
    }

    @Test
    void testGenerateToFileCreatesFile(@TempDir Path tempDir) {
        var grammar = buildGrammar("test",
                new Rule("start", Terminal.literal("x")));
        var outputFile = tempDir.resolve("grammar.html");

        var result = generator.generateToFile(grammar, style, outputFile);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.exists(outputFile)).isTrue();
        assertThat(outputFile.toFile().length()).isGreaterThan(0);
    }

    @Test
    void testHtmlContainsBnfNotation() {
        var grammar = buildGrammar("test",
                new Rule("seq", new Sequence(List.of(
                        Terminal.literal("a"), Terminal.literal("b")))));

        var result = generator.generateHtml(grammar, style);

        assertThat(result.isSuccess()).isTrue();
        var html = result.value();
        assertThat(html).contains("seq ::=");
        assertThat(html).contains("&#39;a&#39;"); // escaped single quotes in HTML
    }

    // ---- Helper ----

    private static Grammar buildGrammar(String name, Rule... rules) {
        var ruleMap = new LinkedHashMap<String, Rule>();
        for (var rule : rules) {
            ruleMap.put(rule.name(), rule);
        }
        return new Grammar(name, ruleMap, rules[0].name());
    }
}

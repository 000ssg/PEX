package ssg.pex.tools.converter;

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

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BnfToAntlrConverterTest {

    private final BnfToAntlrConverter converter = new BnfToAntlrConverter();

    @Test
    void testConvertSimpleGrammar() {
        var grammar = buildGrammar("test",
                new Rule("expr", new NonTerminal("term")),
                new Rule("term", Terminal.literal("x")));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        var text = result.value().textOutput();
        assertThat(text).contains("grammar Test;");
        assertThat(text).contains("expr");
        assertThat(text).contains(":");
        assertThat(text).contains(";");
    }

    @Test
    void testTerminalLiteralsMappedToQuotedStrings() {
        var grammar = buildGrammar("test",
                new Rule("keyword", Terminal.literal("SELECT")));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().textOutput()).contains("'SELECT'");
    }

    @Test
    void testNonTerminalsMappedToRuleReferences() {
        var grammar = buildGrammar("test",
                new Rule("start", new NonTerminal("inner")),
                new Rule("inner", Terminal.literal("a")));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        var text = result.value().textOutput();
        assertThat(text).contains("inner");
        // Should reference inner as plain name, not quoted
        assertThat(text).doesNotContain("'inner'");
    }

    @Test
    void testAlternationMappedToPipe() {
        var grammar = buildGrammar("test",
                new Rule("choice", new Alternation(List.of(
                        Terminal.literal("a"), Terminal.literal("b")))));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().textOutput()).contains("'a' | 'b'");
    }

    @Test
    void testSequenceMappedToSpaceSeparated() {
        var grammar = buildGrammar("test",
                new Rule("seq", new Sequence(List.of(
                        Terminal.literal("a"), Terminal.literal("b"), Terminal.literal("c")))));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().textOutput()).contains("'a' 'b' 'c'");
    }

    @Test
    void testRepetitionZeroOrMore() {
        var grammar = buildGrammar("test",
                new Rule("rep", new Repetition(Terminal.literal("x"), RepetitionKind.ZERO_OR_MORE)));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().textOutput()).contains("'x'*");
    }

    @Test
    void testRepetitionOneOrMore() {
        var grammar = buildGrammar("test",
                new Rule("rep", new Repetition(Terminal.literal("x"), RepetitionKind.ONE_OR_MORE)));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().textOutput()).contains("'x'+");
    }

    @Test
    void testRepetitionOptional() {
        var grammar = buildGrammar("test",
                new Rule("opt", new Repetition(Terminal.literal("x"), RepetitionKind.OPTIONAL)));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().textOutput()).contains("'x'?");
    }

    @Test
    void testRegexTerminalsGenerateLexerRules() {
        var grammar = buildGrammar("test",
                new Rule("num", Terminal.regex("[0-9]+")));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        var text = result.value().textOutput();
        // Should have a lexer rule reference (uppercase)
        assertThat(text).containsPattern("[A-Z][A-Z_0-9]*");
        // Should have the lexer rule definition at the bottom
        assertThat(text).contains("[0-9]+");
        assertThat(text).contains("// Lexer rules");
    }

    @Test
    void testGroupConversion() {
        var grammar = buildGrammar("test",
                new Rule("grouped", new Group(new Alternation(List.of(
                        Terminal.literal("a"), Terminal.literal("b"))))));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().textOutput()).contains("( 'a' | 'b' )");
    }

    @Test
    void testEmptyGrammarProducesValidOutput() {
        var grammar = buildGrammar("empty",
                new Rule("start", Terminal.literal("x")));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        var text = result.value().textOutput();
        assertThat(text).startsWith("grammar Empty;");
        assertThat(text).contains("start");
    }

    @Test
    void testComplexRegexWarnings() {
        var grammar = buildGrammar("test",
                new Rule("pat", Terminal.regex("(?i)hello\\b")));

        var result = converter.convert(grammar);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().warnings()).isNotEmpty();
        assertThat(result.value().warnings().getFirst()).contains("Complex regex");
    }

    @Test
    void testRoundTripBnfToAntlrToBnf() {
        var grammar = buildGrammar("roundtrip",
                new Rule("start", new Sequence(List.of(
                        Terminal.literal("hello"),
                        new Repetition(new NonTerminal("item"), RepetitionKind.ZERO_OR_MORE)))),
                new Rule("item", new Alternation(List.of(
                        Terminal.literal("a"), Terminal.literal("b")))));

        // BNF -> ANTLR
        var antlrResult = converter.convert(grammar);
        assertThat(antlrResult.isSuccess()).isTrue();

        // ANTLR -> BNF
        var antlrToBnf = new AntlrToBnfConverter();
        var bnfResult = antlrToBnf.convert(antlrResult.value().textOutput());
        assertThat(bnfResult.isSuccess()).isTrue();

        var roundTripped = bnfResult.value().grammarOutput();
        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.rules()).containsKeys("start", "item");

        // BNF -> ANTLR again
        var converter2 = new BnfToAntlrConverter();
        var antlrResult2 = converter2.convert(roundTripped);
        assertThat(antlrResult2.isSuccess()).isTrue();

        // Should produce equivalent output
        assertThat(antlrResult2.value().textOutput()).contains("grammar Roundtrip;");
        assertThat(antlrResult2.value().textOutput()).contains("'hello'");
        assertThat(antlrResult2.value().textOutput()).contains("'a' | 'b'");
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

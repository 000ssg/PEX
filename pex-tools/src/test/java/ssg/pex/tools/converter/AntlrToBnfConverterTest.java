package ssg.pex.tools.converter;

import org.junit.jupiter.api.Test;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;

import static org.assertj.core.api.Assertions.assertThat;

class AntlrToBnfConverterTest {

    private final AntlrToBnfConverter converter = new AntlrToBnfConverter();

    @Test
    void testConvertSimpleAntlrGrammar() {
        var result = converter.convert("""
                grammar Hello;
                greeting : 'hello' name ;
                name : WORD ;
                WORD : [a-zA-Z]+ ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var grammar = result.value().grammarOutput();
        assertThat(grammar).isNotNull();
        assertThat(grammar.name()).isEqualTo("Hello");
        assertThat(grammar.rules()).containsKeys("greeting", "name");
    }

    @Test
    void testParserRulesBecomePexRules() {
        var result = converter.convert("""
                grammar Test;
                expr : term '+' term ;
                term : 'x' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var grammar = result.value().grammarOutput();
        assertThat(grammar.rules()).hasSize(2);
        assertThat(grammar.startRuleName()).isEqualTo("expr");

        // expr rule body should be a Sequence
        var exprBody = grammar.rules().get("expr").body();
        assertThat(exprBody).isInstanceOf(Sequence.class);
    }

    @Test
    void testLexerRulesConvertedToRegexTerminals() {
        var result = converter.convert("""
                grammar Test;
                num : NUMBER ;
                NUMBER : [0-9]+ ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var grammar = result.value().grammarOutput();
        var numBody = grammar.rules().get("num").body();
        // NUMBER should be resolved to a regex terminal
        assertThat(numBody).isInstanceOf(Terminal.class);
        var terminal = (Terminal) numBody;
        assertThat(terminal.isRegex()).isTrue();
    }

    @Test
    void testAlternationPreserved() {
        var result = converter.convert("""
                grammar Test;
                choice : 'a' | 'b' | 'c' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("choice").body();
        assertThat(body).isInstanceOf(Alternation.class);
        assertThat(((Alternation) body).alternatives()).hasSize(3);
    }

    @Test
    void testSequencePreserved() {
        var result = converter.convert("""
                grammar Test;
                seq : 'a' 'b' 'c' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("seq").body();
        assertThat(body).isInstanceOf(Sequence.class);
        assertThat(((Sequence) body).elements()).hasSize(3);
    }

    @Test
    void testRepetitionZeroOrMorePreserved() {
        var result = converter.convert("""
                grammar Test;
                items : 'a'* ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("items").body();
        assertThat(body).isInstanceOf(Repetition.class);
        assertThat(((Repetition) body).kind()).isEqualTo(RepetitionKind.ZERO_OR_MORE);
    }

    @Test
    void testRepetitionOneOrMorePreserved() {
        var result = converter.convert("""
                grammar Test;
                items : 'a'+ ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("items").body();
        assertThat(body).isInstanceOf(Repetition.class);
        assertThat(((Repetition) body).kind()).isEqualTo(RepetitionKind.ONE_OR_MORE);
    }

    @Test
    void testRepetitionOptionalPreserved() {
        var result = converter.convert("""
                grammar Test;
                opt : 'a'? ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("opt").body();
        assertThat(body).isInstanceOf(Repetition.class);
        assertThat(((Repetition) body).kind()).isEqualTo(RepetitionKind.OPTIONAL);
    }

    @Test
    void testWarningsForUnsupportedActions() {
        var result = converter.convert("""
                grammar Test;
                expr : 'a' { doSomething(); } 'b' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().warnings()).anyMatch(w -> w.contains("action"));
    }

    @Test
    void testWarningsForArrowCommands() {
        var result = converter.convert("""
                grammar Test;
                expr : 'a' ;
                WS : [ \\t\\r\\n]+ -> skip ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().warnings()).anyMatch(w -> w.contains("->"));
    }

    @Test
    void testErrorForInvalidGrammar() {
        var result = converter.convert("this is not a valid grammar");

        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void testLexerRuleWithCharacterClass() {
        var result = converter.convert("""
                grammar Test;
                letter : ALPHA ;
                ALPHA : [a-zA-Z] ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("letter").body();
        assertThat(body).isInstanceOf(Terminal.class);
        var terminal = (Terminal) body;
        assertThat(terminal.isRegex()).isTrue();
        assertThat(terminal.value()).contains("[a-zA-Z]");
    }

    @Test
    void testNonTerminalReferences() {
        var result = converter.convert("""
                grammar Test;
                start : inner ;
                inner : 'x' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("start").body();
        assertThat(body).isInstanceOf(NonTerminal.class);
        assertThat(((NonTerminal) body).ruleName()).isEqualTo("inner");
    }

    @Test
    void testGroupedExpressions() {
        var result = converter.convert("""
                grammar Test;
                expr : ('a' | 'b') 'c' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().grammarOutput().rules().get("expr").body();
        assertThat(body).isInstanceOf(Sequence.class);
    }
}

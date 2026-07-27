package ssg.pex.bnf.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.result.Result;

import static org.assertj.core.api.Assertions.assertThat;

class BnfParserTest {

    private BnfParser parser;

    @BeforeEach
    void setUp() {
        parser = new BnfParser();
    }

    private Grammar parseSuccessfully(String source) {
        var result = parser.parse(source);
        assertThat(result.isSuccess())
                .withFailMessage(() -> "Expected success but got: " + result.error())
                .isTrue();
        return result.value();
    }

    private void parseExpectFailure(String source) {
        var result = parser.parse(source);
        assertThat(result.isFailure()).isTrue();
    }

    @Nested
    class SimpleLiterals {

        @Test
        @DisplayName("parse simple single-quoted literal rule")
        void singleQuotedLiteral() {
            var g = parseSuccessfully("rule ::= 'hello' ;");
            assertThat(g.rules()).hasSize(1);
            assertThat(g.rule("rule").get().body()).isEqualTo(Terminal.literal("hello"));
        }

        @Test
        @DisplayName("parse simple double-quoted literal rule")
        void doubleQuotedLiteral() {
            var g = parseSuccessfully("rule ::= \"hello\" ;");
            assertThat(g.rule("rule").get().body()).isEqualTo(Terminal.literal("hello"));
        }

        @Test
        @DisplayName("parse rule with = instead of ::=")
        void equalsSign() {
            var g = parseSuccessfully("rule = 'hello' ;");
            assertThat(g.rule("rule").get().body()).isEqualTo(Terminal.literal("hello"));
        }
    }

    @Nested
    class AlternationTests {

        @Test
        @DisplayName("parse alternation with two alternatives")
        void twoAlternatives() {
            var g = parseSuccessfully("rule ::= 'a' | 'b' ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Alternation.class);
            var alt = (Alternation) body;
            assertThat(alt.alternatives()).hasSize(2);
            assertThat(alt.alternatives().get(0)).isEqualTo(Terminal.literal("a"));
            assertThat(alt.alternatives().get(1)).isEqualTo(Terminal.literal("b"));
        }

        @Test
        @DisplayName("parse alternation with three alternatives")
        void threeAlternatives() {
            var g = parseSuccessfully("rule ::= 'a' | 'b' | 'c' ;");
            var alt = (Alternation) g.rule("rule").get().body();
            assertThat(alt.alternatives()).hasSize(3);
        }
    }

    @Nested
    class SequenceTests {

        @Test
        @DisplayName("parse sequence of two literals")
        void twoElements() {
            var g = parseSuccessfully("rule ::= 'a' 'b' ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Sequence.class);
            var seq = (Sequence) body;
            assertThat(seq.elements()).hasSize(2);
        }

        @Test
        @DisplayName("parse sequence of three literals")
        void threeElements() {
            var g = parseSuccessfully("rule ::= 'a' 'b' 'c' ;");
            var seq = (Sequence) g.rule("rule").get().body();
            assertThat(seq.elements()).hasSize(3);
        }
    }

    @Nested
    class RepetitionTests {

        @Test
        @DisplayName("parse zero-or-more repetition")
        void zeroOrMore() {
            var g = parseSuccessfully("rule ::= item* ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Repetition.class);
            var rep = (Repetition) body;
            assertThat(rep.kind()).isEqualTo(RepetitionKind.ZERO_OR_MORE);
            assertThat(rep.body()).isEqualTo(new NonTerminal("item"));
        }

        @Test
        @DisplayName("parse one-or-more repetition")
        void oneOrMore() {
            var g = parseSuccessfully("rule ::= item+ ;");
            var rep = (Repetition) g.rule("rule").get().body();
            assertThat(rep.kind()).isEqualTo(RepetitionKind.ONE_OR_MORE);
        }

        @Test
        @DisplayName("parse optional")
        void optional() {
            var g = parseSuccessfully("rule ::= item? ;");
            var rep = (Repetition) g.rule("rule").get().body();
            assertThat(rep.kind()).isEqualTo(RepetitionKind.OPTIONAL);
        }
    }

    @Nested
    class GroupingTests {

        @Test
        @DisplayName("parse grouped alternation followed by literal")
        void groupedAlternation() {
            var g = parseSuccessfully("rule ::= ( 'a' | 'b' ) 'c' ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Sequence.class);
            var seq = (Sequence) body;
            assertThat(seq.elements()).hasSize(2);
            assertThat(seq.elements().get(0)).isInstanceOf(Group.class);
            var grp = (Group) seq.elements().get(0);
            assertThat(grp.inner()).isInstanceOf(Alternation.class);
        }

        @Test
        @DisplayName("parse grouped expression with postfix repetition")
        void groupedWithRepetition() {
            var g = parseSuccessfully("rule ::= ( 'a' 'b' )* ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Repetition.class);
            var rep = (Repetition) body;
            assertThat(rep.body()).isInstanceOf(Group.class);
        }
    }

    @Nested
    class NonTerminalTests {

        @Test
        @DisplayName("parse non-terminal reference")
        void nonTerminal() {
            var g = parseSuccessfully("rule ::= otherRule ;");
            assertThat(g.rule("rule").get().body()).isEqualTo(new NonTerminal("otherRule"));
        }

        @Test
        @DisplayName("parse non-terminal with underscore and hyphen")
        void nonTerminalWithSpecialChars() {
            var g = parseSuccessfully("rule ::= other_rule-name ;");
            assertThat(g.rule("rule").get().body()).isEqualTo(new NonTerminal("other_rule-name"));
        }
    }

    @Nested
    class RegexTests {

        @Test
        @DisplayName("parse regex terminal")
        void regex() {
            var g = parseSuccessfully("rule ::= /[0-9]+/ ;");
            assertThat(g.rule("rule").get().body()).isEqualTo(Terminal.regex("[0-9]+"));
        }

        @Test
        @DisplayName("parse regex with escaped slash")
        void regexEscapedSlash() {
            var g = parseSuccessfully("rule ::= /a\\/b/ ;");
            assertThat(g.rule("rule").get().body()).isEqualTo(Terminal.regex("a\\/b"));
        }
    }

    @Nested
    class GrammarDeclarationTests {

        @Test
        @DisplayName("parse grammar name declaration")
        void grammarName() {
            var g = parseSuccessfully("grammar myGrammar; rule ::= 'a' ;");
            assertThat(g.name()).isEqualTo("myGrammar");
        }

        @Test
        @DisplayName("default grammar name is unnamed")
        void defaultName() {
            var g = parseSuccessfully("rule ::= 'a' ;");
            assertThat(g.name()).isEqualTo("unnamed");
        }

        @Test
        @DisplayName("first rule becomes the start rule")
        void startRule() {
            var g = parseSuccessfully("first ::= 'a' ; second ::= 'b' ;");
            assertThat(g.startRuleName()).isEqualTo("first");
        }
    }

    @Nested
    class AnnotationTests {

        @Test
        @DisplayName("parse boolean annotation before a rule")
        void booleanAnnotation() {
            var g = parseSuccessfully("@keyword rule ::= 'if' ;");
            var rule = g.rule("rule").get();
            assertThat(rule.hasAnnotation("keyword")).isTrue();
            assertThat(rule.annotation("keyword")).isEqualTo("true");
        }

        @Test
        @DisplayName("parse annotation with value")
        void annotationWithValue() {
            var g = parseSuccessfully("@precedence(10) rule ::= 'a' ;");
            var rule = g.rule("rule").get();
            assertThat(rule.annotation("precedence")).isEqualTo("10");
        }

        @Test
        @DisplayName("parse multiple annotations on one rule")
        void multipleAnnotations() {
            var g = parseSuccessfully("@keyword @precedence(5) rule ::= 'a' ;");
            var rule = g.rule("rule").get();
            assertThat(rule.hasAnnotation("keyword")).isTrue();
            assertThat(rule.annotation("precedence")).isEqualTo("5");
        }
    }

    @Nested
    class MultipleRuleTests {

        @Test
        @DisplayName("parse two rules")
        void twoRules() {
            var g = parseSuccessfully("expr ::= term ; term ::= 'x' ;");
            assertThat(g.rules()).hasSize(2);
            assertThat(g.rules()).containsKey("expr");
            assertThat(g.rules()).containsKey("term");
        }

        @Test
        @DisplayName("parse three rules with mixed syntax")
        void threeRules() {
            var g = parseSuccessfully("""
                    grammar test;
                    expr ::= term ( '+' term )* ;
                    term ::= factor ( '*' factor )* ;
                    factor ::= /[0-9]+/ ;
                    """);
            assertThat(g.rules()).hasSize(3);
            assertThat(g.name()).isEqualTo("test");
        }
    }

    @Nested
    class CommentTests {

        @Test
        @DisplayName("line comments are skipped")
        void lineComments() {
            var g = parseSuccessfully("""
                    // This is a comment
                    rule ::= 'a' ; // trailing comment
                    """);
            assertThat(g.rules()).hasSize(1);
        }

        @Test
        @DisplayName("block comments are skipped")
        void blockComments() {
            var g = parseSuccessfully("""
                    /* multi
                       line
                       comment */
                    rule ::= 'a' ;
                    """);
            assertThat(g.rules()).hasSize(1);
        }

        @Test
        @DisplayName("inline block comment between tokens")
        void inlineBlockComment() {
            var g = parseSuccessfully("rule ::= 'a' /* skip */ 'b' ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Sequence.class);
            assertThat(((Sequence) body).elements()).hasSize(2);
        }
    }

    @Nested
    class ErrorTests {

        @Test
        @DisplayName("error on empty input")
        void emptyInput() {
            var result = parser.parse("");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("BNF_EMPTY");
        }

        @Test
        @DisplayName("error on only whitespace")
        void whitespaceOnly() {
            var result = parser.parse("   \n  \t  ");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        @DisplayName("error on unterminated single-quoted string")
        void unterminatedSingleQuote() {
            var result = parser.parse("rule ::= 'unterminated ;");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("BNF_PARSE_ERROR");
        }

        @Test
        @DisplayName("error on unterminated double-quoted string")
        void unterminatedDoubleQuote() {
            var result = parser.parse("rule ::= \"unterminated ;");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        @DisplayName("error on missing semicolon")
        void missingSemicolon() {
            var result = parser.parse("rule ::= 'a'");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        @DisplayName("error on unexpected character")
        void unexpectedChar() {
            var result = parser.parse("rule ::= # ;");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        @DisplayName("error on unclosed parenthesis")
        void unclosedParen() {
            var result = parser.parse("rule ::= ( 'a' ;");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        @DisplayName("error on missing rule body")
        void missingRuleBody() {
            var result = parser.parse("rule ::= ;");
            assertThat(result.isFailure()).isTrue();
        }
    }

    @Nested
    class ComplexGrammars {

        @Test
        @DisplayName("arithmetic expression grammar")
        void arithmeticGrammar() {
            var g = parseSuccessfully("""
                    grammar arithmetic;
                    expr     ::= term ( ( '+' | '-' ) term )* ;
                    term     ::= factor ( ( '*' | '/' ) factor )* ;
                    factor   ::= /[0-9]+/ | '(' expr ')' ;
                    """);
            assertThat(g.name()).isEqualTo("arithmetic");
            assertThat(g.rules()).hasSize(3);
            assertThat(g.startRuleName()).isEqualTo("expr");
        }

        @Test
        @DisplayName("grammar with escaped characters in strings")
        void escapedStrings() {
            var g = parseSuccessfully("rule ::= 'it\\'s' ;");
            assertThat(g.rule("rule").get().body()).isEqualTo(Terminal.literal("it's"));
        }

        @Test
        @DisplayName("grammar with nested groups and repetitions")
        void nestedGroupsAndRepetitions() {
            var g = parseSuccessfully("rule ::= ( 'a' ( 'b' | 'c' )+ )* ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Repetition.class);
            assertThat(((Repetition) body).kind()).isEqualTo(RepetitionKind.ZERO_OR_MORE);
        }

        @Test
        @DisplayName("grammar with alternation of sequences")
        void alternationOfSequences() {
            var g = parseSuccessfully("rule ::= 'a' 'b' | 'c' 'd' ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Alternation.class);
            var alt = (Alternation) body;
            assertThat(alt.alternatives()).hasSize(2);
            // Each alternative should be a sequence
            assertThat(alt.alternatives().get(0)).isInstanceOf(Sequence.class);
            assertThat(alt.alternatives().get(1)).isInstanceOf(Sequence.class);
        }

        @Test
        @DisplayName("mixed literal and regex in sequence")
        void mixedLiteralAndRegex() {
            var g = parseSuccessfully("rule ::= 'SELECT' /[a-zA-Z_]+/ ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Sequence.class);
            var seq = (Sequence) body;
            assertThat(seq.elements().get(0)).isEqualTo(Terminal.literal("SELECT"));
            assertThat(seq.elements().get(1)).isEqualTo(Terminal.regex("[a-zA-Z_]+"));
        }

        @Test
        @DisplayName("grammar with only comments yields empty error")
        void onlyComments() {
            var result = parser.parse("// just a comment\n/* block */");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("BNF_EMPTY");
        }

        @Test
        @DisplayName("reusing parser instance for multiple parses")
        void reuseParser() {
            var g1 = parseSuccessfully("a ::= 'x' ;");
            var g2 = parseSuccessfully("b ::= 'y' ;");
            assertThat(g1.rules()).containsKey("a");
            assertThat(g2.rules()).containsKey("b");
        }
    }
}

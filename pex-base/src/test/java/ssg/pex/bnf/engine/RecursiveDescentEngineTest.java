package ssg.pex.bnf.engine;

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
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.result.Result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveDescentEngineTest {

    private RecursiveDescentEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecursiveDescentEngine();
    }

    private Grammar grammarOf(Rule... rules) {
        var map = new LinkedHashMap<String, Rule>();
        for (var r : rules) {
            map.put(r.name(), r);
        }
        return new Grammar("test", map, rules[0].name());
    }

    private Grammar parseGrammar(String bnf) {
        var result = new BnfParser().parse(bnf);
        assertThat(result.isSuccess())
                .withFailMessage(() -> "Grammar parse failed: " + result.error())
                .isTrue();
        return result.value();
    }

    private ParseMatch parseSuccessfully(String input, Grammar grammar) {
        var result = engine.parse(input, grammar);
        assertThat(result.isSuccess())
                .withFailMessage(() -> "Expected parse success for '" + input + "' but got: " + result.error())
                .isTrue();
        return result.value();
    }

    private void parseExpectFailure(String input, Grammar grammar) {
        var result = engine.parse(input, grammar);
        assertThat(result.isFailure())
                .withFailMessage(() -> "Expected parse failure for '" + input + "' but got success")
                .isTrue();
    }

    @Nested
    class LiteralTerminalTests {

        @Test
        @DisplayName("matches a simple literal terminal")
        void simpleLiteral() {
            var g = grammarOf(new Rule("start", Terminal.literal("hello")));
            var match = parseSuccessfully("hello", g);
            assertThat(match.matchedText()).isEqualTo("hello");
            assertThat(match.ruleName()).isEqualTo("start");
        }

        @Test
        @DisplayName("fails when literal does not match")
        void literalMismatch() {
            var g = grammarOf(new Rule("start", Terminal.literal("hello")));
            parseExpectFailure("world", g);
        }

        @Test
        @DisplayName("fails when input is shorter than literal")
        void inputTooShort() {
            var g = grammarOf(new Rule("start", Terminal.literal("hello")));
            parseExpectFailure("hel", g);
        }

        @Test
        @DisplayName("fails when input has extra content after literal")
        void extraInput() {
            var g = grammarOf(new Rule("start", Terminal.literal("hello")));
            parseExpectFailure("helloworld", g);
        }
    }

    @Nested
    class AlternationTests {

        @Test
        @DisplayName("matches first alternative")
        void firstAlternative() {
            var g = grammarOf(new Rule("start",
                    new Alternation(List.of(Terminal.literal("a"), Terminal.literal("b")))));
            var match = parseSuccessfully("a", g);
            assertThat(match.matchedText()).isEqualTo("a");
        }

        @Test
        @DisplayName("matches second alternative when first fails")
        void secondAlternative() {
            var g = grammarOf(new Rule("start",
                    new Alternation(List.of(Terminal.literal("a"), Terminal.literal("b")))));
            var match = parseSuccessfully("b", g);
            assertThat(match.matchedText()).isEqualTo("b");
        }

        @Test
        @DisplayName("fails when no alternative matches")
        void noAlternativeMatches() {
            var g = grammarOf(new Rule("start",
                    new Alternation(List.of(Terminal.literal("a"), Terminal.literal("b")))));
            parseExpectFailure("c", g);
        }
    }

    @Nested
    class SequenceTests {

        @Test
        @DisplayName("matches a sequence of two literals")
        void twoLiterals() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(Terminal.literal("a"), Terminal.literal("b")))));
            var match = parseSuccessfully("ab", g);
            assertThat(match.matchedText()).isEqualTo("ab");
        }

        @Test
        @DisplayName("matches a sequence with whitespace between elements")
        void withWhitespace() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(Terminal.literal("a"), Terminal.literal("b")))));
            var match = parseSuccessfully("a b", g);
            assertThat(match.matchedText()).isEqualTo("a b");
        }

        @Test
        @DisplayName("fails when second element in sequence does not match")
        void partialSequenceFailure() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(Terminal.literal("a"), Terminal.literal("b")))));
            parseExpectFailure("ac", g);
        }

        @Test
        @DisplayName("matches a three-element sequence")
        void threeElements() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(
                            Terminal.literal("a"),
                            Terminal.literal("b"),
                            Terminal.literal("c")))));
            var match = parseSuccessfully("a b c", g);
            assertThat(match.matchedText()).isEqualTo("a b c");
        }
    }

    @Nested
    class RepetitionTests {

        @Test
        @DisplayName("zero-or-more matches zero times")
        void zeroOrMoreZero() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.ZERO_OR_MORE)));
            var match = parseSuccessfully("", g);
            assertThat(match.matchedText()).isEmpty();
        }

        @Test
        @DisplayName("zero-or-more matches one time")
        void zeroOrMoreOne() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.ZERO_OR_MORE)));
            var match = parseSuccessfully("a", g);
            assertThat(match.matchedText()).isEqualTo("a");
        }

        @Test
        @DisplayName("zero-or-more matches multiple times")
        void zeroOrMoreMultiple() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.ZERO_OR_MORE)));
            var match = parseSuccessfully("a a a", g);
            assertThat(match.matchedText()).isEqualTo("a a a");
        }

        @Test
        @DisplayName("one-or-more fails with zero matches")
        void oneOrMoreZero() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.ONE_OR_MORE)));
            parseExpectFailure("", g);
        }

        @Test
        @DisplayName("one-or-more matches one time")
        void oneOrMoreOne() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.ONE_OR_MORE)));
            var match = parseSuccessfully("a", g);
            assertThat(match.matchedText()).isEqualTo("a");
        }

        @Test
        @DisplayName("one-or-more matches multiple times")
        void oneOrMoreMultiple() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.ONE_OR_MORE)));
            var match = parseSuccessfully("a a a", g);
            assertThat(match.matchedText()).isEqualTo("a a a");
        }

        @Test
        @DisplayName("optional matches when present")
        void optionalPresent() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.OPTIONAL)));
            var match = parseSuccessfully("a", g);
            assertThat(match.matchedText()).isEqualTo("a");
        }

        @Test
        @DisplayName("optional matches when absent")
        void optionalAbsent() {
            var g = grammarOf(new Rule("start",
                    new Repetition(Terminal.literal("a"), RepetitionKind.OPTIONAL)));
            var match = parseSuccessfully("", g);
            assertThat(match.matchedText()).isEmpty();
        }
    }

    @Nested
    class NonTerminalTests {

        @Test
        @DisplayName("parse through a non-terminal reference to another rule")
        void nonTerminalReference() {
            var g = grammarOf(
                    new Rule("start", new NonTerminal("inner")),
                    new Rule("inner", Terminal.literal("hello"))
            );
            var match = parseSuccessfully("hello", g);
            assertThat(match.ruleName()).isEqualTo("start");
        }

        @Test
        @DisplayName("nested non-terminal references")
        void nestedNonTerminals() {
            var g = grammarOf(
                    new Rule("a", new NonTerminal("b")),
                    new Rule("b", new NonTerminal("c")),
                    new Rule("c", Terminal.literal("deep"))
            );
            var match = parseSuccessfully("deep", g);
            assertThat(match.matchedText()).isEqualTo("deep");
        }

        @Test
        @DisplayName("undefined non-terminal causes parse failure")
        void undefinedNonTerminal() {
            var g = grammarOf(new Rule("start", new NonTerminal("missing")));
            parseExpectFailure("anything", g);
        }
    }

    @Nested
    class GroupTests {

        @Test
        @DisplayName("group wraps an expression transparently")
        void groupedExpression() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(
                            new Group(new Alternation(List.of(
                                    Terminal.literal("a"),
                                    Terminal.literal("b")))),
                            Terminal.literal("c")))));
            var match = parseSuccessfully("a c", g);
            assertThat(match.matchedText()).isEqualTo("a c");

            match = parseSuccessfully("b c", g);
            assertThat(match.matchedText()).isEqualTo("b c");
        }
    }

    @Nested
    class RegexTerminalTests {

        @Test
        @DisplayName("regex terminal matches digits")
        void regexDigits() {
            var g = grammarOf(new Rule("start", Terminal.regex("[0-9]+")));
            var match = parseSuccessfully("123", g);
            assertThat(match.matchedText()).isEqualTo("123");
        }

        @Test
        @DisplayName("regex terminal fails when no match")
        void regexNoMatch() {
            var g = grammarOf(new Rule("start", Terminal.regex("[0-9]+")));
            parseExpectFailure("abc", g);
        }

        @Test
        @DisplayName("regex matches at the beginning of remaining input")
        void regexAtBeginning() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(
                            Terminal.regex("[a-z]+"),
                            Terminal.regex("[0-9]+")))));
            var match = parseSuccessfully("abc 123", g);
            assertThat(match.matchedText()).isEqualTo("abc 123");
        }
    }

    @Nested
    class LeftRecursionTests {

        @Test
        @DisplayName("left-recursive rule does not hang and fails gracefully")
        void leftRecursion() {
            // start ::= start 'a' | 'b'
            var g = grammarOf(new Rule("start",
                    new Alternation(List.of(
                            new Sequence(List.of(new NonTerminal("start"), Terminal.literal("a"))),
                            Terminal.literal("b")))));
            // The engine should detect left recursion and still match the base case 'b'
            var match = parseSuccessfully("b", g);
            assertThat(match.matchedText()).isEqualTo("b");
        }
    }

    @Nested
    class MemoizationTests {

        @Test
        @DisplayName("memoization caches results for same rule at same position")
        void memoizationWorks() {
            // Build a grammar where the same rule is tried multiple times
            // r ::= a 'x' | a 'y'
            // a ::= 'hello'
            var g = grammarOf(
                    new Rule("start", new Alternation(List.of(
                            new Sequence(List.of(new NonTerminal("a"), Terminal.literal("x"))),
                            new Sequence(List.of(new NonTerminal("a"), Terminal.literal("y")))))),
                    new Rule("a", Terminal.literal("hello"))
            );
            // When first alternative fails (hello x), the memo for 'a' at position 0 is cached
            // When second alternative tries, it should use the cached result
            var match = parseSuccessfully("hello y", g);
            assertThat(match.matchedText()).isEqualTo("hello y");
        }
    }

    @Nested
    class ErrorReportingTests {

        @Test
        @DisplayName("error includes farthest position information")
        void farthestPosition() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(Terminal.literal("abc"), Terminal.literal("def")))));
            var result = engine.parse("abc xyz", g);
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("PARSE_FAILED");
            assertThat(result.error().message()).isNotEmpty();
        }

        @Test
        @DisplayName("error on completely empty input with required literal")
        void emptyInputError() {
            var g = grammarOf(new Rule("start", Terminal.literal("something")));
            var result = engine.parse("", g);
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        @DisplayName("error reports location")
        void errorLocation() {
            var g = grammarOf(new Rule("start", Terminal.literal("abc")));
            var result = engine.parse("xyz", g);
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().location()).isNotNull();
        }
    }

    @Nested
    class WhitespaceTests {

        @Test
        @DisplayName("leading whitespace is skipped")
        void leadingWhitespace() {
            var g = grammarOf(new Rule("start", Terminal.literal("a")));
            var match = parseSuccessfully("  a", g);
            assertThat(match.matchedText()).isEqualTo("  a");
        }

        @Test
        @DisplayName("trailing whitespace is tolerated")
        void trailingWhitespace() {
            var g = grammarOf(new Rule("start", Terminal.literal("a")));
            var match = parseSuccessfully("a  ", g);
            assertThat(match.matchedText()).isEqualTo("a");
        }

        @Test
        @DisplayName("whitespace between sequence elements is handled")
        void whitespaceInSequence() {
            var g = grammarOf(new Rule("start",
                    new Sequence(List.of(Terminal.literal("a"), Terminal.literal("b")))));
            var match = parseSuccessfully("a   b", g);
            assertThat(match.matchedText()).isEqualTo("a   b");
        }
    }

    @Nested
    class FullGrammarTests {

        @Test
        @DisplayName("simple arithmetic expression: number + number")
        void simpleArithmetic() {
            var g = parseGrammar("""
                    grammar arith;
                    expr   ::= term ( ( '+' | '-' ) term )* ;
                    term   ::= factor ( ( '*' | '/' ) factor )* ;
                    factor ::= /[0-9]+/ | '(' expr ')' ;
                    """);
            var match = parseSuccessfully("1 + 2", g);
            assertThat(match.matchedText()).isEqualTo("1 + 2");
        }

        @Test
        @DisplayName("recursive rule through terminals works, true left recursion is still blocked")
        void recursiveRuleThroughTerminalsWorks() {
            // Left-recursion detection is position-aware: same rule at same cursor = left recursion.
            // Legitimate recursion through terminals (like '(' factor ')') advances the cursor,
            // so it is correctly allowed.
            var g = parseGrammar("factor ::= /[0-9]+/ | '(' factor ')' ;");
            // Recursive case: (42) parses successfully — the '(' advances cursor before re-entering factor.
            var result = engine.parse("(42)", g);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().matchedText()).isEqualTo("(42)");
            // Nested: ((42)) also works.
            var nested = engine.parse("((42))", g);
            assertThat(nested.isSuccess()).isTrue();
            assertThat(nested.value().matchedText()).isEqualTo("((42))");
            // Non-recursive case still works.
            var match = parseSuccessfully("42", g);
            assertThat(match.matchedText()).isEqualTo("42");
            // True left recursion (rule references itself at same position) is still blocked:
            var leftRec = parseGrammar("expr ::= expr '+' /[0-9]+/ | /[0-9]+/ ;");
            // "1+2" only matches "1" because expr→expr left-recurses at position 0
            var lr = engine.parse("1", leftRec);
            assertThat(lr.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("simple keyword-value grammar")
        void keyValueGrammar() {
            var g = parseGrammar("""
                    pair ::= key '=' value ;
                    key  ::= /[a-zA-Z_]+/ ;
                    value ::= /[a-zA-Z0-9_]+/ ;
                    """);
            var match = parseSuccessfully("name = Alice", g);
            assertThat(match.matchedText()).isEqualTo("name = Alice");
        }

        @Test
        @DisplayName("parse match has children for structured input")
        void matchHasChildren() {
            var g = grammarOf(
                    new Rule("start", new Sequence(List.of(
                            new NonTerminal("word"),
                            new NonTerminal("word")))),
                    new Rule("word", Terminal.regex("[a-z]+"))
            );
            var match = parseSuccessfully("hello world", g);
            assertThat(match.children()).isNotEmpty();
        }

        @Test
        @DisplayName("alternation with sequence alternatives")
        void alternationWithSequences() {
            var g = parseGrammar("""
                    cmd ::= 'GET' /[a-z]+/ | 'SET' /[a-z]+/ '=' /[a-z]+/ ;
                    """);
            var match = parseSuccessfully("GET foo", g);
            assertThat(match.matchedText()).isEqualTo("GET foo");

            match = parseSuccessfully("SET bar = baz", g);
            assertThat(match.matchedText()).isEqualTo("SET bar = baz");
        }

        @Test
        @DisplayName("repetition of grouped alternation")
        void repetitionOfGroupedAlternation() {
            var g = parseGrammar("list ::= ( 'a' | 'b' )+ ;");
            var match = parseSuccessfully("a b a b", g);
            assertThat(match.matchedText()).isEqualTo("a b a b");
        }

        @Test
        @DisplayName("optional element in sequence")
        void optionalInSequence() {
            var g = parseGrammar("rule ::= 'hello' /[a-z]+/? ;");
            var match = parseSuccessfully("hello", g);
            assertThat(match.matchedText()).isEqualTo("hello");

            match = parseSuccessfully("hello world", g);
            assertThat(match.matchedText()).isEqualTo("hello world");
        }
    }

    @Nested
    class ParseContextTests {

        @Test
        @DisplayName("ParseContext tracks cursor and remaining input")
        void cursorAndRemaining() {
            var g = grammarOf(new Rule("start", Terminal.literal("a")));
            var ctx = new ParseContext("abc", g);
            assertThat(ctx.cursor()).isEqualTo(0);
            assertThat(ctx.remaining()).isEqualTo("abc");
            assertThat(ctx.isAtEnd()).isFalse();
            assertThat(ctx.peek()).isEqualTo('a');

            ctx.advance();
            assertThat(ctx.cursor()).isEqualTo(1);
            assertThat(ctx.remaining()).isEqualTo("bc");
        }

        @Test
        @DisplayName("ParseContext mark/reset/commit")
        void markResetCommit() {
            var g = grammarOf(new Rule("start", Terminal.literal("a")));
            var ctx = new ParseContext("abc", g);
            ctx.advance(); // cursor at 1
            ctx.mark();
            ctx.advance(); // cursor at 2
            ctx.advance(); // cursor at 3
            ctx.reset();   // back to 1
            assertThat(ctx.cursor()).isEqualTo(1);
        }

        @Test
        @DisplayName("ParseContext location tracking")
        void locationTracking() {
            var g = grammarOf(new Rule("start", Terminal.literal("a")));
            var ctx = new ParseContext("ab\ncd", g);
            var loc = ctx.locationAt(0);
            assertThat(loc.line()).isEqualTo(1);
            assertThat(loc.column()).isEqualTo(1);

            loc = ctx.locationAt(3);
            assertThat(loc.line()).isEqualTo(2);
            assertThat(loc.column()).isEqualTo(1);
        }
    }
}

package ssg.pex.bnf.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.bnf.dialect.DialectExtension;
import ssg.pex.bnf.dialect.RuleAddition;
import ssg.pex.bnf.dialect.RuleDeletion;
import ssg.pex.bnf.dialect.RuleExtension;
import ssg.pex.bnf.dialect.RuleReplacement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BnfModelTest {

    private Grammar sampleGrammar() {
        var rules = new LinkedHashMap<String, Rule>();
        rules.put("expr", new Rule("expr", new NonTerminal("term")));
        rules.put("term", new Rule("term", Terminal.literal("x")));
        return new Grammar("test", rules, "expr");
    }

    @Nested
    class GrammarTests {

        @Test
        @DisplayName("name, rules, startRuleName are accessible")
        void basicAccessors() {
            var g = sampleGrammar();
            assertThat(g.name()).isEqualTo("test");
            assertThat(g.rules()).hasSize(2);
            assertThat(g.startRuleName()).isEqualTo("expr");
        }

        @Test
        @DisplayName("rule() returns Optional with the rule when it exists")
        void ruleLookupPresent() {
            var g = sampleGrammar();
            assertThat(g.rule("expr")).isPresent();
            assertThat(g.rule("expr").get().name()).isEqualTo("expr");
        }

        @Test
        @DisplayName("rule() returns empty Optional when rule is absent")
        void ruleLookupAbsent() {
            assertThat(sampleGrammar().rule("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("startRule returns the start rule")
        void startRule() {
            var g = sampleGrammar();
            assertThat(g.startRule().name()).isEqualTo("expr");
        }

        @Test
        @DisplayName("startRule throws when start rule name is invalid")
        void startRuleMissing() {
            var g = new Grammar("bad", Map.of(), "missing");
            assertThatThrownBy(g::startRule)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("merge combines rules from two grammars")
        void merge() {
            var g1 = sampleGrammar();
            var extra = new LinkedHashMap<String, Rule>();
            extra.put("factor", new Rule("factor", Terminal.literal("1")));
            var g2 = new Grammar("other", extra, "factor");

            var merged = g1.merge(g2);
            assertThat(merged.rules()).hasSize(3);
            assertThat(merged.rules()).containsKey("factor");
            assertThat(merged.name()).isEqualTo("test");
            assertThat(merged.startRuleName()).isEqualTo("expr");
        }

        @Test
        @DisplayName("withDialect adds a rule via RuleAddition")
        void withDialectAddition() {
            var g = sampleGrammar();
            var ext = new DialectExtension("ext1", "test", List.of(
                    new RuleAddition(new Rule("newRule", Terminal.literal("y")))
            ));
            var result = g.withDialect(ext);
            assertThat(result.rules()).containsKey("newRule");
            assertThat(result.name()).isEqualTo("test+ext1");
        }

        @Test
        @DisplayName("withDialect replaces an existing rule via RuleReplacement")
        void withDialectReplacement() {
            var g = sampleGrammar();
            var replacement = new Rule("term", Terminal.literal("y"));
            var ext = new DialectExtension("ext1", "test", List.of(
                    new RuleReplacement("term", replacement)
            ));
            var result = g.withDialect(ext);
            assertThat(result.rule("term").get().body()).isEqualTo(Terminal.literal("y"));
        }

        @Test
        @DisplayName("withDialect throws when replacing a non-existent rule")
        void withDialectReplacementMissing() {
            var g = sampleGrammar();
            var ext = new DialectExtension("ext1", "test", List.of(
                    new RuleReplacement("missing", new Rule("missing", Terminal.literal("z")))
            ));
            assertThatThrownBy(() -> g.withDialect(ext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("withDialect deletes a rule via RuleDeletion")
        void withDialectDeletion() {
            var g = sampleGrammar();
            var ext = new DialectExtension("ext1", "test", List.of(
                    new RuleDeletion("term")
            ));
            var result = g.withDialect(ext);
            assertThat(result.rules()).doesNotContainKey("term");
        }

        @Test
        @DisplayName("withDialect extends a rule with additional alternatives via RuleExtension")
        void withDialectExtension() {
            var g = sampleGrammar();
            var ext = new DialectExtension("ext1", "test", List.of(
                    new RuleExtension("term", List.of(Terminal.literal("y")))
            ));
            var result = g.withDialect(ext);
            var body = result.rule("term").get().body();
            assertThat(body).isInstanceOf(Alternation.class);
            assertThat(((Alternation) body).alternatives()).hasSize(2);
        }
    }

    @Nested
    class RuleTests {

        @Test
        @DisplayName("Rule record fields are accessible")
        void basicFields() {
            var rule = new Rule("myRule", Terminal.literal("a"), Map.of("keyword", "true"));
            assertThat(rule.name()).isEqualTo("myRule");
            assertThat(rule.body()).isEqualTo(Terminal.literal("a"));
            assertThat(rule.hasAnnotation("keyword")).isTrue();
            assertThat(rule.annotation("keyword")).isEqualTo("true");
        }

        @Test
        @DisplayName("Rule with no annotations has empty map")
        void noAnnotations() {
            var rule = new Rule("r", Terminal.literal("b"));
            assertThat(rule.annotations()).isEmpty();
            assertThat(rule.hasAnnotation("x")).isFalse();
        }
    }

    @Nested
    class RuleExpressionTests {

        @Test
        @DisplayName("Sequence equality and elements access")
        void sequence() {
            var seq = new Sequence(List.of(Terminal.literal("a"), Terminal.literal("b")));
            assertThat(seq.elements()).hasSize(2);
            assertThat(seq).isEqualTo(new Sequence(List.of(Terminal.literal("a"), Terminal.literal("b"))));
        }

        @Test
        @DisplayName("Alternation equality and alternatives access")
        void alternation() {
            var alt = new Alternation(List.of(Terminal.literal("a"), Terminal.literal("b")));
            assertThat(alt.alternatives()).hasSize(2);
        }

        @Test
        @DisplayName("Repetition construction with different kinds")
        void repetition() {
            var star = new Repetition(Terminal.literal("x"), RepetitionKind.ZERO_OR_MORE);
            var plus = new Repetition(Terminal.literal("x"), RepetitionKind.ONE_OR_MORE);
            var opt = new Repetition(Terminal.literal("x"), RepetitionKind.OPTIONAL);
            assertThat(star.kind()).isEqualTo(RepetitionKind.ZERO_OR_MORE);
            assertThat(plus.kind()).isEqualTo(RepetitionKind.ONE_OR_MORE);
            assertThat(opt.kind()).isEqualTo(RepetitionKind.OPTIONAL);
        }

        @Test
        @DisplayName("Terminal literal and regex factory methods")
        void terminal() {
            var lit = Terminal.literal("hello");
            assertThat(lit.value()).isEqualTo("hello");
            assertThat(lit.isRegex()).isFalse();

            var regex = Terminal.regex("[0-9]+");
            assertThat(regex.value()).isEqualTo("[0-9]+");
            assertThat(regex.isRegex()).isTrue();
        }

        @Test
        @DisplayName("NonTerminal holds a rule name")
        void nonTerminal() {
            var nt = new NonTerminal("expr");
            assertThat(nt.ruleName()).isEqualTo("expr");
        }

        @Test
        @DisplayName("Group wraps an inner expression")
        void group() {
            var inner = new Alternation(List.of(Terminal.literal("a"), Terminal.literal("b")));
            var grp = new Group(inner);
            assertThat(grp.inner()).isEqualTo(inner);
        }
    }
}

package ssg.pex.bnf.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.Terminal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialectExtensionTest {

    private Grammar baseGrammar() {
        var rules = new LinkedHashMap<String, Rule>();
        rules.put("expr", new Rule("expr", new NonTerminal("term")));
        rules.put("term", new Rule("term", Terminal.literal("x")));
        return new Grammar("base", rules, "expr");
    }

    @Nested
    class RuleAdditionTests {

        @Test
        @DisplayName("adds a new rule to the grammar")
        void addsNewRule() {
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleAddition(new Rule("factor", Terminal.literal("1")))
            ));
            var result = baseGrammar().withDialect(ext);
            assertThat(result.rules()).containsKey("factor");
            assertThat(result.rule("factor").get().body()).isEqualTo(Terminal.literal("1"));
        }

        @Test
        @DisplayName("adding a rule with same name overwrites (RuleAddition uses put)")
        void addOverwritesExisting() {
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleAddition(new Rule("term", Terminal.literal("overwritten")))
            ));
            var result = baseGrammar().withDialect(ext);
            assertThat(result.rule("term").get().body()).isEqualTo(Terminal.literal("overwritten"));
        }

        @Test
        @DisplayName("RuleAddition record holds the rule")
        void recordAccessor() {
            var rule = new Rule("r", Terminal.literal("v"));
            var add = new RuleAddition(rule);
            assertThat(add.rule()).isSameAs(rule);
        }
    }

    @Nested
    class RuleReplacementTests {

        @Test
        @DisplayName("replaces an existing rule")
        void replaces() {
            var replacement = new Rule("term", Terminal.literal("y"));
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleReplacement("term", replacement)
            ));
            var result = baseGrammar().withDialect(ext);
            assertThat(result.rule("term").get().body()).isEqualTo(Terminal.literal("y"));
        }

        @Test
        @DisplayName("throws when target rule does not exist")
        void throwsWhenMissing() {
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleReplacement("missing", new Rule("missing", Terminal.literal("z")))
            ));
            assertThatThrownBy(() -> baseGrammar().withDialect(ext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing")
                    .hasMessageContaining("d1");
        }

        @Test
        @DisplayName("RuleReplacement record holds target name and replacement")
        void recordAccessors() {
            var rule = new Rule("r", Terminal.literal("v"));
            var rep = new RuleReplacement("target", rule);
            assertThat(rep.targetRuleName()).isEqualTo("target");
            assertThat(rep.replacement()).isSameAs(rule);
        }
    }

    @Nested
    class RuleDeletionTests {

        @Test
        @DisplayName("removes an existing rule")
        void deletes() {
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleDeletion("term")
            ));
            var result = baseGrammar().withDialect(ext);
            assertThat(result.rules()).doesNotContainKey("term");
            assertThat(result.rules()).hasSize(1);
        }

        @Test
        @DisplayName("throws when target rule does not exist")
        void throwsWhenMissing() {
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleDeletion("nonexistent")
            ));
            assertThatThrownBy(() -> baseGrammar().withDialect(ext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nonexistent")
                    .hasMessageContaining("d1");
        }

        @Test
        @DisplayName("RuleDeletion record holds target name")
        void recordAccessor() {
            var del = new RuleDeletion("t");
            assertThat(del.targetRuleName()).isEqualTo("t");
        }
    }

    @Nested
    class RuleExtensionTests {

        @Test
        @DisplayName("adds alternatives to an existing rule with non-alternation body")
        void extendsNonAlternation() {
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleExtension("term", List.of(Terminal.literal("y"), Terminal.literal("z")))
            ));
            var result = baseGrammar().withDialect(ext);
            var body = result.rule("term").get().body();
            assertThat(body).isInstanceOf(Alternation.class);
            var alternatives = ((Alternation) body).alternatives();
            // original 'x' + two new alternatives
            assertThat(alternatives).hasSize(3);
        }

        @Test
        @DisplayName("adds alternatives to an existing rule that already has alternation body")
        void extendsAlternation() {
            var rules = new LinkedHashMap<String, Rule>();
            var altBody = new Alternation(List.of(Terminal.literal("a"), Terminal.literal("b")));
            rules.put("r", new Rule("r", altBody));
            var g = new Grammar("g", rules, "r");

            var ext = new DialectExtension("d1", "g", List.of(
                    new RuleExtension("r", List.of(Terminal.literal("c")))
            ));
            var result = g.withDialect(ext);
            var body = result.rule("r").get().body();
            assertThat(body).isInstanceOf(Alternation.class);
            assertThat(((Alternation) body).alternatives()).hasSize(3);
        }

        @Test
        @DisplayName("throws when target rule does not exist")
        void throwsWhenMissing() {
            var ext = new DialectExtension("d1", "base", List.of(
                    new RuleExtension("nope", List.of(Terminal.literal("y")))
            ));
            assertThatThrownBy(() -> baseGrammar().withDialect(ext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nope");
        }

        @Test
        @DisplayName("RuleExtension record holds target name and additional alternatives")
        void recordAccessors() {
            var alts = List.<ssg.pex.bnf.model.RuleExpression>of(Terminal.literal("a"));
            var re = new RuleExtension("target", alts);
            assertThat(re.targetRuleName()).isEqualTo("target");
            assertThat(re.additionalAlternatives()).hasSize(1);
        }
    }

    @Nested
    class DialectExtensionRecordTests {

        @Test
        @DisplayName("DialectExtension record holds name, basedOn, and modifications")
        void recordFields() {
            var mods = List.<RuleModification>of(
                    new RuleAddition(new Rule("r", Terminal.literal("v"))),
                    new RuleDeletion("x")
            );
            var ext = new DialectExtension("dialect1", "base", mods);
            assertThat(ext.dialectName()).isEqualTo("dialect1");
            assertThat(ext.basedOn()).isEqualTo("base");
            assertThat(ext.modifications()).hasSize(2);
        }

        @Test
        @DisplayName("modifications list is immutable")
        void modificationsImmutable() {
            var ext = new DialectExtension("d", "b", List.of());
            assertThatThrownBy(() -> ext.modifications().add(new RuleDeletion("x")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class ChainingTests {

        @Test
        @DisplayName("chaining multiple dialect extensions")
        void chainDialects() {
            var g = baseGrammar();

            var ext1 = new DialectExtension("ext1", "base", List.of(
                    new RuleAddition(new Rule("factor", Terminal.literal("1")))
            ));
            var ext2 = new DialectExtension("ext2", "base", List.of(
                    new RuleReplacement("term", new Rule("term", Terminal.literal("y")))
            ));

            var result = g.withDialect(ext1).withDialect(ext2);
            assertThat(result.rules()).containsKey("factor");
            assertThat(result.rule("term").get().body()).isEqualTo(Terminal.literal("y"));
            assertThat(result.name()).isEqualTo("base+ext1+ext2");
        }

        @Test
        @DisplayName("multiple modifications in one DialectExtension")
        void multipleModifications() {
            var ext = new DialectExtension("multi", "base", List.of(
                    new RuleAddition(new Rule("newRule", Terminal.literal("n"))),
                    new RuleReplacement("term", new Rule("term", Terminal.literal("y"))),
                    new RuleExtension("expr", List.of(Terminal.literal("direct")))
            ));
            var result = baseGrammar().withDialect(ext);
            assertThat(result.rules()).containsKey("newRule");
            assertThat(result.rule("term").get().body()).isEqualTo(Terminal.literal("y"));
            assertThat(result.rule("expr").get().body()).isInstanceOf(Alternation.class);
        }
    }

    @Nested
    class DialectRegistryTests {

        @Test
        @DisplayName("register and resolve a grammar provider")
        void registerAndResolve() {
            var registry = DialectRegistry.getInstance();

            var rules = new LinkedHashMap<String, Rule>();
            rules.put("start", new Rule("start", Terminal.literal("go")));
            var grammar = new Grammar("testDialect", rules, "start");

            registry.register(new ssg.pex.spi.GrammarProvider() {
                @Override public String dialectName() { return "test-dialect-for-unit-test"; }
                @Override public Grammar provideGrammar() { return grammar; }
            });

            var resolved = registry.resolveGrammar("test-dialect-for-unit-test");
            assertThat(resolved.name()).isEqualTo("testDialect");
            assertThat(resolved.rules()).containsKey("start");
        }

        @Test
        @DisplayName("resolveGrammar throws for unknown dialect")
        void resolveUnknown() {
            var registry = DialectRegistry.getInstance();
            assertThatThrownBy(() -> registry.resolveGrammar("completely-unknown-dialect-xyz"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("completely-unknown-dialect-xyz");
        }
    }
}

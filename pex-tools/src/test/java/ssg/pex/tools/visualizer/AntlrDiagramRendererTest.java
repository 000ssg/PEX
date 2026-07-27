package ssg.pex.tools.visualizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.tools.converter.antlr.AntlrExpression;
import ssg.pex.tools.converter.antlr.AntlrExpression.*;
import ssg.pex.tools.converter.antlr.AntlrRule;
import ssg.pex.tools.converter.antlr.AntlrRule.RuleKind;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AntlrDiagramRendererTest {

    private AntlrDiagramRenderer renderer;
    private DiagramStyle style;
    private Graphics2D graphics;

    @BeforeEach
    void setUp() {
        renderer = new AntlrDiagramRenderer();
        style = DiagramStyle.defaultStyle();
        var img = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_ARGB);
        graphics = img.createGraphics();
        graphics.setFont(style.textFont());
    }

    // ---- Literal ----

    @Nested
    class LiteralTests {
        @Test
        void testMeasureLiteral() {
            var lit = new Literal("hello");
            var metrics = renderer.measure(lit, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderLiteral() {
            var lit = new Literal("hello");
            int railY = renderer.render(lit, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- CharClass ----

    @Nested
    class CharClassTests {
        @Test
        void testMeasureCharClass() {
            var cc = new CharClass("[a-zA-Z]");
            var metrics = renderer.measure(cc, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderCharClass() {
            var cc = new CharClass("[a-zA-Z]");
            int railY = renderer.render(cc, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- RuleRef ----

    @Nested
    class RuleRefTests {
        @Test
        void testMeasureRuleRef() {
            var rr = new RuleRef("expression");
            var metrics = renderer.measure(rr, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderRuleRef() {
            var rr = new RuleRef("expression");
            int railY = renderer.render(rr, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- TokenRef ----

    @Nested
    class TokenRefTests {
        @Test
        void testMeasureTokenRef() {
            var tr = new TokenRef("INT");
            var metrics = renderer.measure(tr, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderTokenRef() {
            var tr = new TokenRef("INT");
            int railY = renderer.render(tr, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testTokenRefWiderThanRuleRefForSameName() {
            // TokenRef uses bold font, so it should be at least as wide
            var tr = new TokenRef("IDENT");
            var rr = new RuleRef("IDENT");
            var trMetrics = renderer.measure(tr, graphics, style);
            var rrMetrics = renderer.measure(rr, graphics, style);
            assertThat(trMetrics.width()).isGreaterThanOrEqualTo(rrMetrics.width());
        }
    }

    // ---- Seq ----

    @Nested
    class SeqTests {
        @Test
        void testMeasureSeq() {
            var seq = new Seq(List.of(
                    new Literal("a"), new RuleRef("b"), new Literal("c")));
            var metrics = renderer.measure(seq, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderSeq() {
            var seq = new Seq(List.of(
                    new Literal("a"), new RuleRef("b"), new Literal("c")));
            int railY = renderer.render(seq, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testSeqWidthIncludesArrowGaps() {
            var a = new Literal("a");
            var b = new Literal("b");
            var seq = new Seq(List.of(a, b));

            var mA = renderer.measure(a, graphics, style);
            var mB = renderer.measure(b, graphics, style);
            var mSeq = renderer.measure(seq, graphics, style);

            assertThat(mSeq.width()).isGreaterThan(mA.width() + mB.width());
        }
    }

    // ---- Alt ----

    @Nested
    class AltTests {
        @Test
        void testMeasureAlt() {
            var alt = new Alt(List.of(
                    new Literal("x"), new Literal("y"), new RuleRef("z")));
            var metrics = renderer.measure(alt, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderAlt() {
            var alt = new Alt(List.of(
                    new Literal("x"), new Literal("y"), new RuleRef("z")));
            int railY = renderer.render(alt, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testAltHeightGreaterThanSingleAlternative() {
            var a1 = new Literal("alpha");
            var alt = new Alt(List.of(a1, new Literal("beta"), new Literal("gamma")));
            var m1 = renderer.measure(a1, graphics, style);
            var mAlt = renderer.measure(alt, graphics, style);
            assertThat(mAlt.height()).isGreaterThan(m1.height());
        }
    }

    // ---- ZeroOrMore ----

    @Nested
    class ZeroOrMoreTests {
        @Test
        void testMeasureZeroOrMore() {
            var zom = new ZeroOrMore(new Literal("item"));
            var metrics = renderer.measure(zom, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderZeroOrMore() {
            var zom = new ZeroOrMore(new Literal("item"));
            int railY = renderer.render(zom, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- OneOrMore ----

    @Nested
    class OneOrMoreTests {
        @Test
        void testMeasureOneOrMore() {
            var oom = new OneOrMore(new RuleRef("digit"));
            var metrics = renderer.measure(oom, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderOneOrMore() {
            var oom = new OneOrMore(new RuleRef("digit"));
            int railY = renderer.render(oom, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- Optional ----

    @Nested
    class OptionalTests {
        @Test
        void testMeasureOptional() {
            var opt = new Optional(new Literal("sign"));
            var metrics = renderer.measure(opt, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderOptional() {
            var opt = new Optional(new Literal("sign"));
            int railY = renderer.render(opt, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- Group ----

    @Nested
    class GroupTests {
        @Test
        void testGroupMeasureSameAsInner() {
            var inner = new Literal("content");
            var group = new Group(inner);
            var innerMetrics = renderer.measure(inner, graphics, style);
            var groupMetrics = renderer.measure(group, graphics, style);
            assertThat(groupMetrics.width()).isEqualTo(innerMetrics.width());
            assertThat(groupMetrics.height()).isEqualTo(innerMetrics.height());
        }

        @Test
        void testRenderGroup() {
            var group = new Group(new Literal("x"));
            int railY = renderer.render(group, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- Dot ----

    @Nested
    class DotTests {
        @Test
        void testMeasureDot() {
            var dot = new Dot();
            var metrics = renderer.measure(dot, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderDot() {
            var dot = new Dot();
            int railY = renderer.render(dot, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- Negation (ANTLR-specific) ----

    @Nested
    class NegationTests {
        @Test
        void testMeasureNegation() {
            var neg = new Negation(new Literal("x"));
            var metrics = renderer.measure(neg, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderNegation() {
            var neg = new Negation(new CharClass("[a-z]"));
            int railY = renderer.render(neg, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testNegationWiderThanInner() {
            var inner = new Literal("x");
            var neg = new Negation(inner);
            var innerMetrics = renderer.measure(inner, graphics, style);
            var negMetrics = renderer.measure(neg, graphics, style);
            assertThat(negMetrics.width()).isGreaterThan(innerMetrics.width());
        }
    }

    // ---- Predicate (ANTLR-specific) ----

    @Nested
    class PredicateTests {
        @Test
        void testMeasurePredicate() {
            var pred = new Predicate("isValid()");
            var metrics = renderer.measure(pred, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderPredicate() {
            var pred = new Predicate("isValid()");
            int railY = renderer.render(pred, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testPredicateWithLongCodeTruncates() {
            var pred = new Predicate("someVeryLongPredicateMethodName()");
            var metrics = renderer.measure(pred, graphics, style);
            // Should still measure OK (truncated text)
            assertThat(metrics.width()).isGreaterThan(0);
        }
    }

    // ---- Action (ANTLR-specific) ----

    @Nested
    class ActionTests {
        @Test
        void testMeasureAction() {
            var act = new Action("doSomething()");
            var metrics = renderer.measure(act, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderAction() {
            var act = new Action("doSomething()");
            int railY = renderer.render(act, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- LabeledElement (ANTLR-specific) ----

    @Nested
    class LabeledElementTests {
        @Test
        void testMeasureLabeledElement() {
            var le = new LabeledElement("left", false, new RuleRef("expr"));
            var metrics = renderer.measure(le, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderLabeledElement() {
            var le = new LabeledElement("left", false, new RuleRef("expr"));
            int railY = renderer.render(le, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testLabeledElementTallerThanInner() {
            var inner = new RuleRef("expr");
            var le = new LabeledElement("op", false, inner);
            var innerMetrics = renderer.measure(inner, graphics, style);
            var leMetrics = renderer.measure(le, graphics, style);
            assertThat(leMetrics.height()).isGreaterThan(innerMetrics.height());
        }

        @Test
        void testListLabelElement() {
            var le = new LabeledElement("args", true, new RuleRef("expr"));
            var metrics = renderer.measure(le, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            int railY = renderer.render(le, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }
    }

    // ---- Rule rendering ----

    @Nested
    class RuleTests {
        @Test
        void testMeasureParserRule() {
            var rule = new AntlrRule("expr",
                    new Alt(List.of(new RuleRef("term"), new RuleRef("factor"))),
                    RuleKind.PARSER, List.of(), List.of());
            var metrics = renderer.measureRule(rule, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            assertThat(metrics.height()).isGreaterThan(0);
        }

        @Test
        void testRenderParserRule() {
            var rule = new AntlrRule("expr",
                    new Alt(List.of(new RuleRef("term"), new RuleRef("factor"))),
                    RuleKind.PARSER, List.of(), List.of());
            int railY = renderer.renderRule(rule, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testRenderLexerRule() {
            var rule = new AntlrRule("INT",
                    new OneOrMore(new CharClass("[0-9]")),
                    RuleKind.LEXER, List.of(), List.of());
            int railY = renderer.renderRule(rule, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testRenderFragmentRule() {
            var rule = new AntlrRule("DIGIT",
                    new CharClass("[0-9]"),
                    RuleKind.FRAGMENT, List.of(), List.of());
            int railY = renderer.renderRule(rule, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testRenderRuleWithCommands() {
            var rule = new AntlrRule("WS",
                    new OneOrMore(new CharClass("[ \\t\\r\\n]")),
                    RuleKind.LEXER, List.of(), List.of("skip"));
            var metrics = renderer.measureRule(rule, graphics, style);
            assertThat(metrics.width()).isGreaterThan(0);
            int railY = renderer.renderRule(rule, graphics, 10, 10, style);
            assertThat(railY).isGreaterThan(0);
        }

        @Test
        void testRuleWithCommandsWiderThanWithout() {
            var body = new OneOrMore(new CharClass("[ \\t]"));
            var withCmd = new AntlrRule("WS", body, RuleKind.LEXER,
                    List.of(), List.of("skip"));
            var withoutCmd = new AntlrRule("WS", body, RuleKind.LEXER,
                    List.of(), List.of());
            var mWith = renderer.measureRule(withCmd, graphics, style);
            var mWithout = renderer.measureRule(withoutCmd, graphics, style);
            assertThat(mWith.width()).isGreaterThan(mWithout.width());
        }

        @Test
        void testMeasureRuleIncludesLabel() {
            var rule = new AntlrRule("myRule", new Literal("x"),
                    RuleKind.PARSER, List.of(), List.of());
            var ruleMetrics = renderer.measureRule(rule, graphics, style);
            var bodyMetrics = renderer.measure(rule.body(), graphics, style);
            assertThat(ruleMetrics.width()).isGreaterThan(bodyMetrics.width());
        }
    }

    // ---- Complex expressions ----

    @Nested
    class ComplexExpressionTests {
        @Test
        void testRenderComplexExpressionNoExceptions() {
            // expr : left=term (op=('+' | '-') right=term)* ;
            var expr = new Seq(List.of(
                    new LabeledElement("left", false, new RuleRef("term")),
                    new ZeroOrMore(new Seq(List.of(
                            new LabeledElement("op", false,
                                    new Alt(List.of(
                                            new Literal("+"),
                                            new Literal("-")))),
                            new LabeledElement("right", false,
                                    new RuleRef("term")))))));

            assertThatNoException().isThrownBy(() -> {
                var metrics = renderer.measure(expr, graphics, style);
                assertThat(metrics.width()).isGreaterThan(0);
                assertThat(metrics.height()).isGreaterThan(0);
                renderer.render(expr, graphics, 10, 10, style);
            });
        }

        @Test
        void testRenderNestedNegation() {
            // ~('a' | 'b')
            var neg = new Negation(new Alt(List.of(
                    new Literal("a"), new Literal("b"))));
            assertThatNoException().isThrownBy(() -> {
                renderer.measure(neg, graphics, style);
                renderer.render(neg, graphics, 10, 10, style);
            });
        }

        @Test
        void testRenderPredicateInSequence() {
            // {isMode()}? ID
            var seq = new Seq(List.of(
                    new Predicate("isMode()"),
                    new TokenRef("ID")));
            assertThatNoException().isThrownBy(() -> {
                renderer.measure(seq, graphics, style);
                renderer.render(seq, graphics, 10, 10, style);
            });
        }

        @Test
        void testRenderDotInSequence() {
            // . -> skip
            var dot = new Dot();
            assertThatNoException().isThrownBy(() -> {
                renderer.measure(dot, graphics, style);
                renderer.render(dot, graphics, 10, 10, style);
            });
        }

        @Test
        void testRenderActionInSequence() {
            var seq = new Seq(List.of(
                    new TokenRef("INT"),
                    new Action("setValue($INT.int)")));
            assertThatNoException().isThrownBy(() -> {
                renderer.measure(seq, graphics, style);
                renderer.render(seq, graphics, 10, 10, style);
            });
        }
    }

    // ---- Panel tests ----

    @Nested
    class PanelTests {
        @Test
        void testPanelPreferredSizeWithSingleRule() {
            var rule = new AntlrRule("test", new Literal("hello"),
                    RuleKind.PARSER, List.of(), List.of());
            var panel = new AntlrMultiRulePanel(style);
            panel.setRule(rule);

            var prefSize = panel.getPreferredSize();
            assertThat(prefSize.width).isGreaterThan(0);
            assertThat(prefSize.height).isGreaterThan(0);
        }

        @Test
        void testPanelPreferredSizeWithMultipleRules() {
            var rules = List.of(
                    new AntlrRule("expr",
                            new Alt(List.of(new RuleRef("term"), new RuleRef("factor"))),
                            RuleKind.PARSER, List.of(), List.of()),
                    new AntlrRule("INT",
                            new OneOrMore(new CharClass("[0-9]")),
                            RuleKind.LEXER, List.of(), List.of()),
                    new AntlrRule("DIGIT",
                            new CharClass("[0-9]"),
                            RuleKind.FRAGMENT, List.of(), List.of()));

            var panel = new AntlrMultiRulePanel(style);
            panel.setRules(rules);

            var prefSize = panel.getPreferredSize();
            assertThat(prefSize.width).isGreaterThan(0);
            assertThat(prefSize.height).isGreaterThan(0);
            assertThat(panel.getRules()).hasSize(3);
        }

        @Test
        void testPanelPaintDoesNotThrow() {
            var rule = new AntlrRule("test", new Literal("hello"),
                    RuleKind.PARSER, List.of(), List.of());
            var panel = new AntlrMultiRulePanel(style);
            panel.setRule(rule);
            panel.setSize(800, 600);

            var img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            var g = img.createGraphics();
            assertThatNoException().isThrownBy(() -> panel.paintComponent(g));
            g.dispose();
        }

        @Test
        void testPanelPaintMultipleRulesDoesNotThrow() {
            var rules = List.of(
                    new AntlrRule("expr", new RuleRef("term"),
                            RuleKind.PARSER, List.of(), List.of()),
                    new AntlrRule("WS",
                            new OneOrMore(new CharClass("[ \\t]")),
                            RuleKind.LEXER, List.of(), List.of("skip")));

            var panel = new AntlrMultiRulePanel(style);
            panel.setRules(rules);
            panel.setSize(800, 600);

            var img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            var g = img.createGraphics();
            assertThatNoException().isThrownBy(() -> panel.paintComponent(g));
            g.dispose();
        }

        @Test
        void testPanelEmptyRulesDoesNotThrow() {
            var panel = new AntlrMultiRulePanel(style);
            panel.setSize(800, 600);

            var img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            var g = img.createGraphics();
            assertThatNoException().isThrownBy(() -> panel.paintComponent(g));
            g.dispose();
        }

        @Test
        void testPanelSetNullRule() {
            var panel = new AntlrMultiRulePanel(style);
            panel.setRule(null);
            assertThat(panel.getRules()).isEmpty();
        }

        @Test
        void testPanelSetNullRules() {
            var panel = new AntlrMultiRulePanel(style);
            panel.setRules(null);
            assertThat(panel.getRules()).isEmpty();
        }

        @Test
        void testPanelSetDiagramStyle() {
            var panel = new AntlrMultiRulePanel(style);
            var newStyle = DiagramStyle.defaultStyle();
            assertThatNoException().isThrownBy(() -> panel.setDiagramStyle(newStyle));
        }
    }

    // ---- Measurement accuracy ----

    @Nested
    class MeasurementAccuracyTests {
        @Test
        void testAllConstructsHavePositiveDimensions() {
            List<AntlrExpression> expressions = List.of(
                    new Literal("test"),
                    new CharClass("[a-z]"),
                    new RuleRef("rule"),
                    new TokenRef("TOKEN"),
                    new Seq(List.of(new Literal("a"), new Literal("b"))),
                    new Alt(List.of(new Literal("x"), new Literal("y"))),
                    new ZeroOrMore(new Literal("z")),
                    new OneOrMore(new Literal("w")),
                    new Optional(new Literal("o")),
                    new Group(new Literal("g")),
                    new Dot(),
                    new Negation(new Literal("n")),
                    new Predicate("pred()"),
                    new Action("act()"),
                    new LabeledElement("lbl", false, new Literal("v"))
            );

            for (var expr : expressions) {
                var metrics = renderer.measure(expr, graphics, style);
                assertThat(metrics.width())
                        .as("Width for %s", expr.getClass().getSimpleName())
                        .isGreaterThan(0);
                assertThat(metrics.height())
                        .as("Height for %s", expr.getClass().getSimpleName())
                        .isGreaterThan(0);
            }
        }

        @Test
        void testAllConstructsRenderWithoutException() {
            List<AntlrExpression> expressions = List.of(
                    new Literal("test"),
                    new CharClass("[a-z]"),
                    new RuleRef("rule"),
                    new TokenRef("TOKEN"),
                    new Seq(List.of(new Literal("a"), new Literal("b"))),
                    new Alt(List.of(new Literal("x"), new Literal("y"))),
                    new ZeroOrMore(new Literal("z")),
                    new OneOrMore(new Literal("w")),
                    new Optional(new Literal("o")),
                    new Group(new Literal("g")),
                    new Dot(),
                    new Negation(new Literal("n")),
                    new Predicate("pred()"),
                    new Action("act()"),
                    new LabeledElement("lbl", false, new Literal("v"))
            );

            for (var expr : expressions) {
                assertThatNoException()
                        .as("Render for %s", expr.getClass().getSimpleName())
                        .isThrownBy(() -> renderer.render(expr, graphics, 10, 10, style));
            }
        }
    }
}

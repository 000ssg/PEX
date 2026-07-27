package ssg.pex.tools.visualizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;

import java.awt.Dimension;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MultiRulePanel} rendering logic and sizing.
 */
class MultiRulePanelTest {

    private MultiRulePanel panel;
    private DiagramStyle style;

    private static final Rule RULE_A = new Rule("alpha",
            new Terminal("HELLO", false), Map.of());
    private static final Rule RULE_B = new Rule("beta",
            new Alternation(List.of(new Terminal("X", false), new Terminal("Y", false))),
            Map.of());
    private static final Rule RULE_C = new Rule("gamma",
            new Sequence(List.of(new NonTerminal("alpha"), new Terminal("+", false), new NonTerminal("beta"))),
            Map.of());

    @BeforeEach
    void setUp() {
        style = DiagramStyle.defaultStyle();
        panel = new MultiRulePanel(style);
    }

    @Test
    void testEmptyPanel() {
        assertThat(panel.getRules()).isEmpty();
        Dimension size = panel.getPreferredSize();
        assertThat(size.width).isGreaterThanOrEqualTo(200);
        assertThat(size.height).isGreaterThanOrEqualTo(100);
    }

    @Test
    void testSetSingleRule() {
        panel.setRule(RULE_A);

        assertThat(panel.getRules()).hasSize(1);
        assertThat(panel.getRules().getFirst().name()).isEqualTo("alpha");

        Dimension size = panel.getPreferredSize();
        assertThat(size.width).isGreaterThan(0);
        assertThat(size.height).isGreaterThan(0);
    }

    @Test
    void testSetMultipleRules() {
        panel.setRules(List.of(RULE_A, RULE_B, RULE_C));

        assertThat(panel.getRules()).hasSize(3);
        assertThat(panel.getRules().stream().map(Rule::name).toList())
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void testMultiRulePanelTallerThanSingle() {
        panel.setRule(RULE_A);
        Dimension singleSize = panel.getPreferredSize();

        panel.setRules(List.of(RULE_A, RULE_B, RULE_C));
        Dimension multiSize = panel.getPreferredSize();

        assertThat(multiSize.height).isGreaterThan(singleSize.height);
    }

    @Test
    void testSetNullRuleClearsPanel() {
        panel.setRule(RULE_A);
        assertThat(panel.getRules()).hasSize(1);

        panel.setRule(null);
        assertThat(panel.getRules()).isEmpty();
    }

    @Test
    void testSetNullRulesClearsPanel() {
        panel.setRules(List.of(RULE_A, RULE_B));
        assertThat(panel.getRules()).hasSize(2);

        panel.setRules(null);
        assertThat(panel.getRules()).isEmpty();
    }

    @Test
    void testSetEmptyRulesList() {
        panel.setRules(List.of(RULE_A));
        panel.setRules(List.of());
        assertThat(panel.getRules()).isEmpty();
    }

    @Test
    void testRulesListIsUnmodifiable() {
        panel.setRules(List.of(RULE_A, RULE_B));
        var rules = panel.getRules();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> rules.add(RULE_C));
    }

    @Test
    void testSetRulesReplacesOld() {
        panel.setRules(List.of(RULE_A));
        panel.setRules(List.of(RULE_B, RULE_C));

        assertThat(panel.getRules()).hasSize(2);
        assertThat(panel.getRules().getFirst().name()).isEqualTo("beta");
    }

    @Test
    void testStyleChange() {
        panel.setRule(RULE_A);
        Dimension before = panel.getPreferredSize();

        // Change to a larger font style
        var bigStyle = new DiagramStyle(
                style.terminalFillColor(), style.nonTerminalFillColor(), style.regexFillColor(),
                style.textFont().deriveFont(24f), style.textColor(), style.lineColor(),
                style.arrowColor(), style.backgroundColor(),
                style.padding(), style.arrowSize(), style.gapH(), style.gapV(), style.cornerRadius());
        panel.setDiagramStyle(bigStyle);
        Dimension after = panel.getPreferredSize();

        // Bigger font → bigger diagram
        assertThat(after.width).isGreaterThanOrEqualTo(before.width);
        assertThat(after.height).isGreaterThanOrEqualTo(before.height);
    }

    @Test
    void testPaintComponentDoesNotThrow() {
        panel.setRules(List.of(RULE_A, RULE_B, RULE_C));
        panel.setSize(800, 600);

        // Create an off-screen image and paint into it
        var img = new java.awt.image.BufferedImage(800, 600, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();

        // Should not throw
        panel.paintComponent(g);
        g.dispose();
    }

    @Test
    void testPaintSingleRuleDoesNotThrow() {
        panel.setRule(RULE_A);
        panel.setSize(400, 300);

        var img = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        panel.paintComponent(g);
        g.dispose();
    }

    @Test
    void testPaintEmptyDoesNotThrow() {
        panel.setSize(400, 300);
        var img = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        panel.paintComponent(g);
        g.dispose();
    }
}

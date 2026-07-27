package ssg.pex.tools.visualizer;

import ssg.pex.bnf.model.Rule;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Swing panel that renders a railroad diagram for a single grammar rule.
 *
 * <p>Calculates its preferred size from {@link DiagramMetrics} and paints
 * via {@link RailroadDiagramRenderer}.
 */
public class RailroadPanel extends JPanel {

    private Rule rule;
    private DiagramStyle style;
    private final RailroadDiagramRenderer renderer = new RailroadDiagramRenderer();

    /**
     * Create a panel for the given rule and style.
     */
    public RailroadPanel(Rule rule, DiagramStyle style) {
        this.rule = rule;
        this.style = style;
        setBackground(style.backgroundColor());
        recalculatePreferredSize();
    }

    /**
     * Update the rule being displayed.
     */
    public void setRule(Rule rule) {
        this.rule = rule;
        recalculatePreferredSize();
        revalidate();
        repaint();
    }

    /**
     * Update the diagram style.
     */
    public void setDiagramStyle(DiagramStyle style) {
        this.style = style;
        setBackground(style.backgroundColor());
        recalculatePreferredSize();
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (rule == null || style == null) return;

        var g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int margin = style.padding() * 2;
        renderer.renderRule(rule, g, margin, margin, style);
    }

    private void recalculatePreferredSize() {
        if (rule == null || style == null) {
            setPreferredSize(new Dimension(200, 100));
            return;
        }

        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setFont(style.textFont());

        var metrics = renderer.measureRule(rule, g, style);
        g.dispose();

        int margin = style.padding() * 2;
        setPreferredSize(new Dimension(
                metrics.width() + margin * 2,
                metrics.height() + margin * 2
        ));
    }
}

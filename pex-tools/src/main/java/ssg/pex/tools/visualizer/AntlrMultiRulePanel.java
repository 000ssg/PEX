package ssg.pex.tools.visualizer;

import ssg.pex.tools.converter.antlr.AntlrRule;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * Swing panel that renders one or more ANTLR grammar rules as railroad diagrams.
 *
 * <p>When a single rule is displayed, the diagram is rendered without a border
 * or rule-name header. When multiple rules are selected, each rule is drawn
 * inside a titled bordered section with the rule name as a header label.
 *
 * <p>Rule kind affects title styling:
 * <ul>
 *   <li>Parser rules: title in blue (standard)</li>
 *   <li>Lexer rules: title in green, bold</li>
 *   <li>Fragment rules: title in grey, italic</li>
 * </ul>
 */
public final class AntlrMultiRulePanel extends JPanel {

    private static final int BORDER_PADDING = 12;
    private static final int TITLE_GAP = 6;
    private static final int SECTION_GAP = 16;
    private static final Color BORDER_COLOR = new Color(140, 140, 140);
    private static final Color PARSER_TITLE_COLOR = new Color(40, 40, 120);
    private static final Color LEXER_TITLE_COLOR = new Color(40, 120, 40);
    private static final Color FRAGMENT_TITLE_COLOR = new Color(140, 140, 140);

    private List<AntlrRule> rules = Collections.emptyList();
    private DiagramStyle style;
    private final AntlrDiagramRenderer renderer = new AntlrDiagramRenderer();

    public AntlrMultiRulePanel(DiagramStyle style) {
        this.style = style;
        setBackground(style.backgroundColor());
        recalculatePreferredSize();
    }

    /**
     * Display a single rule (no border/title header).
     */
    public void setRule(AntlrRule rule) {
        this.rules = rule != null ? List.of(rule) : Collections.emptyList();
        recalculatePreferredSize();
        revalidate();
        repaint();
    }

    /**
     * Display multiple rules, each with a titled border when more than one.
     */
    public void setRules(List<AntlrRule> rules) {
        this.rules = rules != null ? List.copyOf(rules) : Collections.emptyList();
        recalculatePreferredSize();
        revalidate();
        repaint();
    }

    /**
     * Returns the currently displayed rules (unmodifiable).
     */
    public List<AntlrRule> getRules() {
        return rules;
    }

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
        if (rules.isEmpty() || style == null) return;

        var g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int margin = style.padding() * 2;
        boolean multi = rules.size() > 1;

        if (!multi) {
            renderer.renderRule(rules.getFirst(), g, margin, margin, style);
            return;
        }

        // Multiple rules: each in a titled, bordered section
        int yOffset = margin;

        for (var rule : rules) {
            var titleFont = titleFont(rule, style);
            var metrics = measureWithGraphics(rule);
            FontMetrics fm = g.getFontMetrics(titleFont);
            int titleHeight = fm.getHeight();

            int sectionContentW = metrics.width() + BORDER_PADDING * 2;
            int sectionContentH = metrics.height() + BORDER_PADDING * 2;
            int sectionW = Math.max(sectionContentW,
                    fm.stringWidth(rule.name()) + BORDER_PADDING * 2);
            int sectionH = titleHeight + TITLE_GAP + sectionContentH;

            // Draw border rectangle
            g.setColor(BORDER_COLOR);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(margin, yOffset, sectionW, sectionH, 8, 8);

            // Draw title with kind-appropriate styling
            g.setColor(titleColor(rule));
            g.setFont(titleFont);
            g.drawString(rule.name(), margin + BORDER_PADDING,
                    yOffset + fm.getAscent() + 2);

            // Draw separator line under title
            g.setColor(BORDER_COLOR);
            g.setStroke(new BasicStroke(0.5f));
            int lineY = yOffset + titleHeight + TITLE_GAP / 2;
            g.drawLine(margin + 1, lineY, margin + sectionW - 1, lineY);

            // Draw the diagram inside the bordered area
            int diagramX = margin + BORDER_PADDING;
            int diagramY = yOffset + titleHeight + TITLE_GAP;
            renderer.renderRule(rule, g, diagramX, diagramY, style);

            yOffset += sectionH + SECTION_GAP;
        }
    }

    private void recalculatePreferredSize() {
        if (rules.isEmpty() || style == null) {
            setPreferredSize(new Dimension(200, 100));
            return;
        }

        int margin = style.padding() * 2;
        boolean multi = rules.size() > 1;

        if (!multi) {
            var metrics = measureWithGraphics(rules.getFirst());
            setPreferredSize(new Dimension(
                    metrics.width() + margin * 2,
                    metrics.height() + margin * 2));
            return;
        }

        // Multiple: sum heights with titled borders
        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();

        int maxWidth = 200;
        int totalHeight = margin;

        for (var rule : rules) {
            var titleFont = titleFont(rule, style);
            FontMetrics fm = g.getFontMetrics(titleFont);
            int titleHeight = fm.getHeight();

            var metrics = measureWithGraphics(rule);
            int sectionContentW = metrics.width() + BORDER_PADDING * 2;
            int sectionW = Math.max(sectionContentW,
                    fm.stringWidth(rule.name()) + BORDER_PADDING * 2);
            maxWidth = Math.max(maxWidth, sectionW + margin * 2);

            int sectionContentH = metrics.height() + BORDER_PADDING * 2;
            int sectionH = titleHeight + TITLE_GAP + sectionContentH;
            totalHeight += sectionH + SECTION_GAP;
        }

        g.dispose();
        setPreferredSize(new Dimension(maxWidth, totalHeight));
    }

    private DiagramMetrics measureWithGraphics(AntlrRule rule) {
        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setFont(style.textFont());
        var metrics = renderer.measureRule(rule, g, style);
        g.dispose();
        return metrics;
    }

    private static Font titleFont(AntlrRule rule, DiagramStyle style) {
        var baseFont = style.textFont().deriveFont(Font.BOLD,
                style.textFont().getSize2D() + 2);
        return switch (rule.kind()) {
            case PARSER -> baseFont;
            case LEXER -> baseFont; // already bold from base
            case FRAGMENT -> style.textFont().deriveFont(Font.BOLD | Font.ITALIC,
                    style.textFont().getSize2D() + 2);
        };
    }

    private static Color titleColor(AntlrRule rule) {
        return switch (rule.kind()) {
            case PARSER -> PARSER_TITLE_COLOR;
            case LEXER -> LEXER_TITLE_COLOR;
            case FRAGMENT -> FRAGMENT_TITLE_COLOR;
        };
    }
}

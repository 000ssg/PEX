package ssg.pex.tools.visualizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.RuleExpression;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;

/**
 * Core rendering engine for railroad (syntax) diagrams.
 *
 * <p>Uses a two-pass approach:
 * <ol>
 *   <li>{@link #measure(RuleExpression, Graphics2D, DiagramStyle)} calculates width/height</li>
 *   <li>{@link #render(RuleExpression, Graphics2D, int, int, DiagramStyle)} draws the diagram</li>
 * </ol>
 *
 * <p>Pattern-matches on the sealed {@link RuleExpression} hierarchy to handle each node type.
 */
public final class RailroadDiagramRenderer {

    private static final Logger log = LoggerFactory.getLogger(RailroadDiagramRenderer.class);

    private static final Stroke SOLID_STROKE = new BasicStroke(1.5f);
    private static final Stroke DASHED_STROKE = new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{5.0f, 3.0f}, 0.0f);

    // ---- Measurement pass ----

    /**
     * Measure the dimensions required to render the given expression.
     */
    public DiagramMetrics measure(RuleExpression expr, Graphics2D g, DiagramStyle style) {
        g.setFont(style.textFont());
        return switch (expr) {
            case Terminal t -> measureTerminal(t, g, style);
            case NonTerminal nt -> measureNonTerminal(nt, g, style);
            case Sequence seq -> measureSequence(seq, g, style);
            case Alternation alt -> measureAlternation(alt, g, style);
            case Repetition rep -> measureRepetition(rep, g, style);
            case Group grp -> measure(grp.inner(), g, style);
        };
    }

    /**
     * Measure the dimensions for a complete rule (label + body).
     */
    public DiagramMetrics measureRule(Rule rule, Graphics2D g, DiagramStyle style) {
        g.setFont(style.textFont());
        var fm = g.getFontMetrics();
        var labelWidth = fm.stringWidth(rule.name() + " ::= ") + style.gapH();
        var bodyMetrics = measure(rule.body(), g, style);
        return new DiagramMetrics(
                labelWidth + bodyMetrics.width() + style.gapH() * 2,
                Math.max(bodyMetrics.height(), fm.getHeight()) + style.padding() * 2
        );
    }

    // ---- Rendering pass ----

    /**
     * Render the given expression at the specified position.
     * The (x, y) coordinate represents the top-left corner of the rendering area.
     * Returns the vertical center (the "rail" y-coordinate) for connector alignment.
     */
    public int render(RuleExpression expr, Graphics2D g, int x, int y, DiagramStyle style) {
        enableAntialiasing(g);
        g.setFont(style.textFont());
        return switch (expr) {
            case Terminal t -> renderTerminal(t, g, x, y, style);
            case NonTerminal nt -> renderNonTerminal(nt, g, x, y, style);
            case Sequence seq -> renderSequence(seq, g, x, y, style);
            case Alternation alt -> renderAlternation(alt, g, x, y, style);
            case Repetition rep -> renderRepetition(rep, g, x, y, style);
            case Group grp -> render(grp.inner(), g, x, y, style);
        };
    }

    /**
     * Render a complete rule: label followed by body diagram.
     */
    public int renderRule(Rule rule, Graphics2D g, int x, int y, DiagramStyle style) {
        enableAntialiasing(g);
        g.setFont(style.textFont());
        var fm = g.getFontMetrics();

        var labelText = rule.name() + " ::= ";
        var labelWidth = fm.stringWidth(labelText) + style.gapH();
        var bodyMetrics = measure(rule.body(), g, style);
        int railY = y + style.padding() + bodyMetrics.height() / 2;

        // Draw rule label
        g.setColor(style.textColor());
        g.drawString(labelText, x + style.padding(), railY + fm.getAscent() / 2 - fm.getDescent() / 2);

        // Draw entry line
        int bodyX = x + labelWidth;
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        drawHorizontalLine(g, bodyX, bodyX + style.gapH() / 2, railY);

        // Draw body
        int bodyStartX = bodyX + style.gapH() / 2;
        render(rule.body(), g, bodyStartX, y + style.padding(), style);

        // Draw exit line with arrow
        int exitX = bodyStartX + bodyMetrics.width();
        drawHorizontalLine(g, exitX, exitX + style.gapH() / 2, railY);
        drawArrowRight(g, exitX + style.gapH() / 2, railY, style);

        return railY;
    }

    // ---- Terminal ----

    private DiagramMetrics measureTerminal(Terminal t, Graphics2D g, DiagramStyle style) {
        var fm = g.getFontMetrics();
        var text = terminalText(t);
        int textWidth = fm.stringWidth(text);
        int width = textWidth + style.padding() * 4;
        int height = fm.getHeight() + style.padding() * 2;
        return new DiagramMetrics(width, height);
    }

    private int renderTerminal(Terminal t, Graphics2D g, int x, int y, DiagramStyle style) {
        var fm = g.getFontMetrics();
        var text = terminalText(t);
        var metrics = measureTerminal(t, g, style);
        int railY = y + metrics.height() / 2;

        if (t.isRegex()) {
            // Regex: dashed rectangle with regex fill
            g.setColor(style.regexFillColor());
            g.fillRect(x, y, metrics.width(), metrics.height());
            g.setColor(style.lineColor());
            g.setStroke(DASHED_STROKE);
            g.drawRect(x, y, metrics.width(), metrics.height());
        } else {
            // Literal: rounded rectangle with terminal fill
            g.setColor(style.terminalFillColor());
            g.fillRoundRect(x, y, metrics.width(), metrics.height(),
                    style.cornerRadius(), style.cornerRadius());
            g.setColor(style.lineColor());
            g.setStroke(SOLID_STROKE);
            g.drawRoundRect(x, y, metrics.width(), metrics.height(),
                    style.cornerRadius(), style.cornerRadius());
        }

        // Draw text centered
        g.setColor(style.textColor());
        g.setStroke(SOLID_STROKE);
        int textX = x + (metrics.width() - fm.stringWidth(text)) / 2;
        int textY = y + (metrics.height() + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(text, textX, textY);

        return railY;
    }

    private static String terminalText(Terminal t) {
        return t.isRegex() ? "/" + t.value() + "/" : "\"" + t.value() + "\"";
    }

    // ---- NonTerminal ----

    private DiagramMetrics measureNonTerminal(NonTerminal nt, Graphics2D g, DiagramStyle style) {
        var fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(nt.ruleName());
        int width = textWidth + style.padding() * 4;
        int height = fm.getHeight() + style.padding() * 2;
        return new DiagramMetrics(width, height);
    }

    private int renderNonTerminal(NonTerminal nt, Graphics2D g, int x, int y, DiagramStyle style) {
        var fm = g.getFontMetrics();
        var metrics = measureNonTerminal(nt, g, style);
        int railY = y + metrics.height() / 2;

        // Solid rectangle with non-terminal fill
        g.setColor(style.nonTerminalFillColor());
        g.fillRect(x, y, metrics.width(), metrics.height());
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        g.drawRect(x, y, metrics.width(), metrics.height());

        // Draw text centered
        g.setColor(style.textColor());
        int textX = x + (metrics.width() - fm.stringWidth(nt.ruleName())) / 2;
        int textY = y + (metrics.height() + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(nt.ruleName(), textX, textY);

        return railY;
    }

    // ---- Sequence ----

    private DiagramMetrics measureSequence(Sequence seq, Graphics2D g, DiagramStyle style) {
        int totalWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < seq.elements().size(); i++) {
            var m = measure(seq.elements().get(i), g, style);
            totalWidth += m.width();
            if (i > 0) {
                totalWidth += style.gapH(); // arrow gap between elements
            }
            maxHeight = Math.max(maxHeight, m.height());
        }
        return new DiagramMetrics(totalWidth, maxHeight);
    }

    private int renderSequence(Sequence seq, Graphics2D g, int x, int y, DiagramStyle style) {
        var totalMetrics = measureSequence(seq, g, style);
        int railY = y + totalMetrics.height() / 2;
        int curX = x;

        for (int i = 0; i < seq.elements().size(); i++) {
            var elem = seq.elements().get(i);
            var elemMetrics = measure(elem, g, style);

            // Center element vertically on the rail
            int elemY = railY - elemMetrics.height() / 2;

            if (i > 0) {
                // Draw connecting arrow between elements
                g.setColor(style.lineColor());
                g.setStroke(SOLID_STROKE);
                drawHorizontalLine(g, curX, curX + style.gapH(), railY);
                drawArrowRight(g, curX + style.gapH() - 1, railY, style);
                curX += style.gapH();
            }

            render(elem, g, curX, elemY, style);
            curX += elemMetrics.width();
        }

        return railY;
    }

    // ---- Alternation ----

    private DiagramMetrics measureAlternation(Alternation alt, Graphics2D g, DiagramStyle style) {
        int maxWidth = 0;
        int totalHeight = 0;
        for (int i = 0; i < alt.alternatives().size(); i++) {
            var m = measure(alt.alternatives().get(i), g, style);
            maxWidth = Math.max(maxWidth, m.width());
            totalHeight += m.height();
            if (i > 0) {
                totalHeight += style.gapV();
            }
        }
        // Add space for the curved connectors on both sides
        int connectorWidth = style.gapH() * 2;
        return new DiagramMetrics(maxWidth + connectorWidth, totalHeight);
    }

    private int renderAlternation(Alternation alt, Graphics2D g, int x, int y, DiagramStyle style) {
        var totalMetrics = measureAlternation(alt, g, style);
        int connectorOffset = style.gapH();

        // First alternative is on the main rail
        var firstMetrics = measure(alt.alternatives().getFirst(), g, style);
        int firstRailY = y + firstMetrics.height() / 2;

        // Draw all alternatives
        int curY = y;
        int[] railYs = new int[alt.alternatives().size()];

        for (int i = 0; i < alt.alternatives().size(); i++) {
            var altExpr = alt.alternatives().get(i);
            var altMetrics = measure(altExpr, g, style);

            int altX = x + connectorOffset;
            railYs[i] = curY + altMetrics.height() / 2;

            render(altExpr, g, altX, curY, style);

            curY += altMetrics.height() + style.gapV();
        }

        // Draw left-side connectors from first rail down to each alternative
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        int leftX = x;
        int rightX = x + totalMetrics.width();
        int arcRadius = Math.min(style.gapV(), connectorOffset);

        for (int i = 0; i < alt.alternatives().size(); i++) {
            var altMetrics = measure(alt.alternatives().get(i), g, style);
            int altEndX = x + connectorOffset + altMetrics.width();

            if (i == 0) {
                // Main line: straight through
                drawHorizontalLine(g, leftX, x + connectorOffset, railYs[0]);
                drawHorizontalLine(g, altEndX, rightX, railYs[0]);
            } else {
                // Curved connector from split point down to this alternative
                // Left side: vertical line + arc
                drawVerticalLine(g, leftX, railYs[0], railYs[i] - arcRadius);
                g.drawArc(leftX, railYs[i] - arcRadius, arcRadius * 2, arcRadius * 2,
                        180, -90);
                drawHorizontalLine(g, leftX + arcRadius, x + connectorOffset, railYs[i]);

                // Right side: horizontal + arc + vertical line
                drawHorizontalLine(g, altEndX, rightX - arcRadius, railYs[i]);
                g.drawArc(rightX - arcRadius * 2, railYs[i] - arcRadius,
                        arcRadius * 2, arcRadius * 2, 270, 90);
                drawVerticalLine(g, rightX, railYs[i] - arcRadius, railYs[0]);
            }
        }

        return firstRailY;
    }

    // ---- Repetition ----

    private DiagramMetrics measureRepetition(Repetition rep, Graphics2D g, DiagramStyle style) {
        var bodyMetrics = measure(rep.body(), g, style);
        int loopHeight = style.gapV() + style.padding();

        return switch (rep.kind()) {
            case ZERO_OR_MORE -> new DiagramMetrics(
                    bodyMetrics.width() + style.gapH() * 2,
                    bodyMetrics.height() + loopHeight * 2  // bypass above + loop below
            );
            case ONE_OR_MORE -> new DiagramMetrics(
                    bodyMetrics.width() + style.gapH() * 2,
                    bodyMetrics.height() + loopHeight       // loop below only
            );
            case OPTIONAL -> new DiagramMetrics(
                    bodyMetrics.width() + style.gapH() * 2,
                    bodyMetrics.height() + loopHeight       // bypass above only
            );
        };
    }

    private int renderRepetition(Repetition rep, Graphics2D g, int x, int y, DiagramStyle style) {
        var bodyMetrics = measure(rep.body(), g, style);
        var totalMetrics = measureRepetition(rep, g, style);
        int loopHeight = style.gapV() + style.padding();

        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);

        return switch (rep.kind()) {
            case ZERO_OR_MORE -> {
                // Body in the middle, bypass above, loop-back below
                int bodyY = y + loopHeight;
                int railY = bodyY + bodyMetrics.height() / 2;
                int bodyX = x + style.gapH();

                // Draw body
                render(rep.body(), g, bodyX, bodyY, style);

                // Entry/exit lines
                drawHorizontalLine(g, x, bodyX, railY);
                drawHorizontalLine(g, bodyX + bodyMetrics.width(),
                        x + totalMetrics.width(), railY);

                // Bypass path above (skip the body)
                int bypassY = y + loopHeight / 2;
                int arcR = Math.min(loopHeight / 2, style.gapH() / 2);
                g.drawArc(x, bypassY, arcR * 2, arcR * 2, 90, 90);
                drawHorizontalLine(g, x + arcR, x + totalMetrics.width() - arcR, bypassY);
                g.drawArc(x + totalMetrics.width() - arcR * 2, bypassY,
                        arcR * 2, arcR * 2, 0, 90);
                drawVerticalLine(g, x, bypassY + arcR, railY);
                drawVerticalLine(g, x + totalMetrics.width(), bypassY + arcR, railY);

                // Loop-back path below
                int loopY = railY + bodyMetrics.height() / 2 + loopHeight / 2;
                g.drawArc(bodyX - arcR, railY + bodyMetrics.height() / 2 - arcR,
                        arcR * 2, arcR * 2, 180, 90);
                drawHorizontalLine(g, bodyX, bodyX + bodyMetrics.width(), loopY);
                g.drawArc(bodyX + bodyMetrics.width() - arcR,
                        railY + bodyMetrics.height() / 2 - arcR,
                        arcR * 2, arcR * 2, 270, 90);
                drawArrowLeft(g, bodyX + bodyMetrics.width() / 2, loopY, style);

                yield railY;
            }

            case ONE_OR_MORE -> {
                // Body on main line, loop-back below
                int railY = y + bodyMetrics.height() / 2;
                int bodyX = x + style.gapH();

                render(rep.body(), g, bodyX, y, style);

                // Entry/exit lines
                drawHorizontalLine(g, x, bodyX, railY);
                drawHorizontalLine(g, bodyX + bodyMetrics.width(),
                        x + totalMetrics.width(), railY);

                // Loop-back path below
                int arcR = Math.min(loopHeight / 2, style.gapH() / 2);
                int loopY = railY + bodyMetrics.height() / 2 + loopHeight / 2;
                g.drawArc(bodyX - arcR, railY + bodyMetrics.height() / 2 - arcR,
                        arcR * 2, arcR * 2, 180, 90);
                drawHorizontalLine(g, bodyX, bodyX + bodyMetrics.width(), loopY);
                g.drawArc(bodyX + bodyMetrics.width() - arcR,
                        railY + bodyMetrics.height() / 2 - arcR,
                        arcR * 2, arcR * 2, 270, 90);
                drawArrowLeft(g, bodyX + bodyMetrics.width() / 2, loopY, style);

                yield railY;
            }

            case OPTIONAL -> {
                // Body on main line, bypass above (skip path)
                int bodyY = y + loopHeight;
                int railY = bodyY + bodyMetrics.height() / 2;
                int bodyX = x + style.gapH();

                render(rep.body(), g, bodyX, bodyY, style);

                // Entry/exit lines
                drawHorizontalLine(g, x, bodyX, railY);
                drawHorizontalLine(g, bodyX + bodyMetrics.width(),
                        x + totalMetrics.width(), railY);

                // Bypass path above
                int bypassY = y + loopHeight / 2;
                int arcR = Math.min(loopHeight / 2, style.gapH() / 2);
                g.drawArc(x, bypassY, arcR * 2, arcR * 2, 90, 90);
                drawHorizontalLine(g, x + arcR, x + totalMetrics.width() - arcR, bypassY);
                g.drawArc(x + totalMetrics.width() - arcR * 2, bypassY,
                        arcR * 2, arcR * 2, 0, 90);
                drawVerticalLine(g, x, bypassY + arcR, railY);
                drawVerticalLine(g, x + totalMetrics.width(), bypassY + arcR, railY);

                yield railY;
            }
        };
    }

    // ---- Drawing helpers ----

    private static void enableAntialiasing(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void drawHorizontalLine(Graphics2D g, int x1, int x2, int y) {
        g.drawLine(x1, y, x2, y);
    }

    private static void drawVerticalLine(Graphics2D g, int x, int y1, int y2) {
        g.drawLine(x, y1, x, y2);
    }

    private static void drawArrowRight(Graphics2D g, int x, int y, DiagramStyle style) {
        int s = style.arrowSize();
        g.setColor(style.arrowColor());
        g.fillPolygon(
                new int[]{x, x - s, x - s},
                new int[]{y, y - s / 2, y + s / 2},
                3
        );
    }

    private static void drawArrowLeft(Graphics2D g, int x, int y, DiagramStyle style) {
        int s = style.arrowSize();
        g.setColor(style.arrowColor());
        g.fillPolygon(
                new int[]{x, x + s, x + s},
                new int[]{y, y - s / 2, y + s / 2},
                3
        );
    }
}

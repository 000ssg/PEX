package ssg.pex.tools.visualizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.RuleExpression;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.result.Result;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Exports railroad diagrams as SVG markup.
 *
 * <p>Writes SVG elements directly (rect, line, text, path) rather than
 * embedding raster images, producing clean, scalable output.
 */
public final class SvgExporter {

    private static final Logger log = LoggerFactory.getLogger(SvgExporter.class);

    private final RailroadDiagramRenderer renderer = new RailroadDiagramRenderer();

    /**
     * Export a single rule to SVG.
     */
    public Result<String> exportRule(Rule rule, DiagramStyle style) {
        return Result.of(() -> {
            var g = createMeasureGraphics(style);
            var metrics = renderer.measureRule(rule, g, style);
            g.dispose();

            var sb = new StringBuilder();
            appendSvgHeader(sb, metrics.width(), metrics.height(), style);
            appendRuleSvg(sb, rule, 0, 0, style);
            sb.append("</svg>\n");

            log.debug("Exported rule '{}' to SVG ({}x{})", rule.name(), metrics.width(), metrics.height());
            return sb.toString();
        });
    }

    /**
     * Export an entire grammar to a single SVG document with all rules stacked vertically.
     */
    public Result<String> exportGrammar(Grammar grammar, DiagramStyle style) {
        return Result.of(() -> {
            var g = createMeasureGraphics(style);

            // Measure all rules to determine total dimensions
            int totalHeight = 0;
            int maxWidth = 0;
            int ruleGap = style.gapV() * 3;

            for (var rule : grammar.rules().values()) {
                var metrics = renderer.measureRule(rule, g, style);
                maxWidth = Math.max(maxWidth, metrics.width());
                totalHeight += metrics.height() + ruleGap;
            }
            g.dispose();

            var sb = new StringBuilder();
            appendSvgHeader(sb, maxWidth, totalHeight, style);

            int curY = 0;
            for (var rule : grammar.rules().values()) {
                var gm = createMeasureGraphics(style);
                var metrics = renderer.measureRule(rule, gm, style);
                gm.dispose();

                appendRuleSvg(sb, rule, 0, curY, style);
                curY += metrics.height() + ruleGap;
            }

            sb.append("</svg>\n");

            log.debug("Exported grammar '{}' ({} rules) to SVG", grammar.name(), grammar.rules().size());
            return sb.toString();
        });
    }

    // ---- SVG generation ----

    private void appendSvgHeader(StringBuilder sb, int width, int height, DiagramStyle style) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        sb.append("width=\"").append(width).append("\" ");
        sb.append("height=\"").append(height).append("\" ");
        sb.append("viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n");

        // Background
        sb.append("  <rect width=\"100%\" height=\"100%\" fill=\"")
                .append(colorToHex(style.backgroundColor())).append("\"/>\n");
    }

    private void appendRuleSvg(StringBuilder sb, Rule rule, int offsetX, int offsetY, DiagramStyle style) {
        var g = createMeasureGraphics(style);
        var fm = g.getFontMetrics();

        var labelText = rule.name() + " ::= ";
        var labelWidth = fm.stringWidth(labelText) + style.gapH();
        var bodyMetrics = renderer.measure(rule.body(), g, style);
        var ruleMetrics = renderer.measureRule(rule, g, style);
        int railY = offsetY + style.padding() + bodyMetrics.height() / 2;

        g.dispose();

        // Rule label
        sb.append("  <text x=\"").append(offsetX + style.padding())
                .append("\" y=\"").append(railY + fm.getAscent() / 2 - fm.getDescent() / 2)
                .append("\" font-family=\"").append(style.textFont().getFamily())
                .append("\" font-size=\"").append(style.textFont().getSize())
                .append("\" fill=\"").append(colorToHex(style.textColor()))
                .append("\">").append(escapeXml(labelText)).append("</text>\n");

        // Entry line
        int bodyX = offsetX + labelWidth;
        appendLine(sb, bodyX, railY, bodyX + style.gapH() / 2, railY, style);

        // Body expression
        int bodyStartX = bodyX + style.gapH() / 2;
        appendExpressionSvg(sb, rule.body(), bodyStartX, offsetY + style.padding(), style);

        // Exit line and arrow
        int exitX = bodyStartX + bodyMetrics.width();
        appendLine(sb, exitX, railY, exitX + style.gapH() / 2, railY, style);
        appendArrowRight(sb, exitX + style.gapH() / 2, railY, style);
    }

    private void appendExpressionSvg(StringBuilder sb, RuleExpression expr,
                                     int x, int y, DiagramStyle style) {
        switch (expr) {
            case Terminal t -> appendTerminalSvg(sb, t, x, y, style);
            case NonTerminal nt -> appendNonTerminalSvg(sb, nt, x, y, style);
            case Sequence seq -> appendSequenceSvg(sb, seq, x, y, style);
            case Alternation alt -> appendAlternationSvg(sb, alt, x, y, style);
            case Repetition rep -> appendRepetitionSvg(sb, rep, x, y, style);
            case Group grp -> appendExpressionSvg(sb, grp.inner(), x, y, style);
        }
    }

    private void appendTerminalSvg(StringBuilder sb, Terminal t, int x, int y, DiagramStyle style) {
        var g = createMeasureGraphics(style);
        var fm = g.getFontMetrics();
        var text = t.isRegex() ? "/" + t.value() + "/" : "\"" + t.value() + "\"";
        int textWidth = fm.stringWidth(text);
        int width = textWidth + style.padding() * 4;
        int height = fm.getHeight() + style.padding() * 2;
        g.dispose();

        if (t.isRegex()) {
            sb.append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(width).append("\" height=\"").append(height)
                    .append("\" fill=\"").append(colorToHex(style.regexFillColor()))
                    .append("\" stroke=\"").append(colorToHex(style.lineColor()))
                    .append("\" stroke-width=\"1.5\" stroke-dasharray=\"5,3\"/>\n");
        } else {
            sb.append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(width).append("\" height=\"").append(height)
                    .append("\" rx=\"").append(style.cornerRadius())
                    .append("\" ry=\"").append(style.cornerRadius())
                    .append("\" fill=\"").append(colorToHex(style.terminalFillColor()))
                    .append("\" stroke=\"").append(colorToHex(style.lineColor()))
                    .append("\" stroke-width=\"1.5\"/>\n");
        }

        int textX = x + (width - fm.stringWidth(text)) / 2;
        int textY = y + (height + fm.getAscent() - fm.getDescent()) / 2;
        appendText(sb, textX, textY, text, style);
    }

    private void appendNonTerminalSvg(StringBuilder sb, NonTerminal nt, int x, int y, DiagramStyle style) {
        var g = createMeasureGraphics(style);
        var fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(nt.ruleName());
        int width = textWidth + style.padding() * 4;
        int height = fm.getHeight() + style.padding() * 2;
        g.dispose();

        sb.append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(width).append("\" height=\"").append(height)
                .append("\" fill=\"").append(colorToHex(style.nonTerminalFillColor()))
                .append("\" stroke=\"").append(colorToHex(style.lineColor()))
                .append("\" stroke-width=\"1.5\"/>\n");

        int textX = x + (width - fm.stringWidth(nt.ruleName())) / 2;
        int textY = y + (height + fm.getAscent() - fm.getDescent()) / 2;
        appendText(sb, textX, textY, nt.ruleName(), style);
    }

    private void appendSequenceSvg(StringBuilder sb, Sequence seq, int x, int y, DiagramStyle style) {
        var g = createMeasureGraphics(style);
        var totalMetrics = renderer.measure(seq, g, style);
        int railY = y + totalMetrics.height() / 2;
        int curX = x;

        for (int i = 0; i < seq.elements().size(); i++) {
            var elem = seq.elements().get(i);
            var elemMetrics = renderer.measure(elem, g, style);
            int elemY = railY - elemMetrics.height() / 2;

            if (i > 0) {
                appendLine(sb, curX, railY, curX + style.gapH(), railY, style);
                appendArrowRight(sb, curX + style.gapH() - 1, railY, style);
                curX += style.gapH();
            }

            appendExpressionSvg(sb, elem, curX, elemY, style);
            curX += elemMetrics.width();
        }
        g.dispose();
    }

    private void appendAlternationSvg(StringBuilder sb, Alternation alt, int x, int y, DiagramStyle style) {
        var g = createMeasureGraphics(style);
        var totalMetrics = renderer.measure(alt, g, style);
        int connectorOffset = style.gapH();

        int curY = y;
        int[] railYs = new int[alt.alternatives().size()];

        for (int i = 0; i < alt.alternatives().size(); i++) {
            var altExpr = alt.alternatives().get(i);
            var altMetrics = renderer.measure(altExpr, g, style);
            railYs[i] = curY + altMetrics.height() / 2;
            appendExpressionSvg(sb, altExpr, x + connectorOffset, curY, style);
            curY += altMetrics.height() + style.gapV();
        }

        int leftX = x;
        int rightX = x + totalMetrics.width();
        int arcRadius = Math.min(style.gapV(), connectorOffset);

        for (int i = 0; i < alt.alternatives().size(); i++) {
            var altMetrics = renderer.measure(alt.alternatives().get(i), g, style);
            int altEndX = x + connectorOffset + altMetrics.width();

            if (i == 0) {
                appendLine(sb, leftX, railYs[0], x + connectorOffset, railYs[0], style);
                appendLine(sb, altEndX, railYs[0], rightX, railYs[0], style);
            } else {
                // Left curved connector
                appendLine(sb, leftX, railYs[0], leftX, railYs[i] - arcRadius, style);
                sb.append("  <path d=\"M ").append(leftX).append(' ').append(railYs[i] - arcRadius)
                        .append(" Q ").append(leftX).append(' ').append(railYs[i])
                        .append(' ').append(leftX + arcRadius).append(' ').append(railYs[i])
                        .append("\" fill=\"none\" stroke=\"").append(colorToHex(style.lineColor()))
                        .append("\" stroke-width=\"1.5\"/>\n");
                appendLine(sb, leftX + arcRadius, railYs[i], x + connectorOffset, railYs[i], style);

                // Right curved connector
                appendLine(sb, altEndX, railYs[i], rightX - arcRadius, railYs[i], style);
                sb.append("  <path d=\"M ").append(rightX - arcRadius).append(' ').append(railYs[i])
                        .append(" Q ").append(rightX).append(' ').append(railYs[i])
                        .append(' ').append(rightX).append(' ').append(railYs[i] - arcRadius)
                        .append("\" fill=\"none\" stroke=\"").append(colorToHex(style.lineColor()))
                        .append("\" stroke-width=\"1.5\"/>\n");
                appendLine(sb, rightX, railYs[i] - arcRadius, rightX, railYs[0], style);
            }
        }
        g.dispose();
    }

    private void appendRepetitionSvg(StringBuilder sb, Repetition rep, int x, int y, DiagramStyle style) {
        var g = createMeasureGraphics(style);
        var bodyMetrics = renderer.measure(rep.body(), g, style);
        var totalMetrics = renderer.measure(rep, g, style);
        int loopHeight = style.gapV() + style.padding();
        g.dispose();

        switch (rep.kind()) {
            case ZERO_OR_MORE -> {
                int bodyY = y + loopHeight;
                int railY = bodyY + bodyMetrics.height() / 2;
                int bodyX = x + style.gapH();

                appendExpressionSvg(sb, rep.body(), bodyX, bodyY, style);

                appendLine(sb, x, railY, bodyX, railY, style);
                appendLine(sb, bodyX + bodyMetrics.width(), railY,
                        x + totalMetrics.width(), railY, style);

                // Bypass path
                int bypassY = y + loopHeight / 2;
                int arcR = Math.min(loopHeight / 2, style.gapH() / 2);
                appendQuadArc(sb, x, railY, x, bypassY, x + arcR, bypassY, style);
                appendLine(sb, x + arcR, bypassY, x + totalMetrics.width() - arcR, bypassY, style);
                appendQuadArc(sb, x + totalMetrics.width(), railY,
                        x + totalMetrics.width(), bypassY,
                        x + totalMetrics.width() - arcR, bypassY, style);

                // Loop-back path
                int loopY = railY + bodyMetrics.height() / 2 + loopHeight / 2;
                appendQuadArc(sb, bodyX, railY + bodyMetrics.height() / 2,
                        bodyX, loopY, bodyX, loopY, style);
                appendLine(sb, bodyX, loopY, bodyX + bodyMetrics.width(), loopY, style);
                appendQuadArc(sb, bodyX + bodyMetrics.width(), railY + bodyMetrics.height() / 2,
                        bodyX + bodyMetrics.width(), loopY,
                        bodyX + bodyMetrics.width(), loopY, style);
                appendArrowLeft(sb, bodyX + bodyMetrics.width() / 2, loopY, style);
            }
            case ONE_OR_MORE -> {
                int railY = y + bodyMetrics.height() / 2;
                int bodyX = x + style.gapH();

                appendExpressionSvg(sb, rep.body(), bodyX, y, style);

                appendLine(sb, x, railY, bodyX, railY, style);
                appendLine(sb, bodyX + bodyMetrics.width(), railY,
                        x + totalMetrics.width(), railY, style);

                int loopY = railY + bodyMetrics.height() / 2 + loopHeight / 2;
                appendQuadArc(sb, bodyX, railY + bodyMetrics.height() / 2,
                        bodyX, loopY, bodyX, loopY, style);
                appendLine(sb, bodyX, loopY, bodyX + bodyMetrics.width(), loopY, style);
                appendQuadArc(sb, bodyX + bodyMetrics.width(), railY + bodyMetrics.height() / 2,
                        bodyX + bodyMetrics.width(), loopY,
                        bodyX + bodyMetrics.width(), loopY, style);
                appendArrowLeft(sb, bodyX + bodyMetrics.width() / 2, loopY, style);
            }
            case OPTIONAL -> {
                int bodyY = y + loopHeight;
                int railY = bodyY + bodyMetrics.height() / 2;
                int bodyX = x + style.gapH();

                appendExpressionSvg(sb, rep.body(), bodyX, bodyY, style);

                appendLine(sb, x, railY, bodyX, railY, style);
                appendLine(sb, bodyX + bodyMetrics.width(), railY,
                        x + totalMetrics.width(), railY, style);

                int bypassY = y + loopHeight / 2;
                int arcR = Math.min(loopHeight / 2, style.gapH() / 2);
                appendQuadArc(sb, x, railY, x, bypassY, x + arcR, bypassY, style);
                appendLine(sb, x + arcR, bypassY, x + totalMetrics.width() - arcR, bypassY, style);
                appendQuadArc(sb, x + totalMetrics.width(), railY,
                        x + totalMetrics.width(), bypassY,
                        x + totalMetrics.width() - arcR, bypassY, style);
            }
        }
    }

    // ---- SVG primitive helpers ----

    private void appendLine(StringBuilder sb, int x1, int y1, int x2, int y2, DiagramStyle style) {
        sb.append("  <line x1=\"").append(x1).append("\" y1=\"").append(y1)
                .append("\" x2=\"").append(x2).append("\" y2=\"").append(y2)
                .append("\" stroke=\"").append(colorToHex(style.lineColor()))
                .append("\" stroke-width=\"1.5\"/>\n");
    }

    private void appendText(StringBuilder sb, int x, int y, String text, DiagramStyle style) {
        sb.append("  <text x=\"").append(x).append("\" y=\"").append(y)
                .append("\" font-family=\"").append(style.textFont().getFamily())
                .append("\" font-size=\"").append(style.textFont().getSize())
                .append("\" fill=\"").append(colorToHex(style.textColor()))
                .append("\">").append(escapeXml(text)).append("</text>\n");
    }

    private void appendArrowRight(StringBuilder sb, int x, int y, DiagramStyle style) {
        int s = style.arrowSize();
        sb.append("  <polygon points=\"")
                .append(x).append(',').append(y).append(' ')
                .append(x - s).append(',').append(y - s / 2).append(' ')
                .append(x - s).append(',').append(y + s / 2)
                .append("\" fill=\"").append(colorToHex(style.arrowColor())).append("\"/>\n");
    }

    private void appendArrowLeft(StringBuilder sb, int x, int y, DiagramStyle style) {
        int s = style.arrowSize();
        sb.append("  <polygon points=\"")
                .append(x).append(',').append(y).append(' ')
                .append(x + s).append(',').append(y - s / 2).append(' ')
                .append(x + s).append(',').append(y + s / 2)
                .append("\" fill=\"").append(colorToHex(style.arrowColor())).append("\"/>\n");
    }

    private void appendQuadArc(StringBuilder sb, int startX, int startY,
                               int ctrlX, int ctrlY, int endX, int endY, DiagramStyle style) {
        sb.append("  <path d=\"M ").append(startX).append(' ').append(startY)
                .append(" Q ").append(ctrlX).append(' ').append(ctrlY)
                .append(' ').append(endX).append(' ').append(endY)
                .append("\" fill=\"none\" stroke=\"").append(colorToHex(style.lineColor()))
                .append("\" stroke-width=\"1.5\"/>\n");
    }

    // ---- Utility ----

    private Graphics2D createMeasureGraphics(DiagramStyle style) {
        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setFont(style.textFont());
        return g;
    }

    private static String colorToHex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

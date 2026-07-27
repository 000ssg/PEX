package ssg.pex.tools.visualizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.tools.converter.antlr.AntlrExpression;
import ssg.pex.tools.converter.antlr.AntlrExpression.*;
import ssg.pex.tools.converter.antlr.AntlrRule;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;

/**
 * Railroad diagram renderer for the ANTLR4 expression model.
 *
 * <p>Uses the same two-pass approach (measure + render) as {@link RailroadDiagramRenderer}
 * but supports ALL ANTLR constructs, including those that have no BNF equivalent.
 * ANTLR-specific constructs (predicates, actions, negation, labels) are rendered
 * in grey/muted tones to visually distinguish them from standard grammar constructs.
 *
 * <h3>Visual mapping</h3>
 * <ul>
 *   <li><b>BNF-compatible</b> (normal colors): Literal, CharClass, RuleRef, TokenRef,
 *       Seq, Alt, ZeroOrMore, OneOrMore, Optional, Group, Dot</li>
 *   <li><b>ANTLR-specific</b> (grey/muted): Negation, Predicate, Action, LabeledElement</li>
 * </ul>
 */
public final class AntlrDiagramRenderer {

    private static final Logger log = LoggerFactory.getLogger(AntlrDiagramRenderer.class);

    private static final Stroke SOLID_STROKE = new BasicStroke(1.5f);
    private static final Stroke DASHED_STROKE = new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{5.0f, 3.0f}, 0.0f);

    // ANTLR-specific colors (not in DiagramStyle — hardcoded here)
    private static final Color TOKEN_REF_FILL = new Color(200, 235, 200);
    private static final Color DOT_FILL = new Color(180, 180, 180);
    private static final Color PREDICATE_FILL = new Color(220, 220, 220);
    private static final Color ACTION_FILL = new Color(210, 210, 210);
    private static final Color NEGATION_SYMBOL_COLOR = new Color(120, 120, 120);
    private static final Color LABEL_COLOR = new Color(130, 130, 130);
    private static final Color FRAGMENT_COLOR = new Color(140, 140, 140);
    private static final Color PARSER_TITLE_COLOR = new Color(40, 40, 120);
    private static final Color LEXER_TITLE_COLOR = new Color(40, 120, 40);
    private static final Color COMMAND_COLOR = new Color(150, 150, 150);

    // ---- Measurement pass ----

    /**
     * Measure the dimensions required to render the given expression.
     */
    public DiagramMetrics measure(AntlrExpression expr, Graphics2D g, DiagramStyle style) {
        g.setFont(style.textFont());
        return switch (expr) {
            case Literal lit -> measureTextBox('"' + lit.value() + '"', g, style);
            case CharClass cc -> measureTextBox(cc.pattern(), g, style);
            case RuleRef rr -> measureTextBox(rr.name(), g, style);
            case TokenRef tr -> measureTokenRef(tr, g, style);
            case Seq seq -> measureSeq(seq, g, style);
            case Alt alt -> measureAlt(alt, g, style);
            case ZeroOrMore zom -> measureLoop(zom.body(), g, style, true);
            case OneOrMore oom -> measureLoop(oom.body(), g, style, false);
            case Optional opt -> measureOptional(opt.body(), g, style);
            case Group grp -> measure(grp.inner(), g, style);
            case Dot _ -> measureTextBox(".", g, style);
            case Negation neg -> measureNegation(neg, g, style);
            case Predicate pred -> measureTextBox(truncateCode(pred.code()) + "?", g, style);
            case Action act -> measureTextBox("{" + truncateCode(act.code()) + "}", g, style);
            case LabeledElement le -> measureLabeledElement(le, g, style);
        };
    }

    /**
     * Measure the dimensions for a complete rule (label + body).
     */
    public DiagramMetrics measureRule(AntlrRule rule, Graphics2D g, DiagramStyle style) {
        var ruleFont = ruleLabelFont(rule, style);
        g.setFont(ruleFont);
        var fm = g.getFontMetrics();
        var labelText = ruleLabelText(rule);
        var labelWidth = fm.stringWidth(labelText) + style.gapH();

        g.setFont(style.textFont());
        var bodyMetrics = measure(rule.body(), g, style);

        int commandWidth = 0;
        if (!rule.commands().isEmpty()) {
            g.setFont(style.textFont().deriveFont(Font.ITALIC));
            var cfm = g.getFontMetrics();
            commandWidth = cfm.stringWidth(commandText(rule)) + style.gapH() * 2;
            g.setFont(style.textFont());
        }

        return new DiagramMetrics(
                labelWidth + bodyMetrics.width() + commandWidth + style.gapH() * 2,
                Math.max(bodyMetrics.height(), fm.getHeight()) + style.padding() * 2
        );
    }

    // ---- Rendering pass ----

    /**
     * Render the given expression at the specified position.
     * The (x, y) coordinate represents the top-left corner of the rendering area.
     * Returns the vertical center (the "rail" y-coordinate) for connector alignment.
     */
    public int render(AntlrExpression expr, Graphics2D g, int x, int y, DiagramStyle style) {
        enableAntialiasing(g);
        g.setFont(style.textFont());
        return switch (expr) {
            case Literal lit -> renderLiteral(lit, g, x, y, style);
            case CharClass cc -> renderCharClass(cc, g, x, y, style);
            case RuleRef rr -> renderRuleRef(rr, g, x, y, style);
            case TokenRef tr -> renderTokenRef(tr, g, x, y, style);
            case Seq seq -> renderSeq(seq, g, x, y, style);
            case Alt alt -> renderAlt(alt, g, x, y, style);
            case ZeroOrMore zom -> renderZeroOrMore(zom, g, x, y, style);
            case OneOrMore oom -> renderOneOrMore(oom, g, x, y, style);
            case Optional opt -> renderOptional(opt, g, x, y, style);
            case Group grp -> render(grp.inner(), g, x, y, style);
            case Dot _ -> renderDot(g, x, y, style);
            case Negation neg -> renderNegation(neg, g, x, y, style);
            case Predicate pred -> renderPredicate(pred, g, x, y, style);
            case Action act -> renderAction(act, g, x, y, style);
            case LabeledElement le -> renderLabeledElement(le, g, x, y, style);
        };
    }

    /**
     * Render a complete rule: label followed by body diagram.
     */
    public int renderRule(AntlrRule rule, Graphics2D g, int x, int y, DiagramStyle style) {
        enableAntialiasing(g);

        // Draw rule label
        var ruleFont = ruleLabelFont(rule, style);
        g.setFont(ruleFont);
        var fm = g.getFontMetrics();
        var labelText = ruleLabelText(rule);
        var labelWidth = fm.stringWidth(labelText) + style.gapH();

        g.setFont(style.textFont());
        var bodyMetrics = measure(rule.body(), g, style);
        int railY = y + style.padding() + bodyMetrics.height() / 2;

        // Draw the label in the appropriate color/style
        g.setFont(ruleFont);
        g.setColor(ruleLabelColor(rule));
        g.drawString(labelText, x + style.padding(),
                railY + fm.getAscent() / 2 - fm.getDescent() / 2);

        // Draw entry line
        g.setFont(style.textFont());
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

        // Draw commands annotation if present
        if (!rule.commands().isEmpty()) {
            var cmdText = commandText(rule);
            g.setFont(style.textFont().deriveFont(Font.ITALIC));
            g.setColor(COMMAND_COLOR);
            var cfm = g.getFontMetrics();
            int cmdX = exitX + style.gapH();
            g.drawString(cmdText, cmdX,
                    railY + cfm.getAscent() / 2 - cfm.getDescent() / 2);
            int afterCmd = cmdX + cfm.stringWidth(cmdText) + style.gapH() / 2;
            g.setFont(style.textFont());
            g.setColor(style.lineColor());
            g.setStroke(SOLID_STROKE);
            drawArrowRight(g, afterCmd, railY, style);
        } else {
            drawArrowRight(g, exitX + style.gapH() / 2, railY, style);
        }

        return railY;
    }

    // ---- Literal ----

    private int renderLiteral(Literal lit, Graphics2D g, int x, int y, DiagramStyle style) {
        var text = '"' + lit.value() + '"';
        var metrics = measureTextBox(text, g, style);
        int railY = y + metrics.height() / 2;

        g.setColor(style.terminalFillColor());
        g.fillRoundRect(x, y, metrics.width(), metrics.height(),
                style.cornerRadius(), style.cornerRadius());
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        g.drawRoundRect(x, y, metrics.width(), metrics.height(),
                style.cornerRadius(), style.cornerRadius());

        drawCenteredText(g, text, x, y, metrics, style);
        return railY;
    }

    // ---- CharClass ----

    private int renderCharClass(CharClass cc, Graphics2D g, int x, int y, DiagramStyle style) {
        var text = cc.pattern();
        var metrics = measureTextBox(text, g, style);
        int railY = y + metrics.height() / 2;

        g.setColor(style.regexFillColor());
        g.fillRect(x, y, metrics.width(), metrics.height());
        g.setColor(style.lineColor());
        g.setStroke(DASHED_STROKE);
        g.drawRect(x, y, metrics.width(), metrics.height());
        g.setStroke(SOLID_STROKE);

        drawCenteredText(g, text, x, y, metrics, style);
        return railY;
    }

    // ---- RuleRef ----

    private int renderRuleRef(RuleRef rr, Graphics2D g, int x, int y, DiagramStyle style) {
        var text = rr.name();
        var metrics = measureTextBox(text, g, style);
        int railY = y + metrics.height() / 2;

        g.setColor(style.nonTerminalFillColor());
        g.fillRect(x, y, metrics.width(), metrics.height());
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        g.drawRect(x, y, metrics.width(), metrics.height());

        drawCenteredText(g, text, x, y, metrics, style);
        return railY;
    }

    // ---- TokenRef ----

    private DiagramMetrics measureTokenRef(TokenRef tr, Graphics2D g, DiagramStyle style) {
        var boldFont = style.textFont().deriveFont(Font.BOLD);
        g.setFont(boldFont);
        var fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(tr.name());
        int width = textWidth + style.padding() * 4;
        int height = fm.getHeight() + style.padding() * 2;
        g.setFont(style.textFont());
        return new DiagramMetrics(width, height);
    }

    private int renderTokenRef(TokenRef tr, Graphics2D g, int x, int y, DiagramStyle style) {
        var metrics = measureTokenRef(tr, g, style);
        int railY = y + metrics.height() / 2;

        g.setColor(TOKEN_REF_FILL);
        g.fillRect(x, y, metrics.width(), metrics.height());
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        g.drawRect(x, y, metrics.width(), metrics.height());

        var boldFont = style.textFont().deriveFont(Font.BOLD);
        g.setFont(boldFont);
        var fm = g.getFontMetrics();
        g.setColor(style.textColor());
        int textX = x + (metrics.width() - fm.stringWidth(tr.name())) / 2;
        int textY = y + (metrics.height() + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(tr.name(), textX, textY);
        g.setFont(style.textFont());
        return railY;
    }

    // ---- Dot ----

    private int renderDot(Graphics2D g, int x, int y, DiagramStyle style) {
        var text = ".";
        var metrics = measureTextBox(text, g, style);
        int railY = y + metrics.height() / 2;

        g.setColor(DOT_FILL);
        g.fillRoundRect(x, y, metrics.width(), metrics.height(),
                style.cornerRadius(), style.cornerRadius());
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        g.drawRoundRect(x, y, metrics.width(), metrics.height(),
                style.cornerRadius(), style.cornerRadius());

        drawCenteredText(g, text, x, y, metrics, style);
        return railY;
    }

    // ---- Seq ----

    private DiagramMetrics measureSeq(Seq seq, Graphics2D g, DiagramStyle style) {
        int totalWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < seq.elements().size(); i++) {
            var m = measure(seq.elements().get(i), g, style);
            totalWidth += m.width();
            if (i > 0) {
                totalWidth += style.gapH();
            }
            maxHeight = Math.max(maxHeight, m.height());
        }
        return new DiagramMetrics(totalWidth, maxHeight);
    }

    private int renderSeq(Seq seq, Graphics2D g, int x, int y, DiagramStyle style) {
        var totalMetrics = measureSeq(seq, g, style);
        int railY = y + totalMetrics.height() / 2;
        int curX = x;

        for (int i = 0; i < seq.elements().size(); i++) {
            var elem = seq.elements().get(i);
            var elemMetrics = measure(elem, g, style);
            int elemY = railY - elemMetrics.height() / 2;

            if (i > 0) {
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

    // ---- Alt ----

    private DiagramMetrics measureAlt(Alt alt, Graphics2D g, DiagramStyle style) {
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
        int connectorWidth = style.gapH() * 2;
        return new DiagramMetrics(maxWidth + connectorWidth, totalHeight);
    }

    private int renderAlt(Alt alt, Graphics2D g, int x, int y, DiagramStyle style) {
        var totalMetrics = measureAlt(alt, g, style);
        int connectorOffset = style.gapH();

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

        // Draw connectors
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        int leftX = x;
        int rightX = x + totalMetrics.width();
        int arcRadius = Math.min(style.gapV(), connectorOffset);

        for (int i = 0; i < alt.alternatives().size(); i++) {
            var altMetrics = measure(alt.alternatives().get(i), g, style);
            int altEndX = x + connectorOffset + altMetrics.width();

            if (i == 0) {
                drawHorizontalLine(g, leftX, x + connectorOffset, railYs[0]);
                drawHorizontalLine(g, altEndX, rightX, railYs[0]);
            } else {
                drawVerticalLine(g, leftX, railYs[0], railYs[i] - arcRadius);
                g.drawArc(leftX, railYs[i] - arcRadius, arcRadius * 2, arcRadius * 2,
                        180, -90);
                drawHorizontalLine(g, leftX + arcRadius, x + connectorOffset, railYs[i]);

                drawHorizontalLine(g, altEndX, rightX - arcRadius, railYs[i]);
                g.drawArc(rightX - arcRadius * 2, railYs[i] - arcRadius,
                        arcRadius * 2, arcRadius * 2, 270, 90);
                drawVerticalLine(g, rightX, railYs[i] - arcRadius, railYs[0]);
            }
        }

        return railYs[0];
    }

    // ---- ZeroOrMore ----

    private DiagramMetrics measureLoop(AntlrExpression body, Graphics2D g,
                                       DiagramStyle style, boolean hasBypass) {
        var bodyMetrics = measure(body, g, style);
        int loopHeight = style.gapV() + style.padding();
        if (hasBypass) {
            return new DiagramMetrics(
                    bodyMetrics.width() + style.gapH() * 2,
                    bodyMetrics.height() + loopHeight * 2
            );
        } else {
            return new DiagramMetrics(
                    bodyMetrics.width() + style.gapH() * 2,
                    bodyMetrics.height() + loopHeight
            );
        }
    }

    private int renderZeroOrMore(ZeroOrMore zom, Graphics2D g, int x, int y,
                                  DiagramStyle style) {
        var bodyMetrics = measure(zom.body(), g, style);
        var totalMetrics = measureLoop(zom.body(), g, style, true);
        int loopHeight = style.gapV() + style.padding();

        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);

        int bodyY = y + loopHeight;
        int railY = bodyY + bodyMetrics.height() / 2;
        int bodyX = x + style.gapH();

        render(zom.body(), g, bodyX, bodyY, style);

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

        // Loop-back path below
        int loopY = railY + bodyMetrics.height() / 2 + loopHeight / 2;
        g.drawArc(bodyX - arcR, railY + bodyMetrics.height() / 2 - arcR,
                arcR * 2, arcR * 2, 180, 90);
        drawHorizontalLine(g, bodyX, bodyX + bodyMetrics.width(), loopY);
        g.drawArc(bodyX + bodyMetrics.width() - arcR,
                railY + bodyMetrics.height() / 2 - arcR,
                arcR * 2, arcR * 2, 270, 90);
        drawArrowLeft(g, bodyX + bodyMetrics.width() / 2, loopY, style);

        return railY;
    }

    // ---- OneOrMore ----

    private int renderOneOrMore(OneOrMore oom, Graphics2D g, int x, int y,
                                 DiagramStyle style) {
        var bodyMetrics = measure(oom.body(), g, style);
        var totalMetrics = measureLoop(oom.body(), g, style, false);
        int loopHeight = style.gapV() + style.padding();

        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);

        int railY = y + bodyMetrics.height() / 2;
        int bodyX = x + style.gapH();

        render(oom.body(), g, bodyX, y, style);

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

        return railY;
    }

    // ---- Optional ----

    private DiagramMetrics measureOptional(AntlrExpression body, Graphics2D g,
                                           DiagramStyle style) {
        var bodyMetrics = measure(body, g, style);
        int loopHeight = style.gapV() + style.padding();
        return new DiagramMetrics(
                bodyMetrics.width() + style.gapH() * 2,
                bodyMetrics.height() + loopHeight
        );
    }

    private int renderOptional(Optional opt, Graphics2D g, int x, int y,
                                DiagramStyle style) {
        var bodyMetrics = measure(opt.body(), g, style);
        var totalMetrics = measureOptional(opt.body(), g, style);
        int loopHeight = style.gapV() + style.padding();

        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);

        int bodyY = y + loopHeight;
        int railY = bodyY + bodyMetrics.height() / 2;
        int bodyX = x + style.gapH();

        render(opt.body(), g, bodyX, bodyY, style);

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

        return railY;
    }

    // ---- Negation (ANTLR-specific) ----

    private DiagramMetrics measureNegation(Negation neg, Graphics2D g, DiagramStyle style) {
        var innerMetrics = measure(neg.inner(), g, style);
        int symbolSize = style.padding() * 2 + 4;
        return new DiagramMetrics(
                symbolSize + style.gapH() / 2 + innerMetrics.width(),
                Math.max(innerMetrics.height(), symbolSize)
        );
    }

    private int renderNegation(Negation neg, Graphics2D g, int x, int y,
                                DiagramStyle style) {
        var innerMetrics = measure(neg.inner(), g, style);
        var totalMetrics = measureNegation(neg, g, style);
        int railY = y + totalMetrics.height() / 2;
        int symbolSize = style.padding() * 2 + 4;

        // Draw grey diamond with "~"
        int diamondCx = x + symbolSize / 2;
        int diamondCy = railY;
        int half = symbolSize / 2;

        g.setColor(NEGATION_SYMBOL_COLOR);
        g.fillPolygon(
                new int[]{diamondCx, diamondCx + half, diamondCx, diamondCx - half},
                new int[]{diamondCy - half, diamondCy, diamondCy + half, diamondCy},
                4
        );
        g.setColor(style.lineColor());
        g.setStroke(SOLID_STROKE);
        g.drawPolygon(
                new int[]{diamondCx, diamondCx + half, diamondCx, diamondCx - half},
                new int[]{diamondCy - half, diamondCy, diamondCy + half, diamondCy},
                4
        );

        // Draw "~" text inside diamond
        g.setColor(Color.WHITE);
        var fm = g.getFontMetrics();
        int tildeX = diamondCx - fm.stringWidth("~") / 2;
        int tildeY = diamondCy + fm.getAscent() / 2 - fm.getDescent() / 2;
        g.drawString("~", tildeX, tildeY);

        // Draw inner expression after the symbol
        int innerX = x + symbolSize + style.gapH() / 2;
        int innerY = railY - innerMetrics.height() / 2;
        g.setColor(style.textColor());
        render(neg.inner(), g, innerX, innerY, style);

        return railY;
    }

    // ---- Predicate (ANTLR-specific) ----

    private int renderPredicate(Predicate pred, Graphics2D g, int x, int y,
                                 DiagramStyle style) {
        var text = truncateCode(pred.code()) + "?";
        var metrics = measureTextBox(text, g, style);
        int railY = y + metrics.height() / 2;

        g.setColor(PREDICATE_FILL);
        g.fillRoundRect(x, y, metrics.width(), metrics.height(),
                style.cornerRadius(), style.cornerRadius());
        g.setColor(style.lineColor());
        g.setStroke(DASHED_STROKE);
        g.drawRoundRect(x, y, metrics.width(), metrics.height(),
                style.cornerRadius(), style.cornerRadius());
        g.setStroke(SOLID_STROKE);

        drawCenteredText(g, text, x, y, metrics, style);
        return railY;
    }

    // ---- Action (ANTLR-specific) ----

    private int renderAction(Action act, Graphics2D g, int x, int y,
                              DiagramStyle style) {
        var text = "{" + truncateCode(act.code()) + "}";
        var metrics = measureTextBox(text, g, style);
        int railY = y + metrics.height() / 2;

        g.setColor(ACTION_FILL);
        g.fillRect(x, y, metrics.width(), metrics.height());
        g.setColor(style.lineColor());
        g.setStroke(DASHED_STROKE);
        g.drawRect(x, y, metrics.width(), metrics.height());
        g.setStroke(SOLID_STROKE);

        // Italic font for actions
        var italicFont = style.textFont().deriveFont(Font.ITALIC);
        g.setFont(italicFont);
        var fm = g.getFontMetrics();
        g.setColor(style.textColor());
        int textX = x + (metrics.width() - fm.stringWidth(text)) / 2;
        int textY = y + (metrics.height() + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(text, textX, textY);
        g.setFont(style.textFont());
        return railY;
    }

    // ---- LabeledElement (ANTLR-specific) ----

    private DiagramMetrics measureLabeledElement(LabeledElement le, Graphics2D g,
                                                  DiagramStyle style) {
        var innerMetrics = measure(le.element(), g, style);
        var labelText = le.label() + (le.listLabel() ? "+=" : "=");
        var smallFont = style.textFont().deriveFont(style.textFont().getSize2D() - 2);
        g.setFont(smallFont);
        var fm = g.getFontMetrics();
        int labelWidth = fm.stringWidth(labelText);
        int labelHeight = fm.getHeight();
        g.setFont(style.textFont());
        return new DiagramMetrics(
                Math.max(innerMetrics.width(), labelWidth),
                innerMetrics.height() + labelHeight + 2
        );
    }

    private int renderLabeledElement(LabeledElement le, Graphics2D g, int x, int y,
                                      DiagramStyle style) {
        var labelText = le.label() + (le.listLabel() ? "+=" : "=");
        var smallFont = style.textFont().deriveFont(style.textFont().getSize2D() - 2);

        // Draw label annotation above
        g.setFont(smallFont);
        var fm = g.getFontMetrics();
        int labelHeight = fm.getHeight();
        g.setColor(LABEL_COLOR);
        g.drawString(labelText, x, y + fm.getAscent());

        // Draw inner element below the label
        g.setFont(style.textFont());
        int innerY = y + labelHeight + 2;
        var innerMetrics = measure(le.element(), g, style);
        int railY = innerY + innerMetrics.height() / 2;
        render(le.element(), g, x, innerY, style);

        return railY;
    }

    // ---- Shared helpers ----

    private DiagramMetrics measureTextBox(String text, Graphics2D g, DiagramStyle style) {
        var fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int width = textWidth + style.padding() * 4;
        int height = fm.getHeight() + style.padding() * 2;
        return new DiagramMetrics(width, height);
    }

    private void drawCenteredText(Graphics2D g, String text, int x, int y,
                                  DiagramMetrics metrics, DiagramStyle style) {
        var fm = g.getFontMetrics();
        g.setColor(style.textColor());
        g.setStroke(SOLID_STROKE);
        int textX = x + (metrics.width() - fm.stringWidth(text)) / 2;
        int textY = y + (metrics.height() + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(text, textX, textY);
    }

    private static String truncateCode(String code) {
        if (code == null) return "...";
        var trimmed = code.strip();
        if (trimmed.length() <= 20) return trimmed;
        return trimmed.substring(0, 17) + "...";
    }

    private static String ruleLabelText(AntlrRule rule) {
        return switch (rule.kind()) {
            case PARSER -> rule.name() + " :";
            case LEXER -> rule.name() + " :";
            case FRAGMENT -> "fragment " + rule.name() + " :";
        };
    }

    private Font ruleLabelFont(AntlrRule rule, DiagramStyle style) {
        return switch (rule.kind()) {
            case PARSER -> style.textFont();
            case LEXER -> style.textFont().deriveFont(Font.BOLD);
            case FRAGMENT -> style.textFont().deriveFont(Font.ITALIC);
        };
    }

    private static Color ruleLabelColor(AntlrRule rule) {
        return switch (rule.kind()) {
            case PARSER -> PARSER_TITLE_COLOR;
            case LEXER -> LEXER_TITLE_COLOR;
            case FRAGMENT -> FRAGMENT_COLOR;
        };
    }

    private static String commandText(AntlrRule rule) {
        return "→ " + String.join(", ", rule.commands());
    }

    private static void enableAntialiasing(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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

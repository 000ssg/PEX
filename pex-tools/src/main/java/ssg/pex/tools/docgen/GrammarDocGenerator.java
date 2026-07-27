package ssg.pex.tools.docgen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.RuleExpression;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.result.Result;
import ssg.pex.tools.visualizer.DiagramStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates HTML documentation with inline SVG railroad diagrams for a PEX grammar.
 *
 * <p>Produces a self-contained HTML page with:
 * <ul>
 *   <li>A table of contents linking to each rule</li>
 *   <li>An SVG railroad diagram for each rule</li>
 *   <li>BNF notation text for each rule</li>
 * </ul>
 *
 * <p>The SVG diagrams use a standalone renderer (no dependency on the visualizer package's
 * AWT-based renderer). NonTerminal references are rendered as clickable links.
 */
public final class GrammarDocGenerator {

    private static final Logger log = LoggerFactory.getLogger(GrammarDocGenerator.class);

    // SVG layout constants
    private static final int PADDING = 8;
    private static final int TEXT_HEIGHT = 14;
    private static final int BOX_HEIGHT = TEXT_HEIGHT + 2 * PADDING;
    private static final int GAP_H = 20;
    private static final int GAP_V = 12;
    private static final int CORNER_RADIUS = 10;
    private static final int ARROW_SIZE = 6;
    private static final int CHAR_WIDTH = 8; // approximate width per character

    /**
     * Generates an HTML documentation page for the given grammar.
     *
     * @param grammar the grammar to document
     * @param style   diagram styling (colors are mapped to SVG fill/stroke)
     * @return a result containing the HTML string
     */
    public Result<String> generateHtml(Grammar grammar, DiagramStyle style) {
        try {
            var sb = new StringBuilder();
            sb.append("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="UTF-8">
                      <title>Grammar: %s</title>
                      <style>
                        body {
                          font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                          max-width: 960px;
                          margin: 0 auto;
                          padding: 20px 40px;
                          color: #333;
                          background: #fff;
                        }
                        h1 { border-bottom: 2px solid #2c5aa0; padding-bottom: 10px; color: #2c5aa0; }
                        h2 { color: #444; margin-top: 30px; }
                        h3 { color: #2c5aa0; margin-top: 0; }
                        .toc { column-count: 2; column-gap: 30px; }
                        .toc a { color: #2c5aa0; text-decoration: none; }
                        .toc a:hover { text-decoration: underline; }
                        .rule {
                          border-top: 1px solid #ddd;
                          padding: 20px 0;
                        }
                        .diagram {
                          background: #f8f8f8;
                          border: 1px solid #e0e0e0;
                          border-radius: 4px;
                          padding: 16px;
                          margin: 12px 0;
                          overflow-x: auto;
                        }
                        .diagram svg { display: block; }
                        .bnf {
                          background: #f5f5f5;
                          border-left: 3px solid #2c5aa0;
                          padding: 8px 12px;
                          margin: 12px 0;
                          font-family: 'Consolas', 'Monaco', monospace;
                          font-size: 13px;
                          white-space: pre-wrap;
                        }
                        .bnf code { color: #333; }
                        svg text { font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; }
                        svg a text { fill: #2c5aa0; }
                        svg a:hover text { text-decoration: underline; }
                      </style>
                    </head>
                    <body>
                      <h1>Grammar: %s</h1>
                    """.formatted(escapeHtml(grammar.name()), escapeHtml(grammar.name())));

            // Table of Contents
            sb.append("  <h2>Table of Contents</h2>\n");
            sb.append("  <ul class=\"toc\">\n");
            for (var ruleName : grammar.rules().keySet()) {
                sb.append("    <li><a href=\"#rule-").append(escapeHtml(ruleName)).append("\">")
                        .append(escapeHtml(ruleName)).append("</a></li>\n");
            }
            sb.append("  </ul>\n\n");

            // Rule sections
            for (var entry : grammar.rules().entrySet()) {
                Rule rule = entry.getValue();
                sb.append("  <div class=\"rule\" id=\"rule-").append(escapeHtml(rule.name())).append("\">\n");
                sb.append("    <h3>").append(escapeHtml(rule.name())).append("</h3>\n");

                // SVG railroad diagram
                sb.append("    <div class=\"diagram\">\n");
                sb.append(generateSvgDiagram(rule, style));
                sb.append("    </div>\n");

                // BNF notation
                sb.append("    <div class=\"bnf\"><code>");
                sb.append(escapeHtml(rule.name())).append(" ::= ");
                sb.append(escapeHtml(expressionToBnf(rule.body())));
                sb.append("</code></div>\n");

                sb.append("  </div>\n\n");
            }

            sb.append("</body>\n</html>\n");

            log.debug("Generated HTML documentation for grammar '{}' with {} rules",
                    grammar.name(), grammar.rules().size());
            return Result.success(sb.toString());

        } catch (Exception e) {
            return Result.failure("DOCGEN_HTML", "Failed to generate HTML: " + e.getMessage(), e);
        }
    }

    /**
     * Generates HTML documentation and writes it to a file.
     *
     * @param grammar    the grammar to document
     * @param style      diagram styling
     * @param outputFile the path to write the HTML file
     * @return a result containing the output file path
     */
    public Result<Path> generateToFile(Grammar grammar, DiagramStyle style, Path outputFile) {
        return generateHtml(grammar, style).flatMap(html -> {
            try {
                if (outputFile.getParent() != null) {
                    Files.createDirectories(outputFile.getParent());
                }
                Files.writeString(outputFile, html);
                log.debug("Wrote HTML documentation to {}", outputFile);
                return Result.success(outputFile);
            } catch (IOException e) {
                return Result.failure("DOCGEN_FILE", "Failed to write file: " + e.getMessage(), e);
            }
        });
    }

    // ---- SVG Railroad Diagram Renderer ----

    private String generateSvgDiagram(Rule rule, DiagramStyle style) {
        var ctx = new SvgContext();
        int startX = GAP_H;
        int startY = GAP_V + BOX_HEIGHT / 2;

        // Entry line with arrow
        ctx.line(0, startY, startX - ARROW_SIZE, startY, colorToHex(style.lineColor()));
        ctx.arrowRight(startX - ARROW_SIZE, startY, colorToHex(style.arrowColor()));

        int[] dims = renderExpression(ctx, rule.body(), startX, GAP_V, style);
        int endX = dims[0];
        int contentHeight = dims[1];

        // Exit line with arrow
        int exitEndX = endX + GAP_H;
        ctx.line(endX, startY, exitEndX - ARROW_SIZE, startY, colorToHex(style.lineColor()));
        ctx.arrowRight(exitEndX - ARROW_SIZE, startY, colorToHex(style.arrowColor()));

        int totalWidth = exitEndX + GAP_H;
        int totalHeight = contentHeight + 2 * GAP_V;

        return "      <svg width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n%s      </svg>\n"
                .formatted(totalWidth, totalHeight, totalWidth, totalHeight, ctx.build());
    }

    /**
     * Renders an expression into the SVG context.
     *
     * @return int[]{endX, totalHeight} where endX is where the output line exits
     */
    private int[] renderExpression(SvgContext ctx, RuleExpression expr, int x, int y, DiagramStyle style) {
        return switch (expr) {
            case Terminal t -> renderTerminal(ctx, t, x, y, style);
            case NonTerminal nt -> renderNonTerminal(ctx, nt, x, y, style);
            case Sequence seq -> renderSequence(ctx, seq, x, y, style);
            case Alternation alt -> renderAlternation(ctx, alt, x, y, style);
            case Repetition rep -> renderRepetition(ctx, rep, x, y, style);
            case Group grp -> renderExpression(ctx, grp.inner(), x, y, style);
        };
    }

    private int[] renderTerminal(SvgContext ctx, Terminal terminal, int x, int y, DiagramStyle style) {
        String text = terminal.isRegex() ? terminal.value() : "'" + terminal.value() + "'";
        int textWidth = text.length() * CHAR_WIDTH;
        int boxWidth = textWidth + 2 * PADDING;
        int midY = y + BOX_HEIGHT / 2;

        if (terminal.isRegex()) {
            // Dashed rounded rect for regex
            ctx.roundedRect(x, y, boxWidth, BOX_HEIGHT, CORNER_RADIUS,
                    colorToHex(style.regexFillColor()), colorToHex(style.lineColor()), true);
        } else {
            // Solid rounded rect for literals
            ctx.roundedRect(x, y, boxWidth, BOX_HEIGHT, CORNER_RADIUS,
                    colorToHex(style.terminalFillColor()), colorToHex(style.lineColor()), false);
        }
        ctx.text(x + PADDING, midY + TEXT_HEIGHT / 2 - 2, text, colorToHex(style.textColor()));

        return new int[]{x + boxWidth, BOX_HEIGHT};
    }

    private int[] renderNonTerminal(SvgContext ctx, NonTerminal nt, int x, int y, DiagramStyle style) {
        String text = nt.ruleName();
        int textWidth = text.length() * CHAR_WIDTH;
        int boxWidth = textWidth + 2 * PADDING;
        int midY = y + BOX_HEIGHT / 2;

        // Regular rect for non-terminals
        ctx.rect(x, y, boxWidth, BOX_HEIGHT,
                colorToHex(style.nonTerminalFillColor()), colorToHex(style.lineColor()));
        ctx.linkedText(x + PADDING, midY + TEXT_HEIGHT / 2 - 2, text,
                "#rule-" + text, "#2c5aa0");

        return new int[]{x + boxWidth, BOX_HEIGHT};
    }

    private int[] renderSequence(SvgContext ctx, Sequence seq, int x, int y, DiagramStyle style) {
        int curX = x;
        int maxHeight = BOX_HEIGHT;
        int midY = y + BOX_HEIGHT / 2;

        for (int i = 0; i < seq.elements().size(); i++) {
            int[] dims = renderExpression(ctx, seq.elements().get(i), curX, y, style);
            curX = dims[0];
            maxHeight = Math.max(maxHeight, dims[1]);

            if (i < seq.elements().size() - 1) {
                // Connecting line
                ctx.line(curX, midY, curX + GAP_H, midY, colorToHex(style.lineColor()));
                curX += GAP_H;
            }
        }
        return new int[]{curX, maxHeight};
    }

    private int[] renderAlternation(SvgContext ctx, Alternation alt, int x, int y, DiagramStyle style) {
        // Calculate dimensions for each alternative
        var altWidths = new int[alt.alternatives().size()];
        var altHeights = new int[alt.alternatives().size()];
        int maxWidth = 0;

        // Pre-measure using a dummy context
        for (int i = 0; i < alt.alternatives().size(); i++) {
            int[] dims = measureExpression(alt.alternatives().get(i));
            altWidths[i] = dims[0];
            altHeights[i] = dims[1];
            maxWidth = Math.max(maxWidth, dims[0]);
        }

        int totalWidth = maxWidth + 2 * GAP_H;
        int firstMidY = y + BOX_HEIGHT / 2;

        // Render each alternative
        int curY = y;
        for (int i = 0; i < alt.alternatives().size(); i++) {
            int altMidY = curY + BOX_HEIGHT / 2;

            // Left connecting line from branch point
            ctx.line(x, firstMidY, x, altMidY, colorToHex(style.lineColor()));
            ctx.line(x, altMidY, x + GAP_H, altMidY, colorToHex(style.lineColor()));

            // Render the alternative
            int[] dims = renderExpression(ctx, alt.alternatives().get(i), x + GAP_H, curY, style);

            // Right connecting line to merge point
            int mergeX = x + totalWidth;
            ctx.line(dims[0], altMidY, mergeX, altMidY, colorToHex(style.lineColor()));
            ctx.line(mergeX, firstMidY, mergeX, altMidY, colorToHex(style.lineColor()));

            curY += altHeights[i] + GAP_V;
        }

        int totalHeight = curY - y - GAP_V;
        return new int[]{x + totalWidth, Math.max(totalHeight, BOX_HEIGHT)};
    }

    private int[] renderRepetition(SvgContext ctx, Repetition rep, int x, int y, DiagramStyle style) {
        int midY = y + BOX_HEIGHT / 2;
        String suffix = switch (rep.kind()) {
            case ZERO_OR_MORE -> "*";
            case ONE_OR_MORE -> "+";
            case OPTIONAL -> "?";
        };

        // Render the body
        int bodyX = x + GAP_H;
        int[] dims = renderExpression(ctx, rep.body(), bodyX, y, style);
        int bodyEndX = dims[0];
        int endX = bodyEndX + GAP_H;

        // Entry line
        ctx.line(x, midY, bodyX, midY, colorToHex(style.lineColor()));
        // Exit line
        ctx.line(bodyEndX, midY, endX, midY, colorToHex(style.lineColor()));

        // Loop-back arc for * and +
        if (rep.kind() == RepetitionKind.ZERO_OR_MORE || rep.kind() == RepetitionKind.ONE_OR_MORE) {
            int loopY = y + dims[1] + GAP_V;
            ctx.line(bodyEndX, midY, bodyEndX, loopY, colorToHex(style.lineColor()));
            ctx.line(bodyX, loopY, bodyEndX, loopY, colorToHex(style.lineColor()));
            ctx.line(bodyX, midY, bodyX, loopY, colorToHex(style.lineColor()));
        }

        // Bypass arc for * and ?
        if (rep.kind() == RepetitionKind.ZERO_OR_MORE || rep.kind() == RepetitionKind.OPTIONAL) {
            int bypassY = y - GAP_V;
            if (bypassY < 2) bypassY = 2;
            ctx.line(x, midY, x, bypassY, colorToHex(style.lineColor()));
            ctx.line(x, bypassY, endX, bypassY, colorToHex(style.lineColor()));
            ctx.line(endX, bypassY, endX, midY, colorToHex(style.lineColor()));
        }

        // Suffix label
        ctx.text(endX + 2, midY + TEXT_HEIGHT / 2 - 2, suffix, "#999");

        int suffixWidth = CHAR_WIDTH + 4;
        int totalHeight = dims[1];
        if (rep.kind() == RepetitionKind.ZERO_OR_MORE || rep.kind() == RepetitionKind.ONE_OR_MORE) {
            totalHeight = dims[1] + GAP_V + 4;
        }

        return new int[]{endX + suffixWidth, Math.max(totalHeight, BOX_HEIGHT)};
    }

    /**
     * Measures the approximate width and height of an expression without rendering.
     */
    private int[] measureExpression(RuleExpression expr) {
        return switch (expr) {
            case Terminal t -> {
                String text = t.isRegex() ? t.value() : "'" + t.value() + "'";
                yield new int[]{text.length() * CHAR_WIDTH + 2 * PADDING, BOX_HEIGHT};
            }
            case NonTerminal nt -> new int[]{nt.ruleName().length() * CHAR_WIDTH + 2 * PADDING, BOX_HEIGHT};
            case Sequence seq -> {
                int w = 0;
                int h = BOX_HEIGHT;
                for (int i = 0; i < seq.elements().size(); i++) {
                    int[] dims = measureExpression(seq.elements().get(i));
                    w += dims[0];
                    h = Math.max(h, dims[1]);
                    if (i < seq.elements().size() - 1) w += GAP_H;
                }
                yield new int[]{w, h};
            }
            case Alternation alt -> {
                int maxW = 0;
                int totalH = 0;
                for (int i = 0; i < alt.alternatives().size(); i++) {
                    int[] dims = measureExpression(alt.alternatives().get(i));
                    maxW = Math.max(maxW, dims[0]);
                    totalH += dims[1];
                    if (i < alt.alternatives().size() - 1) totalH += GAP_V;
                }
                yield new int[]{maxW + 2 * GAP_H, totalH};
            }
            case Repetition rep -> {
                int[] dims = measureExpression(rep.body());
                yield new int[]{dims[0] + 2 * GAP_H + CHAR_WIDTH + 4, dims[1]};
            }
            case Group grp -> measureExpression(grp.inner());
        };
    }

    // ---- BNF Text Formatter ----

    private String expressionToBnf(RuleExpression expr) {
        return switch (expr) {
            case Terminal t -> t.isRegex() ? "/" + t.value() + "/" : "'" + t.value() + "'";
            case NonTerminal nt -> nt.ruleName();
            case Sequence seq -> {
                var parts = seq.elements().stream().map(this::expressionToBnf).toList();
                yield String.join(" ", parts);
            }
            case Alternation alt -> {
                var parts = alt.alternatives().stream().map(this::expressionToBnf).toList();
                yield String.join(" | ", parts);
            }
            case Repetition rep -> {
                String body = expressionToBnf(rep.body());
                boolean needsParens = rep.body() instanceof Alternation || rep.body() instanceof Sequence;
                if (needsParens) body = "( " + body + " )";
                yield body + switch (rep.kind()) {
                    case ZERO_OR_MORE -> "*";
                    case ONE_OR_MORE -> "+";
                    case OPTIONAL -> "?";
                };
            }
            case Group grp -> "( " + expressionToBnf(grp.inner()) + " )";
        };
    }

    // ---- SVG Builder ----

    private static final class SvgContext {
        private final StringBuilder sb = new StringBuilder();

        void line(int x1, int y1, int x2, int y2, String stroke) {
            sb.append("        <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.5\" />\n"
                    .formatted(x1, y1, x2, y2, stroke));
        }

        void rect(int x, int y, int w, int h, String fill, String stroke) {
            sb.append("        <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.5\" />\n"
                    .formatted(x, y, w, h, fill, stroke));
        }

        void roundedRect(int x, int y, int w, int h, int r, String fill, String stroke, boolean dashed) {
            sb.append("        <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"%d\" ry=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.5\""
                    .formatted(x, y, w, h, r, r, fill, stroke));
            if (dashed) sb.append(" stroke-dasharray=\"4,3\"");
            sb.append(" />\n");
        }

        void text(int x, int y, String content, String fill) {
            sb.append("        <text x=\"%d\" y=\"%d\" fill=\"%s\">%s</text>\n"
                    .formatted(x, y, fill, escapeXml(content)));
        }

        void linkedText(int x, int y, String content, String href, String fill) {
            sb.append("        <a href=\"%s\"><text x=\"%d\" y=\"%d\" fill=\"%s\">%s</text></a>\n"
                    .formatted(escapeXml(href), x, y, fill, escapeXml(content)));
        }

        void arrowRight(int x, int y, String fill) {
            sb.append("        <polygon points=\"%d,%d %d,%d %d,%d\" fill=\"%s\" />\n"
                    .formatted(x, y - ARROW_SIZE / 2, x + ARROW_SIZE, y, x, y + ARROW_SIZE / 2, fill));
        }

        String build() {
            return sb.toString();
        }

        private static String escapeXml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String colorToHex(java.awt.Color c) {
        return "#%02x%02x%02x".formatted(c.getRed(), c.getGreen(), c.getBlue());
    }
}

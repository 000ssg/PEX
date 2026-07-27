package ssg.pex.tools.docgen;

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
import ssg.pex.tools.visualizer.DiagramStyle;
import ssg.pex.tools.visualizer.PngExporter;

import java.nio.file.Path;

/**
 * Generates Markdown documentation with embedded PNG railroad diagrams.
 *
 * <p>For each grammar rule, renders a PNG railroad diagram image (via {@link PngExporter})
 * and produces Markdown with image references and BNF notation code blocks.
 */
public final class MarkdownDiagramGenerator {

    private static final Logger log = LoggerFactory.getLogger(MarkdownDiagramGenerator.class);

    private final PngExporter pngExporter = new PngExporter();

    /**
     * Generates Markdown documentation for the given grammar.
     *
     * <p>PNG images are written to {@code imageDir}; the Markdown references them
     * using a relative path ({@code images/<ruleName>.png}).
     *
     * @param grammar  the grammar to document
     * @param style    diagram styling for PNG rendering
     * @param imageDir the directory to write PNG files into
     * @return a result containing the Markdown text
     */
    public Result<String> generateMarkdown(Grammar grammar, DiagramStyle style, Path imageDir) {
        try {
            // Export all rule diagrams as PNG
            var exportResult = pngExporter.exportGrammar(grammar, style, imageDir);
            if (exportResult.isFailure()) {
                return Result.failure("DOCGEN_MD_PNG",
                        "Failed to export PNG diagrams: " + exportResult.error().message());
            }

            var sb = new StringBuilder();
            sb.append("# Grammar: ").append(grammar.name()).append("\n\n");

            // Table of contents
            sb.append("## Table of Contents\n\n");
            for (var ruleName : grammar.rules().keySet()) {
                sb.append("- [").append(ruleName).append("](#").append(toAnchor(ruleName)).append(")\n");
            }
            sb.append("\n---\n\n");

            // Determine relative path from markdown to images
            String imageRelPath = imageDir.getFileName().toString();

            // Rule sections
            for (var entry : grammar.rules().entrySet()) {
                Rule rule = entry.getValue();
                sb.append("### ").append(rule.name()).append("\n\n");
                sb.append("![").append(rule.name()).append("](")
                        .append(imageRelPath).append("/").append(rule.name()).append(".png)\n\n");
                sb.append("```bnf\n");
                sb.append(rule.name()).append(" ::= ").append(expressionToBnf(rule.body()));
                sb.append("\n```\n\n");
            }

            log.debug("Generated Markdown documentation for grammar '{}' with {} rules",
                    grammar.name(), grammar.rules().size());
            return Result.success(sb.toString());

        } catch (Exception e) {
            return Result.failure("DOCGEN_MD", "Failed to generate Markdown: " + e.getMessage(), e);
        }
    }

    private static String toAnchor(String name) {
        return name.toLowerCase().replace('_', '-');
    }

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
}

package ssg.pex.tools.visualizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Rule;
import ssg.pex.result.Result;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Exports railroad diagrams as PNG image files.
 *
 * <p>Uses {@link RailroadDiagramRenderer} to render onto a {@link BufferedImage}
 * and writes via {@link ImageIO}.
 */
public final class PngExporter {

    private static final Logger log = LoggerFactory.getLogger(PngExporter.class);

    private final RailroadDiagramRenderer renderer = new RailroadDiagramRenderer();

    /**
     * Export a single rule as a PNG file.
     *
     * @param rule       the rule to export
     * @param style      diagram styling
     * @param outputFile the target PNG file path
     * @return the output path on success
     */
    public Result<Path> exportRule(Rule rule, DiagramStyle style, Path outputFile) {
        return Result.of(() -> {
            var measureG = createMeasureGraphics(style);
            var metrics = renderer.measureRule(rule, measureG, style);
            measureG.dispose();

            var image = new BufferedImage(metrics.width(), metrics.height(), BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            enableAntialiasing(g);

            // Fill background
            g.setColor(style.backgroundColor());
            g.fillRect(0, 0, metrics.width(), metrics.height());

            // Render rule
            renderer.renderRule(rule, g, 0, 0, style);
            g.dispose();

            // Write to file
            Files.createDirectories(outputFile.getParent());
            ImageIO.write(image, "PNG", outputFile.toFile());

            log.debug("Exported rule '{}' to PNG: {} ({}x{})",
                    rule.name(), outputFile, metrics.width(), metrics.height());
            return outputFile;
        });
    }

    /**
     * Export all rules of a grammar as separate PNG files in the given directory.
     * Each file is named {@code <ruleName>.png}.
     *
     * @param grammar   the grammar to export
     * @param style     diagram styling
     * @param outputDir the target directory
     * @return the output directory path on success
     */
    public Result<Path> exportGrammar(Grammar grammar, DiagramStyle style, Path outputDir) {
        return Result.of(() -> {
            Files.createDirectories(outputDir);

            for (var rule : grammar.rules().values()) {
                var filePath = outputDir.resolve(rule.name() + ".png");
                var result = exportRule(rule, style, filePath);
                if (result.isFailure()) {
                    throw new RuntimeException("Failed to export rule '%s': %s"
                            .formatted(rule.name(), result.error().message()));
                }
            }

            log.debug("Exported grammar '{}' ({} rules) to PNG directory: {}",
                    grammar.name(), grammar.rules().size(), outputDir);
            return outputDir;
        });
    }

    // ---- Helpers ----

    private Graphics2D createMeasureGraphics(DiagramStyle style) {
        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setFont(style.textFont());
        return g;
    }

    private static void enableAntialiasing(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}

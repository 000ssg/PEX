package ssg.pex.demo.tools;

import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Rule;
import ssg.pex.tools.visualizer.SvgExporter;
import ssg.pex.tools.visualizer.PngExporter;
import ssg.pex.tools.visualizer.DiagramStyle;
import ssg.pex.result.Result;

import java.io.File;
import java.nio.file.Path;

/**
 * Demonstrates grammar railroad diagram rendering as SVG and PNG.
 */
public final class GrammarVisualizerDemo {

    private GrammarVisualizerDemo() {}

    public static void main(String[] args) {
        String bnfSource = """
                grammar arithmetic;

                expr     ::= term ( ( '+' | '-' ) term )* ;
                term     ::= factor ( ( '*' | '/' ) factor )* ;
                factor   ::= /[0-9]+/ | '(' expr ')' ;
                """;

        System.out.println("--- BNF Grammar Visualization ---");
        System.out.println("Grammar source:");
        System.out.println(bnfSource);

        var parser = new BnfParser();
        Result<Grammar> parseResult = parser.parse(bnfSource);

        if (parseResult.isFailure()) {
            System.out.println("Failed to parse grammar: " + parseResult.error());
            return;
        }

        Grammar grammar = parseResult.value();
        System.out.println("✓ Grammar parsed: " + grammar.rules().size() + " rules\n");

        String outputDir = System.getProperty("java.io.tmpdir") + "/pex-demos/grammar";
        new File(outputDir).mkdirs();

        SvgExporter svgExporter = new SvgExporter();
        PngExporter pngExporter = new PngExporter();
        var style = DiagramStyle.defaultStyle();

        for (Rule rule : grammar.rules().values()) {
            String ruleName = rule.name();
            try {
                Result<String> svgResult = svgExporter.exportRule(rule, style);
                if (svgResult.isFailure()) {
                    System.out.println("  ✗ SVG '" + ruleName + "': " + svgResult.error());
                    continue;
                }
                Path svgFile = Path.of(outputDir, ruleName + ".svg");
                java.nio.file.Files.writeString(svgFile, svgResult.value());
                System.out.println("  ✓ SVG: " + svgFile);

                Path pngFile = Path.of(outputDir, ruleName + ".png");
                Result<Path> pngResult = pngExporter.exportRule(rule, style, pngFile);
                if (pngResult.isFailure()) {
                    System.out.println("  ✗ PNG '" + ruleName + "': " + pngResult.error());
                    continue;
                }
                System.out.println("  ✓ PNG: " + pngFile);
            } catch (Exception e) {
                System.out.println("  ✗ Error for '" + ruleName + "': " + e.getMessage());
            }
        }

        System.out.println("\nGrammar visualizer demo completed.");
        System.out.println("Diagrams saved to: " + outputDir);
    }
}

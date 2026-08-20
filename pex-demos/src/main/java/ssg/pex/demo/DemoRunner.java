package ssg.pex.demo;

import ssg.pex.demo.simple.SimpleQueryDemo;
import ssg.pex.demo.simple.DialectComparisonDemo;
import ssg.pex.demo.olap.OlapOperationsDemo;
import ssg.pex.demo.nosql.NoSqlQueryDemo;
import ssg.pex.demo.nosql.NoSqlDialectDemo;
import ssg.pex.demo.tools.GrammarVisualizerDemo;
import ssg.pex.demo.streaming.StreamingDemo;
import ssg.pex.demo.benchmark.BenchmarkDemo;

/**
 * Entry point for running all PEX demos.
 *
 * <p>Each demo is self-contained and can be run individually via its main method,
 * or all demos can be executed together through this runner.
 *
 * <p>Usage:
 * <pre>{@code
 * DemoRunner.runAll();
 * }</pre>
 *
 * @since 0.2.0
 */
public final class DemoRunner {

    private DemoRunner() {}

    /**
     * Runs all demos sequentially, printing results for each.
     */
    public static void runAll() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║              PEX Demo Suite — v0.2.0                    ║");
        System.out.println("║       Expression & Query Framework Demonstrations       ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        run("Simple Query", SimpleQueryDemo::main);
        run("Dialect Comparison", DialectComparisonDemo::main);
        run("OLAP Operations", OlapOperationsDemo::main);
        run("NoSQL Query", NoSqlQueryDemo::main);
        run("NoSQL Dialect", NoSqlDialectDemo::main);
        run("Grammar Visualizer", GrammarVisualizerDemo::main);
        run("Streaming", StreamingDemo::main);
        run("Benchmark", BenchmarkDemo::main);

        System.out.println();
        System.out.println("All demos completed.");
    }

    /**
     * Run a single demo by name.
     */
    public static void run(String name) {
        switch (name.toLowerCase()) {
            case "query" -> SimpleQueryDemo.main(new String[0]);
            case "dialect" -> DialectComparisonDemo.main(new String[0]);
            case "olap" -> OlapOperationsDemo.main(new String[0]);
            case "nosql" -> NoSqlQueryDemo.main(new String[0]);
            case "nosql-dialect" -> NoSqlDialectDemo.main(new String[0]);
            case "grammar" -> GrammarVisualizerDemo.main(new String[0]);
            case "streaming" -> StreamingDemo.main(new String[0]);
            case "benchmark" -> BenchmarkDemo.main(new String[0]);
            default -> System.out.println("Unknown demo: " + name);
        }
    }

    @FunctionalInterface
    private interface MainRunner {
        void run(String[] args);
    }

    private static void run(String name, MainRunner runner) {
        System.out.println("--- " + name + " ---");
        try {
            runner.run(new String[0]);
        } catch (Exception e) {
            System.err.println("  ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
        }
        System.out.println();
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            run(args[0]);
        } else {
            runAll();
        }
    }
}

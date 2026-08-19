package ssg.pex.demo.benchmark;

import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectDatabase;
import ssg.pex.sql.dialects.DialectDatabase.DialectType;

/**
 * Demonstrates performance comparison across PEX modules.
 */
public final class BenchmarkDemo {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 500;

    private BenchmarkDemo() {}

    public static void main(String[] args) {
        System.out.println("--- PEX Benchmark Suite ---");
        System.out.println("Warmup: " + WARMUP_ITERATIONS + " iterations");
        System.out.println("Benchmark: " + BENCHMARK_ITERATIONS + " iterations\n");

        benchmarkParse();
        benchmarkSimpleQuery();
        benchmarkDialects();
        benchmarkInsertThroughput();

        System.out.println("\nBenchmark suite completed.");
    }

    private static void benchmarkParse() {
        String sql = "SELECT * FROM users WHERE age > 25 AND dept = 'Engineering'";

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try (var db = new InMemoryDatabase()) {
                db.execute(sql);
            }
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            try (var db = new InMemoryDatabase()) {
                db.execute(sql);
            }
        }
        long elapsed = System.nanoTime() - start;
        double perOpMs = elapsed / (double) BENCHMARK_ITERATIONS / 1_000_000.0;

        System.out.println("1. SQL Parse Benchmark");
        System.out.println("   " + sql);
        System.out.println("   Avg: " + String.format("%.4f", perOpMs) + " ms/op");
        System.out.println("   Throughput: " + String.format("%.0f", BENCHMARK_ITERATIONS / (elapsed / 1_000_000_000.0)) + " ops/sec\n");
    }

    private static void benchmarkSimpleQuery() {
        var db = new InMemoryDatabase();
        db.execute("CREATE TABLE benchmark (id INT, value DOUBLE)");
        for (int i = 0; i < 10000; i++) {
            db.execute("INSERT INTO benchmark VALUES (" + i + ", " + (i * 1.5) + ")");
        }

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            db.execute("SELECT * FROM benchmark WHERE value > 5000");
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            db.execute("SELECT * FROM benchmark WHERE value > 5000");
        }
        long elapsed = System.nanoTime() - start;

        System.out.println("2. Simple Query Execution Benchmark");
        System.out.println("   SELECT * FROM benchmark WHERE value > 5000");
        System.out.println("   (10K rows, " + BENCHMARK_ITERATIONS + " iterations)");
        System.out.println("   Avg: " + String.format("%.4f", elapsed / (double) BENCHMARK_ITERATIONS / 1_000_000.0) + " ms/op\n");
        db.close();
    }

    private static void benchmarkDialects() {
        String sql = "SELECT COUNT(*) as cnt FROM items";

        for (var dialect : DialectType.values()) {
            var innerDb = new InMemoryDatabase();
            var db = new DialectDatabase(innerDb, dialect);
            db.execute("CREATE TABLE items (id INT, name VARCHAR(100), price DOUBLE)");
            for (int i = 0; i < 1000; i++) {
                db.execute("INSERT INTO items VALUES (" + i + ", 'item" + i + "', " + (i * 0.5) + ")");
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                db.execute(sql);
            }

            // Benchmark
            long start = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                db.execute(sql);
            }
            long elapsed = System.nanoTime() - start;

            System.out.println("3. Dialect: " + dialect);
            System.out.println("   Avg: " + String.format("%.4f", elapsed / (double) BENCHMARK_ITERATIONS / 1_000_000.0) + " ms/op");
            System.out.println("   Throughput: " + String.format("%.0f", BENCHMARK_ITERATIONS / (elapsed / 1_000_000_000.0)) + " ops/sec");

            db.close();
        }
        System.out.println();
    }

    private static void benchmarkInsertThroughput() {
        var db = new InMemoryDatabase();
        db.execute("CREATE TABLE throughput_test (id INT, data VARCHAR(255))");

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            db.execute("INSERT INTO throughput_test VALUES (" + i + ", 'data_" + i + "')");
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            db.execute("INSERT INTO throughput_test VALUES (" + i + ", 'data_" + i + "')");
        }
        long elapsed = System.nanoTime() - start;
        double throughput = BENCHMARK_ITERATIONS / (elapsed / 1_000_000_000.0);

        System.out.println("4. Insert Throughput Benchmark");
        System.out.println("   " + BENCHMARK_ITERATIONS + " inserts in " + String.format("%.2f", elapsed / 1_000_000_000.0) + "s");
        System.out.println("   Throughput: " + String.format("%.0f", throughput) + " inserts/sec\n");
        db.close();
    }
}

package ssg.pex.demo.olap;

import ssg.pex.sql.olap.OlapDatabase;
import ssg.pex.sql.dbms.result.QueryResult;

/**
 * Demonstrates OLAP operations: window functions, ROLLUP, CUBE.
 */
public final class OlapOperationsDemo {

    private OlapOperationsDemo() {}

    public static void main(String[] args) {
        var db = new OlapDatabase();

        db.executeOlap("CREATE TABLE sales (year INT, region VARCHAR(50), product VARCHAR(50), amount DOUBLE)");
        db.executeOlap("INSERT INTO sales VALUES (2023, 'North', 'A', 1000)");
        db.executeOlap("INSERT INTO sales VALUES (2023, 'North', 'B', 1500)");
        db.executeOlap("INSERT INTO sales VALUES (2023, 'South', 'A', 1200)");
        db.executeOlap("INSERT INTO sales VALUES (2023, 'South', 'B', 1800)");
        db.executeOlap("INSERT INTO sales VALUES (2024, 'North', 'A', 1100)");
        db.executeOlap("INSERT INTO sales VALUES (2024, 'North', 'B', 1600)");
        db.executeOlap("INSERT INTO sales VALUES (2024, 'South', 'A', 1300)");
        db.executeOlap("INSERT INTO sales VALUES (2024, 'South', 'B', 1900)");
        System.out.println("✓ 8 sales records inserted");

        System.out.println("\n--- Window Function: Running Total ---");
        var wr = db.executeWindowFunction(
                "SELECT year, region, product, amount, SUM(amount) OVER (PARTITION BY year ORDER BY region) as rt FROM sales ORDER BY year, region",
                java.util.List.of()
        );
        printQuery(wr.value());
        if (wr.isFailure()) System.out.println("  Error: " + wr.error());

        System.out.println("\n--- Aggregate by Region ---");
        var ar = db.executeOlap("SELECT region, SUM(amount) as total FROM sales GROUP BY region ORDER BY region");
        printQuery(ar.value());

        System.out.println("\n--- ROLLUP: Subtotals ---");
        var rr = db.executeOlap("SELECT region, product, SUM(amount) as total FROM sales GROUP BY ROLLUP(region, product) ORDER BY region, product");
        printQuery(rr.value());

        System.out.println("\n--- CUBE: All Combinations ---");
        var cr = db.executeOlap("SELECT year, region, COUNT(*) as cnt, SUM(amount) as total FROM sales GROUP BY CUBE(year, region) ORDER BY year, region");
        printQuery(cr.value());

        db.close();
        System.out.println("\nOLAP demo completed.");
    }

    private static void printQuery(Object obj) {
        if (obj instanceof QueryResult qr) {
            var cols = qr.columnNames();
            System.out.print("  ");
            cols.forEach(c -> System.out.printf("%-18s", c));
            System.out.println();
            qr.rows().forEach(row -> {
                System.out.print("  ");
                for (int i = 0; i < row.columnCount(); i++) {
                    System.out.printf("%-18s", row.getValue(i));
                }
                System.out.println();
            });
        }
    }
}

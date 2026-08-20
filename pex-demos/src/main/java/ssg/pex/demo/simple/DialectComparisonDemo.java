package ssg.pex.demo.simple;

import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectDatabase;
import ssg.pex.sql.dialects.DialectDatabase.DialectType;

/**
 * Demonstrates the same query across all supported SQL dialects.
 */
public final class DialectComparisonDemo {

    private DialectComparisonDemo() {}

    public static void main(String[] args) {
        String query = "SELECT * FROM items WHERE price > 10.0";

        for (var dialect : DialectType.values()) {
            System.out.println("--- " + dialect.name() + " ---");
            var innerDb = new InMemoryDatabase();
            var db = new DialectDatabase(innerDb, dialect);

            db.execute("CREATE TABLE items (id INT, name VARCHAR(100), price DOUBLE)");
            db.execute("INSERT INTO items VALUES (1, 'Widget', 15.99)");
            db.execute("INSERT INTO items VALUES (2, 'Gadget', 8.50)");
            db.execute("INSERT INTO items VALUES (3, 'Doohickey', 22.00)");

            var result = db.execute(query);
            printQuery(result.value());
            if (result.isFailure()) {
                System.out.println("  Error: " + result.error());
            }

            db.close();
            System.out.println();
        }
    }

    private static void printQuery(Object obj) {
        if (obj instanceof QueryResult qr) {
            var cols = qr.columnNames();
            System.out.print("  ");
            cols.forEach(c -> System.out.printf("%-15s", c));
            System.out.println();
            qr.rows().forEach(row -> {
                System.out.print("  ");
                for (int i = 0; i < row.columnCount(); i++) {
                    System.out.printf("%-15s ", row.getValue(i));
                }
                System.out.println();
            });
            System.out.println("  " + qr.rowCount() + " rows returned");
        }
    }
}

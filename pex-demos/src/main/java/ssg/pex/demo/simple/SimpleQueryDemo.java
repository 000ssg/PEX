package ssg.pex.demo.simple;

import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;

/**
 * Demonstrates basic SQL operations: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE.
 */
public final class SimpleQueryDemo {

    private SimpleQueryDemo() {}

    public static void main(String[] args) {
        var db = new InMemoryDatabase();

        db.execute("CREATE TABLE employees (id INT, name VARCHAR(100), department VARCHAR(50), salary DOUBLE)");
        System.out.println("✓ Table 'employees' created");

        db.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 95000)");
        db.execute("INSERT INTO employees VALUES (2, 'Bob', 'Marketing', 72000)");
        db.execute("INSERT INTO employees VALUES (3, 'Carol', 'Engineering', 105000)");
        db.execute("INSERT INTO employees VALUES (4, 'Dave', 'Sales', 68000)");
        System.out.println("✓ 4 employees inserted");

        System.out.println("\n--- All Employees ---");
        printQuery(db.execute("SELECT * FROM employees"));

        System.out.println("\n--- Engineering Department ---");
        printQuery(db.execute("SELECT * FROM employees WHERE department = 'Engineering'"));

        db.execute("UPDATE employees SET salary = salary * 1.10 WHERE department = 'Engineering'");
        System.out.println("\n✓ Engineering salaries increased by 10%");

        System.out.println("\n--- Updated Salaries ---");
        printQuery(db.execute("SELECT * FROM employees"));

        db.execute("DELETE FROM employees WHERE id = 4");
        System.out.println("\n✓ Employee Dave deleted");

        System.out.println("\n--- Final Roster ---");
        printQuery(db.execute("SELECT * FROM employees"));

        db.close();
    }

    private static void printQuery(Object result) {
        if (result instanceof QueryResult qr) {
            var cols = qr.columnNames();
            System.out.print("  ");
            cols.forEach(c -> System.out.printf("%-18s", c));
            System.out.println();
            System.out.println("  " + "-".repeat(cols.size() * 18));
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

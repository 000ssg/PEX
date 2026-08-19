package ssg.pex.demo.nosql;

import ssg.pex.nosql.Document;
import ssg.pex.nosql.dialects.cassandra.CassandraDatabase;
import ssg.pex.nosql.dialects.cassandra.CassandraResultSet;

/**
 * Demonstrates Cassandra CQL operations on the PEX NoSQL engine.
 */
public final class NoSqlDialectDemo {

    private NoSqlDialectDemo() {}

    public static void main(String[] args) {
        var db = new CassandraDatabase();

        System.out.println("--- Cassandra CQL Demo ---");

        // Open a session
        try (var session = db.connect("demo")) {
            // Create table
            session.execute("""
                CREATE TABLE users (
                    id UUID PRIMARY KEY,
                    name TEXT,
                    age INT,
                    email TEXT
                )
                """);
            System.out.println("✓ Table 'users' created");

            // Insert
            session.execute("INSERT INTO users (id, name, age, email) VALUES (uuid(), 'Alice', 30, 'alice@cass.com')");
            session.execute("INSERT INTO users (id, name, age, email) VALUES (uuid(), 'Bob', 25, 'bob@cass.com')");
            session.execute("INSERT INTO users (id, name, age, email) VALUES (uuid(), 'Carol', 35, 'carol@cass.com')");
            System.out.println("✓ 3 users inserted");

            // Select all
            System.out.println("\n--- All Users ---");
            CassandraResultSet result = session.execute("SELECT * FROM users");
            result.rows().forEach(row -> System.out.println("  " + row));

            // Select by age range
            System.out.println("\n--- Users aged 25-32 ---");
            CassandraResultSet filtered = session.execute(
                "SELECT * FROM users WHERE age >= 25 AND age <= 32"
            );
            filtered.rows().forEach(row -> System.out.println("  " + row));
        }

        System.out.println("\nCQL demo completed.");
    }
}

package ssg.pex.demo.nosql;

import ssg.pex.nosql.Document;
import ssg.pex.nosql.InMemoryNoSqlDatabase;
import ssg.pex.nosql.dialects.mongodb.MongoDbDatabase;

import java.util.Map;

/**
 * Demonstrates MongoDB-style queries on the PEX NoSQL engine.
 */
public final class NoSqlQueryDemo {

    private NoSqlQueryDemo() {}

    public static void main(String[] args) {
        var db = new MongoDbDatabase(new InMemoryNoSqlDatabase());
        String collection = "users";

        var col = db.createCollection(collection);
        col.insertOne(Document.of("name", "Alice", "age", 30, "email", "alice@example.com"));
        col.insertOne(Document.of("name", "Bob", "age", 25, "email", "bob@example.com"));
        col.insertOne(Document.of("name", "Carol", "age", 35, "email", "carol@example.com"));
        col.insertOne(Document.of("name", "Dave", "age", 28, "email", "dave@example.com"));
        System.out.println("✓ 4 users inserted");

        System.out.println("\n--- All Users ---");
        col.find().forEach(doc -> System.out.println("  " + doc));

        System.out.println("\n--- Users Aged 28+ ---");
        col.find(Document.of("age", Map.of("$gte", 28))).forEach(doc -> System.out.println("  " + doc));

        System.out.println("\n--- Specific User: Alice ---");
        col.find(Document.of("name", "Alice")).forEach(doc -> System.out.println("  " + doc));

        System.out.println("\n--- Deleting Dave ---");
        col.deleteOne(Document.of("name", "Dave"));

        System.out.println("\n--- Remaining Users ---");
        col.find().forEach(doc -> System.out.println("  " + doc));

        System.out.println("\nNoSQL demo completed.");
    }
}

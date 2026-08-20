package ssg.pex.demo.streaming;

import ssg.pex.sql.streaming.engine.StreamEvent;
import ssg.pex.sql.streaming.engine.StreamSimulator;

import java.util.List;
import java.util.Map;

/**
 * Demonstrates real-time stream processing with PEX SQL streaming engine.
 */
public final class StreamingDemo {

    private StreamingDemo() {}

    public static void main(String[] args) {
        System.out.println("--- Stream Processing Demo ---");

        var simulator = new StreamSimulator();

        // Create a stream and query
        System.out.println("\n--- Creating Stream: 'transactions' ---");
        simulator.executeQuery("CREATE STREAM transactions (product TEXT, category TEXT, price DOUBLE)");
        System.out.println("✓ Stream 'transactions' created");

        // Ingest events
        List<StreamEvent> events = List.of(
                new StreamEvent(System.currentTimeMillis(), null, Map.of("product", "Widget", "category", "tools", "price", 15.99)),
                new StreamEvent(System.currentTimeMillis(), null, Map.of("product", "Gadget", "category", "electronics", "price", 5.00)),
                new StreamEvent(System.currentTimeMillis(), null, Map.of("product", "Doohickey", "category", "tools", "price", 22.50)),
                new StreamEvent(System.currentTimeMillis(), null, Map.of("product", "Thingamajig", "category", "electronics", "price", 12.99)),
                new StreamEvent(System.currentTimeMillis(), null, Map.of("product", "Whatchamacallit", "category", "tools", "price", 8.00)),
                new StreamEvent(System.currentTimeMillis(), null, Map.of("product", "Doodad", "category", "electronics", "price", 35.00))
        );

        System.out.println("\n--- Ingesting Events ---");
        for (StreamEvent event : events) {
            System.out.println("  " + event.values().get("product")
                    + " | " + event.values().get("category")
                    + " | $" + event.values().get("price"));
            simulator.ingestEvent("transactions", event);
        }

        // Filter: price > 10.00
        System.out.println("\n--- Filter: price > 10.00 ---");
        long accepted = events.stream()
                .filter(e -> (double) e.values().getOrDefault("price", 0.0) > 10.0)
                .count();
        System.out.println("  " + accepted + " of " + events.size() + " events passed the filter");

        System.out.println("\nStreaming demo completed.");
    }
}

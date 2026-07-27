package ssg.pex.sql.streaming;

import org.junit.jupiter.api.*;
import ssg.pex.sql.ast.SqlExpression.AggregateFunction;
import ssg.pex.sql.ast.SqlSupport.ColumnDef;
import ssg.pex.sql.ast.SqlSupport.SqlDataType;
import ssg.pex.sql.streaming.ast.*;
import ssg.pex.sql.streaming.ast.WindowSpec.WindowType;
import ssg.pex.sql.streaming.engine.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Real-world streaming test scenarios: IoT sensor monitoring, e-commerce clickstreams,
 * financial transaction processing with tumbling/hopping/session/sliding windows,
 * watermarks, joins, and aggregations.
 */
class RealWorldStreamingTest {

    // ==========================================================================
    // IoT Sensor Monitoring
    // ==========================================================================

    @Nested
    @DisplayName("IoT Sensor Monitoring")
    class IoTSensorTests {

        private StreamSimulator sim;

        @BeforeEach
        void setUp() {
            sim = new StreamSimulator();
            sim.executeQuery("CREATE STREAM sensor_readings (sensor_id VARCHAR, temperature DOUBLE, humidity DOUBLE, ts BIGINT)");
        }

        @Test
        @DisplayName("Tumbling window: 10-second average temperature per sensor")
        void tumblingWindowAvgTempPerSensor() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.tumbling(10000);

            // Sensor A: readings at 1s, 3s, 5s, 7s, 9s
            for (int i = 0; i < 5; i++) {
                long ts = 1000 + i * 2000;
                wm.assignToWindows(new StreamEvent(ts, "sensorA",
                        Map.of("sensor_id", "sensorA", "temperature", 20.0 + i, "ts", ts)));
            }

            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(1);
            assertThat(closed.getFirst().events()).hasSize(5);

            Object avg = aggregator.aggregate(AggregateFunction.AVG, closed.getFirst().events(), "temperature");
            // 20, 21, 22, 23, 24 -> avg = 22.0
            assertThat((Double) avg).isCloseTo(22.0, within(0.01));
        }

        @Test
        @DisplayName("Hopping window: overlapping 30s windows advancing every 10s for anomaly detection")
        void hoppingWindowAnomalyDetection() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.hopping(30000, 10000);

            // 15 readings, one per 2 seconds
            for (int i = 0; i < 15; i++) {
                long ts = i * 2000;
                wm.assignToWindows(new StreamEvent(ts, "sensor1",
                        Map.of("temperature", 20.0 + (i % 5), "ts", ts)));
            }

            var closed = wm.getClosedWindows(30000);
            assertThat(closed).isNotEmpty();

            // First window [0, 30000) should have all 15 events (0 to 28s)
            Window firstWindow = closed.stream().filter(w -> w.startTime() == 0).findFirst().orElseThrow();
            assertThat(firstWindow.events()).hasSize(15);
            Object max = aggregator.aggregate(AggregateFunction.MAX, firstWindow.events(), "temperature");
            assertThat((Double) max).isGreaterThan(20.0);
        }

        @Test
        @DisplayName("Session window: group readings per device session with 5s gap")
        void sessionWindowDeviceSessions() {
            var wm = WindowManager.session(5000);

            // Session 1: rapid readings at 0, 1, 2, 3 seconds
            for (int i = 0; i < 4; i++) {
                wm.assignToWindows(new StreamEvent(i * 1000, "dev1",
                        Map.of("temperature", 25.0 + i)));
            }
            // Gap of 10 seconds
            // Session 2: readings at 14, 15 seconds
            wm.assignToWindows(new StreamEvent(14000, "dev1", Map.of("temperature", 30.0)));
            wm.assignToWindows(new StreamEvent(15000, "dev1", Map.of("temperature", 31.0)));

            var closed = wm.getClosedWindows(25000);
            assertThat(closed).hasSize(2);
            assertThat(closed.get(0).events()).hasSize(4);
            assertThat(closed.get(1).events()).hasSize(2);
        }

        @Test
        @DisplayName("Sliding window: per-event temperature alerts")
        void slidingWindowTempAlerts() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.sliding(5000);

            wm.assignToWindows(new StreamEvent(1000, "s1", Map.of("temperature", 18.0)));
            wm.assignToWindows(new StreamEvent(3000, "s1", Map.of("temperature", 22.0)));
            wm.assignToWindows(new StreamEvent(5000, "s1", Map.of("temperature", 35.0)));

            // Each event creates its own sliding window
            var open = wm.openWindows();
            assertThat(open).hasSize(3);

            // The window ending at 5001 should include events at 1000, 3000, 5000
            Window lastWindow = open.stream()
                    .filter(w -> w.endTime() == 5001)
                    .findFirst()
                    .orElseThrow();
            assertThat(lastWindow.events()).hasSize(3);
            Object max = aggregator.aggregate(AggregateFunction.MAX, lastWindow.events(), "temperature");
            assertThat((Double) max).isEqualTo(35.0);
        }
    }

    // ==========================================================================
    // E-Commerce Clickstream
    // ==========================================================================

    @Nested
    @DisplayName("E-Commerce Clickstream")
    class ClickstreamTests {

        private StreamSimulator sim;

        @BeforeEach
        void setUp() {
            sim = new StreamSimulator();
            sim.executeQuery("CREATE STREAM page_views (user_id VARCHAR, page VARCHAR, action VARCHAR, ts BIGINT)");
        }

        @Test
        @DisplayName("Track user page views and aggregate by session")
        void trackUserPageViews() {
            sim.ingestEvent("page_views", new StreamEvent(1000, "u1",
                    Map.of("user_id", "u1", "page", "/home", "action", "view", "ts", 1000L)));
            sim.ingestEvent("page_views", new StreamEvent(2000, "u1",
                    Map.of("user_id", "u1", "page", "/products", "action", "view", "ts", 2000L)));
            sim.ingestEvent("page_views", new StreamEvent(3000, "u1",
                    Map.of("user_id", "u1", "page", "/products/123", "action", "click", "ts", 3000L)));
            sim.ingestEvent("page_views", new StreamEvent(4000, "u2",
                    Map.of("user_id", "u2", "page", "/home", "action", "view", "ts", 4000L)));

            assertThat(sim.getStream("page_views").peekAll()).hasSize(4);

            var queryResult = sim.executeQuery("SELECT * FROM page_views");
            assertThat(queryResult.isSuccess()).isTrue();
            assertThat(queryResult.value().rowCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("Tumbling window: count clicks per 5-second window")
        void tumblingWindowClickCount() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.tumbling(5000);

            // 8 events across 2 windows
            for (int i = 0; i < 8; i++) {
                wm.assignToWindows(new StreamEvent(i * 1000, "user" + (i % 3),
                        Map.of("action", "click", "page", "/page" + i)));
            }

            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(2);

            Object count1 = aggregator.aggregate(AggregateFunction.COUNT, closed.get(0).events(), "*");
            Object count2 = aggregator.aggregate(AggregateFunction.COUNT, closed.get(1).events(), "*");
            assertThat((Long) count1 + (Long) count2).isEqualTo(8L);
        }

        @Test
        @DisplayName("Group events by user for conversion funnel analysis")
        void groupByUserForFunnel() {
            var aggregator = new StreamAggregator();
            var events = List.of(
                    new StreamEvent(0, "u1", Map.of("user_id", "u1", "action", "view")),
                    new StreamEvent(1000, "u1", Map.of("user_id", "u1", "action", "click")),
                    new StreamEvent(2000, "u2", Map.of("user_id", "u2", "action", "view")),
                    new StreamEvent(3000, "u1", Map.of("user_id", "u1", "action", "purchase")),
                    new StreamEvent(4000, "u2", Map.of("user_id", "u2", "action", "click"))
            );

            var groups = aggregator.groupEvents(events, List.of("user_id"));
            assertThat(groups).hasSize(2);
            assertThat(groups.get(List.of((Object) "u1"))).hasSize(3);
            assertThat(groups.get(List.of((Object) "u2"))).hasSize(2);
        }
    }

    // ==========================================================================
    // Financial Transaction Processing
    // ==========================================================================

    @Nested
    @DisplayName("Financial Transaction Processing")
    class FinancialTransactionTests {

        private StreamSimulator sim;

        @BeforeEach
        void setUp() {
            sim = new StreamSimulator();
            sim.executeQuery("CREATE STREAM transactions (txn_id INTEGER, account_id VARCHAR, amount DOUBLE, type VARCHAR, ts BIGINT)");
            sim.executeQuery("CREATE STREAM alerts (alert_id INTEGER, txn_id INTEGER, reason VARCHAR, ts BIGINT)");
        }

        @Test
        @DisplayName("Stream-to-stream join: match transactions with fraud alerts")
        void streamJoinFraudAlerts() {
            sim.ingestEvent("transactions", new StreamEvent(1000, "t1",
                    Map.of("txn_id", 1, "account_id", "A001", "amount", 5000.0, "ts", 1000L)));
            sim.ingestEvent("transactions", new StreamEvent(2000, "t2",
                    Map.of("txn_id", 2, "account_id", "A002", "amount", 150.0, "ts", 2000L)));

            sim.ingestEvent("alerts", new StreamEvent(1500, "a1",
                    Map.of("alert_id", 1, "txn_id", 1, "reason", "high_amount", "ts", 1500L)));

            var joined = sim.joinStreams("transactions", "alerts", "txn_id", 3000);
            assertThat(joined).hasSize(1);
            assertThat(joined.getFirst().values()).containsEntry("txn_id", 1);
            assertThat(joined.getFirst().values()).containsEntry("right_reason", "high_amount");
        }

        @Test
        @DisplayName("Lookup join: enrich transactions with account info")
        void lookupJoinAccountEnrichment() {
            sim.ingestEvent("transactions", new StreamEvent(1000, "t1",
                    Map.of("txn_id", 1, "account_id", "A001", "amount", 500.0)));
            sim.ingestEvent("transactions", new StreamEvent(2000, "t2",
                    Map.of("txn_id", 2, "account_id", "A002", "amount", 750.0)));

            Map<Object, Map<String, Object>> accountTable = Map.of(
                    "A001", Map.of("holder", "Alice", "tier", "gold"),
                    "A002", Map.of("holder", "Bob", "tier", "silver")
            );

            var enriched = sim.lookupJoin("transactions", accountTable, "account_id");
            assertThat(enriched).hasSize(2);
            assertThat(enriched.get(0).values()).containsEntry("holder", "Alice");
            assertThat(enriched.get(1).values()).containsEntry("holder", "Bob");
        }

        @Test
        @DisplayName("AggregateWindow: SUM + COUNT + AVG for transaction summary")
        void aggregateWindowTransactionSummary() {
            var aggregator = new StreamAggregator();
            var window = new TumblingWindow(0, 60000);

            window.addEvent(new StreamEvent(1000, "t1", Map.of("amount", 100.0)));
            window.addEvent(new StreamEvent(5000, "t2", Map.of("amount", 250.0)));
            window.addEvent(new StreamEvent(10000, "t3", Map.of("amount", 75.0)));
            window.addEvent(new StreamEvent(30000, "t4", Map.of("amount", 500.0)));
            window.addEvent(new StreamEvent(45000, "t5", Map.of("amount", 125.0)));

            var result = aggregator.aggregateWindow(window,
                    List.of(AggregateFunction.COUNT, AggregateFunction.SUM, AggregateFunction.AVG),
                    List.of("*", "amount", "amount"),
                    List.of("txn_count", "total_amount", "avg_amount"),
                    List.of());

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(5L);
            assertThat((Double) qr.rows().getFirst().getValue(1)).isCloseTo(1050.0, within(0.01));
            assertThat((Double) qr.rows().getFirst().getValue(2)).isCloseTo(210.0, within(0.01));
        }
    }

    // ==========================================================================
    // Watermark & Late Data Handling
    // ==========================================================================

    @Nested
    @DisplayName("Watermark & Late Data")
    class WatermarkTests {

        @Test
        @DisplayName("Watermark advances monotonically with out-of-order events")
        void watermarkMonotonicAdvance() {
            var watermark = new Watermark();
            watermark.advance(5000);
            watermark.advance(3000); // out-of-order
            watermark.advance(8000);
            watermark.advance(6000); // out-of-order

            assertThat(watermark.currentWatermark()).isEqualTo(8000);
        }

        @Test
        @DisplayName("Late event detection with 2-second allowed lateness")
        void lateEventDetection() {
            var watermark = new Watermark(0, 2000);
            watermark.advance(10000);

            assertThat(watermark.isLate(10000)).isFalse(); // at watermark
            assertThat(watermark.isLate(9000)).isFalse();  // within 2s lateness
            assertThat(watermark.isLate(8000)).isFalse();  // exactly at boundary
            assertThat(watermark.isLate(7999)).isTrue();   // too late
        }

        @Test
        @DisplayName("Watermark with zero lateness rejects all past events")
        void watermarkZeroLateness() {
            var watermark = new Watermark(0, 0);
            watermark.advance(5000);

            assertThat(watermark.isLate(5000)).isFalse();
            assertThat(watermark.isLate(4999)).isTrue();
        }

        @Test
        @DisplayName("Watermark set() overrides current value")
        void watermarkSetOverride() {
            var watermark = new Watermark();
            watermark.advance(1000);
            watermark.set(5000);
            assertThat(watermark.currentWatermark()).isEqualTo(5000);
        }
    }

    // ==========================================================================
    // Parser - Stream SQL
    // ==========================================================================

    @Nested
    @DisplayName("Stream SQL Parser")
    class ParserTests {

        private StreamSqlParser parser;

        @BeforeEach
        void setUp() {
            parser = new StreamSqlParser();
        }

        @Test
        @DisplayName("Parse CREATE STREAM with properties for Kafka topic")
        void parseCreateStreamWithKafkaProperties() {
            var result = parser.parse(
                    "CREATE STREAM orders (order_id INTEGER, customer_id VARCHAR, amount DOUBLE, ts TIMESTAMP) " +
                    "WITH (topic = 'order-events', format = 'avro', partitions = '12')");
            assertThat(result.isSuccess()).isTrue();
            var node = (CreateStreamNode) result.value();
            assertThat(node.streamName()).isEqualTo("orders");
            assertThat(node.columns()).hasSize(4);
            assertThat(node.properties()).containsEntry("topic", "order-events");
            assertThat(node.properties()).containsEntry("format", "avro");
        }

        @Test
        @DisplayName("Parse windowed aggregation with GROUP BY")
        void parseWindowedAggregationGroupBy() {
            var result = parser.parse(
                    "SELECT region, COUNT(*) AS event_count, SUM(amount) AS total FROM orders " +
                    "WINDOW TUMBLING (SIZE 1 MINUTES) GROUP BY region EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.window().type()).isEqualTo(WindowType.TUMBLING);
            assertThat(node.window().durationMs()).isEqualTo(60000);
            assertThat(node.groupBy().columns()).containsExactly("region");
            assertThat(node.emit()).isEqualTo(EmitStrategy.FINAL);
        }

        @Test
        @DisplayName("Parse INSERT INTO with windowed query")
        void parseInsertIntoWithWindowedQuery() {
            var result = parser.parse(
                    "INSERT INTO hourly_stats SELECT COUNT(*) AS cnt FROM events " +
                    "WINDOW TUMBLING (SIZE 1 HOURS) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (InsertIntoStreamNode) result.value();
            assertThat(node.targetStream()).isEqualTo("hourly_stats");
            assertThat(node.query().window().durationMs()).isEqualTo(3600000);
        }

        @Test
        @DisplayName("Parse DROP STREAM IF EXISTS")
        void parseDropStreamIfExists() {
            var result = parser.parse("DROP STREAM IF EXISTS old_stream");
            assertThat(result.isSuccess()).isTrue();
            var node = (DropStreamNode) result.value();
            assertThat(node.streamName()).isEqualTo("old_stream");
            assertThat(node.ifExists()).isTrue();
        }
    }

    // ==========================================================================
    // Stream Lifecycle
    // ==========================================================================

    @Nested
    @DisplayName("Stream Lifecycle")
    class StreamLifecycleTests {

        private StreamSimulator sim;

        @BeforeEach
        void setUp() {
            sim = new StreamSimulator();
        }

        @Test
        @DisplayName("Create stream, ingest, query, drop")
        void fullStreamLifecycle() {
            var createResult = sim.executeQuery("CREATE STREAM events (id INTEGER, type VARCHAR, ts BIGINT)");
            assertThat(createResult.isSuccess()).isTrue();
            assertThat(sim.hasStream("events")).isTrue();

            sim.ingestEvent("events", new StreamEvent(1000, "e1",
                    Map.of("id", 1, "type", "click", "ts", 1000L)));
            sim.ingestEvent("events", new StreamEvent(2000, "e2",
                    Map.of("id", 2, "type", "view", "ts", 2000L)));

            var queryResult = sim.executeQuery("SELECT * FROM events");
            assertThat(queryResult.isSuccess()).isTrue();
            assertThat(queryResult.value().rowCount()).isEqualTo(2);

            var dropResult = sim.executeQuery("DROP STREAM events");
            assertThat(dropResult.isSuccess()).isTrue();
            assertThat(sim.hasStream("events")).isFalse();
        }

        @Test
        @DisplayName("Multiple streams with independent data")
        void multipleIndependentStreams() {
            sim.executeQuery("CREATE STREAM clicks (page VARCHAR, ts BIGINT)");
            sim.executeQuery("CREATE STREAM purchases (item VARCHAR, amount DOUBLE, ts BIGINT)");

            sim.ingestEvent("clicks", new StreamEvent(1000, "c1", Map.of("page", "/home", "ts", 1000L)));
            sim.ingestEvent("clicks", new StreamEvent(2000, "c2", Map.of("page", "/cart", "ts", 2000L)));
            sim.ingestEvent("purchases", new StreamEvent(3000, "p1", Map.of("item", "Widget", "amount", 29.99, "ts", 3000L)));

            assertThat(sim.getStream("clicks").peekAll()).hasSize(2);
            assertThat(sim.getStream("purchases").peekAll()).hasSize(1);
            assertThat(sim.streamNames()).contains("clicks", "purchases");
        }
    }
}

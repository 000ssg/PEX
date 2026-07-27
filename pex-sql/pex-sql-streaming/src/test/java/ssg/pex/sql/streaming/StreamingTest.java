package ssg.pex.sql.streaming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.ast.SqlExpression.AggregateFunction;
import ssg.pex.sql.ast.SqlSupport.ColumnDef;
import ssg.pex.sql.ast.SqlSupport.SqlDataType;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.streaming.ast.*;
import ssg.pex.sql.streaming.ast.WindowSpec.WindowType;
import ssg.pex.sql.streaming.engine.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class StreamingTest {

    // =============================================================
    // Parser Tests
    // =============================================================

    @Nested
    class ParserTests {

        private StreamSqlParser parser;

        @BeforeEach
        void setUp() {
            parser = new StreamSqlParser();
        }

        @Test
        void testParseCreateStreamWithColumnsAndProperties() {
            var result = parser.parse(
                    "CREATE STREAM clicks (user_id VARCHAR, url VARCHAR, ts TIMESTAMP) " +
                    "WITH (topic = 'click-events', format = 'json', partitions = '4')");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isInstanceOf(CreateStreamNode.class);
            var node = (CreateStreamNode) result.value();
            assertThat(node.streamName()).isEqualTo("clicks");
            assertThat(node.columns()).hasSize(3);
            assertThat(node.columns().get(0).name()).isEqualTo("user_id");
            assertThat(node.columns().get(0).dataType()).isEqualTo(SqlDataType.VARCHAR);
            assertThat(node.columns().get(2).dataType()).isEqualTo(SqlDataType.TIMESTAMP);
            assertThat(node.properties()).containsEntry("topic", "click-events");
            assertThat(node.properties()).containsEntry("format", "json");
            assertThat(node.properties()).containsEntry("partitions", "4");
        }

        @Test
        void testParseCreateStreamWithoutProperties() {
            var result = parser.parse("CREATE STREAM sensor_data (sensor_id VARCHAR, temp DOUBLE, humidity DOUBLE)");
            assertThat(result.isSuccess()).isTrue();
            var node = (CreateStreamNode) result.value();
            assertThat(node.streamName()).isEqualTo("sensor_data");
            assertThat(node.columns()).hasSize(3);
            assertThat(node.properties()).isEmpty();
        }

        @Test
        void testParseCreateStreamWithIntegerColumns() {
            var result = parser.parse("CREATE STREAM orders (order_id INTEGER, amount DOUBLE, quantity INTEGER)");
            assertThat(result.isSuccess()).isTrue();
            var node = (CreateStreamNode) result.value();
            assertThat(node.columns().get(0).dataType()).isEqualTo(SqlDataType.INTEGER);
            assertThat(node.columns().get(1).dataType()).isEqualTo(SqlDataType.DOUBLE);
            assertThat(node.columns().get(2).dataType()).isEqualTo(SqlDataType.INTEGER);
        }

        @Test
        void testParseDropStream() {
            var result = parser.parse("DROP STREAM clicks");
            assertThat(result.isSuccess()).isTrue();
            var node = (DropStreamNode) result.value();
            assertThat(node.streamName()).isEqualTo("clicks");
            assertThat(node.ifExists()).isFalse();
        }

        @Test
        void testParseDropStreamIfExists() {
            var result = parser.parse("DROP STREAM IF EXISTS clicks");
            assertThat(result.isSuccess()).isTrue();
            var node = (DropStreamNode) result.value();
            assertThat(node.streamName()).isEqualTo("clicks");
            assertThat(node.ifExists()).isTrue();
        }

        @Test
        void testParseStreamSelectWithTumblingWindow() {
            var result = parser.parse(
                    "SELECT COUNT(*) AS cnt FROM clicks WINDOW TUMBLING (SIZE 5 SECONDS) EMIT CHANGES");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.fromStream()).isEqualTo("clicks");
            assertThat(node.window()).isNotNull();
            assertThat(node.window().type()).isEqualTo(WindowType.TUMBLING);
            assertThat(node.window().durationMs()).isEqualTo(5000);
            assertThat(node.emit()).isEqualTo(EmitStrategy.CHANGES);
        }

        @Test
        void testParseStreamSelectWithHoppingWindow() {
            var result = parser.parse(
                    "SELECT SUM(amount) FROM orders WINDOW HOPPING (SIZE 10 SECONDS, ADVANCE 5 SECONDS) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.window().type()).isEqualTo(WindowType.HOPPING);
            assertThat(node.window().durationMs()).isEqualTo(10000);
            assertThat(node.window().advanceMs()).isEqualTo(5000);
            assertThat(node.emit()).isEqualTo(EmitStrategy.FINAL);
        }

        @Test
        void testParseStreamSelectWithSessionWindow() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM sessions WINDOW SESSION (GAP 30 SECONDS) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.window().type()).isEqualTo(WindowType.SESSION);
            assertThat(node.window().gapMs()).isEqualTo(30000);
        }

        @Test
        void testParseStreamSelectWithSlidingWindow() {
            var result = parser.parse(
                    "SELECT AVG(temp) FROM sensors WINDOW SLIDING (SIZE 10 SECONDS) EMIT CHANGES");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.window().type()).isEqualTo(WindowType.SLIDING);
            assertThat(node.window().durationMs()).isEqualTo(10000);
        }

        @Test
        void testParseEmitChanges() {
            var result = parser.parse("SELECT * FROM stream1 EMIT CHANGES");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.emit()).isEqualTo(EmitStrategy.CHANGES);
        }

        @Test
        void testParseEmitFinal() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM stream1 WINDOW TUMBLING (SIZE 1 MINUTES) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.emit()).isEqualTo(EmitStrategy.FINAL);
        }

        @Test
        void testParseDefaultEmitIsChanges() {
            var result = parser.parse("SELECT * FROM stream1");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.emit()).isEqualTo(EmitStrategy.CHANGES);
        }

        @Test
        void testParseInsertIntoStreamSelect() {
            var result = parser.parse(
                    "INSERT INTO output_stream SELECT COUNT(*) AS cnt FROM input_stream WINDOW TUMBLING (SIZE 1 MINUTES) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (InsertIntoStreamNode) result.value();
            assertThat(node.targetStream()).isEqualTo("output_stream");
            assertThat(node.query().fromStream()).isEqualTo("input_stream");
            assertThat(node.query().window().type()).isEqualTo(WindowType.TUMBLING);
        }

        @Test
        void testParseSelectWithWhereClause() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM clicks WINDOW TUMBLING (SIZE 5 SECONDS) WHERE status = 'active' EMIT CHANGES");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.where()).isNotNull();
        }

        @Test
        void testParseSelectWithGroupBy() {
            var result = parser.parse(
                    "SELECT user_id, COUNT(*) FROM clicks WINDOW TUMBLING (SIZE 5 SECONDS) GROUP BY user_id EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.groupBy()).isNotNull();
            assertThat(node.groupBy().columns()).containsExactly("user_id");
        }

        @Test
        void testParseWindowWithMinutes() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM s WINDOW TUMBLING (SIZE 2 MINUTES) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.window().durationMs()).isEqualTo(120000);
        }

        @Test
        void testParseWindowWithHours() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM s WINDOW TUMBLING (SIZE 1 HOURS) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.window().durationMs()).isEqualTo(3600000);
        }

        @Test
        void testParseWindowWithMilliseconds() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM s WINDOW TUMBLING (SIZE 500 MILLISECONDS) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.window().durationMs()).isEqualTo(500);
        }

        @Test
        void testParseErrorInvalidWindowType() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM s WINDOW UNKNOWN (SIZE 5 SECONDS) EMIT FINAL");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().message()).contains("Invalid window type");
        }

        @Test
        void testParseErrorMissingDuration() {
            var result = parser.parse(
                    "SELECT COUNT(*) FROM s WINDOW TUMBLING (SIZE SECONDS) EMIT FINAL");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().message()).contains("Expected number");
        }

        @Test
        void testParseErrorInvalidSyntax() {
            var result = parser.parse("STREAM CREATE bad");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void testParseMultipleSelectItems() {
            var result = parser.parse(
                    "SELECT user_id, COUNT(*) AS event_count, SUM(amount) AS total FROM orders WINDOW TUMBLING (SIZE 10 SECONDS) GROUP BY user_id EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.selectItems()).hasSize(3);
            assertThat(node.selectItems().get(1).alias()).isEqualTo("event_count");
            assertThat(node.selectItems().get(2).alias()).isEqualTo("total");
        }

        @Test
        void testParseStarSelect() {
            var result = parser.parse("SELECT * FROM mystream EMIT CHANGES");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.selectItems()).hasSize(1);
            assertThat(node.selectItems().getFirst().star()).isTrue();
        }

        @Test
        void testParseCreateStreamWithSemicolon() {
            var result = parser.parse("CREATE STREAM events (id INTEGER, name VARCHAR);");
            assertThat(result.isSuccess()).isTrue();
            var node = (CreateStreamNode) result.value();
            assertThat(node.streamName()).isEqualTo("events");
        }

        @Test
        void testParseDropStreamWithSemicolon() {
            var result = parser.parse("DROP STREAM events;");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void testParseSelectWithMultipleGroupByColumns() {
            var result = parser.parse(
                    "SELECT region, category, SUM(amount) FROM sales WINDOW TUMBLING (SIZE 1 MINUTES) GROUP BY region, category EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            var node = (StreamSelectNode) result.value();
            assertThat(node.groupBy().columns()).containsExactly("region", "category");
        }
    }

    // =============================================================
    // Tumbling Window Tests
    // =============================================================

    @Nested
    class TumblingWindowTests {

        @Test
        void testTumblingWindowAcceptsEventsInRange() {
            var window = new TumblingWindow(0, 5000);
            assertThat(window.accepts(0)).isTrue();
            assertThat(window.accepts(2500)).isTrue();
            assertThat(window.accepts(4999)).isTrue();
            assertThat(window.accepts(5000)).isFalse();
            assertThat(window.accepts(-1)).isFalse();
        }

        @Test
        void testTumblingWindowAddEvent() {
            var window = new TumblingWindow(0, 5000);
            var event = new StreamEvent(1000, "k1", Map.of("value", 42));
            window.addEvent(event);
            assertThat(window.events()).hasSize(1);
            assertThat(window.events().getFirst()).isEqualTo(event);
        }

        @Test
        void testTumblingWindowRejectsOutOfRange() {
            var window = new TumblingWindow(0, 5000);
            window.addEvent(new StreamEvent(6000, "k1", Map.of()));
            assertThat(window.events()).isEmpty();
        }

        @Test
        void testTumblingWindowClosed() {
            var window = new TumblingWindow(0, 5000);
            assertThat(window.isClosed(4999)).isFalse();
            assertThat(window.isClosed(5000)).isTrue();
            assertThat(window.isClosed(10000)).isTrue();
        }

        @Test
        void testFiveSecondTumblingWindowWith10Events() {
            var wm = WindowManager.tumbling(5000);
            for (int i = 0; i < 10; i++) {
                wm.assignToWindows(new StreamEvent(i * 1000, "k", Map.of("val", i)));
            }
            // Should have 2 windows: [0-5000) and [5000-10000)
            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(2);
            assertThat(closed.get(0).events()).hasSize(5);
            assertThat(closed.get(1).events()).hasSize(5);
        }

        @Test
        void testEventsSpanningMultipleWindows() {
            var wm = WindowManager.tumbling(3000);
            wm.assignToWindows(new StreamEvent(0, "k", Map.of("v", 1)));
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of("v", 2)));
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of("v", 3)));
            wm.assignToWindows(new StreamEvent(5000, "k", Map.of("v", 4)));
            wm.assignToWindows(new StreamEvent(7000, "k", Map.of("v", 5)));

            var closed = wm.getClosedWindows(9000);
            assertThat(closed).hasSize(3);
            assertThat(closed.get(0).events()).hasSize(2); // [0,3000) has events at 0, 1000
            assertThat(closed.get(1).events()).hasSize(2); // [3000,6000) has events at 3000, 5000
            assertThat(closed.get(2).events()).hasSize(1); // [6000,9000) has event at 7000
        }

        @Test
        void testTumblingCountPerWindow() {
            var aggregator = new StreamAggregator();
            var window = new TumblingWindow(0, 5000);
            for (int i = 0; i < 7; i++) {
                window.addEvent(new StreamEvent(i * 500, "k", Map.of("val", i)));
            }
            Object count = aggregator.aggregate(AggregateFunction.COUNT, window.events(), "*");
            assertThat(count).isEqualTo(7L);
        }

        @Test
        void testTumblingSumPerWindow() {
            var aggregator = new StreamAggregator();
            var window = new TumblingWindow(0, 5000);
            window.addEvent(new StreamEvent(0, "k", Map.of("amount", 10L)));
            window.addEvent(new StreamEvent(1000, "k", Map.of("amount", 20L)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("amount", 30L)));
            Object sum = aggregator.aggregate(AggregateFunction.SUM, window.events(), "amount");
            assertThat(sum).isEqualTo(60L);
        }

        @Test
        void testTumblingAvgPerWindow() {
            var aggregator = new StreamAggregator();
            var window = new TumblingWindow(0, 5000);
            window.addEvent(new StreamEvent(0, "k", Map.of("value", 10.0)));
            window.addEvent(new StreamEvent(1000, "k", Map.of("value", 20.0)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("value", 30.0)));
            Object avg = aggregator.aggregate(AggregateFunction.AVG, window.events(), "value");
            assertThat((Double) avg).isCloseTo(20.0, within(0.01));
        }

        @Test
        void testEmptyWindow() {
            var window = new TumblingWindow(5000, 5000);
            assertThat(window.events()).isEmpty();
        }

        @Test
        void testExactlyOnWindowBoundary() {
            var wm = WindowManager.tumbling(5000);
            // Event exactly at boundary goes to next window
            wm.assignToWindows(new StreamEvent(5000, "k", Map.of("v", 1)));
            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(1);
            assertThat(closed.getFirst().startTime()).isEqualTo(5000);
        }

        @Test
        void testTumblingWindowStartEndTimes() {
            var window = new TumblingWindow(10000, 5000);
            assertThat(window.startTime()).isEqualTo(10000);
            assertThat(window.endTime()).isEqualTo(15000);
        }

        @Test
        void testMultipleTumblingWindowsProcessing() {
            var wm = WindowManager.tumbling(2000);
            for (int i = 0; i < 20; i++) {
                wm.assignToWindows(new StreamEvent(i * 500, "k", Map.of("v", i)));
            }
            // Events from 0 to 9500ms, window size 2s
            // Closed at watermark 10000: windows [0,2000), [2000,4000), [4000,6000), [6000,8000), [8000,10000)
            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(5);
            for (Window w : closed) {
                assertThat(w.events()).hasSize(4); // 4 events per 2s window with 500ms spacing
            }
        }

        @Test
        void testTumblingWindowClosureSequence() {
            var wm = WindowManager.tumbling(5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of()));
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of()));

            // Not yet closed
            var closed1 = wm.getClosedWindows(4000);
            assertThat(closed1).isEmpty();

            // Now closed
            wm.assignToWindows(new StreamEvent(6000, "k", Map.of()));
            var closed2 = wm.getClosedWindows(6000);
            assertThat(closed2).hasSize(1);
            assertThat(closed2.getFirst().events()).hasSize(2);
        }

        @Test
        void testTumblingWindowNoEventsReturnsEmptyClosed() {
            var wm = WindowManager.tumbling(5000);
            var closed = wm.getClosedWindows(10000);
            assertThat(closed).isEmpty();
        }

        @Test
        void testTumblingSingleEventWindow() {
            var wm = WindowManager.tumbling(5000);
            wm.assignToWindows(new StreamEvent(2500, "k", Map.of("v", 1)));
            var closed = wm.getClosedWindows(5000);
            assertThat(closed).hasSize(1);
            assertThat(closed.getFirst().events()).hasSize(1);
        }

        @Test
        void testTumblingWindowWithLargeWindowSize() {
            var wm = WindowManager.tumbling(60000); // 1 minute
            for (int i = 0; i < 100; i++) {
                wm.assignToWindows(new StreamEvent(i * 500, "k", Map.of("v", i)));
            }
            // All events fit in one window [0, 60000)
            var closed = wm.getClosedWindows(60000);
            assertThat(closed).hasSize(1);
            assertThat(closed.getFirst().events()).hasSize(100);
        }

        @Test
        void testTumblingMinPerWindow() {
            var aggregator = new StreamAggregator();
            var window = new TumblingWindow(0, 5000);
            window.addEvent(new StreamEvent(0, "k", Map.of("temp", 25.0)));
            window.addEvent(new StreamEvent(1000, "k", Map.of("temp", 18.5)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("temp", 30.2)));
            Object min = aggregator.aggregate(AggregateFunction.MIN, window.events(), "temp");
            assertThat(min).isEqualTo(18.5);
        }

        @Test
        void testTumblingMaxPerWindow() {
            var aggregator = new StreamAggregator();
            var window = new TumblingWindow(0, 5000);
            window.addEvent(new StreamEvent(0, "k", Map.of("temp", 25.0)));
            window.addEvent(new StreamEvent(1000, "k", Map.of("temp", 18.5)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("temp", 30.2)));
            Object max = aggregator.aggregate(AggregateFunction.MAX, window.events(), "temp");
            assertThat(max).isEqualTo(30.2);
        }
    }

    // =============================================================
    // Hopping Window Tests
    // =============================================================

    @Nested
    class HoppingWindowTests {

        @Test
        void testHoppingWindowAcceptsEventsInRange() {
            var window = new HoppingWindow(0, 10000);
            assertThat(window.accepts(0)).isTrue();
            assertThat(window.accepts(5000)).isTrue();
            assertThat(window.accepts(9999)).isTrue();
            assertThat(window.accepts(10000)).isFalse();
        }

        @Test
        void testHoppingWindowOverlap() {
            var wm = WindowManager.hopping(10000, 5000);
            // Insert an event at 7000ms
            wm.assignToWindows(new StreamEvent(7000, "k", Map.of("v", 1)));

            // Event at 7000 should be in windows [0,10000) and [5000,15000)
            var open = wm.openWindows();
            assertThat(open).hasSize(2);
            for (Window w : open) {
                assertThat(w.events()).hasSize(1);
            }
        }

        @Test
        void testHoppingEventInMultipleWindows() {
            var wm = WindowManager.hopping(10000, 5000);
            wm.assignToWindows(new StreamEvent(6000, "k", Map.of("v", 1)));
            wm.assignToWindows(new StreamEvent(8000, "k", Map.of("v", 2)));

            // Both events should be in windows [0, 10000) and [5000, 15000)
            var open = wm.openWindows();
            assertThat(open).hasSize(2);

            Window first = open.stream().filter(w -> w.startTime() == 0).findFirst().orElseThrow();
            Window second = open.stream().filter(w -> w.startTime() == 5000).findFirst().orElseThrow();
            assertThat(first.events()).hasSize(2);
            assertThat(second.events()).hasSize(2);
        }

        @Test
        void testHoppingAggregatePerWindow() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.hopping(10000, 5000);

            // 6 events at 0, 2, 4, 6, 8, 10 seconds
            for (int i = 0; i <= 5; i++) {
                wm.assignToWindows(new StreamEvent(i * 2000, "k", Map.of("amount", (long)(i * 10))));
            }

            var closed = wm.getClosedWindows(15000);
            assertThat(closed).isNotEmpty();

            // Check first closed window [0, 10000) has events at 0,2000,4000,6000,8000
            Window firstWindow = closed.stream().filter(w -> w.startTime() == 0).findFirst().orElseThrow();
            Object sum = aggregator.aggregate(AggregateFunction.SUM, firstWindow.events(), "amount");
            // 0+10+20+30+40 = 100
            assertThat(sum).isEqualTo(100L);
        }

        @Test
        void testHoppingWindowClosure() {
            var wm = WindowManager.hopping(10000, 5000);
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of()));

            // At watermark 10000, window [0,10000) should close
            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(1);
            assertThat(closed.getFirst().startTime()).isEqualTo(0);
        }

        @Test
        void testHoppingMultipleWindowClosures() {
            var wm = WindowManager.hopping(6000, 3000);
            for (int i = 0; i < 10; i++) {
                wm.assignToWindows(new StreamEvent(i * 1000, "k", Map.of("v", i)));
            }
            var closed = wm.getClosedWindows(12000);
            assertThat(closed.size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        void testHoppingWindowStartEndTimes() {
            var window = new HoppingWindow(5000, 10000);
            assertThat(window.startTime()).isEqualTo(5000);
            assertThat(window.endTime()).isEqualTo(15000);
        }

        @Test
        void testHoppingWindowCountPerWindow() {
            var aggregator = new StreamAggregator();
            var window = new HoppingWindow(0, 10000);
            for (int i = 0; i < 5; i++) {
                window.addEvent(new StreamEvent(i * 1000, "k", Map.of()));
            }
            Object count = aggregator.aggregate(AggregateFunction.COUNT, window.events(), "*");
            assertThat(count).isEqualTo(5L);
        }

        @Test
        void testHoppingLargeAdvanceEquivalentToTumbling() {
            // When advance == duration, hopping degenerates to tumbling
            var wm = WindowManager.hopping(5000, 5000);
            wm.assignToWindows(new StreamEvent(2000, "k", Map.of()));
            wm.assignToWindows(new StreamEvent(7000, "k", Map.of()));
            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(2);
            assertThat(closed.get(0).events()).hasSize(1);
            assertThat(closed.get(1).events()).hasSize(1);
        }

        @Test
        void testHoppingSumAcrossWindows() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.hopping(10000, 5000);
            wm.assignToWindows(new StreamEvent(2000, "k", Map.of("amount", 100L)));
            wm.assignToWindows(new StreamEvent(7000, "k", Map.of("amount", 200L)));

            // Both events appear in window [0,10000)
            var closed = wm.getClosedWindows(10000);
            Window w = closed.stream().filter(win -> win.startTime() == 0).findFirst().orElseThrow();
            Object sum = aggregator.aggregate(AggregateFunction.SUM, w.events(), "amount");
            assertThat(sum).isEqualTo(300L);
        }

        @Test
        void testHoppingZeroEventsInAdvanceSlice() {
            var wm = WindowManager.hopping(10000, 5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of()));
            // Event at 1000 is in [0,10000)
            var closed = wm.getClosedWindows(10000);
            Window first = closed.stream().filter(w -> w.startTime() == 0).findFirst().orElseThrow();
            assertThat(first.events()).hasSize(1);
        }

        @Test
        void testHoppingWindowEventAt0() {
            var wm = WindowManager.hopping(10000, 5000);
            wm.assignToWindows(new StreamEvent(0, "k", Map.of("v", 1)));
            var open = wm.openWindows();
            assertThat(open).isNotEmpty();
            // Event at 0 should be in window [0, 10000)
            boolean found = open.stream().anyMatch(w -> w.startTime() == 0 && w.events().size() == 1);
            assertThat(found).isTrue();
        }

        @Test
        void testHoppingSmallAdvance() {
            var wm = WindowManager.hopping(6000, 2000);
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of("v", 1)));
            // Event at 3000 should appear in multiple windows
            var open = wm.openWindows();
            long windowsWithEvent = open.stream().filter(w -> !w.events().isEmpty()).count();
            assertThat(windowsWithEvent).isGreaterThanOrEqualTo(2);
        }

        @Test
        void testHoppingAvgPerWindow() {
            var aggregator = new StreamAggregator();
            var window = new HoppingWindow(0, 10000);
            window.addEvent(new StreamEvent(1000, "k", Map.of("price", 10.0)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("price", 20.0)));
            window.addEvent(new StreamEvent(3000, "k", Map.of("price", 30.0)));
            Object avg = aggregator.aggregate(AggregateFunction.AVG, window.events(), "price");
            assertThat((Double) avg).isCloseTo(20.0, within(0.01));
        }
    }

    // =============================================================
    // Session Window Tests
    // =============================================================

    @Nested
    class SessionWindowTests {

        @Test
        void testSessionWindowCreation() {
            var window = new SessionWindow(1000, 5000);
            assertThat(window.startTime()).isEqualTo(1000);
            assertThat(window.endTime()).isEqualTo(6000);
            assertThat(window.gapMs()).isEqualTo(5000);
        }

        @Test
        void testSessionWindowAccepts() {
            var window = new SessionWindow(1000, 5000);
            assertThat(window.accepts(1000)).isTrue();
            assertThat(window.accepts(3000)).isTrue();
            assertThat(window.accepts(5999)).isTrue();
            assertThat(window.accepts(6000)).isFalse();
            assertThat(window.accepts(999)).isFalse();
        }

        @Test
        void testSessionWindowExtension() {
            var window = new SessionWindow(1000, 5000);
            window.addEvent(new StreamEvent(1000, "k", Map.of()));
            // Window end is now max(6000, 1000+5000) = 6000
            window.addEvent(new StreamEvent(4000, "k", Map.of()));
            // Window end extends to 4000+5000 = 9000
            assertThat(window.endTime()).isEqualTo(9000);
            assertThat(window.events()).hasSize(2);
        }

        @Test
        void testSessionWindowGapTimeout() {
            var wm = WindowManager.session(5000);
            // Cluster 1: events at 0, 2000, 4000
            wm.assignToWindows(new StreamEvent(0, "k", Map.of("v", 1)));
            wm.assignToWindows(new StreamEvent(2000, "k", Map.of("v", 2)));
            wm.assignToWindows(new StreamEvent(4000, "k", Map.of("v", 3)));

            // Gap: no events between 4000 and 20000
            // Cluster 2: events at 20000, 22000
            wm.assignToWindows(new StreamEvent(20000, "k", Map.of("v", 4)));
            wm.assignToWindows(new StreamEvent(22000, "k", Map.of("v", 5)));

            var closed = wm.getClosedWindows(30000);
            assertThat(closed).hasSize(2);
            assertThat(closed.get(0).events()).hasSize(3);
            assertThat(closed.get(1).events()).hasSize(2);
        }

        @Test
        void testMultipleSessionsFromOneStream() {
            var wm = WindowManager.session(3000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of()));
            wm.assignToWindows(new StreamEvent(2000, "k", Map.of()));
            // Gap > 3000
            wm.assignToWindows(new StreamEvent(10000, "k", Map.of()));
            wm.assignToWindows(new StreamEvent(11000, "k", Map.of()));
            // Gap > 3000
            wm.assignToWindows(new StreamEvent(20000, "k", Map.of()));

            var closed = wm.getClosedWindows(30000);
            assertThat(closed).hasSize(3);
        }

        @Test
        void testSingleEventSession() {
            var wm = WindowManager.session(5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of("v", 1)));

            var closed = wm.getClosedWindows(10000);
            assertThat(closed).hasSize(1);
            assertThat(closed.getFirst().events()).hasSize(1);
        }

        @Test
        void testSessionWindowIsClosed() {
            var window = new SessionWindow(1000, 5000);
            window.addEvent(new StreamEvent(1000, "k", Map.of()));
            assertThat(window.isClosed(5999)).isFalse();
            assertThat(window.isClosed(6000)).isTrue();
        }

        @Test
        void testSessionMerge() {
            var w1 = new SessionWindow(1000, 5000);
            w1.addEvent(new StreamEvent(1000, "k", Map.of("v", 1)));

            var w2 = new SessionWindow(3000, 5000);
            w2.addEvent(new StreamEvent(3000, "k", Map.of("v", 2)));

            // Overlapping: w1 [1000,6000) and w2 [3000,8000)
            w1.merge(w2);
            assertThat(w1.startTime()).isEqualTo(1000);
            assertThat(w1.endTime()).isEqualTo(8000);
            assertThat(w1.events()).hasSize(2);
        }

        @Test
        void testSessionWindowCountAggregate() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.session(5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of()));
            wm.assignToWindows(new StreamEvent(2000, "k", Map.of()));
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of()));

            var closed = wm.getClosedWindows(20000);
            assertThat(closed).hasSize(1);
            Object count = aggregator.aggregate(AggregateFunction.COUNT, closed.getFirst().events(), "*");
            assertThat(count).isEqualTo(3L);
        }

        @Test
        void testSessionWindowWithContinuousActivity() {
            var wm = WindowManager.session(3000);
            // Continuous activity: each event within gap of previous
            for (int i = 0; i < 10; i++) {
                wm.assignToWindows(new StreamEvent(i * 2000, "k", Map.of("v", i)));
            }
            // All events should be in one session because gap is 3000 and spacing is 2000
            var closed = wm.getClosedWindows(25000);
            assertThat(closed).hasSize(1);
            assertThat(closed.getFirst().events()).hasSize(10);
        }

        @Test
        void testSessionWindowNotClosedYet() {
            var wm = WindowManager.session(5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of()));
            // Watermark at 4000: session ends at 6000, not yet closed
            var closed = wm.getClosedWindows(4000);
            assertThat(closed).isEmpty();
        }

        @Test
        void testSessionSumAggregate() {
            var aggregator = new StreamAggregator();
            var session = new SessionWindow(0, 10000);
            session.addEvent(new StreamEvent(0, "k", Map.of("amount", 100L)));
            session.addEvent(new StreamEvent(2000, "k", Map.of("amount", 200L)));
            session.addEvent(new StreamEvent(5000, "k", Map.of("amount", 300L)));
            Object sum = aggregator.aggregate(AggregateFunction.SUM, session.events(), "amount");
            assertThat(sum).isEqualTo(600L);
        }

        @Test
        void testSessionWindowMergeOverlapping() {
            var wm = WindowManager.session(5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of()));
            wm.assignToWindows(new StreamEvent(4000, "k", Map.of())); // extends first session
            wm.assignToWindows(new StreamEvent(8000, "k", Map.of())); // still within gap of extended session

            var open = wm.openWindows();
            assertThat(open).hasSize(1); // all merged into one session
            assertThat(open.getFirst().events()).hasSize(3);
        }

        @Test
        void testSessionAvgAggregate() {
            var aggregator = new StreamAggregator();
            var session = new SessionWindow(0, 10000);
            session.addEvent(new StreamEvent(0, "k", Map.of("score", 80.0)));
            session.addEvent(new StreamEvent(1000, "k", Map.of("score", 90.0)));
            session.addEvent(new StreamEvent(2000, "k", Map.of("score", 100.0)));
            Object avg = aggregator.aggregate(AggregateFunction.AVG, session.events(), "score");
            assertThat((Double) avg).isCloseTo(90.0, within(0.01));
        }
    }

    // =============================================================
    // Sliding Window Tests
    // =============================================================

    @Nested
    class SlidingWindowTests {

        @Test
        void testSlidingWindowCreation() {
            var window = new SlidingWindow(0, 5000);
            assertThat(window.startTime()).isEqualTo(0);
            assertThat(window.endTime()).isEqualTo(5000);
        }

        @Test
        void testSlidingWindowAccepts() {
            var window = new SlidingWindow(1000, 6000);
            assertThat(window.accepts(1000)).isTrue();
            assertThat(window.accepts(3000)).isTrue();
            assertThat(window.accepts(5999)).isTrue();
            assertThat(window.accepts(6000)).isFalse();
            assertThat(window.accepts(999)).isFalse();
        }

        @Test
        void testSlidingWindowPerEvent() {
            var wm = WindowManager.sliding(5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of("v", 1)));
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of("v", 2)));
            wm.assignToWindows(new StreamEvent(5000, "k", Map.of("v", 3)));

            // Each event creates its own sliding window
            var open = wm.openWindows();
            assertThat(open).hasSize(3);
        }

        @Test
        void testSlidingWindowEventOverlap() {
            var wm = WindowManager.sliding(5000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of("v", 1)));
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of("v", 2)));

            // Second window [0, 3001) should contain both events
            var open = wm.openWindows();
            Window secondWindow = open.stream()
                    .filter(w -> w.endTime() == 3001)
                    .findFirst()
                    .orElseThrow();
            assertThat(secondWindow.events()).hasSize(2);
        }

        @Test
        void testSlidingWindowRunningAverage() {
            var aggregator = new StreamAggregator();
            var wm = WindowManager.sliding(3000);

            wm.assignToWindows(new StreamEvent(1000, "k", Map.of("val", 10.0)));
            wm.assignToWindows(new StreamEvent(2000, "k", Map.of("val", 20.0)));
            wm.assignToWindows(new StreamEvent(3000, "k", Map.of("val", 30.0)));

            // The window created at event 3000 should include events at 1000, 2000, 3000
            var open = wm.openWindows();
            Window lastWindow = open.stream()
                    .filter(w -> w.endTime() == 3001)
                    .findFirst()
                    .orElseThrow();
            Object avg = aggregator.aggregate(AggregateFunction.AVG, lastWindow.events(), "val");
            assertThat((Double) avg).isCloseTo(20.0, within(0.01));
        }

        @Test
        void testSlidingWindowClosure() {
            var wm = WindowManager.sliding(3000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of()));
            // Window [0, 1001) - closed when watermark >= 1001
            var closed = wm.getClosedWindows(2000);
            assertThat(closed).hasSize(1);
        }

        @Test
        void testSlidingWindowSum() {
            var aggregator = new StreamAggregator();
            var window = new SlidingWindow(0, 5000);
            window.addEvent(new StreamEvent(1000, "k", Map.of("amount", 10L)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("amount", 20L)));
            window.addEvent(new StreamEvent(3000, "k", Map.of("amount", 30L)));
            Object sum = aggregator.aggregate(AggregateFunction.SUM, window.events(), "amount");
            assertThat(sum).isEqualTo(60L);
        }

        @Test
        void testSlidingWindowOldEventsFallOut() {
            var wm = WindowManager.sliding(3000);
            wm.assignToWindows(new StreamEvent(1000, "k", Map.of("v", 1)));
            wm.assignToWindows(new StreamEvent(5000, "k", Map.of("v", 2)));

            // Window for event at 5000 covers [4999-2999, 5001) = [2001, 5001)
            // Event at 1000 is NOT in this window
            var open = wm.openWindows();
            Window lastWindow = open.stream()
                    .filter(w -> w.endTime() == 5001)
                    .findFirst()
                    .orElseThrow();
            assertThat(lastWindow.events()).hasSize(1);
        }

        @Test
        void testSlidingWindowCount() {
            var aggregator = new StreamAggregator();
            var window = new SlidingWindow(0, 10000);
            for (int i = 0; i < 7; i++) {
                window.addEvent(new StreamEvent(i * 1000, "k", Map.of()));
            }
            Object count = aggregator.aggregate(AggregateFunction.COUNT, window.events(), "*");
            assertThat(count).isEqualTo(7L);
        }

        @Test
        void testSlidingWindowMinMax() {
            var aggregator = new StreamAggregator();
            var window = new SlidingWindow(0, 10000);
            window.addEvent(new StreamEvent(0, "k", Map.of("temp", 15.0)));
            window.addEvent(new StreamEvent(1000, "k", Map.of("temp", 22.0)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("temp", 8.5)));
            assertThat(aggregator.aggregate(AggregateFunction.MIN, window.events(), "temp")).isEqualTo(8.5);
            assertThat(aggregator.aggregate(AggregateFunction.MAX, window.events(), "temp")).isEqualTo(22.0);
        }

        @Test
        void testSlidingWindowIsClosed() {
            var window = new SlidingWindow(1000, 5000);
            assertThat(window.isClosed(4999)).isFalse();
            assertThat(window.isClosed(5000)).isTrue();
        }
    }

    // =============================================================
    // Stream Aggregation Tests
    // =============================================================

    @Nested
    class StreamAggregationTests {

        private StreamAggregator aggregator;

        @BeforeEach
        void setUp() {
            aggregator = new StreamAggregator();
        }

        @Test
        void testCountStarPerWindow() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("v", 1)),
                    new StreamEvent(1000, "k", Map.of("v", 2)),
                    new StreamEvent(2000, "k", Map.of("v", 3))
            );
            Object count = aggregator.aggregate(AggregateFunction.COUNT, events, "*");
            assertThat(count).isEqualTo(3L);
        }

        @Test
        void testSumAmountPerWindow() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("amount", 100L)),
                    new StreamEvent(1000, "k", Map.of("amount", 200L)),
                    new StreamEvent(2000, "k", Map.of("amount", 50L))
            );
            Object sum = aggregator.aggregate(AggregateFunction.SUM, events, "amount");
            assertThat(sum).isEqualTo(350L);
        }

        @Test
        void testAvgValueGroupByKey() {
            var events = List.of(
                    new StreamEvent(0, "k1", Map.of("key", "A", "value", 10.0)),
                    new StreamEvent(1000, "k2", Map.of("key", "B", "value", 20.0)),
                    new StreamEvent(2000, "k1", Map.of("key", "A", "value", 30.0)),
                    new StreamEvent(3000, "k2", Map.of("key", "B", "value", 40.0))
            );

            var groups = aggregator.groupEvents(events, List.of("key"));
            assertThat(groups).hasSize(2);

            List<StreamEvent> groupA = groups.get(List.of((Object) "A"));
            Object avgA = aggregator.aggregate(AggregateFunction.AVG, groupA, "value");
            assertThat((Double) avgA).isCloseTo(20.0, within(0.01));

            List<StreamEvent> groupB = groups.get(List.of((Object) "B"));
            Object avgB = aggregator.aggregate(AggregateFunction.AVG, groupB, "value");
            assertThat((Double) avgB).isCloseTo(30.0, within(0.01));
        }

        @Test
        void testMinPerWindow() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("temp", 25.0)),
                    new StreamEvent(1000, "k", Map.of("temp", 18.5)),
                    new StreamEvent(2000, "k", Map.of("temp", 22.0))
            );
            Object min = aggregator.aggregate(AggregateFunction.MIN, events, "temp");
            assertThat(min).isEqualTo(18.5);
        }

        @Test
        void testMaxPerWindow() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("temp", 25.0)),
                    new StreamEvent(1000, "k", Map.of("temp", 18.5)),
                    new StreamEvent(2000, "k", Map.of("temp", 32.1))
            );
            Object max = aggregator.aggregate(AggregateFunction.MAX, events, "temp");
            assertThat(max).isEqualTo(32.1);
        }

        @Test
        void testMultipleAggregates() {
            var window = new TumblingWindow(0, 10000);
            window.addEvent(new StreamEvent(0, "k", Map.of("amount", 10L)));
            window.addEvent(new StreamEvent(1000, "k", Map.of("amount", 20L)));
            window.addEvent(new StreamEvent(2000, "k", Map.of("amount", 30L)));

            var result = aggregator.aggregateWindow(window,
                    List.of(AggregateFunction.COUNT, AggregateFunction.SUM, AggregateFunction.AVG),
                    List.of("*", "amount", "amount"),
                    List.of("cnt", "total", "avg_amount"),
                    List.of());

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.columnNames()).containsExactly("cnt", "total", "avg_amount");
            assertThat(qr.rows()).hasSize(1);
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(3L);
            assertThat(qr.rows().getFirst().getValue(1)).isEqualTo(60L);
            assertThat((Double) qr.rows().getFirst().getValue(2)).isCloseTo(20.0, within(0.01));
        }

        @Test
        void testAggregateWindowWithGroupBy() {
            var window = new TumblingWindow(0, 10000);
            window.addEvent(new StreamEvent(0, "k1", Map.of("region", "US", "amount", 100L)));
            window.addEvent(new StreamEvent(1000, "k2", Map.of("region", "EU", "amount", 200L)));
            window.addEvent(new StreamEvent(2000, "k1", Map.of("region", "US", "amount", 150L)));
            window.addEvent(new StreamEvent(3000, "k2", Map.of("region", "EU", "amount", 250L)));

            var result = aggregator.aggregateWindow(window,
                    List.of(AggregateFunction.SUM),
                    List.of("amount"),
                    List.of("total"),
                    List.of("region"));

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.rows()).hasSize(2);
            assertThat(qr.columnNames()).containsExactly("region", "total");
        }

        @Test
        void testCountWithNullValues() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("v", 1)),
                    new StreamEvent(1000, "k", Map.of()),
                    new StreamEvent(2000, "k", Map.of("v", 3))
            );
            Object count = aggregator.aggregate(AggregateFunction.COUNT, events, "v");
            assertThat(count).isEqualTo(2L); // null is excluded from COUNT(column)
        }

        @Test
        void testSumWithDoubles() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("price", 10.5)),
                    new StreamEvent(1000, "k", Map.of("price", 20.3)),
                    new StreamEvent(2000, "k", Map.of("price", 30.2))
            );
            Object sum = aggregator.aggregate(AggregateFunction.SUM, events, "price");
            assertThat((Double) sum).isCloseTo(61.0, within(0.01));
        }

        @Test
        void testGroupConcatAggregate() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("name", "Alice")),
                    new StreamEvent(1000, "k", Map.of("name", "Bob")),
                    new StreamEvent(2000, "k", Map.of("name", "Charlie"))
            );
            Object result = aggregator.aggregate(AggregateFunction.GROUP_CONCAT, events, "name");
            assertThat(result).isEqualTo("Alice,Bob,Charlie");
        }

        @Test
        void testCountStarIncludesAllEvents() {
            // COUNT(*) should count all events, even those with null column values
            var events = List.of(
                    new StreamEvent(0, "k", Map.of()),
                    new StreamEvent(1000, "k", Map.of()),
                    new StreamEvent(2000, "k", Map.of())
            );
            Object count = aggregator.aggregate(AggregateFunction.COUNT, events, "*");
            assertThat(count).isEqualTo(3L);
        }

        @Test
        void testSumWithMixedTypes() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("val", 10)),
                    new StreamEvent(1000, "k", Map.of("val", 20L)),
                    new StreamEvent(2000, "k", Map.of("val", 30.0))
            );
            Object sum = aggregator.aggregate(AggregateFunction.SUM, events, "val");
            // Mixed: int, long, double -> result is double
            assertThat(sum instanceof Double).isTrue();
            assertThat((Double) sum).isCloseTo(60.0, within(0.01));
        }

        @Test
        void testEmptyEventsAggregate() {
            List<StreamEvent> events = List.of();
            Object count = aggregator.aggregate(AggregateFunction.COUNT, events, "*");
            assertThat(count).isEqualTo(0L);
        }

        @Test
        void testAvgWithSingleEvent() {
            var events = List.of(new StreamEvent(0, "k", Map.of("value", 42.0)));
            Object avg = aggregator.aggregate(AggregateFunction.AVG, events, "value");
            assertThat((Double) avg).isCloseTo(42.0, within(0.01));
        }

        @Test
        void testMinWithLongs() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("id", 100L)),
                    new StreamEvent(1000, "k", Map.of("id", 50L)),
                    new StreamEvent(2000, "k", Map.of("id", 200L))
            );
            Object min = aggregator.aggregate(AggregateFunction.MIN, events, "id");
            assertThat(min).isEqualTo(50L);
        }

        @Test
        void testMaxWithLongs() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("id", 100L)),
                    new StreamEvent(1000, "k", Map.of("id", 50L)),
                    new StreamEvent(2000, "k", Map.of("id", 200L))
            );
            Object max = aggregator.aggregate(AggregateFunction.MAX, events, "id");
            assertThat(max).isEqualTo(200L);
        }

        @Test
        void testSumEmptyReturnsZero() {
            List<StreamEvent> events = List.of();
            Object sum = aggregator.aggregate(AggregateFunction.SUM, events, "amount");
            assertThat(sum).isEqualTo(0L);
        }

        @Test
        void testAvgEmptyReturnsNull() {
            List<StreamEvent> events = List.of();
            Object avg = aggregator.aggregate(AggregateFunction.AVG, events, "value");
            assertThat(avg).isNull();
        }

        @Test
        void testGroupByMultipleColumns() {
            var events = List.of(
                    new StreamEvent(0, "k", Map.of("region", "US", "product", "A", "amount", 10L)),
                    new StreamEvent(1000, "k", Map.of("region", "US", "product", "B", "amount", 20L)),
                    new StreamEvent(2000, "k", Map.of("region", "EU", "product", "A", "amount", 30L)),
                    new StreamEvent(3000, "k", Map.of("region", "US", "product", "A", "amount", 40L))
            );
            var groups = aggregator.groupEvents(events, List.of("region", "product"));
            assertThat(groups).hasSize(3);
        }
    }

    // =============================================================
    // Watermark & Late Data Tests
    // =============================================================

    @Nested
    class WatermarkTests {

        @Test
        void testWatermarkAdvancesWithEvents() {
            var watermark = new Watermark();
            watermark.advance(1000);
            assertThat(watermark.currentWatermark()).isEqualTo(1000);
            watermark.advance(5000);
            assertThat(watermark.currentWatermark()).isEqualTo(5000);
        }

        @Test
        void testWatermarkDoesNotGoBackward() {
            var watermark = new Watermark();
            watermark.advance(5000);
            watermark.advance(3000); // earlier event
            assertThat(watermark.currentWatermark()).isEqualTo(5000);
        }

        @Test
        void testLateEventDetection() {
            var watermark = new Watermark(0, 1000); // 1 second allowed lateness
            watermark.advance(10000);
            assertThat(watermark.isLate(9500)).isFalse(); // within lateness
            assertThat(watermark.isLate(9000)).isFalse(); // within lateness
            assertThat(watermark.isLate(8999)).isTrue();  // too late
        }

        @Test
        void testLateEventWithNoAllowedLateness() {
            var watermark = new Watermark(0, 0);
            watermark.advance(10000);
            assertThat(watermark.isLate(9999)).isTrue();
            assertThat(watermark.isLate(10000)).isFalse();
        }

        @Test
        void testWatermarkInitialValue() {
            var watermark = new Watermark();
            assertThat(watermark.currentWatermark()).isEqualTo(0);
        }

        @Test
        void testWatermarkCustomInitialValue() {
            var watermark = new Watermark(5000, 0);
            assertThat(watermark.currentWatermark()).isEqualTo(5000);
        }

        @Test
        void testWatermarkSet() {
            var watermark = new Watermark();
            watermark.set(10000);
            assertThat(watermark.currentWatermark()).isEqualTo(10000);
        }

        @Test
        void testWatermarkLatenessThreshold() {
            var watermark = new Watermark(0, 5000);
            watermark.advance(20000);
            // Late threshold: 20000 - 5000 = 15000
            assertThat(watermark.isLate(15000)).isFalse();
            assertThat(watermark.isLate(14999)).isTrue();
        }

        @Test
        void testWatermarkAllowedLatenessMs() {
            var watermark = new Watermark(0, 3000);
            assertThat(watermark.allowedLatenessMs()).isEqualTo(3000);
        }

        @Test
        void testWatermarkWithSequentialAdvance() {
            var watermark = new Watermark();
            for (int i = 0; i < 100; i++) {
                watermark.advance(i * 100);
            }
            assertThat(watermark.currentWatermark()).isEqualTo(9900);
        }
    }

    // =============================================================
    // Stream Join Tests
    // =============================================================

    @Nested
    class StreamJoinTests {

        private StreamSimulator simulator;

        @BeforeEach
        void setUp() {
            simulator = new StreamSimulator();
            simulator.createStream(new CreateStreamNode("orders", List.of(
                    new ColumnDef("order_id", SqlDataType.INTEGER, true, null, false, false),
                    new ColumnDef("customer_id", SqlDataType.VARCHAR, true, null, false, false),
                    new ColumnDef("amount", SqlDataType.DOUBLE, true, null, false, false)
            ), Map.of(), ssg.pex.ast.SourceLocation.UNKNOWN));

            simulator.createStream(new CreateStreamNode("payments", List.of(
                    new ColumnDef("payment_id", SqlDataType.INTEGER, true, null, false, false),
                    new ColumnDef("customer_id", SqlDataType.VARCHAR, true, null, false, false),
                    new ColumnDef("paid", SqlDataType.DOUBLE, true, null, false, false)
            ), Map.of(), ssg.pex.ast.SourceLocation.UNKNOWN));
        }

        @Test
        void testStreamToTableLookupJoin() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k1",
                    Map.of("order_id", 1, "customer_id", "C1", "amount", 100.0)));
            simulator.ingestEvent("orders", new StreamEvent(2000, "k2",
                    Map.of("order_id", 2, "customer_id", "C2", "amount", 200.0)));

            // Lookup table: customer_id -> customer info
            Map<Object, Map<String, Object>> lookupTable = Map.of(
                    "C1", Map.of("customer_name", "Alice", "tier", "gold"),
                    "C2", Map.of("customer_name", "Bob", "tier", "silver")
            );

            var results = simulator.lookupJoin("orders", lookupTable, "customer_id");
            assertThat(results).hasSize(2);
            assertThat(results.get(0).values()).containsEntry("customer_name", "Alice");
            assertThat(results.get(1).values()).containsEntry("customer_name", "Bob");
        }

        @Test
        void testStreamToStreamJoinWithinTimeWindow() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k1",
                    Map.of("customer_id", "C1", "amount", 100.0)));
            simulator.ingestEvent("orders", new StreamEvent(5000, "k2",
                    Map.of("customer_id", "C2", "amount", 200.0)));

            simulator.ingestEvent("payments", new StreamEvent(2000, "k1",
                    Map.of("customer_id", "C1", "paid", 100.0)));
            simulator.ingestEvent("payments", new StreamEvent(20000, "k2",
                    Map.of("customer_id", "C2", "paid", 200.0)));

            // Join with 5-second window
            var results = simulator.joinStreams("orders", "payments", "customer_id", 5000);
            assertThat(results).hasSize(1); // Only C1 matched (within 5s)
            assertThat(results.getFirst().values()).containsEntry("customer_id", "C1");
            assertThat(results.getFirst().values()).containsEntry("amount", 100.0);
            assertThat(results.getFirst().values()).containsEntry("right_paid", 100.0);
        }

        @Test
        void testStreamJoinNoMatch() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k1",
                    Map.of("customer_id", "C1", "amount", 100.0)));
            simulator.ingestEvent("payments", new StreamEvent(1000, "k2",
                    Map.of("customer_id", "C2", "paid", 200.0)));

            var results = simulator.joinStreams("orders", "payments", "customer_id", 5000);
            assertThat(results).isEmpty();
        }

        @Test
        void testLookupJoinNoMatch() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k1",
                    Map.of("customer_id", "C99", "amount", 100.0)));

            Map<Object, Map<String, Object>> lookupTable = Map.of(
                    "C1", Map.of("name", "Alice"));

            var results = simulator.lookupJoin("orders", lookupTable, "customer_id");
            assertThat(results).isEmpty();
        }

        @Test
        void testStreamJoinMultipleMatches() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k1",
                    Map.of("customer_id", "C1", "amount", 100.0)));
            simulator.ingestEvent("orders", new StreamEvent(2000, "k2",
                    Map.of("customer_id", "C1", "amount", 150.0)));

            simulator.ingestEvent("payments", new StreamEvent(1500, "k3",
                    Map.of("customer_id", "C1", "paid", 250.0)));

            var results = simulator.joinStreams("orders", "payments", "customer_id", 5000);
            assertThat(results).hasSize(2); // Both orders match the payment
        }

        @Test
        void testStreamJoinOutsideTimeWindow() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k1",
                    Map.of("customer_id", "C1", "amount", 100.0)));
            simulator.ingestEvent("payments", new StreamEvent(10000, "k1",
                    Map.of("customer_id", "C1", "paid", 100.0)));

            var results = simulator.joinStreams("orders", "payments", "customer_id", 2000);
            assertThat(results).isEmpty();
        }

        @Test
        void testLookupJoinPreservesStreamFields() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k1",
                    Map.of("order_id", 42, "customer_id", "C1", "amount", 99.99)));

            Map<Object, Map<String, Object>> lookupTable = Map.of(
                    "C1", Map.of("customer_name", "Alice"));

            var results = simulator.lookupJoin("orders", lookupTable, "customer_id");
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().values()).containsEntry("order_id", 42);
            assertThat(results.getFirst().values()).containsEntry("amount", 99.99);
            assertThat(results.getFirst().values()).containsEntry("customer_name", "Alice");
        }

        @Test
        void testStreamJoinNonExistentStream() {
            var results = simulator.joinStreams("nonexistent", "payments", "id", 5000);
            assertThat(results).isEmpty();
        }

        @Test
        void testLookupJoinNonExistentStream() {
            var results = simulator.lookupJoin("nonexistent", Map.of(), "id");
            assertThat(results).isEmpty();
        }

        @Test
        void testStreamJoinBothDirections() {
            simulator.ingestEvent("orders", new StreamEvent(1000, "k",
                    Map.of("customer_id", "C1", "amount", 100.0)));
            simulator.ingestEvent("payments", new StreamEvent(1500, "k",
                    Map.of("customer_id", "C1", "paid", 100.0)));

            var forward = simulator.joinStreams("orders", "payments", "customer_id", 5000);
            var reverse = simulator.joinStreams("payments", "orders", "customer_id", 5000);
            assertThat(forward).hasSize(1);
            assertThat(reverse).hasSize(1);
        }
    }

    // =============================================================
    // End-to-End Tests
    // =============================================================

    @Nested
    class EndToEndTests {

        private StreamSimulator simulator;

        @BeforeEach
        void setUp() {
            simulator = new StreamSimulator();
        }

        @Test
        void testCreateStreamIngestEventsQueryResults() {
            simulator.executeQuery("CREATE STREAM events (user_id VARCHAR, action VARCHAR, ts TIMESTAMP)");
            assertThat(simulator.hasStream("events")).isTrue();

            simulator.ingestEvent("events", new StreamEvent(1000, "u1", Map.of("user_id", "u1", "action", "click")));
            simulator.ingestEvent("events", new StreamEvent(2000, "u2", Map.of("user_id", "u2", "action", "view")));
            simulator.ingestEvent("events", new StreamEvent(3000, "u1", Map.of("user_id", "u1", "action", "click")));

            var result = simulator.executeQuery("SELECT * FROM events EMIT CHANGES");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testCreateAndDropStream() {
            simulator.executeQuery("CREATE STREAM temp_stream (id INTEGER, value DOUBLE)");
            assertThat(simulator.hasStream("temp_stream")).isTrue();

            simulator.executeQuery("DROP STREAM temp_stream");
            assertThat(simulator.hasStream("temp_stream")).isFalse();
        }

        @Test
        void testDropStreamIfExistsOnNonExistent() {
            var result = simulator.executeQuery("DROP STREAM IF EXISTS nonexistent");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void testMultipleStreamsWithDifferentWindowTypes() {
            simulator.executeQuery("CREATE STREAM clicks (user_id VARCHAR, url VARCHAR)");
            simulator.executeQuery("CREATE STREAM sales (product VARCHAR, amount DOUBLE)");

            // Insert events into both streams
            for (int i = 0; i < 10; i++) {
                simulator.ingestEvent("clicks", new StreamEvent(i * 1000, "u" + i,
                        Map.of("user_id", "u" + i, "url", "/page" + i)));
                simulator.ingestEvent("sales", new StreamEvent(i * 1000, "p" + i,
                        Map.of("product", "p" + i, "amount", (double)(i * 10))));
            }

            assertThat(simulator.streamNames()).hasSize(2);
        }

        @Test
        void testContinuousQueryPattern() {
            simulator.executeQuery("CREATE STREAM metrics (sensor_id VARCHAR, value DOUBLE)");

            var ssn = new StreamSelectNode(
                    List.of(new ssg.pex.sql.ast.SqlSupport.SelectItem("COUNT(*)", "cnt", false)),
                    "metrics",
                    new WindowSpec(WindowType.TUMBLING, 5000, 0, 0),
                    null, null,
                    EmitStrategy.FINAL,
                    ssg.pex.ast.SourceLocation.UNKNOWN);

            var regResult = simulator.registerQuery(ssn);
            assertThat(regResult.isSuccess()).isTrue();

            // Batch 1
            for (int i = 0; i < 5; i++) {
                simulator.ingestEvent("metrics", new StreamEvent(i * 1000, "s1",
                        Map.of("sensor_id", "s1", "value", 20.0 + i)));
            }

            // Batch 2: events in next window
            for (int i = 5; i < 10; i++) {
                simulator.ingestEvent("metrics", new StreamEvent(i * 1000, "s1",
                        Map.of("sensor_id", "s1", "value", 30.0 + i)));
            }

            var results = simulator.processEvents();
            // Window [0,5000) should be closed, [5000,10000) closed by watermark
            assertThat(results).isNotEmpty();
        }

        @Test
        void testStreamWithGroupByAndAggregation() {
            simulator.executeQuery("CREATE STREAM sales (region VARCHAR, amount DOUBLE)");

            simulator.ingestEvent("sales", new StreamEvent(0, "k", Map.of("region", "US", "amount", 100.0)));
            simulator.ingestEvent("sales", new StreamEvent(1000, "k", Map.of("region", "EU", "amount", 200.0)));
            simulator.ingestEvent("sales", new StreamEvent(2000, "k", Map.of("region", "US", "amount", 150.0)));
            simulator.ingestEvent("sales", new StreamEvent(3000, "k", Map.of("region", "EU", "amount", 250.0)));

            var result = simulator.executeQuery(
                    "SELECT region, SUM(amount) AS total FROM sales WINDOW TUMBLING (SIZE 10 SECONDS) GROUP BY region EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(2);
        }

        @Test
        void testClickstreamAnalysisCountClicksPerUserPerMinute() {
            simulator.executeQuery("CREATE STREAM clickstream (user_id VARCHAR, page VARCHAR)");

            // User A: 3 clicks in first minute
            simulator.ingestEvent("clickstream", new StreamEvent(10000, "A",
                    Map.of("user_id", "A", "page", "/home")));
            simulator.ingestEvent("clickstream", new StreamEvent(20000, "A",
                    Map.of("user_id", "A", "page", "/products")));
            simulator.ingestEvent("clickstream", new StreamEvent(30000, "A",
                    Map.of("user_id", "A", "page", "/cart")));

            // User B: 2 clicks in first minute
            simulator.ingestEvent("clickstream", new StreamEvent(15000, "B",
                    Map.of("user_id", "B", "page", "/home")));
            simulator.ingestEvent("clickstream", new StreamEvent(45000, "B",
                    Map.of("user_id", "B", "page", "/about")));

            var result = simulator.executeQuery(
                    "SELECT user_id, COUNT(*) AS click_count FROM clickstream WINDOW TUMBLING (SIZE 1 MINUTES) GROUP BY user_id EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(2);

            // Verify user A has 3 clicks and user B has 2
            var rows = result.value().rows();
            for (var row : rows) {
                String userId = (String) row.getValue(0);
                long count = (Long) row.getValue(1);
                if (userId.equals("A")) assertThat(count).isEqualTo(3);
                else if (userId.equals("B")) assertThat(count).isEqualTo(2);
            }
        }

        @Test
        void testIoTSensorAvgTemperaturePer5MinuteWindow() {
            simulator.executeQuery("CREATE STREAM sensors (sensor_id VARCHAR, temp DOUBLE)");

            // Sensor A readings over 10 minutes
            for (int i = 0; i < 10; i++) {
                simulator.ingestEvent("sensors", new StreamEvent(i * 60000, "A",
                        Map.of("sensor_id", "A", "temp", 20.0 + i)));
            }

            // Sensor B readings
            for (int i = 0; i < 10; i++) {
                simulator.ingestEvent("sensors", new StreamEvent(i * 60000, "B",
                        Map.of("sensor_id", "B", "temp", 30.0 + i)));
            }

            var result = simulator.executeQuery(
                    "SELECT sensor_id, AVG(temp) AS avg_temp FROM sensors WINDOW TUMBLING (SIZE 5 MINUTES) GROUP BY sensor_id EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            // 2 windows x 2 sensors = 4 rows
            assertThat(result.value().rowCount()).isEqualTo(4);
        }

        @Test
        void testStreamCreateAlreadyExists() {
            simulator.executeQuery("CREATE STREAM test (id INTEGER)");
            var result = simulator.executeQuery("CREATE STREAM test (id INTEGER)");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().message()).contains("already exists");
        }

        @Test
        void testDropNonExistentStream() {
            var result = simulator.executeQuery("DROP STREAM nonexistent");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void testQueryNonExistentStream() {
            var result = simulator.executeQuery("SELECT * FROM nonexistent EMIT CHANGES");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void testActiveQueryCount() {
            simulator.executeQuery("CREATE STREAM s1 (id INTEGER)");
            assertThat(simulator.activeQueryCount()).isEqualTo(0);

            var ssn = new StreamSelectNode(
                    List.of(new ssg.pex.sql.ast.SqlSupport.SelectItem("COUNT(*)", "cnt", false)),
                    "s1",
                    new WindowSpec(WindowType.TUMBLING, 5000, 0, 0),
                    null, null,
                    EmitStrategy.FINAL,
                    ssg.pex.ast.SourceLocation.UNKNOWN);
            simulator.registerQuery(ssn);
            assertThat(simulator.activeQueryCount()).isEqualTo(1);
        }

        @Test
        void testInsertIntoStreamQuery() {
            simulator.executeQuery("CREATE STREAM input_events (user_id VARCHAR, action VARCHAR)");
            simulator.executeQuery("CREATE STREAM output_counts (cnt INTEGER)");

            var result = simulator.executeQuery(
                    "INSERT INTO output_counts SELECT COUNT(*) AS cnt FROM input_events WINDOW TUMBLING (SIZE 5 SECONDS) EMIT FINAL");
            assertThat(result.isSuccess()).isTrue();
            assertThat(simulator.activeQueryCount()).isEqualTo(1);
        }

        @Test
        void testStreamSourcePeekAll() {
            simulator.executeQuery("CREATE STREAM test (value INTEGER)");
            simulator.ingestEvent("test", new StreamEvent(0, "k", Map.of("value", 1)));
            simulator.ingestEvent("test", new StreamEvent(1000, "k", Map.of("value", 2)));

            StreamSource source = simulator.getStream("test");
            assertThat(source.peekAll()).hasSize(2);
            // peekAll should not consume events
            assertThat(source.peekAll()).hasSize(2);
        }

        @Test
        void testStreamSourcePoll() {
            simulator.executeQuery("CREATE STREAM test (value INTEGER)");
            simulator.ingestEvent("test", new StreamEvent(0, "k", Map.of("value", 1)));
            simulator.ingestEvent("test", new StreamEvent(1000, "k", Map.of("value", 2)));
            simulator.ingestEvent("test", new StreamEvent(2000, "k", Map.of("value", 3)));

            StreamSource source = simulator.getStream("test");
            var polled = source.poll(2);
            assertThat(polled).hasSize(2);
            assertThat(source.bufferedCount()).isEqualTo(1);
        }

        @Test
        void testStreamSourcePollWindow() {
            simulator.executeQuery("CREATE STREAM test (value INTEGER)");
            simulator.ingestEvent("test", new StreamEvent(1000, "k", Map.of("value", 1)));
            simulator.ingestEvent("test", new StreamEvent(3000, "k", Map.of("value", 2)));
            simulator.ingestEvent("test", new StreamEvent(5000, "k", Map.of("value", 3)));

            StreamSource source = simulator.getStream("test");
            var windowed = source.pollWindow(0, 4000);
            assertThat(windowed).hasSize(2); // events at 1000 and 3000
            assertThat(source.bufferedCount()).isEqualTo(1);
        }

        @Test
        void testPluginNameAndLoadOrder() {
            var plugin = new StreamingPlugin();
            assertThat(plugin.name()).isEqualTo("sql-streaming");
            assertThat(plugin.loadOrder()).isEqualTo(220);
        }
    }
}

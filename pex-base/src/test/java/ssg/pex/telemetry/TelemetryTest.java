package ssg.pex.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.result.PexError;
import ssg.pex.scope.ScopePath;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Telemetry events")
class TelemetryTest {

    @Nested
    @DisplayName("ParseEvent")
    class ParseEventTests {

        @Test
        @DisplayName("stores all fields correctly")
        void testParseEventFields() {
            var event = new ParseEvent("arithmetic", "1+2", 50_000L, true);
            assertThat(event.grammarName()).isEqualTo("arithmetic");
            assertThat(event.input()).isEqualTo("1+2");
            assertThat(event.durationNanos()).isEqualTo(50_000L);
            assertThat(event.success()).isTrue();
        }

        @Test
        @DisplayName("represents failure")
        void testParseEventFailure() {
            var event = new ParseEvent("sql", "INVALID", 1_000L, false);
            assertThat(event.success()).isFalse();
            assertThat(event.grammarName()).isEqualTo("sql");
        }

        @Test
        @DisplayName("implements PexEvent sealed interface")
        void testParseEventIsPexEvent() {
            PexEvent event = new ParseEvent("g", "i", 0L, true);
            assertThat(event).isInstanceOf(ParseEvent.class);
        }

        @Test
        @DisplayName("equality based on all fields")
        void testParseEventEquality() {
            var e1 = new ParseEvent("g", "i", 100L, true);
            var e2 = new ParseEvent("g", "i", 100L, true);
            var e3 = new ParseEvent("g", "i", 200L, true);
            assertThat(e1).isEqualTo(e2);
            assertThat(e1).isNotEqualTo(e3);
        }
    }

    @Nested
    @DisplayName("ExecuteEvent")
    class ExecuteEventTests {

        @Test
        @DisplayName("stores all fields correctly")
        void testExecuteEventFields() {
            var event = new ExecuteEvent("BinaryOpNode", 12_000L, true);
            assertThat(event.nodeType()).isEqualTo("BinaryOpNode");
            assertThat(event.durationNanos()).isEqualTo(12_000L);
            assertThat(event.success()).isTrue();
        }

        @Test
        @DisplayName("represents execution failure")
        void testExecuteEventFailure() {
            var event = new ExecuteEvent("FunctionCallNode", 500L, false);
            assertThat(event.success()).isFalse();
        }

        @Test
        @DisplayName("implements PexEvent")
        void testExecuteEventIsPexEvent() {
            PexEvent event = new ExecuteEvent("n", 0L, true);
            assertThat(event).isInstanceOf(ExecuteEvent.class);
        }
    }

    @Nested
    @DisplayName("ScopeEvent")
    class ScopeEventTests {

        @Test
        @DisplayName("stores all fields correctly for entering")
        void testScopeEventEntering() {
            var path = ScopePath.of("root", "func1");
            var event = new ScopeEvent("func1", path, true);
            assertThat(event.scopeName()).isEqualTo("func1");
            assertThat(event.path()).isEqualTo(path);
            assertThat(event.entering()).isTrue();
        }

        @Test
        @DisplayName("stores all fields correctly for exiting")
        void testScopeEventExiting() {
            var path = ScopePath.of("root");
            var event = new ScopeEvent("root", path, false);
            assertThat(event.entering()).isFalse();
        }

        @Test
        @DisplayName("implements PexEvent")
        void testScopeEventIsPexEvent() {
            PexEvent event = new ScopeEvent("s", ScopePath.of("s"), true);
            assertThat(event).isInstanceOf(ScopeEvent.class);
        }
    }

    @Nested
    @DisplayName("ErrorEvent")
    class ErrorEventTests {

        @Test
        @DisplayName("stores all fields correctly")
        void testErrorEventFields() {
            var error = new PexError("EXEC_001", "Division by zero");
            var event = new ErrorEvent(error, "expression evaluation");
            assertThat(event.error()).isEqualTo(error);
            assertThat(event.context()).isEqualTo("expression evaluation");
        }

        @Test
        @DisplayName("implements PexEvent")
        void testErrorEventIsPexEvent() {
            var error = new PexError("E", "msg");
            PexEvent event = new ErrorEvent(error, "ctx");
            assertThat(event).isInstanceOf(ErrorEvent.class);
        }

        @Test
        @DisplayName("preserves error code and message")
        void testErrorEventPreservesError() {
            var error = new PexError("TYPE_MISMATCH", "expected int");
            var event = new ErrorEvent(error, "handler dispatch");
            assertThat(event.error().code()).isEqualTo("TYPE_MISMATCH");
            assertThat(event.error().message()).isEqualTo("expected int");
        }
    }

    @Nested
    @DisplayName("PexTelemetryListener")
    class TelemetryListenerTests {

        @Test
        @DisplayName("listener receives all event types")
        void testListenerReceivesAllEventTypes() {
            List<PexEvent> received = new ArrayList<>();
            PexTelemetryListener listener = received::add;

            listener.onEvent(new ParseEvent("g", "i", 100L, true));
            listener.onEvent(new ExecuteEvent("n", 200L, true));
            listener.onEvent(new ScopeEvent("s", ScopePath.of("s"), true));
            listener.onEvent(new ErrorEvent(new PexError("E", "msg"), "ctx"));

            assertThat(received).hasSize(4);
            assertThat(received.get(0)).isInstanceOf(ParseEvent.class);
            assertThat(received.get(1)).isInstanceOf(ExecuteEvent.class);
            assertThat(received.get(2)).isInstanceOf(ScopeEvent.class);
            assertThat(received.get(3)).isInstanceOf(ErrorEvent.class);
        }

        @Test
        @DisplayName("can use pattern matching over sealed PexEvent")
        void testPatternMatchingOverEvents() {
            List<String> log = new ArrayList<>();
            PexTelemetryListener listener = event -> {
                switch (event) {
                    case ParseEvent pe -> log.add("PARSE:" + pe.grammarName());
                    case ExecuteEvent ee -> log.add("EXEC:" + ee.nodeType());
                    case ScopeEvent se -> log.add("SCOPE:" + (se.entering() ? "IN" : "OUT") + ":" + se.scopeName());
                    case ErrorEvent err -> log.add("ERR:" + err.error().code());
                }
            };

            listener.onEvent(new ParseEvent("sql", "SELECT 1", 0L, true));
            listener.onEvent(new ExecuteEvent("BinaryOpNode", 0L, true));
            listener.onEvent(new ScopeEvent("func", ScopePath.of("func"), false));
            listener.onEvent(new ErrorEvent(new PexError("PARSE_ERR", "unexpected token"), "parser"));

            assertThat(log).containsExactly(
                    "PARSE:sql",
                    "EXEC:BinaryOpNode",
                    "SCOPE:OUT:func",
                    "ERR:PARSE_ERR"
            );
        }

        @Test
        @DisplayName("multiple listeners can receive same event")
        void testMultipleListeners() {
            List<PexEvent> log1 = new ArrayList<>();
            List<PexEvent> log2 = new ArrayList<>();
            PexTelemetryListener l1 = log1::add;
            PexTelemetryListener l2 = log2::add;

            var event = new ParseEvent("g", "x", 0L, true);
            l1.onEvent(event);
            l2.onEvent(event);

            assertThat(log1).hasSize(1);
            assertThat(log2).hasSize(1);
            assertThat(log1.get(0)).isSameAs(log2.get(0));
        }
    }
}

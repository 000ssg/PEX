package ssg.pex.sql.streaming;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.sql.streaming.ast.EmitStrategy;
import ssg.pex.sql.streaming.ast.WindowSpec;
import ssg.pex.sql.streaming.ast.WindowSpec.WindowType;
import ssg.pex.sql.streaming.engine.*;

import java.util.Map;

class WindowingTest {

    private StreamEvent evt(long ts, String col, Object val) {
        return new StreamEvent(ts, null, Map.of(col, val));
    }

    // ── TumblingWindow ─────────────────────────────────────────

    @Test
    void tumblingAcceptsInRange() {
        TumblingWindow w = new TumblingWindow(100, 50);
        assertThat(w.accepts(100)).isTrue();
        assertThat(w.accepts(149)).isTrue();
        assertThat(w.accepts(150)).isFalse();
    }

    @Test
    void tumblingAddAndEvents() {
        TumblingWindow w = new TumblingWindow(100, 50);
        w.addEvent(evt(120, "x", 1));
        assertThat(w.events()).hasSize(1);
    }

    @Test
    void tumblingIsClosed() {
        TumblingWindow w = new TumblingWindow(100, 50);
        assertThat(w.isClosed(149)).isFalse();
        assertThat(w.isClosed(150)).isTrue();
    }

    // ── SlidingWindow ──────────────────────────────────────────

    @Test
    void slidingAccepts() {
        SlidingWindow w = new SlidingWindow(100, 150);
        assertThat(w.accepts(100)).isTrue();
        assertThat(w.accepts(149)).isTrue();
        assertThat(w.accepts(150)).isFalse();
    }

    // ── HoppingWindow ──────────────────────────────────────────

    @Test
    void hoppingWindow() {
        HoppingWindow w = new HoppingWindow(100, 50);
        assertThat(w.startTime()).isEqualTo(100);
        assertThat(w.endTime()).isEqualTo(150);
        assertThat(w.accepts(120)).isTrue();
    }

    // ── SessionWindow ──────────────────────────────────────────

    @Test
    void sessionWindowExtend() {
        SessionWindow w = new SessionWindow(100, 50);
        assertThat(w.endTime()).isEqualTo(150);
        w.addEvent(evt(140, "x", 1));
        assertThat(w.endTime()).isEqualTo(190); // extended
    }

    @Test
    void sessionWindowMerge() {
        SessionWindow w1 = new SessionWindow(100, 50);
        SessionWindow w2 = new SessionWindow(50, 50);
        w2.addEvent(evt(60, "x", 1));
        w1.merge(w2);
        assertThat(w1.startTime()).isEqualTo(50);
    }

    // ── Watermark ──────────────────────────────────────────────

    @Test
    void watermarkAdvance() {
        Watermark wm = new Watermark();
        wm.advance(100);
        assertThat(wm.currentWatermark()).isEqualTo(100);
    }

    @Test
    void watermarkNoBacktrack() {
        Watermark wm = new Watermark();
        wm.advance(200);
        wm.advance(100);
        assertThat(wm.currentWatermark()).isEqualTo(200);
    }

    @Test
    void watermarkLate() {
        Watermark wm = new Watermark(100, 10);
        assertThat(wm.isLate(89)).isTrue();
        assertThat(wm.isLate(90)).isFalse();
    }

    // ── StreamEvent ────────────────────────────────────────────

    @Test
    void streamEventGet() {
        StreamEvent e = new StreamEvent(100, "k1", Map.of("x", 42));
        assertThat(e.<Integer>get("x")).isEqualTo(42);
        assertThat(e.timestamp()).isEqualTo(100);
        assertThat(e.key()).isEqualTo("k1");
    }

    // ── WindowSpec ─────────────────────────────────────────────

    @Test
    void windowSpecTumbling() {
        WindowSpec spec = new WindowSpec(WindowType.TUMBLING, 60_000, 0, 0);
        assertThat(spec.type()).isEqualTo(WindowType.TUMBLING);
        assertThat(spec.durationMs()).isEqualTo(60_000);
    }

    @Test
    void windowSpecSession() {
        WindowSpec spec = new WindowSpec(WindowType.SESSION, 0, 0, 5_000);
        assertThat(spec.gapMs()).isEqualTo(5_000);
    }

    @Test
    void windowTypeValues() {
        assertThat(WindowType.values()).hasSize(4);
    }

    // ── EmitStrategy ───────────────────────────────────────────

    @Test
    void emitStrategyValues() {
        assertThat(EmitStrategy.values()).hasSize(2);
    }
}

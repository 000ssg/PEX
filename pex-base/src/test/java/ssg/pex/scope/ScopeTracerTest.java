package ssg.pex.scope;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;

class ScopeTracerTest {

    @Test
    void recordAndHistory() {
        ScopeTracer tracer = new ScopeTracer();
        ShadowRecord rec = new ShadowRecord("x", ScopePath.of("g"), ScopePath.of("g.f"), 1, 2);
        tracer.recordShadow(rec);
        assertThat(tracer.history()).containsExactly(rec);
    }

    @Test
    void historyFor() {
        ScopeTracer tracer = new ScopeTracer();
        tracer.recordShadow(new ShadowRecord("x", ScopePath.of("g"), ScopePath.of("g.f"), 1, 2));
        tracer.recordShadow(new ShadowRecord("y", ScopePath.of("g"), ScopePath.of("g.f"), "a", "b"));
        tracer.recordShadow(new ShadowRecord("x", ScopePath.of("g.f"), ScopePath.of("g.f.b"), 2, 3));
        assertThat(tracer.historyFor("x")).hasSize(2);
        assertThat(tracer.historyFor("y")).hasSize(1);
        assertThat(tracer.historyFor("z")).isEmpty();
    }

    @Test
    void historyImmutable() {
        ScopeTracer tracer = new ScopeTracer();
        tracer.recordShadow(new ShadowRecord("x", ScopePath.of("g"), ScopePath.of("g.f"), 1, 2));
        assertThatThrownBy(() -> tracer.history().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void clear() {
        ScopeTracer tracer = new ScopeTracer();
        tracer.recordShadow(new ShadowRecord("x", ScopePath.of("g"), ScopePath.of("g.f"), 1, 2));
        tracer.clear();
        assertThat(tracer.history()).isEmpty();
    }
}

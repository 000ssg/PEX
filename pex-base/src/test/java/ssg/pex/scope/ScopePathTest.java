package ssg.pex.scope;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ScopePathTest {

    @Test
    void of() {
        ScopePath path = ScopePath.of("global", "fn1", "block");
        assertThat(path.segments()).containsExactly("global", "fn1", "block");
    }

    @Test
    void ofSingle() {
        ScopePath path = ScopePath.of("global");
        assertThat(path.segments()).containsExactly("global");
    }

    @Test
    void append() {
        ScopePath path = ScopePath.of("global");
        ScopePath extended = path.append("fn1");
        assertThat(extended.segments()).containsExactly("global", "fn1");
        // Original unchanged
        assertThat(path.segments()).containsExactly("global");
    }

    @Test
    void depth() {
        ScopePath path = ScopePath.of("global", "fn1", "block");
        assertThat(path.depth()).isEqualTo(3);
    }

    @Test
    void emptyDepth() {
        ScopePath path = new ScopePath(java.util.List.of());
        assertThat(path.depth()).isZero();
    }

    @Test
    void isAncestorOf() {
        ScopePath a = ScopePath.of("global", "fn1");
        ScopePath b = ScopePath.of("global", "fn1", "block");
        assertThat(a.isAncestorOf(b)).isTrue();
        assertThat(b.isAncestorOf(a)).isFalse();
    }

    @Test
    void isAncestorOfDifferentPath() {
        ScopePath a = ScopePath.of("global", "fn1");
        ScopePath b = ScopePath.of("global", "fn2");
        assertThat(a.isAncestorOf(b)).isFalse();
    }

    @Test
    void isAncestorOfSamePath() {
        ScopePath a = ScopePath.of("global", "fn1");
        ScopePath b = ScopePath.of("global", "fn1");
        assertThat(a.isAncestorOf(b)).isFalse(); // same length, not ancestor
    }

    @Test
    void toStringOutput() {
        ScopePath path = ScopePath.of("global", "fn1", "block");
        assertThat(path.toString()).isEqualTo("global.fn1.block");
    }

    @Test
    void segmentsImmutable() {
        ScopePath path = ScopePath.of("global", "fn1");
        assertThatThrownBy(() -> path.segments().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package ssg.pex.scope;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class NumericIndexStoreTest {

    @Test
    void setAndGet() {
        NumericIndexStore store = new NumericIndexStore();
        store.set(0, "first");
        assertThat(store.get(0)).isEqualTo("first");
    }

    @Test
    void add() {
        NumericIndexStore store = new NumericIndexStore();
        store.add("a");
        store.add("b");
        assertThat(store.get(0)).isEqualTo("a");
        assertThat(store.get(1)).isEqualTo("b");
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void setExpands() {
        NumericIndexStore store = new NumericIndexStore();
        store.set(5, "sixth");
        assertThat(store.size()).isEqualTo(6);
        assertThat(store.get(4)).isNull();
        assertThat(store.get(5)).isEqualTo("sixth");
    }

    @Test
    void negativeIndexThrows() {
        NumericIndexStore store = new NumericIndexStore();
        assertThatThrownBy(() -> store.set(-1, "value"))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void getOutOfBounds() {
        NumericIndexStore store = new NumericIndexStore();
        store.add("a");
        assertThat(store.get(5)).isNull();
    }

    @Test
    void containsKey() {
        NumericIndexStore store = new NumericIndexStore();
        store.set(0, "a");
        assertThat(store.containsKey(0)).isTrue();
        assertThat(store.containsKey(1)).isFalse();
    }

    @Test
    void longKey() {
        NumericIndexStore store = new NumericIndexStore();
        store.set(0L, "value");
        assertThat(store.get(0L)).isEqualTo("value");
    }

    @Test
    void stringKeyThrows() {
        NumericIndexStore store = new NumericIndexStore();
        assertThatThrownBy(() -> store.set("not-a-number", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("integer key");
    }

    @Test
    void toList() {
        NumericIndexStore store = new NumericIndexStore();
        store.add(1);
        store.add(2);
        assertThat(store.toList()).containsExactly(1, 2);
    }

    @Test
    void toListImmutable() {
        NumericIndexStore store = new NumericIndexStore();
        store.add(1);
        assertThatThrownBy(() -> store.toList().add(2))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package ssg.pex.scope;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class KeyMappedStoreTest {

    @Test
    void getAndSet() {
        KeyMappedStore store = new KeyMappedStore();
        store.set("name", "Alice");
        assertThat(store.get("name")).isEqualTo("Alice");
    }

    @Test
    void getMissing() {
        KeyMappedStore store = new KeyMappedStore();
        assertThat(store.get("missing")).isNull();
    }

    @Test
    void containsKey() {
        KeyMappedStore store = new KeyMappedStore();
        store.set("x", 1);
        assertThat(store.containsKey("x")).isTrue();
        assertThat(store.containsKey("y")).isFalse();
    }

    @Test
    void nonStringKey() {
        KeyMappedStore store = new KeyMappedStore();
        store.set(123, "value");
        assertThat(store.get(123)).isEqualTo("value");
        assertThat(store.get("123")).isEqualTo("value");
    }

    @Test
    void size() {
        KeyMappedStore store = new KeyMappedStore();
        store.set("a", 1);
        store.set("b", 2);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void keys() {
        KeyMappedStore store = new KeyMappedStore();
        store.set("a", 1);
        store.set("b", 2);
        assertThat(store.keys()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void keysImmutable() {
        KeyMappedStore store = new KeyMappedStore();
        store.set("a", 1);
        assertThatThrownBy(() -> store.keys().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toMap() {
        KeyMappedStore store = new KeyMappedStore();
        store.set("a", 1);
        assertThat(store.toMap()).containsEntry("a", 1);
    }

    @Test
    void toMapImmutable() {
        KeyMappedStore store = new KeyMappedStore();
        store.set("a", 1);
        assertThatThrownBy(() -> store.toMap().put("b", 2))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

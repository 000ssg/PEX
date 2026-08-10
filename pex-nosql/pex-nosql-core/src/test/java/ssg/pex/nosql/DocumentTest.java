package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class DocumentTest {

    @Test
    void emptyConstructor() {
        Document doc = new Document();
        assertThat(doc).isEmpty();
    }

    @Test
    void mapConstructor() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");
        map.put("age", 30);
        Document doc = new Document(map);
        assertThat(doc.get("name")).isEqualTo("Alice");
        assertThat(doc.get("age")).isEqualTo(30);
        assertThat(doc).hasSize(2);
    }

    @Test
    void ofFactoryEvenPairs() {
        Document doc = Document.of("name", "Alice", "age", 30);
        assertThat(doc.get("name")).isEqualTo("Alice");
        assertThat(doc.get("age")).isEqualTo(30);
    }

    @Test
    void ofFactoryEmpty() {
        Document doc = Document.of();
        assertThat(doc).isEmpty();
    }

    @Test
    void ofFactoryOddArgs() {
        assertThatThrownBy(() -> Document.of("name", "Alice", "extra"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("even number");
    }

    @Test
    void ofFactoryNonStringKey() {
        assertThatThrownBy(() -> Document.of(123, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a String");
    }

    @Test
    void idField() {
        Document doc = new Document();
        doc.put("_id", "doc-1");
        assertThat(doc.id()).isEqualTo("doc-1");
    }

    @Test
    void idFieldNull() {
        Document doc = new Document();
        doc.put("name", "Bob");
        assertThat(doc.id()).isNull();
    }

    @Test
    void copyShallow() {
        Document doc = Document.of("name", "Alice", "age", 30);
        Document copy = doc.copy();
        assertThat(copy).isNotSameAs(doc);
        assertThat(copy.get("name")).isEqualTo("Alice");
        assertThat(copy.get("age")).isEqualTo(30);
        // Mutating copy should not affect original
        copy.put("name", "Bob");
        assertThat(doc.get("name")).isEqualTo("Alice");
    }

    @Test
    void copyDeep() {
        Document nested = Document.of("city", "Helsinki");
        Document doc = Document.of("address", nested, "name", "Alice");
        Document copy = doc.copy();
        assertThat(copy).isNotSameAs(doc);
        // Mutating nested copy should not affect original
        ((Document) copy.get("address")).put("city", "Tampere");
        assertThat(((Document) doc.get("address")).get("city")).isEqualTo("Helsinki");
    }

    @Test
    void copyWithList() {
        List<Object> tags = new ArrayList<>(List.of("java", "nosql"));
        Document doc = Document.of("name", "Alice", "tags", tags);
        Document copy = doc.copy();
        // Mutating list in copy should not affect original
        ((List<Object>) copy.get("tags")).add("mongodb");
        assertThat((List<Object>) doc.get("tags")).hasSize(2);
        assertThat((List<Object>) copy.get("tags")).hasSize(3);
    }

    @Test
    void toStringContainsDocument() {
        Document doc = Document.of("name", "Alice");
        String s = doc.toString();
        assertThat(s).contains("Document");
        assertThat(s).contains("name");
        assertThat(s).contains("Alice");
    }

    @Test
    void idFieldConstant() {
        assertThat(Document.ID_FIELD).isEqualTo("_id");
    }

    @Test
    void putAndGet() {
        Document doc = new Document();
        doc.put("x", 42);
        assertThat(doc.get("x")).isEqualTo(42);
    }

    @Test
    void containsKey() {
        Document doc = Document.of("name", "Alice");
        assertThat(doc.containsKey("name")).isTrue();
        assertThat(doc.containsKey("missing")).isFalse();
    }

    @Test
    void remove() {
        Document doc = Document.of("name", "Alice", "age", 30);
        Object removed = doc.remove("age");
        assertThat(removed).isEqualTo(30);
        assertThat(doc.containsKey("age")).isFalse();
        assertThat(doc).hasSize(1);
    }

    @Test
    void keySet() {
        Document doc = Document.of("a", 1, "b", 2);
        Set<String> keys = doc.keySet();
        assertThat(keys).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void values() {
        Document doc = Document.of("a", 1, "b", 2);
        Collection<Object> values = doc.values();
        assertThat(values).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void entrySet() {
        Document doc = Document.of("x", 10);
        assertThat(doc.entrySet()).hasSize(1);
    }

    @Test
    void insertionOrderPreserved() {
        Document doc = new Document();
        doc.put("first", 1);
        doc.put("second", 2);
        doc.put("third", 3);
        List<String> keys = new ArrayList<>(doc.keySet());
        assertThat(keys).containsExactly("first", "second", "third");
    }
}

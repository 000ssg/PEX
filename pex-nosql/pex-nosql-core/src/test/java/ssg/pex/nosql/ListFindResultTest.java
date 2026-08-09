package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class ListFindResultTest {

    private Document doc(String id, String name, int age) {
        Document d = new Document();
        d.put("_id", id);
        d.put("name", name);
        d.put("age", age);
        return d;
    }

    @Test
    void toListReturnsAll() {
        List<Document> docs = List.of(doc("1", "Alice", 30), doc("2", "Bob", 25));
        FindResult result = new ListFindResult(docs);
        assertThat(result.toList()).hasSize(2);
    }

    @Test
    void count() {
        FindResult result = new ListFindResult(List.of(doc("1", "Alice", 30)));
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    void sortAscending() {
        List<Document> docs = List.of(doc("1", "Bob", 25), doc("2", "Alice", 30));
        FindResult result = new ListFindResult(docs).sort("name", 1);
        List<Document> sorted = result.toList();
        assertThat(sorted.get(0).get("name")).isEqualTo("Alice");
        assertThat(sorted.get(1).get("name")).isEqualTo("Bob");
    }

    @Test
    void sortDescending() {
        List<Document> docs = List.of(doc("1", "Alice", 30), doc("2", "Bob", 25));
        FindResult result = new ListFindResult(docs).sort("name", -1);
        List<Document> sorted = result.toList();
        assertThat(sorted.get(0).get("name")).isEqualTo("Bob");
        assertThat(sorted.get(1).get("name")).isEqualTo("Alice");
    }

    @Test
    void sortNumeric() {
        List<Document> docs = List.of(doc("1", "Alice", 30), doc("2", "Bob", 5));
        FindResult result = new ListFindResult(docs).sort("age", 1);
        List<Document> sorted = result.toList();
        assertThat(sorted.get(0).get("name")).isEqualTo("Bob");
        assertThat(sorted.get(1).get("name")).isEqualTo("Alice");
    }

    @Test
    void limit() {
        List<Document> docs = List.of(doc("1", "Alice", 30), doc("2", "Bob", 25), doc("3", "Charlie", 35));
        FindResult result = new ListFindResult(docs).limit(2);
        assertThat(result.toList()).hasSize(2);
    }

    @Test
    void skip() {
        List<Document> docs = List.of(doc("1", "Alice", 30), doc("2", "Bob", 25), doc("3", "Charlie", 35));
        FindResult result = new ListFindResult(docs).skip(2);
        assertThat(result.toList()).hasSize(1);
        assertThat(result.toList().get(0).get("name")).isEqualTo("Charlie");
    }

    @Test
    void skipMoreThanAvailable() {
        List<Document> docs = List.of(doc("1", "Alice", 30));
        FindResult result = new ListFindResult(docs).skip(10);
        assertThat(result.toList()).isEmpty();
    }

    @Test
    void projectInclude() {
        List<Document> docs = List.of(doc("1", "Alice", 30));
        FindResult result = new ListFindResult(docs).project("name");
        Document d = result.toList().get(0);
        assertThat(d).containsKey("_id");
        assertThat(d).containsKey("name");
        assertThat(d).doesNotContainKey("age");
    }

    @Test
    void projectExclude() {
        List<Document> docs = List.of(doc("1", "Alice", 30));
        FindResult result = new ListFindResult(docs).projectExclude("age");
        Document d = result.toList().get(0);
        assertThat(d).containsKey("_id");
        assertThat(d).containsKey("name");
        assertThat(d).doesNotContainKey("age");
    }

    @Test
    void chainedOperations() {
        List<Document> docs = List.of(
                doc("1", "Alice", 30),
                doc("2", "Bob", 25),
                doc("3", "Charlie", 35)
        );
        FindResult result = new ListFindResult(docs)
                .sort("age", 1)
                .skip(1)
                .limit(1);
        List<Document> out = result.toList();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("name")).isEqualTo("Alice");
    }

    @Test
    void originalDataNotMutated() {
        List<Document> docs = new ArrayList<>(List.of(doc("1", "Alice", 30)));
        FindResult result = new ListFindResult(docs);
        result.toList().get(0).put("extra", true);
        assertThat(docs.get(0).containsKey("extra")).isFalse();
    }

    @Test
    void iterator() {
        List<Document> docs = List.of(doc("1", "Alice", 30), doc("2", "Bob", 25));
        FindResult result = new ListFindResult(docs);
        List<Document> collected = new ArrayList<>();
        for (Document d : result) collected.add(d);
        assertThat(collected).hasSize(2);
    }

    @Test
    void emptyList() {
        FindResult result = new ListFindResult(List.of());
        assertThat(result.toList()).isEmpty();
        assertThat(result.count()).isZero();
    }
}

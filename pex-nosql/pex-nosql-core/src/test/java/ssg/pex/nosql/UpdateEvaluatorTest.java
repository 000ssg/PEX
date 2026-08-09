package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class UpdateEvaluatorTest {

    // ── $set ────────────────────────────────────────────────────

    @Test
    void setSingleField() {
        Document update = Document.of("$set", Document.of("name", "Bob"));
        Document target = Document.of("_id", "1", "name", "Alice");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("name")).isEqualTo("Bob");
        assertThat(result.get("_id")).isEqualTo("1");
    }

    @Test
    void setNewField() {
        Document update = Document.of("$set", Document.of("city", "Helsinki"));
        Document target = Document.of("_id", "1", "name", "Alice");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("city")).isEqualTo("Helsinki");
        assertThat(result).hasSize(3);
    }

    @Test
    void setNestedField() {
        Document update = Document.of("$set", Document.of("address.city", "Helsinki"));
        Document target = Document.of("_id", "1");
        Document result = UpdateEvaluator.apply(update, target);
        Document address = (Document) result.get("address");
        assertThat(address.get("city")).isEqualTo("Helsinki");
    }

    @Test
    void setNestedFieldExistingDoc() {
        Document address = Document.of("city", "Tampere", "zip", "33100");
        Document update = Document.of("$set", Document.of("address.city", "Helsinki"));
        Document target = Document.of("_id", "1", "address", address);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(((Document) result.get("address")).get("zip")).isEqualTo("33100");
    }

    // ── $unset ──────────────────────────────────────────────────

    @Test
    void unsetField() {
        Document update = Document.of("$unset", Document.of("age", ""));
        Document target = Document.of("_id", "1", "name", "Alice", "age", 30);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.containsKey("age")).isFalse();
        assertThat(result.get("name")).isEqualTo("Alice");
    }

    @Test
    void unsetNestedField() {
        Document address = Document.of("city", "Helsinki");
        Document update = Document.of("$unset", Document.of("address.city", ""));
        Document target = Document.of("_id", "1", "address", address);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(((Document) result.get("address")).containsKey("city")).isFalse();
    }

    @Test
    void unsetMissingField() {
        Document update = Document.of("$unset", Document.of("missing", ""));
        Document target = Document.of("_id", "1", "name", "Alice");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result).hasSize(2);
    }

    // ── $inc ────────────────────────────────────────────────────

    @Test
    void incExisting() {
        Document update = Document.of("$inc", Document.of("count", 5));
        Document target = Document.of("_id", "1", "count", 10);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("count")).isEqualTo(15L);
    }

    @Test
    void incNewField() {
        Document update = Document.of("$inc", Document.of("count", 5));
        Document target = Document.of("_id", "1");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("count")).isEqualTo(5.0);
    }

    @Test
    void incWithDouble() {
        Document update = Document.of("$inc", Document.of("score", 1));
        Document target = Document.of("_id", "1", "score", 3.5);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("score")).isEqualTo(4.5);
    }

    @Test
    void incNegative() {
        Document update = Document.of("$inc", Document.of("count", -3));
        Document target = Document.of("_id", "1", "count", 10);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("count")).isEqualTo(7L);
    }

    // ── $push ───────────────────────────────────────────────────

    @Test
    void pushToNewArray() {
        Document update = Document.of("$push", Document.of("tags", "java"));
        Document target = Document.of("_id", "1", "name", "Alice");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("tags")).isEqualTo(List.of("java"));
    }

    @Test
    void pushToExistingArray() {
        Document update = Document.of("$push", Document.of("tags", "nosql"));
        Document target = Document.of("_id", "1", "tags", List.of("java", "sql"));
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("tags")).isEqualTo(List.of("java", "sql", "nosql"));
    }

    // ── $pull ───────────────────────────────────────────────────

    @Test
    void pullFromArray() {
        Document update = Document.of("$pull", Document.of("tags", "java"));
        Document target = Document.of("_id", "1", "tags", List.of("java", "sql", "nosql"));
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("tags")).isEqualTo(List.of("sql", "nosql"));
    }

    @Test
    void pullNonExistent() {
        Document update = Document.of("$pull", Document.of("tags", "rust"));
        Document target = Document.of("_id", "1", "tags", List.of("java", "sql"));
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("tags")).isEqualTo(List.of("java", "sql"));
    }

    // ── $addToSet ───────────────────────────────────────────────

    @Test
    void addToSetNew() {
        Document update = Document.of("$addToSet", Document.of("tags", "nosql"));
        Document target = Document.of("_id", "1", "tags", List.of("java", "sql"));
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("tags")).isEqualTo(List.of("java", "sql", "nosql"));
    }

    @Test
    void addToSetDuplicate() {
        Document update = Document.of("$addToSet", Document.of("tags", "java"));
        Document target = Document.of("_id", "1", "tags", List.of("java", "sql"));
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("tags")).isEqualTo(List.of("java", "sql"));
    }

    @Test
    void addToSetNewField() {
        Document update = Document.of("$addToSet", Document.of("tags", "first"));
        Document target = Document.of("_id", "1");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("tags")).isEqualTo(List.of("first"));
    }

    // ── $rename ─────────────────────────────────────────────────

    @Test
    void renameField() {
        Document update = Document.of("$rename", Document.of("oldName", "newName"));
        Document target = Document.of("_id", "1", "oldName", "Alice");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.containsKey("oldName")).isFalse();
        assertThat(result.get("newName")).isEqualTo("Alice");
    }

    @Test
    void renameNonExistent() {
        Document update = Document.of("$rename", Document.of("missing", "newName"));
        Document target = Document.of("_id", "1", "name", "Alice");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.containsKey("newName")).isFalse();
    }

    // ── $mul ────────────────────────────────────────────────────

    @Test
    void mulIntegral() {
        Document update = Document.of("$mul", Document.of("count", 3));
        Document target = Document.of("_id", "1", "count", 10);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("count")).isEqualTo(30L);
    }

    @Test
    void mulDouble() {
        Document update = Document.of("$mul", Document.of("score", 2));
        Document target = Document.of("_id", "1", "score", 3.5);
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("score")).isEqualTo(7.0);
    }

    @Test
    void mulNull() {
        Document update = Document.of("$mul", Document.of("score", 2));
        Document target = Document.of("_id", "1");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.containsKey("score")).isFalse();
    }

    // ── Plain $set (no operator) ────────────────────────────────

    @Test
    void plainSet() {
        Document update = Document.of("name", "Bob", "city", "Helsinki");
        Document target = Document.of("_id", "1", "name", "Alice");
        Document result = UpdateEvaluator.apply(update, target);
        assertThat(result.get("name")).isEqualTo("Bob");
        assertThat(result.get("city")).isEqualTo("Helsinki");
    }

    // ── Original not mutated ────────────────────────────────────

    @Test
    void originalNotMutated() {
        Document update = Document.of("$set", Document.of("name", "Bob"));
        Document target = Document.of("_id", "1", "name", "Alice");
        UpdateEvaluator.apply(update, target);
        assertThat(target.get("name")).isEqualTo("Alice");
    }

    // ── Error cases ─────────────────────────────────────────────

    @Test
    void unknownOperator() {
        Document update = Document.of("$unknown", Document.of("name", "Bob"));
        Document target = Document.of("_id", "1");
        assertThatThrownBy(() -> UpdateEvaluator.apply(update, target))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Unknown update operator");
    }

    @Test
    void incNonNumber() {
        Document update = Document.of("$inc", Document.of("count", "not a number"));
        Document target = Document.of("_id", "1");
        assertThatThrownBy(() -> UpdateEvaluator.apply(update, target))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Expected number");
    }
}

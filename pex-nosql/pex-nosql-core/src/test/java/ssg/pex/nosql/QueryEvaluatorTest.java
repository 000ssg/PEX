package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class QueryEvaluatorTest {

    // ── Plain equality ──────────────────────────────────────────

    @Test
    void emptyFilterMatchesAll() {
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(new Document(), stored)).isTrue();
    }

    @Test
    void nullFilterMatchesAll() {
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(null, stored)).isTrue();
    }

    @Test
    void plainEqualityMatch() {
        Document filter = Document.of("name", "Alice");
        Document stored = Document.of("name", "Alice", "age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void plainEqualityMismatch() {
        Document filter = Document.of("name", "Bob");
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void multiFieldEqualityAllMatch() {
        Document filter = Document.of("name", "Alice", "age", 30);
        Document stored = Document.of("name", "Alice", "age", 30, "city", "Helsinki");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void multiFieldEqualityOneMismatch() {
        Document filter = Document.of("name", "Alice", "age", 25);
        Document stored = Document.of("name", "Alice", "age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void numberEqualityIntVsLong() {
        Document filter = Document.of("count", 10);
        Document stored = Document.of("count", 10L);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void numberEqualityIntVsDouble() {
        Document filter = Document.of("score", 5);
        Document stored = Document.of("score", 5.0);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    // ── Comparison operators ────────────────────────────────────

    @Test
    void eqOperator() {
        Document filter = Document.of("age", Document.of("$eq", 30));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void neOperator() {
        Document filter = Document.of("age", Document.of("$ne", 25));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void gtOperator() {
        Document filter = Document.of("age", Document.of("$gt", 25));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void gtOperatorNotMatch() {
        Document filter = Document.of("age", Document.of("$gt", 35));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void gteOperator() {
        Document filter = Document.of("age", Document.of("$gte", 30));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void ltOperator() {
        Document filter = Document.of("age", Document.of("$lt", 35));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void lteOperator() {
        Document filter = Document.of("age", Document.of("$lte", 30));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void comparisonWithNull() {
        Document filter = Document.of("age", Document.of("$gt", 25));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    // ── Membership operators ────────────────────────────────────

    @Test
    void inOperator() {
        Document filter = Document.of("status", Document.of("$in", List.of("active", "pending")));
        Document stored = Document.of("status", "active");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void inOperatorNotMatch() {
        Document filter = Document.of("status", Document.of("$in", List.of("active")));
        Document stored = Document.of("status", "deleted");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void ninOperator() {
        Document filter = Document.of("status", Document.of("$nin", List.of("deleted", "banned")));
        Document stored = Document.of("status", "active");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void ninOperatorNotMatch() {
        Document filter = Document.of("status", Document.of("$nin", List.of("active", "pending")));
        Document stored = Document.of("status", "active");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    // ── Existence operator ──────────────────────────────────────

    @Test
    void existsTrue() {
        Document filter = Document.of("age", Document.of("$exists", true));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void existsFalse() {
        Document filter = Document.of("age", Document.of("$exists", false));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void existsTrueFieldMissing() {
        Document filter = Document.of("age", Document.of("$exists", true));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    // ── Regex operator ──────────────────────────────────────────

    @Test
    void regexMatch() {
        Document filter = Document.of("name", Document.of("$regex", "Al.*"));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void regexNotMatch() {
        Document filter = Document.of("name", Document.of("$regex", "Bob.*"));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void regexNullValue() {
        Document filter = Document.of("name", Document.of("$regex", ".*"));
        Document stored = Document.of("age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    // ── Logical operators ───────────────────────────────────────

    @Test
    void andOperator() {
        Document filter = new Document();
        filter.put("$and", List.of(
                Document.of("name", "Alice"),
                Document.of("age", 30)
        ));
        Document stored = Document.of("name", "Alice", "age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void andOperatorOneFails() {
        Document filter = new Document();
        filter.put("$and", List.of(
                Document.of("name", "Alice"),
                Document.of("age", 25)
        ));
        Document stored = Document.of("name", "Alice", "age", 30);
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void orOperator() {
        Document filter = new Document();
        filter.put("$or", List.of(
                Document.of("name", "Alice"),
                Document.of("name", "Bob")
        ));
        Document stored = Document.of("name", "Bob");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void orOperatorAllFail() {
        Document filter = new Document();
        filter.put("$or", List.of(
                Document.of("name", "Alice"),
                Document.of("name", "Bob")
        ));
        Document stored = Document.of("name", "Charlie");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void notOperator() {
        Document filter = new Document();
        filter.put("$not", Document.of("name", "Bob"));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void notOperatorMatched() {
        Document filter = new Document();
        filter.put("$not", Document.of("name", "Alice"));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void norOperator() {
        Document filter = new Document();
        filter.put("$nor", List.of(
                Document.of("name", "Alice"),
                Document.of("name", "Bob")
        ));
        Document stored = Document.of("name", "Charlie");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void norOperatorOneMatches() {
        Document filter = new Document();
        filter.put("$nor", List.of(
                Document.of("name", "Alice"),
                Document.of("name", "Bob")
        ));
        Document stored = Document.of("name", "Alice");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    // ── Dot notation ────────────────────────────────────────────

    @Test
    void dotNotation() {
        Document address = Document.of("city", "Helsinki");
        Document stored = Document.of("name", "Alice", "address", address);
        Document filter = Document.of("address.city", "Helsinki");
        assertThat(QueryEvaluator.matches(filter, stored)).isTrue();
    }

    @Test
    void dotNotationMismatch() {
        Document address = Document.of("city", "Tampere");
        Document stored = Document.of("name", "Alice", "address", address);
        Document filter = Document.of("address.city", "Helsinki");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void dotNotationMissing() {
        Document stored = Document.of("name", "Alice");
        Document filter = Document.of("address.city", "Helsinki");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    @Test
    void dotNotationIntermediateNotMap() {
        Document stored = Document.of("address", "plain string");
        Document filter = Document.of("address.city", "Helsinki");
        assertThat(QueryEvaluator.matches(filter, stored)).isFalse();
    }

    // ── resolveField ────────────────────────────────────────────

    @Test
    void resolveFieldSimple() {
        Document doc = Document.of("name", "Alice");
        assertThat(QueryEvaluator.resolveField("name", doc)).isEqualTo("Alice");
    }

    @Test
    void resolveFieldMissing() {
        Document doc = Document.of("name", "Alice");
        assertThat(QueryEvaluator.resolveField("missing", doc)).isNull();
    }

    @Test
    void resolveFieldNested() {
        Document nested = Document.of("city", "Helsinki");
        Document doc = Document.of("address", nested);
        assertThat(QueryEvaluator.resolveField("address.city", doc)).isEqualTo("Helsinki");
    }

    @Test
    void unknownOperatorThrows() {
        Document filter = Document.of("age", Document.of("$unknown", 30));
        Document stored = Document.of("age", 30);
        assertThatThrownBy(() -> QueryEvaluator.matches(filter, stored))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Unknown operator");
    }

    @Test
    void unknownLogicalOperatorThrows() {
        Document filter = new Document();
        filter.put("$unknown", List.of());
        Document stored = Document.of("age", 30);
        assertThatThrownBy(() -> QueryEvaluator.matches(filter, stored))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("Unknown logical operator");
    }
}

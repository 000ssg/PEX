package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ProjectionTest {

    @Test
    void none() {
        Projection p = Projection.none();
        assertThat(p.mode()).isEqualTo(Projection.Mode.NONE);
        assertThat(p.fields()).isEmpty();
    }

    @Test
    void includeMode() {
        Projection p = Projection.include("name", "age");
        assertThat(p.mode()).isEqualTo(Projection.Mode.INCLUDE);
        assertThat(p.fields()).containsExactlyInAnyOrder("name", "age");
    }

    @Test
    void excludeMode() {
        Projection p = Projection.exclude("secret");
        assertThat(p.mode()).isEqualTo(Projection.Mode.EXCLUDE);
        assertThat(p.fields()).containsExactly("secret");
    }

    @Test
    void noneApplyReturnsCopy() {
        Document doc = Document.of("_id", "1", "name", "Alice", "age", 30);
        Projection p = Projection.none();
        Document result = p.apply(doc);
        assertThat(result).isNotSameAs(doc);
        assertThat(result).containsEntry("name", "Alice");
        assertThat(result).containsEntry("age", 30);
    }

    @Test
    void includeApply() {
        Document doc = Document.of("_id", "1", "name", "Alice", "age", 30, "city", "Helsinki");
        Projection p = Projection.include("name");
        Document result = p.apply(doc);
        assertThat(result).containsEntry("_id", "1");
        assertThat(result).containsEntry("name", "Alice");
        assertThat(result).doesNotContainKey("age");
        assertThat(result).doesNotContainKey("city");
    }

    @Test
    void includeApplyMissingField() {
        Document doc = Document.of("_id", "1", "name", "Alice");
        Projection p = Projection.include("name", "missing");
        Document result = p.apply(doc);
        assertThat(result).containsEntry("name", "Alice");
        assertThat(result).doesNotContainKey("missing");
    }

    @Test
    void excludeApply() {
        Document doc = Document.of("_id", "1", "name", "Alice", "secret", "hidden");
        Projection p = Projection.exclude("secret");
        Document result = p.apply(doc);
        assertThat(result).containsEntry("_id", "1");
        assertThat(result).containsEntry("name", "Alice");
        assertThat(result).doesNotContainKey("secret");
    }

    @Test
    void excludeMultipleFields() {
        Document doc = Document.of("_id", "1", "name", "Alice", "age", 30, "secret", "hidden");
        Projection p = Projection.exclude("age", "secret");
        Document result = p.apply(doc);
        assertThat(result).containsEntry("_id", "1");
        assertThat(result).containsEntry("name", "Alice");
        assertThat(result).doesNotContainKey("age");
        assertThat(result).doesNotContainKey("secret");
    }
}

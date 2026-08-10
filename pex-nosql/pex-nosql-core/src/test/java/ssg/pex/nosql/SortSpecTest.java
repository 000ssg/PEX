package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class SortSpecTest {

    @Test
    void asc() {
        SortSpec spec = SortSpec.asc("name");
        assertThat(spec.field()).isEqualTo("name");
        assertThat(spec.direction()).isEqualTo(1);
        assertThat(spec.isAscending()).isTrue();
    }

    @Test
    void desc() {
        SortSpec spec = SortSpec.desc("name");
        assertThat(spec.field()).isEqualTo("name");
        assertThat(spec.direction()).isEqualTo(-1);
        assertThat(spec.isAscending()).isFalse();
    }

    @Test
    void constructor() {
        SortSpec spec = new SortSpec("age", 1);
        assertThat(spec.field()).isEqualTo("age");
        assertThat(spec.direction()).isEqualTo(1);
    }

    @Test
    void invalidDirection() {
        assertThatThrownBy(() -> new SortSpec("name", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be 1");
    }

    @Test
    void invalidDirectionTwo() {
        assertThatThrownBy(() -> new SortSpec("name", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be 1");
    }

    @Test
    void recordEquality() {
        SortSpec a = SortSpec.asc("name");
        SortSpec b = SortSpec.asc("name");
        assertThat(a).isEqualTo(b);
    }
}

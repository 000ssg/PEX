package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class IndexEntryTest {

    @Test
    void unique() {
        IndexEntry idx = IndexEntry.unique("email");
        assertThat(idx.field()).isEqualTo("email");
        assertThat(idx.unique()).isTrue();
    }

    @Test
    void nonUnique() {
        IndexEntry idx = IndexEntry.nonUnique("name");
        assertThat(idx.field()).isEqualTo("name");
        assertThat(idx.unique()).isFalse();
    }

    @Test
    void constructor() {
        IndexEntry idx = new IndexEntry("age", true);
        assertThat(idx.field()).isEqualTo("age");
        assertThat(idx.unique()).isTrue();
    }

    @Test
    void recordEquality() {
        IndexEntry a = IndexEntry.unique("email");
        IndexEntry b = IndexEntry.unique("email");
        assertThat(a).isEqualTo(b);
    }
}

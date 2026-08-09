package ssg.pex.scope;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ShadowRecordTest {

    @Test
    void fields() {
        ScopePath defining = ScopePath.of("global", "fn1");
        ScopePath shadowed = ScopePath.of("global");
        ShadowRecord rec = new ShadowRecord("x", defining, shadowed, 1, 2);
        assertThat(rec.variableName()).isEqualTo("x");
        assertThat(rec.definingScope()).isSameAs(defining);
        assertThat(rec.shadowedScope()).isSameAs(shadowed);
        assertThat(rec.previousValue()).isEqualTo(1);
        assertThat(rec.newValue()).isEqualTo(2);
    }
}

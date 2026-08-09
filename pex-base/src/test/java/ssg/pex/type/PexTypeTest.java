package ssg.pex.type;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class PexTypeTest {

    @Test
    void allValues() {
        PexType[] values = PexType.values();
        assertThat(values).hasSize(11);
    }

    @Test
    void displayName() {
        assertThat(PexType.INT.displayName()).isEqualTo("int");
        assertThat(PexType.STRING.displayName()).isEqualTo("string");
        assertThat(PexType.BOOL.displayName()).isEqualTo("bool");
        assertThat(PexType.VOID.displayName()).isEqualTo("void");
    }

    @Test
    void javaType() {
        assertThat(PexType.INT.javaType()).isEqualTo(Integer.class);
        assertThat(PexType.STRING.javaType()).isEqualTo(String.class);
        assertThat(PexType.BOOL.javaType()).isEqualTo(Boolean.class);
        assertThat(PexType.ARRAY.javaType()).isNull();
        assertThat(PexType.MAP.javaType()).isNull();
    }

    @Test
    void isNumeric() {
        assertThat(PexType.INT.isNumeric()).isTrue();
        assertThat(PexType.LONG.isNumeric()).isTrue();
        assertThat(PexType.FLOAT.isNumeric()).isTrue();
        assertThat(PexType.DOUBLE.isNumeric()).isTrue();
        assertThat(PexType.STRING.isNumeric()).isFalse();
        assertThat(PexType.BOOL.isNumeric()).isFalse();
        assertThat(PexType.NULL.isNumeric()).isFalse();
    }

    @Test
    void isInteger() {
        assertThat(PexType.INT.isInteger()).isTrue();
        assertThat(PexType.LONG.isInteger()).isTrue();
        assertThat(PexType.FLOAT.isInteger()).isFalse();
        assertThat(PexType.DOUBLE.isInteger()).isFalse();
        assertThat(PexType.STRING.isInteger()).isFalse();
    }

    @Test
    void isFloatingPoint() {
        assertThat(PexType.FLOAT.isFloatingPoint()).isTrue();
        assertThat(PexType.DOUBLE.isFloatingPoint()).isTrue();
        assertThat(PexType.INT.isFloatingPoint()).isFalse();
        assertThat(PexType.STRING.isFloatingPoint()).isFalse();
    }

    @Test
    void valueOf() {
        assertThat(PexType.valueOf("INT")).isEqualTo(PexType.INT);
    }
}

package ssg.pex.type;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class TypeDescriptorTest {

    @Test
    void ofInt() {
        TypeDescriptor td = TypeDescriptor.INT;
        assertThat(td.type()).isEqualTo(PexType.INT);
        assertThat(td.name()).isEqualTo("int");
        assertThat(td.javaType()).isEqualTo(Integer.class);
    }

    @Test
    void ofFactory() {
        TypeDescriptor td = TypeDescriptor.of(PexType.STRING);
        assertThat(td.type()).isEqualTo(PexType.STRING);
        assertThat(td.name()).isEqualTo("string");
        assertThat(td.javaType()).isEqualTo(String.class);
    }

    @Test
    void custom() {
        TypeDescriptor td = TypeDescriptor.custom("MyType", Object.class);
        assertThat(td.name()).isEqualTo("MyType");
        assertThat(td.javaType()).isEqualTo(Object.class);
        assertThat(td.type()).isNull();
    }

    @Test
    void staticConstants() {
        assertThat(TypeDescriptor.INT.type()).isEqualTo(PexType.INT);
        assertThat(TypeDescriptor.LONG.type()).isEqualTo(PexType.LONG);
        assertThat(TypeDescriptor.FLOAT.type()).isEqualTo(PexType.FLOAT);
        assertThat(TypeDescriptor.DOUBLE.type()).isEqualTo(PexType.DOUBLE);
        assertThat(TypeDescriptor.STRING.type()).isEqualTo(PexType.STRING);
        assertThat(TypeDescriptor.BOOL.type()).isEqualTo(PexType.BOOL);
        assertThat(TypeDescriptor.NULL.type()).isEqualTo(PexType.NULL);
        assertThat(TypeDescriptor.VOID.type()).isEqualTo(PexType.VOID);
    }

    @Test
    void equalsAndHashCode() {
        TypeDescriptor a = TypeDescriptor.of(PexType.INT);
        TypeDescriptor b = TypeDescriptor.INT;
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void ofNullThrows() {
        assertThatThrownBy(() -> TypeDescriptor.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customNullNameThrows() {
        assertThatThrownBy(() -> TypeDescriptor.custom(null, Object.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customNullClassThrows() {
        assertThatThrownBy(() -> TypeDescriptor.custom("name", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

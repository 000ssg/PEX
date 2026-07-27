package ssg.pex.type;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSystemTest {

    // --- PexType enum properties ---

    @Test
    void pexType_isNumeric() {
        assertThat(PexType.INT.isNumeric()).isTrue();
        assertThat(PexType.LONG.isNumeric()).isTrue();
        assertThat(PexType.FLOAT.isNumeric()).isTrue();
        assertThat(PexType.DOUBLE.isNumeric()).isTrue();
        assertThat(PexType.STRING.isNumeric()).isFalse();
        assertThat(PexType.BOOL.isNumeric()).isFalse();
        assertThat(PexType.NULL.isNumeric()).isFalse();
    }

    @Test
    void pexType_isInteger() {
        assertThat(PexType.INT.isInteger()).isTrue();
        assertThat(PexType.LONG.isInteger()).isTrue();
        assertThat(PexType.FLOAT.isInteger()).isFalse();
        assertThat(PexType.DOUBLE.isInteger()).isFalse();
    }

    @Test
    void pexType_isFloatingPoint() {
        assertThat(PexType.FLOAT.isFloatingPoint()).isTrue();
        assertThat(PexType.DOUBLE.isFloatingPoint()).isTrue();
        assertThat(PexType.INT.isFloatingPoint()).isFalse();
        assertThat(PexType.LONG.isFloatingPoint()).isFalse();
    }

    @Test
    void pexType_displayName() {
        assertThat(PexType.INT.displayName()).isEqualTo("int");
        assertThat(PexType.STRING.displayName()).isEqualTo("string");
    }

    // --- TypeDescriptor ---

    @Test
    void typeDescriptor_of_eachType() {
        var intDesc = TypeDescriptor.of(PexType.INT);
        assertThat(intDesc.type()).isEqualTo(PexType.INT);
        assertThat(intDesc.name()).isEqualTo("int");
        assertThat(intDesc.javaType()).isEqualTo(Integer.class);

        var strDesc = TypeDescriptor.of(PexType.STRING);
        assertThat(strDesc.type()).isEqualTo(PexType.STRING);
        assertThat(strDesc.javaType()).isEqualTo(String.class);
    }

    @Test
    void typeDescriptor_constants() {
        assertThat(TypeDescriptor.INT.type()).isEqualTo(PexType.INT);
        assertThat(TypeDescriptor.DOUBLE.type()).isEqualTo(PexType.DOUBLE);
        assertThat(TypeDescriptor.BOOL.type()).isEqualTo(PexType.BOOL);
        assertThat(TypeDescriptor.NULL.type()).isEqualTo(PexType.NULL);
    }

    @Test
    void typeDescriptor_custom() {
        var desc = TypeDescriptor.custom("BigDecimal", java.math.BigDecimal.class);
        assertThat(desc.type()).isNull();
        assertThat(desc.name()).isEqualTo("BigDecimal");
        assertThat(desc.javaType()).isEqualTo(java.math.BigDecimal.class);
    }

    @Test
    void typeDescriptor_of_nullTypeThrows() {
        assertThatThrownBy(() -> TypeDescriptor.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typeDescriptor_custom_emptyNameThrows() {
        assertThatThrownBy(() -> TypeDescriptor.custom("", String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- TypeCoercion.coerce ---

    @Test
    void coerce_intToLong() {
        var result = TypeCoercion.coerce(42, PexType.LONG);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(42L);
    }

    @Test
    void coerce_intToFloat() {
        var result = TypeCoercion.coerce(5, PexType.FLOAT);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(5.0f);
    }

    @Test
    void coerce_intToDouble() {
        var result = TypeCoercion.coerce(7, PexType.DOUBLE);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(7.0);
    }

    @Test
    void coerce_longToDouble() {
        var result = TypeCoercion.coerce(100L, PexType.DOUBLE);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(100.0);
    }

    @Test
    void coerce_floatToDouble() {
        var result = TypeCoercion.coerce(1.5f, PexType.DOUBLE);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo((double) 1.5f);
    }

    @Test
    void coerce_stringToInt() {
        var result = TypeCoercion.coerce("123", PexType.INT);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(123);
    }

    @Test
    void coerce_stringToFloat() {
        var result = TypeCoercion.coerce("2.5", PexType.FLOAT);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(2.5f);
    }

    @Test
    void coerce_stringToInt_invalidFails() {
        var result = TypeCoercion.coerce("abc", PexType.INT);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("TYPE_COERCION");
    }

    @Test
    void coerce_boolToString() {
        var result = TypeCoercion.coerce(true, PexType.STRING);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo("true");
    }

    @Test
    void coerce_nullToInt() {
        var result = TypeCoercion.coerce(null, PexType.INT);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(0);
    }

    @Test
    void coerce_nullToString() {
        var result = TypeCoercion.coerce(null, PexType.STRING);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo("null");
    }

    @Test
    void coerce_nullToBool() {
        var result = TypeCoercion.coerce(null, PexType.BOOL);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(false);
    }

    @Test
    void coerce_sameTypeReturnsOriginal() {
        var result = TypeCoercion.coerce(42, PexType.INT);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(42);
    }

    // --- TypeCoercion.widenNumeric ---

    @Test
    void widenNumeric_intAndLong() {
        assertThat(TypeCoercion.widenNumeric(PexType.INT, PexType.LONG)).isEqualTo(PexType.LONG);
    }

    @Test
    void widenNumeric_intAndFloat() {
        assertThat(TypeCoercion.widenNumeric(PexType.INT, PexType.FLOAT)).isEqualTo(PexType.FLOAT);
    }

    @Test
    void widenNumeric_longAndDouble() {
        assertThat(TypeCoercion.widenNumeric(PexType.LONG, PexType.DOUBLE)).isEqualTo(PexType.DOUBLE);
    }

    @Test
    void widenNumeric_floatAndDouble() {
        assertThat(TypeCoercion.widenNumeric(PexType.FLOAT, PexType.DOUBLE)).isEqualTo(PexType.DOUBLE);
    }

    @Test
    void widenNumeric_sameType() {
        assertThat(TypeCoercion.widenNumeric(PexType.INT, PexType.INT)).isEqualTo(PexType.INT);
    }

    @Test
    void widenNumeric_nonNumericThrows() {
        assertThatThrownBy(() -> TypeCoercion.widenNumeric(PexType.STRING, PexType.INT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- TypeCoercion.canImplicitlyCoerce ---

    @Test
    void canImplicitlyCoerce_sameType() {
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.INT, PexType.INT)).isTrue();
    }

    @Test
    void canImplicitlyCoerce_intToDouble() {
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.INT, PexType.DOUBLE)).isTrue();
    }

    @Test
    void canImplicitlyCoerce_doubleToInt_false() {
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.DOUBLE, PexType.INT)).isFalse();
    }

    @Test
    void canImplicitlyCoerce_anyToString() {
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.INT, PexType.STRING)).isTrue();
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.BOOL, PexType.STRING)).isTrue();
    }

    @Test
    void canImplicitlyCoerce_nullToAnything() {
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.NULL, PexType.INT)).isTrue();
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.NULL, PexType.STRING)).isTrue();
    }

    @Test
    void canImplicitlyCoerce_stringToInt_false() {
        assertThat(TypeCoercion.canImplicitlyCoerce(PexType.STRING, PexType.INT)).isFalse();
    }

    // --- TypeCoercion.inferType ---

    @Test
    void inferType_integer() {
        assertThat(TypeCoercion.inferType(42)).isEqualTo(PexType.INT);
    }

    @Test
    void inferType_long() {
        assertThat(TypeCoercion.inferType(42L)).isEqualTo(PexType.LONG);
    }

    @Test
    void inferType_float() {
        assertThat(TypeCoercion.inferType(1.0f)).isEqualTo(PexType.FLOAT);
    }

    @Test
    void inferType_double() {
        assertThat(TypeCoercion.inferType(1.0)).isEqualTo(PexType.DOUBLE);
    }

    @Test
    void inferType_string() {
        assertThat(TypeCoercion.inferType("hello")).isEqualTo(PexType.STRING);
    }

    @Test
    void inferType_boolean() {
        assertThat(TypeCoercion.inferType(true)).isEqualTo(PexType.BOOL);
    }

    @Test
    void inferType_null() {
        assertThat(TypeCoercion.inferType(null)).isEqualTo(PexType.NULL);
    }

    @Test
    void inferType_list() {
        assertThat(TypeCoercion.inferType(List.of(1, 2))).isEqualTo(PexType.ARRAY);
    }

    @Test
    void inferType_map() {
        assertThat(TypeCoercion.inferType(Map.of("a", 1))).isEqualTo(PexType.MAP);
    }

    @Test
    void inferType_unknownObject() {
        assertThat(TypeCoercion.inferType(new Object())).isEqualTo(PexType.VOID);
    }
}

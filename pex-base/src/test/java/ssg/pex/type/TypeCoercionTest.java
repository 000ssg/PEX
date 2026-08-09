package ssg.pex.type;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.result.Result;

class TypeCoercionTest {

    @Test
    void coerceIntToDouble() {
        Result<Object> r = TypeCoercion.coerce(42, PexType.DOUBLE);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(42.0);
    }

    @Test
    void coerceStringToDouble() {
        Result<Object> r = TypeCoercion.coerce("3.14", PexType.DOUBLE);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(3.14);
    }

    @Test
    void coerceInvalidStringToDouble() {
        Result<Object> r = TypeCoercion.coerce("not-a-number", PexType.DOUBLE);
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    void coerceIntToBool() {
        Result<Object> r = TypeCoercion.coerce(1, PexType.BOOL);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(true);
    }

    @Test
    void coerceZeroToBool() {
        Result<Object> r = TypeCoercion.coerce(0, PexType.BOOL);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(false);
    }

    @Test
    void coerceStringToBool() {
        Result<Object> r = TypeCoercion.coerce("hello", PexType.BOOL);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(true);
    }

    @Test
    void coerceEmptyStringToBool() {
        Result<Object> r = TypeCoercion.coerce("", PexType.BOOL);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(false);
    }

    @Test
    void coerceNullToBool() {
        Result<Object> r = TypeCoercion.coerce(null, PexType.BOOL);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(false);
    }

    @Test
    void coerceBoolToInt() {
        Result<Object> r = TypeCoercion.coerce(true, PexType.INT);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(1);
    }

    @Test
    void coerceSameType() {
        Result<Object> r = TypeCoercion.coerce(42, PexType.INT);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(42);
    }

    @Test
    void coerceNullToNull() {
        Result<Object> r = TypeCoercion.coerce(null, PexType.NULL);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isNull();
    }

    @Test
    void coerceNullToString() {
        Result<Object> r = TypeCoercion.coerce(null, PexType.STRING);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo("null");
    }

    @Test
    void coerceIntToString() {
        Result<Object> r = TypeCoercion.coerce(42, PexType.STRING);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo("42");
    }

    @Test
    void coerceNumericWidening() {
        Result<Object> r = TypeCoercion.coerce(42, PexType.LONG);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.value()).isEqualTo(42L);
    }
}

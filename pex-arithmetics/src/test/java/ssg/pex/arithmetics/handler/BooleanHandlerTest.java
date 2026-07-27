package ssg.pex.arithmetics.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssg.pex.arithmetics.type.ArithmeticTypeCoercion;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BooleanHandler")
class BooleanHandlerTest {

    // Tests use ArithmeticTypeCoercion directly for the core logic,
    // since BooleanHandler delegates to it after evaluating operands.

    @Test
    @DisplayName("true coercion")
    void trueCoercion() {
        var r = ArithmeticTypeCoercion.coerceToBoolean(true);
        assertThat(r.value()).isTrue();
    }

    @Test
    @DisplayName("false coercion")
    void falseCoercion() {
        var r = ArithmeticTypeCoercion.coerceToBoolean(false);
        assertThat(r.value()).isFalse();
    }

    @Test
    @DisplayName("null is falsy")
    void nullIsFalsy() {
        var r = ArithmeticTypeCoercion.coerceToBoolean(null);
        assertThat(r.value()).isFalse();
    }

    @Test
    @DisplayName("zero is falsy")
    void zeroIsFalsy() {
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(0).value()).isFalse();
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(0L).value()).isFalse();
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(0.0f).value()).isFalse();
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(0.0).value()).isFalse();
    }

    @Test
    @DisplayName("non-zero is truthy")
    void nonZeroIsTruthy() {
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(1).value()).isTrue();
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(-1L).value()).isTrue();
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(0.1f).value()).isTrue();
        assertThat(ArithmeticTypeCoercion.coerceToBoolean(3.14).value()).isTrue();
    }

    @Test
    @DisplayName("empty string is falsy")
    void emptyStringIsFalsy() {
        assertThat(ArithmeticTypeCoercion.coerceToBoolean("").value()).isFalse();
    }

    @Test
    @DisplayName("non-empty string is truthy")
    void nonEmptyStringIsTruthy() {
        assertThat(ArithmeticTypeCoercion.coerceToBoolean("hello").value()).isTrue();
        assertThat(ArithmeticTypeCoercion.coerceToBoolean("false").value()).isTrue();
    }

    @Test
    @DisplayName("NOT true is false")
    void notTrue() {
        // Simulate: !true
        var r = ArithmeticTypeCoercion.coerceToBoolean(true);
        assertThat(!r.value()).isFalse();
    }

    @Test
    @DisplayName("NOT false is true")
    void notFalse() {
        var r = ArithmeticTypeCoercion.coerceToBoolean(false);
        assertThat(!r.value()).isTrue();
    }
}

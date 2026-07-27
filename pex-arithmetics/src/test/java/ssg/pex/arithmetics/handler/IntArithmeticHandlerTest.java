package ssg.pex.arithmetics.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.Operator;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IntArithmeticHandler")
class IntArithmeticHandlerTest {

    @Nested
    @DisplayName("int + int")
    class Addition {
        @Test void positives() {
            var r = IntArithmeticHandler.computeInt(3, 4, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isEqualTo(7);
        }

        @Test void negativeResult() {
            var r = IntArithmeticHandler.computeInt(-10, 3, Operator.PLUS);
            assertThat(r.value()).isEqualTo(-7);
        }

        @Test void overflowPromotesToLong() {
            var r = IntArithmeticHandler.computeInt(Integer.MAX_VALUE, 1, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
            assertThat(r.value()).isEqualTo((long) Integer.MAX_VALUE + 1);
        }

        @Test void underflowPromotesToLong() {
            var r = IntArithmeticHandler.computeInt(Integer.MIN_VALUE, -1, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
            assertThat(r.value()).isEqualTo((long) Integer.MIN_VALUE - 1);
        }
    }

    @Nested
    @DisplayName("int - int")
    class Subtraction {
        @Test void basic() {
            var r = IntArithmeticHandler.computeInt(10, 3, Operator.MINUS);
            assertThat(r.value()).isEqualTo(7);
        }

        @Test void negativeResult() {
            var r = IntArithmeticHandler.computeInt(3, 10, Operator.MINUS);
            assertThat(r.value()).isEqualTo(-7);
        }

        @Test void overflowPromotesToLong() {
            var r = IntArithmeticHandler.computeInt(Integer.MIN_VALUE, 1, Operator.MINUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
        }
    }

    @Nested
    @DisplayName("int * int")
    class Multiplication {
        @Test void basic() {
            var r = IntArithmeticHandler.computeInt(6, 7, Operator.MULTIPLY);
            assertThat(r.value()).isEqualTo(42);
        }

        @Test void byZero() {
            var r = IntArithmeticHandler.computeInt(42, 0, Operator.MULTIPLY);
            assertThat(r.value()).isEqualTo(0);
        }

        @Test void overflowPromotesToLong() {
            var r = IntArithmeticHandler.computeInt(Integer.MAX_VALUE, 2, Operator.MULTIPLY);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
            assertThat(r.value()).isEqualTo((long) Integer.MAX_VALUE * 2);
        }
    }

    @Nested
    @DisplayName("int / int")
    class Division {
        @Test void exact() {
            var r = IntArithmeticHandler.computeInt(10, 2, Operator.DIVIDE);
            assertThat(r.value()).isEqualTo(5);
        }

        @Test void truncates() {
            var r = IntArithmeticHandler.computeInt(7, 2, Operator.DIVIDE);
            assertThat(r.value()).isEqualTo(3);
        }

        @Test void byZeroReturnsError() {
            var r = IntArithmeticHandler.computeInt(10, 0, Operator.DIVIDE);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("DIVISION_BY_ZERO");
        }
    }

    @Nested
    @DisplayName("int % int")
    class Modulo {
        @Test void basic() {
            var r = IntArithmeticHandler.computeInt(10, 3, Operator.MODULO);
            assertThat(r.value()).isEqualTo(1);
        }

        @Test void byZeroReturnsError() {
            var r = IntArithmeticHandler.computeInt(10, 0, Operator.MODULO);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("DIVISION_BY_ZERO");
        }
    }

    @Nested
    @DisplayName("long arithmetic")
    class LongArithmetic {
        @Test void addLongs() {
            var r = IntArithmeticHandler.computeLong(1_000_000_000L, 2_000_000_000L, Operator.PLUS);
            assertThat(r.value()).isEqualTo(3_000_000_000L);
        }

        @Test void subtractLongs() {
            var r = IntArithmeticHandler.computeLong(5L, 3L, Operator.MINUS);
            assertThat(r.value()).isEqualTo(2L);
        }

        @Test void multiplyLongs() {
            var r = IntArithmeticHandler.computeLong(100_000L, 100_000L, Operator.MULTIPLY);
            assertThat(r.value()).isEqualTo(10_000_000_000L);
        }

        @Test void divideLongByZero() {
            var r = IntArithmeticHandler.computeLong(42L, 0L, Operator.DIVIDE);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("DIVISION_BY_ZERO");
        }

        @Test void moduloLongByZero() {
            var r = IntArithmeticHandler.computeLong(42L, 0L, Operator.MODULO);
            assertThat(r.isFailure()).isTrue();
        }

        @Test void longOverflow() {
            var r = IntArithmeticHandler.computeLong(Long.MAX_VALUE, 1L, Operator.PLUS);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("OVERFLOW");
        }
    }

    @Test
    @DisplayName("unsupported operator returns error")
    void unsupportedOperator() {
        var r = IntArithmeticHandler.computeInt(1, 2, Operator.EQ);
        assertThat(r.isFailure()).isTrue();
        assertThat(r.error().code()).isEqualTo("UNSUPPORTED_OP");
    }
}

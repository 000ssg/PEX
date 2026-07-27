package ssg.pex.arithmetics.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.Operator;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BitwiseHandler")
class BitwiseHandlerTest {

    @Nested
    @DisplayName("int bitwise AND")
    class BitwiseAnd {
        @Test void basic() {
            var r = BitwiseHandler.computeInt(0b1100, 0b1010, Operator.BIT_AND);
            assertThat(r.value()).isEqualTo(0b1000);
        }

        @Test void withZero() {
            var r = BitwiseHandler.computeInt(0xFF, 0x00, Operator.BIT_AND);
            assertThat(r.value()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("int bitwise OR")
    class BitwiseOr {
        @Test void basic() {
            var r = BitwiseHandler.computeInt(0b1100, 0b1010, Operator.BIT_OR);
            assertThat(r.value()).isEqualTo(0b1110);
        }

        @Test void withZero() {
            var r = BitwiseHandler.computeInt(0xFF, 0x00, Operator.BIT_OR);
            assertThat(r.value()).isEqualTo(0xFF);
        }
    }

    @Nested
    @DisplayName("int bitwise XOR")
    class BitwiseXor {
        @Test void basic() {
            var r = BitwiseHandler.computeInt(0b1100, 0b1010, Operator.BIT_XOR);
            assertThat(r.value()).isEqualTo(0b0110);
        }

        @Test void selfXorIsZero() {
            var r = BitwiseHandler.computeInt(42, 42, Operator.BIT_XOR);
            assertThat(r.value()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("shift operations")
    class Shifts {
        @Test void shiftLeft() {
            var r = BitwiseHandler.computeInt(1, 4, Operator.SHIFT_LEFT);
            assertThat(r.value()).isEqualTo(16);
        }

        @Test void shiftRight() {
            var r = BitwiseHandler.computeInt(16, 2, Operator.SHIFT_RIGHT);
            assertThat(r.value()).isEqualTo(4);
        }

        @Test void signedShiftRightPreservesSign() {
            var r = BitwiseHandler.computeInt(-16, 2, Operator.SHIFT_RIGHT);
            assertThat((int) r.value()).isNegative();
        }

        @Test void unsignedShiftRight() {
            var r = BitwiseHandler.computeInt(-1, 1, Operator.UNSIGNED_SHIFT_RIGHT);
            assertThat(r.isSuccess()).isTrue();
            assertThat((int) r.value()).isEqualTo(Integer.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("long bitwise operations")
    class LongOps {
        @Test void andLong() {
            var r = BitwiseHandler.computeLong(0xFFL, 0x0FL, Operator.BIT_AND);
            assertThat(r.value()).isEqualTo(0x0FL);
        }

        @Test void orLong() {
            var r = BitwiseHandler.computeLong(0xF0L, 0x0FL, Operator.BIT_OR);
            assertThat(r.value()).isEqualTo(0xFFL);
        }

        @Test void shiftLeftLong() {
            var r = BitwiseHandler.computeLong(1L, 32, Operator.SHIFT_LEFT);
            assertThat(r.value()).isEqualTo(1L << 32);
        }
    }

    @Test
    @DisplayName("unsupported operator returns error")
    void unsupportedOperator() {
        var r = BitwiseHandler.computeInt(1, 2, Operator.PLUS);
        assertThat(r.isFailure()).isTrue();
        assertThat(r.error().code()).isEqualTo("UNSUPPORTED_OP");
    }
}

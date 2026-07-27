package ssg.pex.arithmetics.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.Operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("FloatArithmeticHandler")
class FloatArithmeticHandlerTest {

    @Nested
    @DisplayName("float arithmetic")
    class FloatOps {
        @Test void addFloats() {
            var r = FloatArithmeticHandler.computeFloat(1.5f, 2.5f, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((float) r.value()).isCloseTo(4.0f, within(0.001f));
        }

        @Test void subtractFloats() {
            var r = FloatArithmeticHandler.computeFloat(5.5f, 2.5f, Operator.MINUS);
            assertThat((float) r.value()).isCloseTo(3.0f, within(0.001f));
        }

        @Test void multiplyFloats() {
            var r = FloatArithmeticHandler.computeFloat(3.0f, 4.0f, Operator.MULTIPLY);
            assertThat((float) r.value()).isCloseTo(12.0f, within(0.001f));
        }

        @Test void divideFloats() {
            var r = FloatArithmeticHandler.computeFloat(10.0f, 3.0f, Operator.DIVIDE);
            assertThat((float) r.value()).isCloseTo(3.3333f, within(0.01f));
        }

        @Test void moduloFloats() {
            var r = FloatArithmeticHandler.computeFloat(10.5f, 3.0f, Operator.MODULO);
            assertThat((float) r.value()).isCloseTo(1.5f, within(0.001f));
        }

        @Test void divideByZeroProducesInfinity() {
            var r = FloatArithmeticHandler.computeFloat(1.0f, 0.0f, Operator.DIVIDE);
            assertThat(r.isSuccess()).isTrue();
            assertThat((float) r.value()).isInfinite();
        }

        @Test void nanPropagation() {
            var r = FloatArithmeticHandler.computeFloat(Float.NaN, 1.0f, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((float) r.value()).isNaN();
        }

        @Test void infinityPropagation() {
            var r = FloatArithmeticHandler.computeFloat(Float.POSITIVE_INFINITY, 1.0f, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((float) r.value()).isInfinite();
        }

        @Test void infinityMinusInfinityIsNaN() {
            var r = FloatArithmeticHandler.computeFloat(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Operator.MINUS);
            assertThat((float) r.value()).isNaN();
        }

        @Test void negativeZero() {
            var r = FloatArithmeticHandler.computeFloat(-0.0f, 0.0f, Operator.PLUS);
            assertThat((float) r.value()).isEqualTo(0.0f);
        }
    }

    @Nested
    @DisplayName("double arithmetic")
    class DoubleOps {
        @Test void addDoubles() {
            var r = FloatArithmeticHandler.computeDouble(1.5, 2.5, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isCloseTo(4.0, within(0.001));
        }

        @Test void subtractDoubles() {
            var r = FloatArithmeticHandler.computeDouble(5.5, 2.5, Operator.MINUS);
            assertThat((double) r.value()).isCloseTo(3.0, within(0.001));
        }

        @Test void multiplyDoubles() {
            var r = FloatArithmeticHandler.computeDouble(3.0, 4.0, Operator.MULTIPLY);
            assertThat((double) r.value()).isCloseTo(12.0, within(0.001));
        }

        @Test void divideDoubles() {
            var r = FloatArithmeticHandler.computeDouble(10.0, 3.0, Operator.DIVIDE);
            assertThat((double) r.value()).isCloseTo(3.3333, within(0.01));
        }

        @Test void divideByZeroProducesInfinity() {
            var r = FloatArithmeticHandler.computeDouble(1.0, 0.0, Operator.DIVIDE);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isInfinite();
        }

        @Test void nanPropagation() {
            var r = FloatArithmeticHandler.computeDouble(Double.NaN, 1.0, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }

        @Test void negativeInfinity() {
            var r = FloatArithmeticHandler.computeDouble(Double.NEGATIVE_INFINITY, 1.0, Operator.MULTIPLY);
            assertThat((double) r.value()).isEqualTo(Double.NEGATIVE_INFINITY);
        }

        @Test void verySmallNumbers() {
            var r = FloatArithmeticHandler.computeDouble(Double.MIN_VALUE, 2.0, Operator.MULTIPLY);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isPositive();
        }

        @Test void moduloDoubles() {
            var r = FloatArithmeticHandler.computeDouble(10.5, 3.0, Operator.MODULO);
            assertThat((double) r.value()).isCloseTo(1.5, within(0.001));
        }
    }

    @Test
    @DisplayName("unsupported operator returns error")
    void unsupportedOperator() {
        var r = FloatArithmeticHandler.computeDouble(1.0, 2.0, Operator.EQ);
        assertThat(r.isFailure()).isTrue();
        assertThat(r.error().code()).isEqualTo("UNSUPPORTED_OP");
    }
}

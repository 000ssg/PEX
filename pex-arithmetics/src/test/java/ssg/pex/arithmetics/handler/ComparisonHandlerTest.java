package ssg.pex.arithmetics.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.Operator;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ComparisonHandler")
class ComparisonHandlerTest {

    @Nested
    @DisplayName("integer equality")
    class IntEquality {
        @Test void equal() {
            assertThat(ComparisonHandler.compare(5, 5, Operator.EQ).value()).isEqualTo(true);
        }

        @Test void notEqual() {
            assertThat(ComparisonHandler.compare(5, 3, Operator.NEQ).value()).isEqualTo(true);
        }

        @Test void equalReturnsFalse() {
            assertThat(ComparisonHandler.compare(5, 3, Operator.EQ).value()).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("integer relational")
    class IntRelational {
        @Test void lessThan() {
            assertThat(ComparisonHandler.compare(3, 5, Operator.LT).value()).isEqualTo(true);
        }

        @Test void greaterThan() {
            assertThat(ComparisonHandler.compare(5, 3, Operator.GT).value()).isEqualTo(true);
        }

        @Test void lessOrEqual() {
            assertThat(ComparisonHandler.compare(5, 5, Operator.LE).value()).isEqualTo(true);
        }

        @Test void greaterOrEqual() {
            assertThat(ComparisonHandler.compare(5, 5, Operator.GE).value()).isEqualTo(true);
        }

        @Test void notLessThan() {
            assertThat(ComparisonHandler.compare(5, 3, Operator.LT).value()).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("cross-type numeric comparison")
    class CrossType {
        @Test void intAndLong() {
            assertThat(ComparisonHandler.compare(5, 5L, Operator.EQ).value()).isEqualTo(true);
        }

        @Test void intAndDouble() {
            assertThat(ComparisonHandler.compare(5, 5.0, Operator.EQ).value()).isEqualTo(true);
        }

        @Test void intAndFloat() {
            assertThat(ComparisonHandler.compare(5, 5.0f, Operator.EQ).value()).isEqualTo(true);
        }

        @Test void longAndDouble() {
            assertThat(ComparisonHandler.compare(100L, 100.0, Operator.LE).value()).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("null comparison")
    class NullComparison {
        @Test void nullEqualsNull() {
            assertThat(ComparisonHandler.compare(null, null, Operator.EQ).value()).isEqualTo(true);
        }

        @Test void nullNotEqualsValue() {
            assertThat(ComparisonHandler.compare(null, 5, Operator.EQ).value()).isEqualTo(false);
        }

        @Test void nullNEQ() {
            assertThat(ComparisonHandler.compare(null, 5, Operator.NEQ).value()).isEqualTo(true);
        }

        @Test void nullLtReturnsError() {
            var r = ComparisonHandler.compare(null, 5, Operator.LT);
            assertThat(r.isFailure()).isTrue();
        }
    }

    @Nested
    @DisplayName("boolean comparison")
    class BoolComparison {
        @Test void trueEqualsTrue() {
            assertThat(ComparisonHandler.compare(true, true, Operator.EQ).value()).isEqualTo(true);
        }

        @Test void trueNotEqualsFalse() {
            assertThat(ComparisonHandler.compare(true, false, Operator.NEQ).value()).isEqualTo(true);
        }

        @Test void boolLtReturnsError() {
            var r = ComparisonHandler.compare(true, false, Operator.LT);
            assertThat(r.isFailure()).isTrue();
        }
    }

    @Nested
    @DisplayName("string comparison")
    class StringComparison {
        @Test void equal() {
            assertThat(ComparisonHandler.compare("abc", "abc", Operator.EQ).value()).isEqualTo(true);
        }

        @Test void lessThan() {
            assertThat(ComparisonHandler.compare("abc", "def", Operator.LT).value()).isEqualTo(true);
        }

        @Test void greaterThan() {
            assertThat(ComparisonHandler.compare("def", "abc", Operator.GT).value()).isEqualTo(true);
        }
    }
}

package ssg.pex.arithmetics.function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.exec.FunctionRegistry;
import ssg.pex.exec.HandlerRegistry;
import ssg.pex.exec.NativeFunction;
import ssg.pex.result.Result;
import ssg.pex.spi.PluginContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("MathFunctions")
class MathFunctionsTest {

    private static FunctionRegistry registry;

    @BeforeAll
    static void setup() {
        registry = new FunctionRegistry();
        var ctx = new PluginContext(new HandlerRegistry(), registry, null);
        MathFunctions.register(ctx);
    }

    private Result<Object> call(String name, Object... args) {
        var fn = registry.lookup(name).orElseThrow().nativeImpl();
        return fn.invoke(List.of(args), null);
    }

    @Nested
    @DisplayName("abs")
    class Abs {
        @Test void positiveInt() {
            assertThat(call("abs", 5).value()).isEqualTo(5);
        }

        @Test void negativeInt() {
            assertThat(call("abs", -5).value()).isEqualTo(5);
        }

        @Test void negativeDouble() {
            assertThat((double) call("abs", -3.14).value()).isCloseTo(3.14, within(0.001));
        }

        @Test void negativeLong() {
            assertThat(call("abs", -100L).value()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("sqrt")
    class Sqrt {
        @Test void perfectSquare() {
            assertThat((double) call("sqrt", 9).value()).isCloseTo(3.0, within(0.001));
        }

        @Test void zero() {
            assertThat((double) call("sqrt", 0).value()).isCloseTo(0.0, within(0.001));
        }

        @Test void negativeReturnsNaN() {
            assertThat((double) call("sqrt", -1).value()).isNaN();
        }
    }

    @Nested
    @DisplayName("pow")
    class Pow {
        @Test void squaring() {
            assertThat((double) call("pow", 3, 2).value()).isCloseTo(9.0, within(0.001));
        }

        @Test void zeroExponent() {
            assertThat((double) call("pow", 5, 0).value()).isCloseTo(1.0, within(0.001));
        }

        @Test void negativeExponent() {
            assertThat((double) call("pow", 2, -1).value()).isCloseTo(0.5, within(0.001));
        }
    }

    @Nested
    @DisplayName("max and min")
    class MaxMin {
        @Test void maxInts() {
            assertThat(call("max", 3, 7).value()).isEqualTo(7);
        }

        @Test void minInts() {
            assertThat(call("min", 3, 7).value()).isEqualTo(3);
        }

        @Test void maxDoubles() {
            assertThat((double) call("max", 3.14, 2.71).value()).isCloseTo(3.14, within(0.001));
        }

        @Test void minDoubles() {
            assertThat((double) call("min", 3.14, 2.71).value()).isCloseTo(2.71, within(0.001));
        }
    }

    @Nested
    @DisplayName("trigonometric")
    class Trig {
        @Test void sinZero() {
            assertThat((double) call("sin", 0).value()).isCloseTo(0.0, within(0.001));
        }

        @Test void cosZero() {
            assertThat((double) call("cos", 0).value()).isCloseTo(1.0, within(0.001));
        }

        @Test void sinPiOverTwo() {
            assertThat((double) call("sin", Math.PI / 2).value()).isCloseTo(1.0, within(0.001));
        }

        @Test void tanZero() {
            assertThat((double) call("tan", 0).value()).isCloseTo(0.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("rounding")
    class Rounding {
        @Test void floorPositive() {
            assertThat((double) call("floor", 3.7).value()).isCloseTo(3.0, within(0.001));
        }

        @Test void ceilPositive() {
            assertThat((double) call("ceil", 3.2).value()).isCloseTo(4.0, within(0.001));
        }

        @Test void roundPositive() {
            assertThat(call("round", 3.5).value()).isEqualTo(4L);
        }

        @Test void roundNegative() {
            assertThat(call("round", -3.5).value()).isEqualTo(-3L);
        }
    }

    @Nested
    @DisplayName("logarithmic")
    class Log {
        @Test void logE() {
            assertThat((double) call("log", Math.E).value()).isCloseTo(1.0, within(0.001));
        }

        @Test void log10Of100() {
            assertThat((double) call("log10", 100).value()).isCloseTo(2.0, within(0.001));
        }

        @Test void log2Of8() {
            assertThat((double) call("log2", 8).value()).isCloseTo(3.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("constants")
    class Constants {
        @Test void pi() {
            assertThat((double) call("PI").value()).isCloseTo(Math.PI, within(0.000001));
        }

        @Test void e() {
            assertThat((double) call("E").value()).isCloseTo(Math.E, within(0.000001));
        }
    }

    @Test
    @DisplayName("arity error")
    void arityError() {
        var r = call("sqrt", 1, 2);
        assertThat(r.isFailure()).isTrue();
        assertThat(r.error().code()).isEqualTo("ARITY_ERROR");
    }

    @Test
    @DisplayName("type error for non-numeric")
    void typeError() {
        var r = call("sqrt", "hello");
        assertThat(r.isFailure()).isTrue();
        assertThat(r.error().code()).isEqualTo("TYPE_ERROR");
    }
}

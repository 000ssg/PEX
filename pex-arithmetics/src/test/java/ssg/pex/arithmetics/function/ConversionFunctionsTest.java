package ssg.pex.arithmetics.function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.exec.FunctionRegistry;
import ssg.pex.exec.HandlerRegistry;
import ssg.pex.result.Result;
import ssg.pex.spi.PluginContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ConversionFunctions")
class ConversionFunctionsTest {

    private static FunctionRegistry registry;

    @BeforeAll
    static void setup() {
        registry = new FunctionRegistry();
        var ctx = new PluginContext(new HandlerRegistry(), registry, null);
        ConversionFunctions.register(ctx);
    }

    private Result<Object> call(String name, Object... args) {
        var fn = registry.lookup(name).orElseThrow().nativeImpl();
        return fn.invoke(java.util.Arrays.asList(args), null);
    }

    @Nested
    @DisplayName("int()")
    class IntConversion {
        @Test void fromDouble() {
            assertThat(call("int", 3.14).value()).isEqualTo(3);
        }

        @Test void fromLong() {
            assertThat(call("int", 42L).value()).isEqualTo(42);
        }

        @Test void fromString() {
            assertThat(call("int", "42").value()).isEqualTo(42);
        }

        @Test void fromBool() {
            assertThat(call("int", true).value()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("long()")
    class LongConversion {
        @Test void fromInt() {
            assertThat(call("long", 42).value()).isEqualTo(42L);
        }

        @Test void fromDouble() {
            assertThat(call("long", 3.14).value()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("float()")
    class FloatConversion {
        @Test void fromInt() {
            assertThat((float) call("float", 42).value()).isCloseTo(42.0f, within(0.001f));
        }

        @Test void fromString() {
            assertThat((float) call("float", "3.14").value()).isCloseTo(3.14f, within(0.01f));
        }
    }

    @Nested
    @DisplayName("double()")
    class DoubleConversion {
        @Test void fromInt() {
            assertThat((double) call("double", 42).value()).isCloseTo(42.0, within(0.001));
        }

        @Test void fromFloat() {
            assertThat((double) call("double", 3.14f).value()).isCloseTo(3.14, within(0.01));
        }
    }

    @Nested
    @DisplayName("str()")
    class StrConversion {
        @Test void fromInt() {
            assertThat(call("str", 42).value()).isEqualTo("42");
        }

        @Test void fromBool() {
            assertThat(call("str", true).value()).isEqualTo("true");
        }

        @Test void fromNull() {
            assertThat(call("str", (Object) null).value()).isEqualTo("null");
        }
    }

    @Nested
    @DisplayName("bool()")
    class BoolConversion {
        @Test void fromNonZero() {
            assertThat(call("bool", 1).value()).isEqualTo(true);
        }

        @Test void fromZero() {
            assertThat(call("bool", 0).value()).isEqualTo(false);
        }

        @Test void fromNonEmptyString() {
            assertThat(call("bool", "hello").value()).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("bit conversions")
    class BitConversions {
        @Test void floatToIntBitsAndBack() {
            float original = 3.14f;
            var bitsResult = call("floatToIntBits", original);
            assertThat(bitsResult.isSuccess()).isTrue();
            int bits = (int) bitsResult.value();

            var backResult = call("intBitsToFloat", bits);
            assertThat(backResult.isSuccess()).isTrue();
            assertThat((float) backResult.value()).isCloseTo(original, within(0.001f));
        }

        @Test void doubleToLongBitsAndBack() {
            double original = 3.14159;
            var bitsResult = call("doubleToLongBits", original);
            assertThat(bitsResult.isSuccess()).isTrue();
            long bits = (long) bitsResult.value();

            var backResult = call("longBitsToDouble", bits);
            assertThat(backResult.isSuccess()).isTrue();
            assertThat((double) backResult.value()).isCloseTo(original, within(0.00001));
        }
    }

    @Test
    @DisplayName("arity error")
    void arityError() {
        var r = call("int", 1, 2);
        assertThat(r.isFailure()).isTrue();
    }
}

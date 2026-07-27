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

@DisplayName("RadixFunctions")
class RadixFunctionsTest {

    private static FunctionRegistry registry;

    @BeforeAll
    static void setup() {
        registry = new FunctionRegistry();
        var ctx = new PluginContext(new HandlerRegistry(), registry, null);
        RadixFunctions.register(ctx);
    }

    private Result<Object> call(String name, Object... args) {
        var fn = registry.lookup(name).orElseThrow().nativeImpl();
        return fn.invoke(List.of(args), null);
    }

    @Nested
    @DisplayName("hex()")
    class Hex {
        @Test void decimal255() {
            assertThat(call("hex", 255).value()).isEqualTo("0xff");
        }

        @Test void zero() {
            assertThat(call("hex", 0).value()).isEqualTo("0x0");
        }

        @Test void longValue() {
            assertThat(call("hex", 256L).value()).isEqualTo("0x100");
        }
    }

    @Nested
    @DisplayName("bin()")
    class Bin {
        @Test void decimal10() {
            assertThat(call("bin", 10).value()).isEqualTo("0b1010");
        }

        @Test void zero() {
            assertThat(call("bin", 0).value()).isEqualTo("0b0");
        }

        @Test void one() {
            assertThat(call("bin", 1).value()).isEqualTo("0b1");
        }
    }

    @Nested
    @DisplayName("oct()")
    class Oct {
        @Test void decimal8() {
            assertThat(call("oct", 8).value()).isEqualTo("0o10");
        }

        @Test void decimal63() {
            assertThat(call("oct", 63).value()).isEqualTo("0o77");
        }

        @Test void zero() {
            assertThat(call("oct", 0).value()).isEqualTo("0o0");
        }
    }

    @Nested
    @DisplayName("toRadix()")
    class ToRadix {
        @Test void base3() {
            assertThat(call("toRadix", 9, 3).value()).isEqualTo("100");
        }

        @Test void base16() {
            assertThat(call("toRadix", 255, 16).value()).isEqualTo("ff");
        }

        @Test void invalidRadix() {
            var r = call("toRadix", 10, 37);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("VALUE_ERROR");
        }
    }

    @Nested
    @DisplayName("parseRadix()")
    class ParseRadix {
        @Test void hexString() {
            assertThat(call("parseRadix", "ff", 16).value()).isEqualTo(255);
        }

        @Test void binaryString() {
            assertThat(call("parseRadix", "1010", 2).value()).isEqualTo(10);
        }

        @Test void octalString() {
            assertThat(call("parseRadix", "77", 8).value()).isEqualTo(63);
        }

        @Test void invalidString() {
            var r = call("parseRadix", "xyz", 10);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("PARSE_ERROR");
        }

        @Test void invalidRadix() {
            var r = call("parseRadix", "10", 1);
            assertThat(r.isFailure()).isTrue();
        }
    }

    @Test
    @DisplayName("type error for float input")
    void typeErrorFloat() {
        var r = call("hex", 3.14);
        // Should convert via longValue
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("type error for string input to hex")
    void typeErrorString() {
        var r = call("hex", "hello");
        assertThat(r.isFailure()).isTrue();
    }
}

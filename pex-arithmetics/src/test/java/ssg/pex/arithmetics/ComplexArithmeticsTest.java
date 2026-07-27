package ssg.pex.arithmetics;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.arithmetics.function.ConversionFunctions;
import ssg.pex.arithmetics.function.MathFunctions;
import ssg.pex.arithmetics.function.RadixFunctions;
import ssg.pex.arithmetics.handler.BitwiseHandler;
import ssg.pex.arithmetics.handler.ComparisonHandler;
import ssg.pex.arithmetics.handler.FloatArithmeticHandler;
import ssg.pex.arithmetics.handler.IntArithmeticHandler;
import ssg.pex.arithmetics.type.NumericPromotion;
import ssg.pex.ast.node.Operator;
import ssg.pex.exec.FunctionRegistry;
import ssg.pex.exec.HandlerRegistry;
import ssg.pex.result.Result;
import ssg.pex.spi.PluginContext;
import ssg.pex.type.PexType;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Complex real-life test cases for pex-arithmetics: mathematical expressions,
 * bitwise operations, type conversions, and edge cases.
 */
@DisplayName("Complex pex-arithmetics tests")
class ComplexArithmeticsTest {

    private static FunctionRegistry mathRegistry;
    private static FunctionRegistry conversionRegistry;
    private static FunctionRegistry radixRegistry;

    @BeforeAll
    static void setupAll() {
        mathRegistry = new FunctionRegistry();
        var mathCtx = new PluginContext(new HandlerRegistry(), mathRegistry, null);
        MathFunctions.register(mathCtx);

        conversionRegistry = new FunctionRegistry();
        var convCtx = new PluginContext(new HandlerRegistry(), conversionRegistry, null);
        ConversionFunctions.register(convCtx);

        radixRegistry = new FunctionRegistry();
        var radixCtx = new PluginContext(new HandlerRegistry(), radixRegistry, null);
        RadixFunctions.register(radixCtx);
    }

    private Result<Object> math(String name, Object... args) {
        var fn = mathRegistry.lookup(name).orElseThrow().nativeImpl();
        return fn.invoke(Arrays.asList(args), null);
    }

    private Result<Object> conv(String name, Object... args) {
        var fn = conversionRegistry.lookup(name).orElseThrow().nativeImpl();
        return fn.invoke(Arrays.asList(args), null);
    }

    private Result<Object> radix(String name, Object... args) {
        var fn = radixRegistry.lookup(name).orElseThrow().nativeImpl();
        return fn.invoke(Arrays.asList(args), null);
    }

    // ===========================================================================================
    // Complex Mathematical Expressions
    // ===========================================================================================

    @Nested
    @DisplayName("Complex Mathematical Expressions")
    class ComplexMathExpressions {

        @Test
        @DisplayName("quadratic formula: (-b + sqrt(b*b - 4*a*c)) / (2*a) for a=1, b=-3, c=2")
        void quadraticFormula() {
            // x = (-b + sqrt(b^2 - 4ac)) / (2a) with a=1, b=-3, c=2
            // x = (3 + sqrt(9 - 8)) / 2 = (3 + 1) / 2 = 2.0
            double a = 1, b = -3, c = 2;
            double discriminant = b * b - 4 * a * c;
            double sqrtDisc = (double) math("sqrt", discriminant).value();
            double x = (-b + sqrtDisc) / (2 * a);
            assertThat(x).isCloseTo(2.0, within(0.001));
        }

        @Test
        @DisplayName("quadratic formula: second root for a=1, b=-3, c=2")
        void quadraticFormulaSecondRoot() {
            double a = 1, b = -3, c = 2;
            double discriminant = b * b - 4 * a * c;
            double sqrtDisc = (double) math("sqrt", discriminant).value();
            double x2 = (-b - sqrtDisc) / (2 * a);
            assertThat(x2).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("Pythagorean theorem: sqrt(a*a + b*b) for a=3, b=4 -> 5.0")
        void pythagoreanTheorem() {
            double a = 3, b = 4;
            double hypot = (double) math("sqrt", a * a + b * b).value();
            assertThat(hypot).isCloseTo(5.0, within(0.001));
        }

        @Test
        @DisplayName("compound interest: P * pow(1 + r/n, n*t)")
        void compoundInterest() {
            // P=1000, r=0.05, n=12, t=10
            double P = 1000, r = 0.05, n = 12, t = 10;
            double amount = P * (double) math("pow", 1 + r / n, n * t).value();
            // Expected ~1647.01
            assertThat(amount).isCloseTo(1647.01, within(0.1));
        }

        @Test
        @DisplayName("BMI calculation: weight / (height * height)")
        void bmiCalculation() {
            double weight = 70.0, height = 1.75;
            var r = FloatArithmeticHandler.computeDouble(weight, height * height, Operator.DIVIDE);
            assertThat((double) r.value()).isCloseTo(22.857, within(0.01));
        }

        @Test
        @DisplayName("distance formula: sqrt(pow(x2-x1, 2) + pow(y2-y1, 2))")
        void distanceFormula() {
            double x1 = 1, y1 = 2, x2 = 4, y2 = 6;
            double dx = (double) math("pow", x2 - x1, 2).value();
            double dy = (double) math("pow", y2 - y1, 2).value();
            double dist = (double) math("sqrt", dx + dy).value();
            assertThat(dist).isCloseTo(5.0, within(0.001));
        }

        @Test
        @DisplayName("temperature conversion: Fahrenheit to Celsius: (F - 32) * 5 / 9")
        void temperatureConversion() {
            // 212F -> 100C
            var sub = FloatArithmeticHandler.computeDouble(212.0, 32.0, Operator.MINUS);
            var mul = FloatArithmeticHandler.computeDouble((double) sub.value(), 5.0, Operator.MULTIPLY);
            var div = FloatArithmeticHandler.computeDouble((double) mul.value(), 9.0, Operator.DIVIDE);
            assertThat((double) div.value()).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("circle area: PI * r * r")
        void circleArea() {
            double r = 5.0;
            double pi = (double) math("PI").value();
            var result = FloatArithmeticHandler.computeDouble(pi * r, r, Operator.MULTIPLY);
            assertThat((double) result.value()).isCloseTo(78.5398, within(0.01));
        }

        @Test
        @DisplayName("mixed int/float chain: 1 + 2.0 * 3 / 4 - 5")
        void mixedIntFloatChain() {
            // 2.0 * 3 = 6.0; 6.0 / 4 = 1.5; 1 + 1.5 = 2.5; 2.5 - 5 = -2.5
            var step1 = FloatArithmeticHandler.computeDouble(2.0, 3.0, Operator.MULTIPLY);
            var step2 = FloatArithmeticHandler.computeDouble((double) step1.value(), 4.0, Operator.DIVIDE);
            var step3 = FloatArithmeticHandler.computeDouble(1.0, (double) step2.value(), Operator.PLUS);
            var step4 = FloatArithmeticHandler.computeDouble((double) step3.value(), 5.0, Operator.MINUS);
            assertThat((double) step4.value()).isCloseTo(-2.5, within(0.001));
        }

        @Test
        @DisplayName("deeply nested parentheses: ((((1 + 2) * 3) - 4) / 5)")
        void deeplyNestedParentheses() {
            var r1 = IntArithmeticHandler.computeInt(1, 2, Operator.PLUS);        // 3
            var r2 = IntArithmeticHandler.computeInt((int) r1.value(), 3, Operator.MULTIPLY); // 9
            var r3 = IntArithmeticHandler.computeInt((int) r2.value(), 4, Operator.MINUS);    // 5
            var r4 = IntArithmeticHandler.computeInt((int) r3.value(), 5, Operator.DIVIDE);   // 1
            assertThat(r4.value()).isEqualTo(1);
        }

        @Test
        @DisplayName("expression with 10+ operators chained")
        void expressionWith10PlusOperators() {
            // 1+2-3+4-5+6-7+8-9+10 = 5 (pairs: -1,-1,-1,-1,1 + initial 1+2=3 ... simpler: running sum)
            int result = 0;
            int[] vals = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            Operator[] ops = {Operator.PLUS, Operator.MINUS, Operator.PLUS, Operator.MINUS,
                    Operator.PLUS, Operator.MINUS, Operator.PLUS, Operator.MINUS, Operator.PLUS};
            long running = vals[0];
            for (int i = 0; i < ops.length; i++) {
                var r = IntArithmeticHandler.computeInt((int) running, vals[i + 1], ops[i]);
                running = ((Number) r.value()).longValue();
            }
            // 1+2-3+4-5+6-7+8-9+10 = 7
            assertThat(running).isEqualTo(7L);
        }

        @Test
        @DisplayName("trigonometric identity: sin^2(x) + cos^2(x) = 1")
        void trigIdentity() {
            double x = 1.234;
            double sinX = (double) math("sin", x).value();
            double cosX = (double) math("cos", x).value();
            double sum = sinX * sinX + cosX * cosX;
            assertThat(sum).isCloseTo(1.0, within(0.0001));
        }

        @Test
        @DisplayName("logarithmic identity: log(a*b) = log(a) + log(b)")
        void logIdentity() {
            double a = 5.0, b = 7.0;
            double logAB = (double) math("log", a * b).value();
            double logA = (double) math("log", a).value();
            double logB = (double) math("log", b).value();
            assertThat(logAB).isCloseTo(logA + logB, within(0.0001));
        }

        @Test
        @DisplayName("exp and log are inverses: exp(log(x)) = x")
        void expLogInverse() {
            double x = 42.0;
            double logX = (double) math("log", x).value();
            double expLogX = (double) math("exp", logX).value();
            assertThat(expLogX).isCloseTo(x, within(0.001));
        }

        @Test
        @DisplayName("pow and sqrt are inverses: sqrt(pow(x, 2)) = |x|")
        void powSqrtInverse() {
            double x = 7.5;
            double squared = (double) math("pow", x, 2).value();
            double sqrtSquared = (double) math("sqrt", squared).value();
            assertThat(sqrtSquared).isCloseTo(x, within(0.001));
        }
    }

    // ===========================================================================================
    // Complex Bitwise Operations
    // ===========================================================================================

    @Nested
    @DisplayName("Complex Bitwise Operations")
    class ComplexBitwiseOperations {

        @Test
        @DisplayName("bit mask creation: 1 << n for n=0..7")
        void bitMaskCreation() {
            for (int n = 0; n < 8; n++) {
                var r = BitwiseHandler.computeInt(1, n, Operator.SHIFT_LEFT);
                assertThat(r.value()).isEqualTo(1 << n);
            }
        }

        @Test
        @DisplayName("bit test: (value & (1 << n)) != 0")
        void bitTest() {
            int value = 0b10101010;
            // Bit 1 (from right) should be set
            var mask = BitwiseHandler.computeInt(1, 1, Operator.SHIFT_LEFT);
            var test = BitwiseHandler.computeInt(value, (int) mask.value(), Operator.BIT_AND);
            assertThat((int) test.value()).isNotEqualTo(0);

            // Bit 0 should NOT be set
            mask = BitwiseHandler.computeInt(1, 0, Operator.SHIFT_LEFT);
            test = BitwiseHandler.computeInt(value, (int) mask.value(), Operator.BIT_AND);
            assertThat((int) test.value()).isEqualTo(0);
        }

        @Test
        @DisplayName("bit set: value | mask")
        void bitSet() {
            int value = 0b0000;
            int mask = 0b1010;
            var r = BitwiseHandler.computeInt(value, mask, Operator.BIT_OR);
            assertThat(r.value()).isEqualTo(0b1010);
        }

        @Test
        @DisplayName("bit clear: value & ~mask (simulated)")
        void bitClear() {
            // Clear bit 1: 0b1111 & ~(1 << 1) = 0b1111 & 0b1101 = 0b1101
            int value = 0b1111;
            int mask = 1 << 1; // 0b0010
            int invMask = ~mask; // 0b...1101
            var r = BitwiseHandler.computeInt(value, invMask, Operator.BIT_AND);
            assertThat((int) r.value()).isEqualTo(0b1101);
        }

        @Test
        @DisplayName("byte extraction: (value >> 8) & 0xFF")
        void byteExtraction() {
            int value = 0xABCD;
            var shifted = BitwiseHandler.computeInt(value, 8, Operator.SHIFT_RIGHT);
            var masked = BitwiseHandler.computeInt((int) shifted.value(), 0xFF, Operator.BIT_AND);
            assertThat(masked.value()).isEqualTo(0xAB);
        }

        @Test
        @DisplayName("RGB color packing: (r << 16) | (g << 8) | b")
        void rgbColorPacking() {
            int r = 255, g = 128, b = 64;
            var rShifted = BitwiseHandler.computeInt(r, 16, Operator.SHIFT_LEFT);
            var gShifted = BitwiseHandler.computeInt(g, 8, Operator.SHIFT_LEFT);
            var rg = BitwiseHandler.computeInt((int) rShifted.value(), (int) gShifted.value(), Operator.BIT_OR);
            var rgb = BitwiseHandler.computeInt((int) rg.value(), b, Operator.BIT_OR);
            assertThat(rgb.value()).isEqualTo((255 << 16) | (128 << 8) | 64);
        }

        @Test
        @DisplayName("RGB color unpacking: extract r, g, b from packed int")
        void rgbColorUnpacking() {
            int packed = (200 << 16) | (100 << 8) | 50;
            var rShifted = BitwiseHandler.computeInt(packed, 16, Operator.SHIFT_RIGHT);
            var rMasked = BitwiseHandler.computeInt((int) rShifted.value(), 0xFF, Operator.BIT_AND);
            assertThat(rMasked.value()).isEqualTo(200);

            var gShifted = BitwiseHandler.computeInt(packed, 8, Operator.SHIFT_RIGHT);
            var gMasked = BitwiseHandler.computeInt((int) gShifted.value(), 0xFF, Operator.BIT_AND);
            assertThat(gMasked.value()).isEqualTo(100);

            var bMasked = BitwiseHandler.computeInt(packed, 0xFF, Operator.BIT_AND);
            assertThat(bMasked.value()).isEqualTo(50);
        }

        @Test
        @DisplayName("power of 2 check: (n & (n-1)) == 0")
        void powerOf2Check() {
            // 16 is power of 2
            int n = 16;
            var r = BitwiseHandler.computeInt(n, n - 1, Operator.BIT_AND);
            assertThat(r.value()).isEqualTo(0);

            // 15 is not power of 2
            n = 15;
            r = BitwiseHandler.computeInt(n, n - 1, Operator.BIT_AND);
            assertThat((int) r.value()).isNotEqualTo(0);
        }

        @Test
        @DisplayName("circular shift simulation: left rotate by 4 bits in a byte")
        void circularShiftSimulation() {
            // Left rotate 0b10110011 by 4 bits in an 8-bit context
            int value = 0b10110011;
            int rotateBy = 4;
            int bits = 8;
            var leftPart = BitwiseHandler.computeInt(value, rotateBy, Operator.SHIFT_LEFT);
            var rightPart = BitwiseHandler.computeInt(value, bits - rotateBy, Operator.SHIFT_RIGHT);
            var combined = BitwiseHandler.computeInt((int) leftPart.value(), (int) rightPart.value(), Operator.BIT_OR);
            var masked = BitwiseHandler.computeInt((int) combined.value(), 0xFF, Operator.BIT_AND);
            // 0b00111011 = 0x3B = 59
            assertThat(masked.value()).isEqualTo(0b00111011);
        }

        @Test
        @DisplayName("XOR swap: a ^ b ^ b == a")
        void xorSwap() {
            int a = 42, b = 99;
            var step1 = BitwiseHandler.computeInt(a, b, Operator.BIT_XOR);
            var step2 = BitwiseHandler.computeInt((int) step1.value(), b, Operator.BIT_XOR);
            assertThat(step2.value()).isEqualTo(a);
        }

        @Test
        @DisplayName("long bitwise operations: 64-bit mask")
        void longBitwise64BitMask() {
            var r = BitwiseHandler.computeLong(1L, 32, Operator.SHIFT_LEFT);
            assertThat(r.value()).isEqualTo(4294967296L); // 2^32
        }
    }

    // ===========================================================================================
    // Complex Type Conversions
    // ===========================================================================================

    @Nested
    @DisplayName("Complex Type Conversions")
    class ComplexTypeConversions {

        @Test
        @DisplayName("chain: int -> float -> string via conversion functions")
        void intToFloatToString() {
            var floatResult = conv("float", 42);
            assertThat(floatResult.isSuccess()).isTrue();
            var strResult = conv("str", floatResult.value());
            assertThat(strResult.isSuccess()).isTrue();
            assertThat(strResult.value().toString()).contains("42");
        }

        @Test
        @DisplayName("IEEE 754 bit representation round-trip: intBitsToFloat(floatToIntBits(x)) == x")
        void ieee754FloatRoundTrip() {
            float original = 3.14159f;
            var bits = conv("floatToIntBits", original);
            assertThat(bits.isSuccess()).isTrue();
            var back = conv("intBitsToFloat", bits.value());
            assertThat(back.isSuccess()).isTrue();
            assertThat((float) back.value()).isCloseTo(original, within(0.00001f));
        }

        @Test
        @DisplayName("long bits to double round-trip: longBitsToDouble(doubleToLongBits(x)) == x")
        void ieee754DoubleRoundTrip() {
            double original = 2.718281828;
            var bits = conv("doubleToLongBits", original);
            assertThat(bits.isSuccess()).isTrue();
            var back = conv("longBitsToDouble", bits.value());
            assertThat(back.isSuccess()).isTrue();
            assertThat((double) back.value()).isCloseTo(original, within(0.0000001));
        }

        @Test
        @DisplayName("mixed type arithmetic: int + long promotion")
        void mixedTypeIntLongPromotion() {
            var promoted = NumericPromotion.promotePair(42, 100L);
            assertThat(promoted.resultType()).isEqualTo(PexType.LONG);
            assertThat(promoted.left().longValue()).isEqualTo(42L);
            assertThat(promoted.right().longValue()).isEqualTo(100L);
        }

        @Test
        @DisplayName("mixed type arithmetic: int + float promotion")
        void mixedTypeIntFloatPromotion() {
            var promoted = NumericPromotion.promotePair(42, 3.14f);
            assertThat(promoted.resultType()).isEqualTo(PexType.FLOAT);
        }

        @Test
        @DisplayName("mixed type arithmetic: long + float -> double promotion")
        void mixedTypeLongFloatPromotion() {
            var promoted = NumericPromotion.promotePair(100L, 3.14f);
            assertThat(promoted.resultType()).isEqualTo(PexType.DOUBLE);
        }

        @Test
        @DisplayName("mixed type arithmetic: int + double promotion")
        void mixedTypeIntDoublePromotion() {
            var promoted = NumericPromotion.promotePair(42, 3.14);
            assertThat(promoted.resultType()).isEqualTo(PexType.DOUBLE);
        }

        @Test
        @DisplayName("radix round-trip: toRadix(parseRadix('FF', 16), 16) == 'ff'")
        void radixRoundTrip() {
            var parsed = radix("parseRadix", "FF", 16);
            assertThat(parsed.isSuccess()).isTrue();
            assertThat(parsed.value()).isEqualTo(255);

            var back = radix("toRadix", parsed.value(), 16);
            assertThat(back.isSuccess()).isTrue();
            assertThat(back.value()).isEqualTo("ff");
        }

        @Test
        @DisplayName("convert between all numeric types in sequence")
        void convertAllNumericTypesSequence() {
            // Start as int 42
            var longVal = conv("long", 42);
            assertThat(longVal.value()).isEqualTo(42L);

            var floatVal = conv("float", longVal.value());
            assertThat(floatVal.isSuccess()).isTrue();

            var doubleVal = conv("double", floatVal.value());
            assertThat(doubleVal.isSuccess()).isTrue();

            var intVal = conv("int", doubleVal.value());
            assertThat(intVal.value()).isEqualTo(42);
        }

        @Test
        @DisplayName("radix conversion: hex, bin, oct")
        void radixConversionsHexBinOct() {
            assertThat(radix("hex", 255).value()).isEqualTo("0xff");
            assertThat(radix("bin", 10).value()).isEqualTo("0b1010");
            assertThat(radix("oct", 8).value()).isEqualTo("0o10");
        }

        @Test
        @DisplayName("string to int to string round-trip")
        void stringToIntToStringRoundTrip() {
            var intResult = conv("int", "12345");
            assertThat(intResult.value()).isEqualTo(12345);
            var strResult = conv("str", intResult.value());
            assertThat(strResult.value()).isEqualTo("12345");
        }
    }

    // ===========================================================================================
    // Edge Cases
    // ===========================================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("integer overflow: MAX_VALUE + 1 promotes to long")
        void integerOverflowPromotesToLong() {
            var r = IntArithmeticHandler.computeInt(Integer.MAX_VALUE, 1, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
            assertThat(r.value()).isEqualTo((long) Integer.MAX_VALUE + 1);
        }

        @Test
        @DisplayName("long overflow: MAX_VALUE + 1 returns error")
        void longOverflow() {
            var r = IntArithmeticHandler.computeLong(Long.MAX_VALUE, 1L, Operator.PLUS);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("OVERFLOW");
        }

        @Test
        @DisplayName("int division by zero returns error")
        void intDivisionByZero() {
            var r = IntArithmeticHandler.computeInt(42, 0, Operator.DIVIDE);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("DIVISION_BY_ZERO");
        }

        @Test
        @DisplayName("long division by zero returns error")
        void longDivisionByZero() {
            var r = IntArithmeticHandler.computeLong(42L, 0L, Operator.DIVIDE);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("DIVISION_BY_ZERO");
        }

        @Test
        @DisplayName("float division by zero produces Infinity")
        void floatDivisionByZero() {
            var r = FloatArithmeticHandler.computeFloat(1.0f, 0.0f, Operator.DIVIDE);
            assertThat(r.isSuccess()).isTrue();
            assertThat((float) r.value()).isEqualTo(Float.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("double division by zero produces Infinity")
        void doubleDivisionByZero() {
            var r = FloatArithmeticHandler.computeDouble(1.0, 0.0, Operator.DIVIDE);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("float precision: 0.1 + 0.2 is not exactly 0.3")
        void floatPrecision01Plus02() {
            var r = FloatArithmeticHandler.computeDouble(0.1, 0.2, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            double result = (double) r.value();
            // Not exactly 0.3, but close
            assertThat(result).isNotEqualTo(0.3);
            assertThat(result).isCloseTo(0.3, within(0.0001));
        }

        @Test
        @DisplayName("NaN + 1 = NaN")
        void nanPlusOne() {
            var r = FloatArithmeticHandler.computeDouble(Double.NaN, 1.0, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }

        @Test
        @DisplayName("NaN == NaN is false per IEEE 754")
        void nanEqualNan() {
            var r = ComparisonHandler.compare(Double.NaN, Double.NaN, Operator.EQ);
            assertThat(r.isSuccess()).isTrue();
            // IEEE 754: NaN is not equal to anything, including itself
            assertThat(r.value()).isEqualTo(false);
        }

        @Test
        @DisplayName("Infinity + 1 = Infinity")
        void infinityPlusOne() {
            var r = FloatArithmeticHandler.computeDouble(Double.POSITIVE_INFINITY, 1.0, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("Infinity * -1 = -Infinity")
        void infinityTimesNegativeOne() {
            var r = FloatArithmeticHandler.computeDouble(Double.POSITIVE_INFINITY, -1.0, Operator.MULTIPLY);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isEqualTo(Double.NEGATIVE_INFINITY);
        }

        @Test
        @DisplayName("Infinity - Infinity = NaN")
        void infinityMinusInfinity() {
            var r = FloatArithmeticHandler.computeDouble(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Operator.MINUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }

        @Test
        @DisplayName("modulo with negative numbers: -10 % 3 = -1")
        void moduloNegativeNumbers() {
            var r = IntArithmeticHandler.computeInt(-10, 3, Operator.MODULO);
            assertThat(r.value()).isEqualTo(-1);
        }

        @Test
        @DisplayName("modulo with negative divisor: 10 % -3 = 1")
        void moduloNegativeDivisor() {
            var r = IntArithmeticHandler.computeInt(10, -3, Operator.MODULO);
            assertThat(r.value()).isEqualTo(1);
        }

        @Test
        @DisplayName("zero divided by zero (float -> NaN)")
        void zeroDivZeroFloat() {
            var r = FloatArithmeticHandler.computeDouble(0.0, 0.0, Operator.DIVIDE);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }

        @Test
        @DisplayName("very large exponent: pow(2, 63)")
        void veryLargeExponent() {
            var r = math("pow", 2, 63);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isCloseTo(9.223372036854776E18, within(1E10));
        }

        @Test
        @DisplayName("comparison: cross-type int vs double")
        void comparisonCrossType() {
            var r = ComparisonHandler.compare(42, 42.0, Operator.EQ);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isEqualTo(true);
        }

        @Test
        @DisplayName("comparison: string ordering")
        void comparisonStringOrdering() {
            var r1 = ComparisonHandler.compare("apple", "banana", Operator.LT);
            assertThat(r1.value()).isEqualTo(true);

            var r2 = ComparisonHandler.compare("zebra", "apple", Operator.GT);
            assertThat(r2.value()).isEqualTo(true);
        }

        @Test
        @DisplayName("comparison: null equality")
        void comparisonNullEquality() {
            var r1 = ComparisonHandler.compare(null, null, Operator.EQ);
            assertThat(r1.value()).isEqualTo(true);

            var r2 = ComparisonHandler.compare(null, 42, Operator.EQ);
            assertThat(r2.value()).isEqualTo(false);
        }

        @Test
        @DisplayName("integer underflow: MIN_VALUE - 1 promotes to long")
        void integerUnderflow() {
            var r = IntArithmeticHandler.computeInt(Integer.MIN_VALUE, 1, Operator.MINUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
            assertThat(r.value()).isEqualTo((long) Integer.MIN_VALUE - 1);
        }

        @Test
        @DisplayName("multiplication overflow: MAX_VALUE * MAX_VALUE")
        void multiplicationOverflow() {
            var r = IntArithmeticHandler.computeInt(Integer.MAX_VALUE, Integer.MAX_VALUE, Operator.MULTIPLY);
            assertThat(r.isSuccess()).isTrue();
            // Should promote to long
            assertThat(r.value()).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("NumericPromotion.isNumeric for various types")
        void numericPromotionIsNumeric() {
            assertThat(NumericPromotion.isNumeric(42)).isTrue();
            assertThat(NumericPromotion.isNumeric(42L)).isTrue();
            assertThat(NumericPromotion.isNumeric(42.0f)).isTrue();
            assertThat(NumericPromotion.isNumeric(42.0)).isTrue();
            assertThat(NumericPromotion.isNumeric("42")).isFalse();
            assertThat(NumericPromotion.isNumeric(null)).isFalse();
        }

        @Test
        @DisplayName("double modulo produces correct fractional result")
        void doubleModulo() {
            var r = FloatArithmeticHandler.computeDouble(10.5, 3.0, Operator.MODULO);
            assertThat((double) r.value()).isCloseTo(1.5, within(0.001));
        }

        @Test
        @DisplayName("negative infinity multiplication")
        void negativeInfinityMultiplication() {
            var r = FloatArithmeticHandler.computeDouble(Double.NEGATIVE_INFINITY, 2.0, Operator.MULTIPLY);
            assertThat((double) r.value()).isEqualTo(Double.NEGATIVE_INFINITY);
        }
    }
}

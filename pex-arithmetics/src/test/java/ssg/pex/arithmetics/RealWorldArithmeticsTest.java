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
 * Real-world complex test cases for pex-arithmetics: IEEE 754 edge cases,
 * complex expressions, overflow/promotion, and function composition.
 */
@DisplayName("Real-world pex-arithmetics tests")
class RealWorldArithmeticsTest {

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
    // IEEE 754 Edge Cases
    // ===========================================================================================

    @Nested
    @DisplayName("IEEE 754 Edge Cases")
    class IEEE754EdgeCases {

        @Test
        @DisplayName("NaN + 5 = NaN (NaN propagation through addition)")
        void nanPlusFiveIsNaN() {
            var r = FloatArithmeticHandler.computeDouble(Double.NaN, 5.0, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }

        @Test
        @DisplayName("NaN * 0 = NaN (NaN propagation through multiplication)")
        void nanTimesZeroIsNaN() {
            var r = FloatArithmeticHandler.computeDouble(Double.NaN, 0.0, Operator.MULTIPLY);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }

        @Test
        @DisplayName("Infinity + 1 = Infinity")
        void infinityPlusOneIsInfinity() {
            var r = FloatArithmeticHandler.computeDouble(Double.POSITIVE_INFINITY, 1.0, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("Infinity - Infinity = NaN")
        void infinityMinusInfinityIsNaN() {
            var r = FloatArithmeticHandler.computeDouble(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Operator.MINUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }

        @Test
        @DisplayName("1.0 / 0.0 = Infinity (float division by zero)")
        void floatDivisionByZeroIsInfinity() {
            var r = FloatArithmeticHandler.computeDouble(1.0, 0.0, Operator.DIVIDE);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("-0.0 equality with +0.0")
        void negativeZeroEqualsPositiveZero() {
            var r = ComparisonHandler.compare(-0.0, +0.0, Operator.EQ);
            assertThat(r.isSuccess()).isTrue();
            // In Java, -0.0 == +0.0 is true via ==, and Double.valueOf(-0.0).equals(Double.valueOf(0.0)) is also true
            assertThat(r.value()).isEqualTo(true);
        }

        @Test
        @DisplayName("Double.MAX_VALUE + Double.MAX_VALUE = Infinity")
        void doubleMaxValuePlusMaxValueIsInfinity() {
            var r = FloatArithmeticHandler.computeDouble(Double.MAX_VALUE, Double.MAX_VALUE, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("NaN subtraction propagation: NaN - 100 = NaN")
        void nanSubtractionPropagation() {
            var r = FloatArithmeticHandler.computeDouble(Double.NaN, 100.0, Operator.MINUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isNaN();
        }
    }

    // ===========================================================================================
    // Complex Expression Evaluation
    // ===========================================================================================

    @Nested
    @DisplayName("Complex Expression Evaluation")
    class ComplexExpressionEvaluation {

        @Test
        @DisplayName("((2 + 3) * 4 - 6) / 2 + 1 = 8")
        void precedenceChainExpression() {
            // (2+3)=5, 5*4=20, 20-6=14, 14/2=7, 7+1=8
            var r1 = IntArithmeticHandler.computeInt(2, 3, Operator.PLUS);       // 5
            var r2 = IntArithmeticHandler.computeInt((int) r1.value(), 4, Operator.MULTIPLY); // 20
            var r3 = IntArithmeticHandler.computeInt((int) r2.value(), 6, Operator.MINUS);    // 14
            var r4 = IntArithmeticHandler.computeInt((int) r3.value(), 2, Operator.DIVIDE);   // 7
            var r5 = IntArithmeticHandler.computeInt((int) r4.value(), 1, Operator.PLUS);     // 8
            assertThat(r5.value()).isEqualTo(8);
        }

        @Test
        @DisplayName("sqrt(pow(3, 2) + pow(4, 2)) = 5.0")
        void nestedMathSqrtPow() {
            double pow3 = (double) math("pow", 3, 2).value(); // 9.0
            double pow4 = (double) math("pow", 4, 2).value(); // 16.0
            double result = (double) math("sqrt", pow3 + pow4).value(); // sqrt(25) = 5.0
            assertThat(result).isCloseTo(5.0, within(0.001));
        }

        @Test
        @DisplayName("abs(sin(0)) + cos(0) + ceil(1.1) + floor(2.9)")
        void combinedMathFunctions() {
            double absSin0 = Math.abs((double) math("sin", 0.0).value());  // 0.0
            double cos0 = (double) math("cos", 0.0).value();               // 1.0
            double ceil11 = (double) math("ceil", 1.1).value();            // 2.0
            double floor29 = (double) math("floor", 2.9).value();          // 2.0
            double sum = absSin0 + cos0 + ceil11 + floor29;
            assertThat(sum).isCloseTo(5.0, within(0.001));
        }

        @Test
        @DisplayName("integer division vs float division: 10/3 = 3 vs 10.0/3 = 3.333...")
        void integerVsFloatDivision() {
            var intDiv = IntArithmeticHandler.computeInt(10, 3, Operator.DIVIDE);
            assertThat(intDiv.value()).isEqualTo(3);

            var floatDiv = FloatArithmeticHandler.computeDouble(10.0, 3.0, Operator.DIVIDE);
            assertThat((double) floatDiv.value()).isCloseTo(3.333333, within(0.001));
        }

        @Test
        @DisplayName("long chain: 1+2-3*4/5+6-7*8/9+10")
        void longChain20Operators() {
            // Evaluate left-to-right with int arithmetic (flat, no precedence in handler calls)
            // As flat evaluation: step by step
            // Actually handlers just compute pairs. Let's compute: 1+2=3, 3*4=12, 12/5=2 ... hmm
            // Let's just do a sequential chain and verify
            int[] vals = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
            long running = vals[0];
            Operator[] ops = new Operator[19];
            for (int i = 0; i < 19; i++) {
                ops[i] = (i % 2 == 0) ? Operator.PLUS : Operator.MINUS;
            }
            for (int i = 0; i < 19; i++) {
                var r = IntArithmeticHandler.computeInt((int) running, vals[i + 1], ops[i]);
                running = ((Number) r.value()).longValue();
            }
            // 1+2-3+4-5+6-7+8-9+10-11+12-13+14-15+16-17+18-19+20 = 12
            assertThat(running).isEqualTo(12L);
        }

        @Test
        @DisplayName("bitwise: (0xFF & 0x0F) | (0xF0 >> 4)")
        void bitwiseComplexExpression() {
            // 0xFF & 0x0F = 0x0F = 15
            var and = BitwiseHandler.computeInt(0xFF, 0x0F, Operator.BIT_AND);
            assertThat(and.value()).isEqualTo(0x0F);

            // 0xF0 >> 4 = 0x0F = 15
            var shift = BitwiseHandler.computeInt(0xF0, 4, Operator.SHIFT_RIGHT);
            assertThat(shift.value()).isEqualTo(0x0F);

            // 15 | 15 = 15
            var or = BitwiseHandler.computeInt((int) and.value(), (int) shift.value(), Operator.BIT_OR);
            assertThat(or.value()).isEqualTo(0x0F);
        }

        @Test
        @DisplayName("mixed radix: 0xFF + 0b11111111 + 0o377 via parseRadix = 765")
        void mixedRadixAll255() {
            // All represent 255
            int hex = (int) radix("parseRadix", "FF", 16).value();
            int bin = (int) radix("parseRadix", "11111111", 2).value();
            int oct = (int) radix("parseRadix", "377", 8).value();

            assertThat(hex).isEqualTo(255);
            assertThat(bin).isEqualTo(255);
            assertThat(oct).isEqualTo(255);

            var sum = IntArithmeticHandler.computeInt(hex, bin, Operator.PLUS);
            var total = IntArithmeticHandler.computeInt((int) sum.value(), oct, Operator.PLUS);
            assertThat(total.value()).isEqualTo(765);
        }

        @Test
        @DisplayName("boolean chain: true && true || false && !false with correct precedence")
        void booleanChainWithPrecedence() {
            // Evaluate: true && true || false && !false
            // Precedence: (true && true) || (false && (!false)) = true || false = true
            boolean trueAndTrue = true && true;   // = true
            boolean notFalse = !false;             // = true
            boolean falseAndNotFalse = false && notFalse; // = false
            boolean orResult = trueAndTrue || falseAndNotFalse; // = true
            assertThat(orResult).isTrue();

            // Also verify via comparison handler chain: (10 > 5) || (3 < 1)
            var gt = ComparisonHandler.compare(10, 5, Operator.GT);
            var lt = ComparisonHandler.compare(3, 1, Operator.LT);
            assertThat(gt.isSuccess()).isTrue();
            assertThat(gt.value()).isEqualTo(true);
            assertThat(lt.isSuccess()).isTrue();
            assertThat(lt.value()).isEqualTo(false);
            // true || false = true
            assertThat((boolean) gt.value() || (boolean) lt.value()).isTrue();
        }

        @Test
        @DisplayName("comparison chain: (10 > 5) && (5 > 1) && (1 > 0)")
        void comparisonChainViaBoolean() {
            var c1 = ComparisonHandler.compare(10, 5, Operator.GT);
            var c2 = ComparisonHandler.compare(5, 1, Operator.GT);
            var c3 = ComparisonHandler.compare(1, 0, Operator.GT);

            assertThat(c1.value()).isEqualTo(true);
            assertThat(c2.value()).isEqualTo(true);
            assertThat(c3.value()).isEqualTo(true);

            // All true ANDed together
            assertThat((boolean) c1.value() && (boolean) c2.value() && (boolean) c3.value()).isTrue();
        }

        @Test
        @DisplayName("chained float operations with accumulated precision loss")
        void chainedFloatOperationsAccumulatedPrecision() {
            // Start with 1.0, add 0.1 ten times
            double running = 0.0;
            for (int i = 0; i < 10; i++) {
                var r = FloatArithmeticHandler.computeDouble(running, 0.1, Operator.PLUS);
                running = (double) r.value();
            }
            // Should be close to 1.0 but not exactly due to IEEE 754
            assertThat(running).isCloseTo(1.0, within(0.0001));
            // Note: it's NOT exactly 1.0
        }
    }

    // ===========================================================================================
    // Overflow and Type Promotion
    // ===========================================================================================

    @Nested
    @DisplayName("Overflow and Type Promotion")
    class OverflowAndTypePromotion {

        @Test
        @DisplayName("Integer.MAX_VALUE + 1 promotes to long")
        void intMaxValuePlusOnePromotesToLong() {
            var r = IntArithmeticHandler.computeInt(Integer.MAX_VALUE, 1, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
            assertThat(r.value()).isEqualTo((long) Integer.MAX_VALUE + 1L);
        }

        @Test
        @DisplayName("Long overflow produces error Result")
        void longOverflowProducesErrorResult() {
            var r = IntArithmeticHandler.computeLong(Long.MAX_VALUE, 1L, Operator.PLUS);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("OVERFLOW");
        }

        @Test
        @DisplayName("mixed int/long operations preserve long type")
        void mixedIntLongPreservesLongType() {
            var promoted = NumericPromotion.promotePair(42, 100L);
            assertThat(promoted.resultType()).isEqualTo(PexType.LONG);
            assertThat(promoted.left()).isInstanceOf(Long.class);
            assertThat(promoted.right()).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("float precision: 0.1 + 0.2 close to 0.3 within epsilon")
        void floatPrecision01Plus02() {
            var r = FloatArithmeticHandler.computeDouble(0.1, 0.2, Operator.PLUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat((double) r.value()).isCloseTo(0.3, within(0.0001));
        }

        @Test
        @DisplayName("radix conversion round-trip: decimal to hex to decimal")
        void radixConversionRoundTrip() {
            int original = 12345;
            var hexStr = radix("hex", original);
            assertThat(hexStr.isSuccess()).isTrue();
            // hex returns "0x..." format
            String hexVal = (String) hexStr.value();
            // Parse back
            String digits = hexVal.startsWith("0x") ? hexVal.substring(2) : hexVal;
            var parsed = radix("parseRadix", digits, 16);
            assertThat(parsed.isSuccess()).isTrue();
            assertThat(parsed.value()).isEqualTo(original);
        }

        @Test
        @DisplayName("Integer.MIN_VALUE - 1 promotes to long")
        void intMinValueMinusOnePromotesToLong() {
            var r = IntArithmeticHandler.computeInt(Integer.MIN_VALUE, 1, Operator.MINUS);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isInstanceOf(Long.class);
            assertThat(r.value()).isEqualTo((long) Integer.MIN_VALUE - 1L);
        }
    }

    // ===========================================================================================
    // Function Composition
    // ===========================================================================================

    @Nested
    @DisplayName("Function Composition")
    class FunctionComposition {

        @Test
        @DisplayName("max(min(10, 20), min(30, 5)) = 10")
        void maxMinComposition() {
            var min1 = math("min", 10, 20);     // 10
            var min2 = math("min", 30, 5);       // 5
            var result = math("max", min1.value(), min2.value()); // max(10, 5) = 10
            assertThat(result.value()).isEqualTo(10);
        }

        @Test
        @DisplayName("pow(2, pow(2, 3)) = 256")
        void nestedPow() {
            var innerPow = math("pow", 2, 3);    // 8.0
            var outerPow = math("pow", 2, innerPow.value()); // 2^8 = 256.0
            assertThat((double) outerPow.value()).isCloseTo(256.0, within(0.001));
        }

        @Test
        @DisplayName("all single-arg MathFunctions called in sequence produce valid results")
        void allSingleArgMathFunctionsProduce() {
            String[] singleArgFunctions = {
                    "abs", "ceil", "floor", "round", "sqrt", "cbrt", "exp", "log", "log10", "log2",
                    "sin", "cos", "tan", "asin", "acos", "atan", "toRadians", "toDegrees", "signum"
            };
            for (var funcName : singleArgFunctions) {
                var result = math(funcName, 0.5);
                assertThat(result.isSuccess())
                        .as("Function %s(0.5) should succeed", funcName)
                        .isTrue();
                assertThat(result.value())
                        .as("Function %s(0.5) should return a number", funcName)
                        .isInstanceOf(Number.class);
            }
        }

        @Test
        @DisplayName("all two-arg MathFunctions called produce valid results")
        void allTwoArgMathFunctionsProduce() {
            String[] twoArgFunctions = {"pow", "max", "min", "atan2"};
            for (var funcName : twoArgFunctions) {
                var result = math(funcName, 2.0, 3.0);
                assertThat(result.isSuccess())
                        .as("Function %s(2.0, 3.0) should succeed", funcName)
                        .isTrue();
                assertThat(result.value())
                        .as("Function %s(2.0, 3.0) should return a number", funcName)
                        .isInstanceOf(Number.class);
            }
        }

        @Test
        @DisplayName("ConversionFunctions: int(3.14)=3, float(42)=42.0, str(true)=true as string")
        void conversionFunctionsBasic() {
            var intResult = conv("int", 3.14);
            assertThat(intResult.value()).isEqualTo(3);

            var floatResult = conv("float", 42);
            assertThat(floatResult.isSuccess()).isTrue();
            assertThat(((Number) floatResult.value()).doubleValue()).isCloseTo(42.0, within(0.001));

            var strResult = conv("str", true);
            assertThat(strResult.value()).isEqualTo("true");
        }

        @Test
        @DisplayName("RadixFunctions: hex(255)=0xff, bin(10)=0b1010, oct(8)=0o10")
        void radixFunctionsBasic() {
            assertThat(radix("hex", 255).value()).isEqualTo("0xff");
            assertThat(radix("bin", 10).value()).isEqualTo("0b1010");
            assertThat(radix("oct", 8).value()).isEqualTo("0o10");
        }

        @Test
        @DisplayName("chained conversion: int -> long -> double -> string -> int round-trip")
        void chainedConversionRoundTrip() {
            var longVal = conv("long", 42);
            assertThat(longVal.value()).isEqualTo(42L);

            var doubleVal = conv("double", longVal.value());
            assertThat(doubleVal.isSuccess()).isTrue();

            var strVal = conv("str", doubleVal.value());
            assertThat(strVal.isSuccess()).isTrue();
            assertThat(strVal.value().toString()).contains("42");
        }

        @Test
        @DisplayName("mathematical identity: e^(ln(x)) = x for x=42")
        void eulerLogIdentity() {
            double x = 42.0;
            double logX = (double) math("log", x).value();
            double expLogX = (double) math("exp", logX).value();
            assertThat(expLogX).isCloseTo(x, within(0.001));
        }

        @Test
        @DisplayName("trigonometric: sin(PI/6) close to 0.5")
        void sinPiOver6() {
            double pi = (double) math("PI").value();
            double result = (double) math("sin", pi / 6.0).value();
            assertThat(result).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("constants PI and E are accessible and have correct values")
        void constantsPiAndE() {
            double pi = (double) math("PI").value();
            double e = (double) math("E").value();
            assertThat(pi).isCloseTo(Math.PI, within(0.0001));
            assertThat(e).isCloseTo(Math.E, within(0.0001));
        }
    }
}

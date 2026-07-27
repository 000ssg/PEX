package ssg.pex.arithmetics.function;

import ssg.pex.exec.NativeFunction;
import ssg.pex.result.Result;
import ssg.pex.spi.PluginContext;

import java.util.List;

/**
 * Registers all java.lang.Math static methods as native functions.
 */
public final class MathFunctions {

    private MathFunctions() {}

    /**
     * Register all math functions into the given plugin context.
     */
    public static void register(PluginContext ctx) {
        // Single-argument functions
        ctx.registerFunction("abs", List.of("x"), unaryMath(MathFunctions::abs));
        ctx.registerFunction("ceil", List.of("x"), unaryDouble(Math::ceil));
        ctx.registerFunction("floor", List.of("x"), unaryDouble(Math::floor));
        ctx.registerFunction("round", List.of("x"), unaryMath(MathFunctions::round));
        ctx.registerFunction("sqrt", List.of("x"), unaryDouble(Math::sqrt));
        ctx.registerFunction("cbrt", List.of("x"), unaryDouble(Math::cbrt));
        ctx.registerFunction("exp", List.of("x"), unaryDouble(Math::exp));
        ctx.registerFunction("log", List.of("x"), unaryDouble(Math::log));
        ctx.registerFunction("log10", List.of("x"), unaryDouble(Math::log10));
        ctx.registerFunction("log2", List.of("x"), unaryDouble(MathFunctions::log2));
        ctx.registerFunction("sin", List.of("x"), unaryDouble(Math::sin));
        ctx.registerFunction("cos", List.of("x"), unaryDouble(Math::cos));
        ctx.registerFunction("tan", List.of("x"), unaryDouble(Math::tan));
        ctx.registerFunction("asin", List.of("x"), unaryDouble(Math::asin));
        ctx.registerFunction("acos", List.of("x"), unaryDouble(Math::acos));
        ctx.registerFunction("atan", List.of("x"), unaryDouble(Math::atan));
        ctx.registerFunction("toRadians", List.of("x"), unaryDouble(Math::toRadians));
        ctx.registerFunction("toDegrees", List.of("x"), unaryDouble(Math::toDegrees));
        ctx.registerFunction("signum", List.of("x"), unaryDouble(Math::signum));

        // Two-argument functions
        ctx.registerFunction("pow", List.of("x", "y"), binaryDouble(Math::pow));
        ctx.registerFunction("max", List.of("x", "y"), binaryMath(MathFunctions::max));
        ctx.registerFunction("min", List.of("x", "y"), binaryMath(MathFunctions::min));
        ctx.registerFunction("atan2", List.of("y", "x"), binaryDouble(Math::atan2));

        // Zero-argument / constants
        ctx.registerFunction("random", List.of(), (args, ectx) -> Result.success(Math.random()));
        ctx.registerFunction("PI", List.of(), (args, ectx) -> Result.success(Math.PI));
        ctx.registerFunction("E", List.of(), (args, ectx) -> Result.success(Math.E));
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    private static Object abs(Number n) {
        if (n instanceof Integer i) return Math.abs(i);
        if (n instanceof Long l) return Math.abs(l);
        if (n instanceof Float f) return Math.abs(f);
        return Math.abs(n.doubleValue());
    }

    private static Object round(Number n) {
        if (n instanceof Float f) return Math.round(f);
        return Math.round(n.doubleValue());
    }

    private static Object max(Number a, Number b) {
        if (a instanceof Integer ai && b instanceof Integer bi) return Math.max(ai, bi);
        if (a instanceof Long al && b instanceof Long bl) return Math.max(al, bl);
        if (a instanceof Float af && b instanceof Float bf) return Math.max(af, bf);
        return Math.max(a.doubleValue(), b.doubleValue());
    }

    private static Object min(Number a, Number b) {
        if (a instanceof Integer ai && b instanceof Integer bi) return Math.min(ai, bi);
        if (a instanceof Long al && b instanceof Long bl) return Math.min(al, bl);
        if (a instanceof Float af && b instanceof Float bf) return Math.min(af, bf);
        return Math.min(a.doubleValue(), b.doubleValue());
    }

    @FunctionalInterface
    private interface DoubleUnary {
        double apply(double x);
    }

    @FunctionalInterface
    private interface DoubleBinary {
        double apply(double x, double y);
    }

    @FunctionalInterface
    private interface NumberUnary {
        Object apply(Number x);
    }

    @FunctionalInterface
    private interface NumberBinary {
        Object apply(Number x, Number y);
    }

    private static NativeFunction unaryDouble(DoubleUnary fn) {
        return (args, ctx) -> {
            if (args.size() != 1) {
                return Result.failure("ARITY_ERROR", "Expected 1 argument, got " + args.size());
            }
            Object arg = args.getFirst();
            if (arg instanceof Number n) {
                return Result.success(fn.apply(n.doubleValue()));
            }
            return Result.failure("TYPE_ERROR", "Expected number argument");
        };
    }

    private static NativeFunction binaryDouble(DoubleBinary fn) {
        return (args, ctx) -> {
            if (args.size() != 2) {
                return Result.failure("ARITY_ERROR", "Expected 2 arguments, got " + args.size());
            }
            Object a = args.get(0);
            Object b = args.get(1);
            if (a instanceof Number na && b instanceof Number nb) {
                return Result.success(fn.apply(na.doubleValue(), nb.doubleValue()));
            }
            return Result.failure("TYPE_ERROR", "Expected number arguments");
        };
    }

    private static NativeFunction unaryMath(NumberUnary fn) {
        return (args, ctx) -> {
            if (args.size() != 1) {
                return Result.failure("ARITY_ERROR", "Expected 1 argument, got " + args.size());
            }
            Object arg = args.getFirst();
            if (arg instanceof Number n) {
                return Result.success(fn.apply(n));
            }
            return Result.failure("TYPE_ERROR", "Expected number argument");
        };
    }

    private static NativeFunction binaryMath(NumberBinary fn) {
        return (args, ctx) -> {
            if (args.size() != 2) {
                return Result.failure("ARITY_ERROR", "Expected 2 arguments, got " + args.size());
            }
            Object a = args.get(0);
            Object b = args.get(1);
            if (a instanceof Number na && b instanceof Number nb) {
                return Result.success(fn.apply(na, nb));
            }
            return Result.failure("TYPE_ERROR", "Expected number arguments");
        };
    }
}

package ssg.pex.arithmetics.function;

import ssg.pex.result.Result;
import ssg.pex.spi.PluginContext;

import java.util.List;

/**
 * Registers radix conversion functions:
 * hex(x), bin(x), oct(x) - convert integer to string in given radix
 * toRadix(value, radix) - generic radix conversion
 * parseRadix(str, radix) - parse string in given radix to integer
 */
public final class RadixFunctions {

    private RadixFunctions() {}

    /**
     * Register all radix functions into the given plugin context.
     */
    public static void register(PluginContext ctx) {
        ctx.registerFunction("hex", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "hex() expects 1 argument");
            return toRadixString(args.getFirst(), 16, "0x");
        });

        ctx.registerFunction("bin", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "bin() expects 1 argument");
            return toRadixString(args.getFirst(), 2, "0b");
        });

        ctx.registerFunction("oct", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "oct() expects 1 argument");
            return toRadixString(args.getFirst(), 8, "0o");
        });

        ctx.registerFunction("toRadix", List.of("value", "radix"), (args, ectx) -> {
            if (args.size() != 2) return Result.failure("ARITY_ERROR", "toRadix() expects 2 arguments");
            Object value = args.get(0);
            Object radixObj = args.get(1);

            if (!(radixObj instanceof Number radixNum)) {
                return Result.failure("TYPE_ERROR", "toRadix() radix must be a number");
            }
            int radix = radixNum.intValue();
            if (radix < 2 || radix > 36) {
                return Result.failure("VALUE_ERROR", "Radix must be between 2 and 36, got " + radix);
            }

            return toRadixString(value, radix, "");
        });

        ctx.registerFunction("parseRadix", List.of("str", "radix"), (args, ectx) -> {
            if (args.size() != 2) return Result.failure("ARITY_ERROR", "parseRadix() expects 2 arguments");
            Object strObj = args.get(0);
            Object radixObj = args.get(1);

            if (!(strObj instanceof String str)) {
                return Result.failure("TYPE_ERROR", "parseRadix() first argument must be a string");
            }
            if (!(radixObj instanceof Number radixNum)) {
                return Result.failure("TYPE_ERROR", "parseRadix() second argument must be a number");
            }
            int radix = radixNum.intValue();
            if (radix < 2 || radix > 36) {
                return Result.failure("VALUE_ERROR", "Radix must be between 2 and 36, got " + radix);
            }

            try {
                long result = Long.parseLong(str, radix);
                if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                    return Result.success((int) result);
                }
                return Result.success(result);
            } catch (NumberFormatException e) {
                return Result.failure("PARSE_ERROR",
                        "Cannot parse '" + str + "' as radix-" + radix + " integer");
            }
        });
    }

    private static Result<Object> toRadixString(Object value, int radix, String prefix) {
        if (value instanceof Integer i) {
            return Result.success(prefix + Integer.toString(i, radix));
        }
        if (value instanceof Long l) {
            return Result.success(prefix + Long.toString(l, radix));
        }
        if (value instanceof Number n) {
            return Result.success(prefix + Long.toString(n.longValue(), radix));
        }
        return Result.failure("TYPE_ERROR", "Radix conversion requires integer value, got " +
                (value == null ? "null" : value.getClass().getSimpleName()));
    }
}

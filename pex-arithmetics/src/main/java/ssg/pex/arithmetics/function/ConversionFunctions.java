package ssg.pex.arithmetics.function;

import ssg.pex.result.Result;
import ssg.pex.spi.PluginContext;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeCoercion;

import java.util.List;

/**
 * Registers type conversion functions:
 * int(x), long(x), float(x), double(x), str(x), bool(x),
 * intBitsToFloat(x), floatToIntBits(x), longBitsToDouble(x), doubleToLongBits(x),
 * intToFloat(x), longToDouble(x), floatToInt(x), doubleToLong(x).
 */
public final class ConversionFunctions {

    private ConversionFunctions() {}

    /**
     * Register all conversion functions into the given plugin context.
     */
    public static void register(PluginContext ctx) {
        // Basic type conversions
        ctx.registerFunction("int", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "int() expects 1 argument");
            return TypeCoercion.coerce(args.getFirst(), PexType.INT);
        });

        ctx.registerFunction("long", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "long() expects 1 argument");
            return TypeCoercion.coerce(args.getFirst(), PexType.LONG);
        });

        ctx.registerFunction("float", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "float() expects 1 argument");
            return TypeCoercion.coerce(args.getFirst(), PexType.FLOAT);
        });

        ctx.registerFunction("double", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "double() expects 1 argument");
            return TypeCoercion.coerce(args.getFirst(), PexType.DOUBLE);
        });

        ctx.registerFunction("str", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "str() expects 1 argument");
            return TypeCoercion.coerce(args.getFirst(), PexType.STRING);
        });

        ctx.registerFunction("bool", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "bool() expects 1 argument");
            return TypeCoercion.coerce(args.getFirst(), PexType.BOOL);
        });

        // Bit-level conversions
        ctx.registerFunction("intBitsToFloat", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "intBitsToFloat() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Integer i) {
                return Result.success(Float.intBitsToFloat(i));
            }
            if (arg instanceof Long l) {
                return Result.success(Float.intBitsToFloat(l.intValue()));
            }
            return Result.failure("TYPE_ERROR", "intBitsToFloat() requires integer argument");
        });

        ctx.registerFunction("floatToIntBits", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "floatToIntBits() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Float f) {
                return Result.success(Float.floatToIntBits(f));
            }
            if (arg instanceof Double d) {
                return Result.success(Float.floatToIntBits(d.floatValue()));
            }
            return Result.failure("TYPE_ERROR", "floatToIntBits() requires float argument");
        });

        ctx.registerFunction("longBitsToDouble", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "longBitsToDouble() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Long l) {
                return Result.success(Double.longBitsToDouble(l));
            }
            if (arg instanceof Integer i) {
                return Result.success(Double.longBitsToDouble(i.longValue()));
            }
            return Result.failure("TYPE_ERROR", "longBitsToDouble() requires long argument");
        });

        ctx.registerFunction("doubleToLongBits", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "doubleToLongBits() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Double d) {
                return Result.success(Double.doubleToLongBits(d));
            }
            if (arg instanceof Float f) {
                return Result.success(Double.doubleToLongBits(f.doubleValue()));
            }
            return Result.failure("TYPE_ERROR", "doubleToLongBits() requires double argument");
        });

        // Convenient numeric conversions
        ctx.registerFunction("intToFloat", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "intToFloat() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Integer i) return Result.success((float) i);
            if (arg instanceof Long l) return Result.success((float) l.intValue());
            return Result.failure("TYPE_ERROR", "intToFloat() requires integer argument");
        });

        ctx.registerFunction("longToDouble", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "longToDouble() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Long l) return Result.success((double) l);
            if (arg instanceof Integer i) return Result.success((double) i);
            return Result.failure("TYPE_ERROR", "longToDouble() requires long argument");
        });

        ctx.registerFunction("floatToInt", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "floatToInt() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Float f) return Result.success((int) f.floatValue());
            if (arg instanceof Double d) return Result.success((int) d.doubleValue());
            return Result.failure("TYPE_ERROR", "floatToInt() requires float argument");
        });

        ctx.registerFunction("doubleToLong", List.of("x"), (args, ectx) -> {
            if (args.size() != 1) return Result.failure("ARITY_ERROR", "doubleToLong() expects 1 argument");
            Object arg = args.getFirst();
            if (arg instanceof Double d) return Result.success((long) d.doubleValue());
            if (arg instanceof Float f) return Result.success((long) f.floatValue());
            return Result.failure("TYPE_ERROR", "doubleToLong() requires double argument");
        });
    }
}

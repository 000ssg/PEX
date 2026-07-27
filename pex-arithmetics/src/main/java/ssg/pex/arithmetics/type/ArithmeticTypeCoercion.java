package ssg.pex.arithmetics.type;

import ssg.pex.result.Result;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeCoercion;

/**
 * Arithmetic-specific coercion rules: converts operands to compatible types before operation.
 */
public final class ArithmeticTypeCoercion {

    private ArithmeticTypeCoercion() {}

    /**
     * Coerce a value to a numeric type suitable for arithmetic.
     * Booleans are converted to int (true=1, false=0).
     * Strings are parsed as numbers.
     * Null becomes 0.
     */
    public static Result<Number> coerceToNumber(Object value) {
        if (value == null) {
            return Result.success(0);
        }
        if (value instanceof Number num) {
            return Result.success(num);
        }
        if (value instanceof Boolean b) {
            return Result.success(b ? 1 : 0);
        }
        if (value instanceof String s) {
            return parseString(s);
        }
        return Result.failure("TYPE_ERROR", "Cannot coerce " + value.getClass().getSimpleName() + " to number");
    }

    /**
     * Coerce a value to boolean for logical operations.
     */
    public static Result<Boolean> coerceToBoolean(Object value) {
        if (value == null) {
            return Result.success(false);
        }
        if (value instanceof Boolean b) {
            return Result.success(b);
        }
        if (value instanceof Number num) {
            return Result.success(num.doubleValue() != 0.0);
        }
        if (value instanceof String s) {
            return Result.success(!s.isEmpty());
        }
        return Result.failure("TYPE_ERROR", "Cannot coerce " + value.getClass().getSimpleName() + " to boolean");
    }

    /**
     * Coerce a value to an integer type (int or long) for bitwise operations.
     */
    public static Result<Number> coerceToInteger(Object value) {
        if (value == null) {
            return Result.success(0);
        }
        if (value instanceof Integer i) {
            return Result.success(i);
        }
        if (value instanceof Long l) {
            return Result.success(l);
        }
        if (value instanceof Boolean b) {
            return Result.success(b ? 1 : 0);
        }
        if (value instanceof Float || value instanceof Double) {
            return Result.failure("TYPE_ERROR", "Bitwise operations require integer types, got floating-point");
        }
        if (value instanceof String s) {
            try {
                return Result.success(Long.parseLong(s));
            } catch (NumberFormatException e) {
                return Result.failure("TYPE_ERROR", "Cannot parse '" + s + "' as integer");
            }
        }
        return Result.failure("TYPE_ERROR", "Cannot coerce " + value.getClass().getSimpleName() + " to integer");
    }

    /**
     * Coerce both operands to a compatible numeric type for a binary operation.
     */
    public static Result<NumericPromotion.PromotedPair> coerceForBinaryOp(Object left, Object right) {
        var leftResult = coerceToNumber(left);
        if (leftResult.isFailure()) {
            return Result.failure(leftResult.error());
        }
        var rightResult = coerceToNumber(right);
        if (rightResult.isFailure()) {
            return Result.failure(rightResult.error());
        }
        try {
            return Result.success(NumericPromotion.promotePair(leftResult.value(), rightResult.value()));
        } catch (IllegalArgumentException e) {
            return Result.failure("TYPE_ERROR", e.getMessage());
        }
    }

    private static Result<Number> parseString(String s) {
        try {
            if (s.contains(".") || s.contains("e") || s.contains("E")) {
                return Result.success(Double.parseDouble(s));
            }
            try {
                return Result.success(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                return Result.success(Long.parseLong(s));
            }
        } catch (NumberFormatException e) {
            return Result.failure("TYPE_ERROR", "Cannot parse '" + s + "' as number");
        }
    }
}

package ssg.pex.arithmetics.type;

import ssg.pex.type.PexType;

/**
 * Handles numeric type promotion for binary operations.
 * <p>
 * Promotion rules:
 * <ul>
 *   <li>int op int -> int (or long on overflow)</li>
 *   <li>int op long -> long</li>
 *   <li>int op float -> float</li>
 *   <li>int op double -> double</li>
 *   <li>long op float -> double (to avoid precision loss)</li>
 *   <li>long op double -> double</li>
 *   <li>float op double -> double</li>
 * </ul>
 */
public final class NumericPromotion {

    private NumericPromotion() {}

    /**
     * Determines the result type when performing a binary operation on two numeric types.
     * Special case: long op float -> double to avoid precision loss.
     */
    public static PexType promote(PexType left, PexType right) {
        if (!left.isNumeric() || !right.isNumeric()) {
            throw new IllegalArgumentException("Both types must be numeric: " + left + ", " + right);
        }

        if (left == right) {
            return left;
        }

        // Special case: long + float -> double to avoid precision loss
        if ((left == PexType.LONG && right == PexType.FLOAT) ||
            (left == PexType.FLOAT && right == PexType.LONG)) {
            return PexType.DOUBLE;
        }

        // Otherwise, widen to the larger type
        return wider(left, right);
    }

    /**
     * Promotes a value to the target numeric type.
     */
    public static Number promoteValue(Object value, PexType targetType) {
        if (value instanceof Number num) {
            return switch (targetType) {
                case INT -> num.intValue();
                case LONG -> num.longValue();
                case FLOAT -> num.floatValue();
                case DOUBLE -> num.doubleValue();
                default -> throw new IllegalArgumentException("Target type must be numeric: " + targetType);
            };
        }
        throw new IllegalArgumentException("Value must be a Number: " + (value == null ? "null" : value.getClass()));
    }

    /**
     * Promotes both operands to their common type and returns a pair.
     */
    public static PromotedPair promotePair(Object left, Object right) {
        PexType leftType = inferNumericType(left);
        PexType rightType = inferNumericType(right);
        PexType resultType = promote(leftType, rightType);
        return new PromotedPair(
                promoteValue(left, resultType),
                promoteValue(right, resultType),
                resultType
        );
    }

    /**
     * Check if a value is a numeric type.
     */
    public static boolean isNumeric(Object value) {
        return value instanceof Integer || value instanceof Long ||
               value instanceof Float || value instanceof Double;
    }

    /**
     * Infer the PexType for a numeric value.
     */
    public static PexType inferNumericType(Object value) {
        if (value instanceof Integer) return PexType.INT;
        if (value instanceof Long) return PexType.LONG;
        if (value instanceof Float) return PexType.FLOAT;
        if (value instanceof Double) return PexType.DOUBLE;
        throw new IllegalArgumentException("Value is not numeric: " + (value == null ? "null" : value.getClass()));
    }

    private static PexType wider(PexType a, PexType b) {
        int oa = ordinal(a);
        int ob = ordinal(b);
        return oa >= ob ? a : b;
    }

    private static int ordinal(PexType t) {
        return switch (t) {
            case INT -> 0;
            case LONG -> 1;
            case FLOAT -> 2;
            case DOUBLE -> 3;
            default -> -1;
        };
    }

    /**
     * Result of promoting two operands to a common type.
     */
    public record PromotedPair(Number left, Number right, PexType resultType) {}
}

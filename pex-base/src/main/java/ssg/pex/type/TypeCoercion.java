package ssg.pex.type;

import ssg.pex.result.Result;

import java.util.List;
import java.util.Map;

public final class TypeCoercion {

    private TypeCoercion() {}

    private static final List<PexType> NUMERIC_WIDENING_ORDER = List.of(
            PexType.INT, PexType.LONG, PexType.FLOAT, PexType.DOUBLE
    );

    public static Result<Object> coerce(Object value, PexType targetType) {
        if (value == null) {
            return switch (targetType) {
                case NULL -> Result.success(null);
                case STRING -> Result.success("null");
                case BOOL -> Result.success(false);
                case INT -> Result.success(0);
                case LONG -> Result.success(0L);
                case FLOAT -> Result.success(0.0f);
                case DOUBLE -> Result.success(0.0);
                default -> Result.failure("TYPE_COERCION", "Cannot coerce null to " + targetType.displayName());
            };
        }

        PexType sourceType = inferType(value);

        if (sourceType == targetType) {
            return Result.success(value);
        }

        return switch (targetType) {
            case INT -> coerceToInt(value, sourceType);
            case LONG -> coerceToLong(value, sourceType);
            case FLOAT -> coerceToFloat(value, sourceType);
            case DOUBLE -> coerceToDouble(value, sourceType);
            case STRING -> Result.success(String.valueOf(value));
            case BOOL -> coerceToBool(value, sourceType);
            default -> Result.failure("TYPE_COERCION",
                    "Cannot coerce " + sourceType.displayName() + " to " + targetType.displayName());
        };
    }

    public static PexType widenNumeric(PexType a, PexType b) {
        if (!a.isNumeric() || !b.isNumeric()) {
            throw new IllegalArgumentException("Both types must be numeric: " + a + ", " + b);
        }
        int indexA = NUMERIC_WIDENING_ORDER.indexOf(a);
        int indexB = NUMERIC_WIDENING_ORDER.indexOf(b);
        return NUMERIC_WIDENING_ORDER.get(Math.max(indexA, indexB));
    }

    public static boolean canImplicitlyCoerce(PexType from, PexType to) {
        if (from == to) return true;
        if (from == PexType.NULL) return true;

        if (from.isNumeric() && to.isNumeric()) {
            int indexFrom = NUMERIC_WIDENING_ORDER.indexOf(from);
            int indexTo = NUMERIC_WIDENING_ORDER.indexOf(to);
            return indexTo >= indexFrom;
        }

        return to == PexType.STRING;
    }

    public static boolean canExplicitlyCoerce(PexType from, PexType to) {
        if (canImplicitlyCoerce(from, to)) return true;

        if (from.isNumeric() && to.isNumeric()) return true;

        if (from == PexType.STRING && to.isNumeric()) return true;
        if (from == PexType.STRING && to == PexType.BOOL) return true;
        if (from == PexType.BOOL && to.isNumeric()) return true;

        return false;
    }

    public static PexType inferType(Object value) {
        if (value == null) return PexType.NULL;
        if (value instanceof Integer) return PexType.INT;
        if (value instanceof Long) return PexType.LONG;
        if (value instanceof Float) return PexType.FLOAT;
        if (value instanceof Double) return PexType.DOUBLE;
        if (value instanceof String) return PexType.STRING;
        if (value instanceof Boolean) return PexType.BOOL;
        if (value instanceof List<?>) return PexType.ARRAY;
        if (value instanceof Map<?, ?>) return PexType.MAP;
        return PexType.VOID;
    }

    private static Result<Object> coerceToInt(Object value, PexType sourceType) {
        return switch (sourceType) {
            case LONG -> Result.success(((Long) value).intValue());
            case FLOAT -> Result.success(((Float) value).intValue());
            case DOUBLE -> Result.success(((Double) value).intValue());
            case BOOL -> Result.success(((Boolean) value) ? 1 : 0);
            case STRING -> {
                try {
                    yield Result.success(Integer.parseInt((String) value));
                } catch (NumberFormatException e) {
                    yield Result.failure("TYPE_COERCION", "Cannot parse '" + value + "' as int");
                }
            }
            default -> Result.failure("TYPE_COERCION",
                    "Cannot coerce " + sourceType.displayName() + " to int");
        };
    }

    private static Result<Object> coerceToLong(Object value, PexType sourceType) {
        return switch (sourceType) {
            case INT -> Result.success(((Integer) value).longValue());
            case FLOAT -> Result.success(((Float) value).longValue());
            case DOUBLE -> Result.success(((Double) value).longValue());
            case BOOL -> Result.success(((Boolean) value) ? 1L : 0L);
            case STRING -> {
                try {
                    yield Result.success(Long.parseLong((String) value));
                } catch (NumberFormatException e) {
                    yield Result.failure("TYPE_COERCION", "Cannot parse '" + value + "' as long");
                }
            }
            default -> Result.failure("TYPE_COERCION",
                    "Cannot coerce " + sourceType.displayName() + " to long");
        };
    }

    private static Result<Object> coerceToFloat(Object value, PexType sourceType) {
        return switch (sourceType) {
            case INT -> Result.success(((Integer) value).floatValue());
            case LONG -> Result.success(((Long) value).floatValue());
            case DOUBLE -> Result.success(((Double) value).floatValue());
            case BOOL -> Result.success(((Boolean) value) ? 1.0f : 0.0f);
            case STRING -> {
                try {
                    yield Result.success(Float.parseFloat((String) value));
                } catch (NumberFormatException e) {
                    yield Result.failure("TYPE_COERCION", "Cannot parse '" + value + "' as float");
                }
            }
            default -> Result.failure("TYPE_COERCION",
                    "Cannot coerce " + sourceType.displayName() + " to float");
        };
    }

    private static Result<Object> coerceToDouble(Object value, PexType sourceType) {
        return switch (sourceType) {
            case INT -> Result.success(((Integer) value).doubleValue());
            case LONG -> Result.success(((Long) value).doubleValue());
            case FLOAT -> Result.success(((Float) value).doubleValue());
            case BOOL -> Result.success(((Boolean) value) ? 1.0 : 0.0);
            case STRING -> {
                try {
                    yield Result.success(Double.parseDouble((String) value));
                } catch (NumberFormatException e) {
                    yield Result.failure("TYPE_COERCION", "Cannot parse '" + value + "' as double");
                }
            }
            default -> Result.failure("TYPE_COERCION",
                    "Cannot coerce " + sourceType.displayName() + " to double");
        };
    }

    private static Result<Object> coerceToBool(Object value, PexType sourceType) {
        return switch (sourceType) {
            case INT -> Result.success(((Integer) value) != 0);
            case LONG -> Result.success(((Long) value) != 0L);
            case FLOAT -> Result.success(((Float) value) != 0.0f);
            case DOUBLE -> Result.success(((Double) value) != 0.0);
            case STRING -> Result.success(!((String) value).isEmpty());
            case NULL -> Result.success(false);
            default -> Result.failure("TYPE_COERCION",
                    "Cannot coerce " + sourceType.displayName() + " to bool");
        };
    }
}

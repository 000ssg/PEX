package ssg.pex.sql.dialects.oracle;

import ssg.pex.sql.dialects.DialectFunctionRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Registers Oracle-specific SQL functions.
 */
public final class OracleFunctions {

    private OracleFunctions() {}

    public static void register(DialectFunctionRegistry registry) {
        registry.register("NVL", OracleFunctions::nvl);
        registry.register("NVL2", OracleFunctions::nvl2);
        registry.register("DECODE", OracleFunctions::decode);
        registry.register("TO_CHAR", OracleFunctions::toChar);
        registry.register("TO_DATE", OracleFunctions::toDate);
        registry.register("TO_NUMBER", OracleFunctions::toNumber);
        registry.register("SYSDATE", OracleFunctions::sysdate);
        registry.register("MONTHS_BETWEEN", OracleFunctions::monthsBetween);
        registry.register("ADD_MONTHS", OracleFunctions::addMonths);
        registry.register("TRUNC", OracleFunctions::trunc);
        registry.register("REGEXP_LIKE", OracleFunctions::regexpLike);
        registry.register("REGEXP_SUBSTR", OracleFunctions::regexpSubstr);
    }

    /**
     * NVL(expr, default) - returns default if expr is null.
     */
    private static Object nvl(List<Object> args) {
        if (args.size() < 2) return null;
        return args.get(0) != null ? args.get(0) : args.get(1);
    }

    /**
     * NVL2(expr, notNull, isNull) - returns notNull if expr is not null, isNull otherwise.
     */
    private static Object nvl2(List<Object> args) {
        if (args.size() < 3) return null;
        return args.get(0) != null ? args.get(1) : args.get(2);
    }

    /**
     * DECODE(expr, search1, result1, ..., default)
     */
    private static Object decode(List<Object> args) {
        if (args.size() < 3) return null;
        Object expr = args.get(0);
        for (int i = 1; i + 1 < args.size(); i += 2) {
            Object search = args.get(i);
            Object result = args.get(i + 1);
            if (valuesEqual(expr, search)) {
                return result;
            }
        }
        // If odd number of remaining args, last one is default
        if (args.size() % 2 == 0) {
            return args.getLast();
        }
        return null;
    }

    /**
     * TO_CHAR(value, format) - converts value to string with format.
     */
    private static Object toChar(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        Object val = args.get(0);
        if (args.size() >= 2 && args.get(1) != null) {
            String format = args.get(1).toString();
            if (val instanceof Number n) {
                // Simplified numeric formatting
                return String.valueOf(n);
            }
            // Date formatting would go here
            return val.toString();
        }
        return val.toString();
    }

    /**
     * TO_DATE(str, format) - parses string to date.
     */
    private static Object toDate(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        String str = args.get(0).toString();
        // Simplified: return the string as-is (in a real impl, would parse with format)
        return str;
    }

    /**
     * TO_NUMBER(str) - converts string to number.
     */
    private static Object toNumber(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        String str = args.get(0).toString().trim();
        try {
            if (str.contains(".")) return Double.parseDouble(str);
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * SYSDATE - returns current date.
     */
    private static Object sysdate(List<Object> args) {
        return LocalDate.now().toString();
    }

    /**
     * MONTHS_BETWEEN(d1, d2) - returns months between two dates.
     */
    private static Object monthsBetween(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return null;
        try {
            LocalDate d1 = LocalDate.parse(args.get(0).toString());
            LocalDate d2 = LocalDate.parse(args.get(1).toString());
            long months = ChronoUnit.MONTHS.between(d2, d1);
            return (double) months;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ADD_MONTHS(date, n) - adds n months to date.
     */
    private static Object addMonths(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return null;
        try {
            LocalDate date = LocalDate.parse(args.get(0).toString());
            long months = ((Number) args.get(1)).longValue();
            return date.plusMonths(months).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * TRUNC(date) - truncates to day.
     */
    private static Object trunc(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        try {
            // If it's a date string, truncate to day (already is)
            LocalDate date = LocalDate.parse(args.get(0).toString().substring(0, 10));
            return date.toString();
        } catch (Exception e) {
            // Numeric truncation
            if (args.get(0) instanceof Number n) {
                return (long) Math.floor(n.doubleValue());
            }
            return args.get(0);
        }
    }

    /**
     * REGEXP_LIKE(str, pattern) - returns true if str matches pattern.
     */
    private static Object regexpLike(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return false;
        String str = args.get(0).toString();
        String pattern = args.get(1).toString();
        try {
            return Pattern.compile(pattern).matcher(str).find();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * REGEXP_SUBSTR(str, pattern) - returns first match of pattern in str.
     */
    private static Object regexpSubstr(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return null;
        String str = args.get(0).toString();
        String pattern = args.get(1).toString();
        try {
            var matcher = Pattern.compile(pattern).matcher(str);
            if (matcher.find()) {
                return matcher.group();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        return a.toString().equals(b.toString());
    }
}

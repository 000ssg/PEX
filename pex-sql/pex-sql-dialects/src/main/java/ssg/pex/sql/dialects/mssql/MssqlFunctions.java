package ssg.pex.sql.dialects.mssql;

import ssg.pex.sql.dialects.DialectFunctionRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Registers MSSQL-specific SQL functions.
 */
public final class MssqlFunctions {

    private MssqlFunctions() {}

    public static void register(DialectFunctionRegistry registry) {
        registry.register("ISNULL", MssqlFunctions::isNull);
        registry.register("CONVERT", MssqlFunctions::convert);
        registry.register("GETDATE", MssqlFunctions::getDate);
        registry.register("DATEDIFF", MssqlFunctions::dateDiff);
        registry.register("DATEADD", MssqlFunctions::dateAdd);
        registry.register("STUFF", MssqlFunctions::stuff);
        registry.register("CHARINDEX", MssqlFunctions::charIndex);
        registry.register("PATINDEX", MssqlFunctions::patIndex);
        registry.register("FORMAT", MssqlFunctions::format);
        registry.register("NEWID", MssqlFunctions::newId);
        registry.register("SCOPE_IDENTITY", MssqlFunctions::scopeIdentity);
        registry.register("@@ROWCOUNT", MssqlFunctions::rowCount);
    }

    /**
     * ISNULL(expr, replacement) - returns replacement if expr is null.
     */
    private static Object isNull(List<Object> args) {
        if (args.size() < 2) return null;
        return args.get(0) != null ? args.get(0) : args.get(1);
    }

    /**
     * CONVERT(type, expr) - type conversion.
     */
    private static Object convert(List<Object> args) {
        if (args.size() < 2) return null;
        String targetType = args.get(0) != null ? args.get(0).toString().toUpperCase() : "";
        Object value = args.get(1);
        if (value == null) return null;

        return switch (targetType) {
            case "VARCHAR", "NVARCHAR", "CHAR", "NCHAR" -> value.toString();
            case "INT", "INTEGER" -> {
                if (value instanceof Number n) yield n.longValue();
                try { yield Long.parseLong(value.toString()); }
                catch (NumberFormatException e) { yield null; }
            }
            case "FLOAT", "REAL", "DECIMAL" -> {
                if (value instanceof Number n) yield n.doubleValue();
                try { yield Double.parseDouble(value.toString()); }
                catch (NumberFormatException e) { yield null; }
            }
            default -> value.toString();
        };
    }

    /**
     * GETDATE() - returns current date/time.
     */
    private static Object getDate(List<Object> args) {
        return LocalDate.now().toString();
    }

    /**
     * DATEDIFF(unit, start, end) - difference between dates.
     */
    private static Object dateDiff(List<Object> args) {
        if (args.size() < 3 || args.get(1) == null || args.get(2) == null) return null;
        String unit = args.get(0).toString().toUpperCase();
        try {
            LocalDate start = LocalDate.parse(args.get(1).toString().substring(0, 10));
            LocalDate end = LocalDate.parse(args.get(2).toString().substring(0, 10));
            return switch (unit) {
                case "DAY", "DD", "D" -> (long) ChronoUnit.DAYS.between(start, end);
                case "MONTH", "MM", "M" -> (long) ChronoUnit.MONTHS.between(start, end);
                case "YEAR", "YY", "YYYY" -> (long) ChronoUnit.YEARS.between(start, end);
                case "WEEK", "WK", "WW" -> (long) ChronoUnit.WEEKS.between(start, end);
                case "HOUR", "HH" -> ChronoUnit.DAYS.between(start, end) * 24;
                default -> (long) ChronoUnit.DAYS.between(start, end);
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * DATEADD(unit, count, date) - adds interval to date.
     */
    private static Object dateAdd(List<Object> args) {
        if (args.size() < 3 || args.get(2) == null) return null;
        String unit = args.get(0).toString().toUpperCase();
        long count = args.get(1) instanceof Number n ? n.longValue() : 0;
        try {
            LocalDate date = LocalDate.parse(args.get(2).toString().substring(0, 10));
            LocalDate result = switch (unit) {
                case "DAY", "DD", "D" -> date.plusDays(count);
                case "MONTH", "MM", "M" -> date.plusMonths(count);
                case "YEAR", "YY", "YYYY" -> date.plusYears(count);
                case "WEEK", "WK", "WW" -> date.plusWeeks(count);
                default -> date.plusDays(count);
            };
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * STUFF(str, start, length, replacement) - insert string at position.
     */
    private static Object stuff(List<Object> args) {
        if (args.size() < 4 || args.get(0) == null) return null;
        String str = args.get(0).toString();
        int start = args.get(1) instanceof Number n ? n.intValue() : 1;
        int length = args.get(2) instanceof Number n ? n.intValue() : 0;
        String replacement = args.get(3) != null ? args.get(3).toString() : "";
        // SQL is 1-based
        start = start - 1;
        if (start < 0) start = 0;
        int end = Math.min(start + length, str.length());
        return str.substring(0, start) + replacement + str.substring(end);
    }

    /**
     * CHARINDEX(search, str) - find position of substring (1-based).
     */
    private static Object charIndex(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return 0L;
        String search = args.get(0).toString();
        String str = args.get(1).toString();
        int start = args.size() >= 3 && args.get(2) instanceof Number n ? n.intValue() - 1 : 0;
        int idx = str.indexOf(search, start);
        return (long) (idx + 1); // 1-based, 0 if not found
    }

    /**
     * PATINDEX(pattern, str) - pattern index (simplified).
     */
    private static Object patIndex(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return 0L;
        String pattern = args.get(0).toString();
        String str = args.get(1).toString();
        // Convert SQL pattern (% = wildcard) to regex
        String regex = pattern.replace("%", ".*").replace("_", ".");
        try {
            var matcher = java.util.regex.Pattern.compile("(?i)" + regex).matcher(str);
            if (matcher.find()) {
                return (long) (matcher.start() + 1);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    /**
     * FORMAT(value, format) - format a value.
     */
    private static Object format(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return null;
        Object value = args.get(0);
        String fmt = args.get(1) != null ? args.get(1).toString() : "";
        if (value instanceof Number n) {
            // Simple number formatting
            if (fmt.equalsIgnoreCase("N0") || fmt.equalsIgnoreCase("#,##0")) {
                return String.format("%,.0f", n.doubleValue());
            }
            if (fmt.equalsIgnoreCase("N2") || fmt.equalsIgnoreCase("#,##0.00")) {
                return String.format("%,.2f", n.doubleValue());
            }
            return value.toString();
        }
        return value.toString();
    }

    /**
     * NEWID() - generates a UUID.
     */
    private static Object newId(List<Object> args) {
        return UUID.randomUUID().toString();
    }

    /**
     * SCOPE_IDENTITY() - returns last identity value (simplified).
     */
    private static Object scopeIdentity(List<Object> args) {
        return 0L; // simplified - would need proper tracking
    }

    /**
     * @@ROWCOUNT - returns affected row count from last statement.
     */
    private static Object rowCount(List<Object> args) {
        return 0L; // simplified
    }
}

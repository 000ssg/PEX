package ssg.pex.sql.dialects.mysql;

import ssg.pex.sql.dialects.DialectFunctionRegistry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registers MySQL-specific SQL functions.
 */
public final class MysqlFunctions {

    private MysqlFunctions() {}

    public static void register(DialectFunctionRegistry registry) {
        registry.register("IF", MysqlFunctions::ifFunc);
        registry.register("IFNULL", MysqlFunctions::ifNull);
        registry.register("GROUP_CONCAT", MysqlFunctions::groupConcat);
        registry.register("LOCATE", MysqlFunctions::locate);
        registry.register("INSTR", MysqlFunctions::instr);
        registry.register("LPAD", MysqlFunctions::lpad);
        registry.register("RPAD", MysqlFunctions::rpad);
        registry.register("FIELD", MysqlFunctions::field);
        registry.register("FIND_IN_SET", MysqlFunctions::findInSet);
        registry.register("DATE_FORMAT", MysqlFunctions::dateFormat);
        registry.register("STR_TO_DATE", MysqlFunctions::strToDate);
        registry.register("UNIX_TIMESTAMP", MysqlFunctions::unixTimestamp);
        registry.register("FROM_UNIXTIME", MysqlFunctions::fromUnixtime);
    }

    /**
     * IF(cond, trueVal, falseVal)
     */
    private static Object ifFunc(List<Object> args) {
        if (args.size() < 3) return null;
        Object cond = args.get(0);
        boolean truthy;
        if (cond instanceof Boolean b) truthy = b;
        else if (cond instanceof Number n) truthy = n.doubleValue() != 0;
        else if (cond == null) truthy = false;
        else truthy = !cond.toString().isEmpty();
        return truthy ? args.get(1) : args.get(2);
    }

    /**
     * IFNULL(expr, default)
     */
    private static Object ifNull(List<Object> args) {
        if (args.size() < 2) return null;
        return args.get(0) != null ? args.get(0) : args.get(1);
    }

    /**
     * GROUP_CONCAT(values... [SEPARATOR sep])
     * In a simplified form, concatenates all non-null args with a separator.
     */
    private static Object groupConcat(List<Object> args) {
        if (args.isEmpty()) return null;
        String separator = ",";
        // Last arg could be a separator specification
        List<Object> values = args;
        if (args.size() >= 2) {
            Object last = args.getLast();
            if (last instanceof String s && !s.isEmpty()) {
                // Check if there's a separator hint in the args
                // For simplified impl, use all args as values with comma separator
            }
        }
        return values.stream()
                .filter(v -> v != null)
                .map(Object::toString)
                .collect(Collectors.joining(separator));
    }

    /**
     * LOCATE(substr, str [, start]) - 1-based position.
     */
    private static Object locate(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return 0L;
        String substr = args.get(0).toString();
        String str = args.get(1).toString();
        int start = args.size() >= 3 && args.get(2) instanceof Number n ? n.intValue() - 1 : 0;
        int idx = str.indexOf(substr, start);
        return (long) (idx + 1);
    }

    /**
     * INSTR(str, substr) - 1-based position.
     */
    private static Object instr(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return 0L;
        String str = args.get(0).toString();
        String substr = args.get(1).toString();
        int idx = str.indexOf(substr);
        return (long) (idx + 1);
    }

    /**
     * LPAD(str, len, pad)
     */
    private static Object lpad(List<Object> args) {
        if (args.size() < 3 || args.get(0) == null) return null;
        String str = args.get(0).toString();
        int len = args.get(1) instanceof Number n ? n.intValue() : 0;
        String pad = args.get(2) != null ? args.get(2).toString() : " ";
        if (str.length() >= len) return str.substring(0, len);
        StringBuilder sb = new StringBuilder();
        while (sb.length() + str.length() < len) {
            sb.append(pad);
        }
        String padded = sb.toString();
        if (padded.length() + str.length() > len) {
            padded = padded.substring(0, len - str.length());
        }
        return padded + str;
    }

    /**
     * RPAD(str, len, pad)
     */
    private static Object rpad(List<Object> args) {
        if (args.size() < 3 || args.get(0) == null) return null;
        String str = args.get(0).toString();
        int len = args.get(1) instanceof Number n ? n.intValue() : 0;
        String pad = args.get(2) != null ? args.get(2).toString() : " ";
        if (str.length() >= len) return str.substring(0, len);
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < len) {
            sb.append(pad);
        }
        return sb.substring(0, len);
    }

    /**
     * FIELD(str, str1, str2, ...) - returns index (1-based) of str in the list.
     */
    private static Object field(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return 0L;
        String target = args.get(0).toString();
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i) != null && target.equals(args.get(i).toString())) {
                return (long) i;
            }
        }
        return 0L;
    }

    /**
     * FIND_IN_SET(str, strlist) - finds str in comma-separated strlist.
     */
    private static Object findInSet(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return 0L;
        String target = args.get(0).toString();
        String[] list = args.get(1).toString().split(",");
        for (int i = 0; i < list.length; i++) {
            if (list[i].trim().equals(target)) {
                return (long) (i + 1);
            }
        }
        return 0L;
    }

    /**
     * DATE_FORMAT(date, format)
     */
    private static Object dateFormat(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return null;
        try {
            LocalDate date = LocalDate.parse(args.get(0).toString().substring(0, 10));
            String format = args.get(1).toString();
            // Simple MySQL format conversion
            String javaFormat = format
                    .replace("%Y", "yyyy")
                    .replace("%m", "MM")
                    .replace("%d", "dd")
                    .replace("%H", "HH")
                    .replace("%i", "mm")
                    .replace("%s", "ss");
            return date.format(DateTimeFormatter.ofPattern(javaFormat));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * STR_TO_DATE(str, format)
     */
    private static Object strToDate(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return null;
        try {
            String str = args.get(0).toString();
            String format = args.get(1).toString();
            String javaFormat = format
                    .replace("%Y", "yyyy")
                    .replace("%m", "MM")
                    .replace("%d", "dd");
            LocalDate date = LocalDate.parse(str, DateTimeFormatter.ofPattern(javaFormat));
            return date.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * UNIX_TIMESTAMP() - returns current unix timestamp.
     */
    private static Object unixTimestamp(List<Object> args) {
        return Instant.now().getEpochSecond();
    }

    /**
     * FROM_UNIXTIME(ts) - converts unix timestamp to date string.
     */
    private static Object fromUnixtime(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        long ts = args.get(0) instanceof Number n ? n.longValue() : 0;
        return Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDateTime().toString();
    }
}

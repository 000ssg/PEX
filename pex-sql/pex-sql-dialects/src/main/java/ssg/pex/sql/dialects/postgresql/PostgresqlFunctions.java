package ssg.pex.sql.dialects.postgresql;

import ssg.pex.sql.dialects.DialectFunctionRegistry;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Registers PostgreSQL-specific SQL functions.
 */
public final class PostgresqlFunctions {

    private PostgresqlFunctions() {}

    public static void register(DialectFunctionRegistry registry) {
        registry.register("COALESCE", PostgresqlFunctions::coalesce);
        registry.register("NULLIF", PostgresqlFunctions::nullif);
        registry.register("STRING_AGG", PostgresqlFunctions::stringAgg);
        registry.register("ARRAY_AGG", PostgresqlFunctions::arrayAgg);
        registry.register("NOW", PostgresqlFunctions::now);
        registry.register("CURRENT_TIMESTAMP", PostgresqlFunctions::now);
        registry.register("AGE", PostgresqlFunctions::age);
        registry.register("DATE_TRUNC", PostgresqlFunctions::dateTrunc);
        registry.register("EXTRACT", PostgresqlFunctions::extract);
        registry.register("TO_CHAR", PostgresqlFunctions::toChar);
        registry.register("TO_TIMESTAMP", PostgresqlFunctions::toTimestamp);
        registry.register("GENERATE_SERIES", PostgresqlFunctions::generateSeries);
        registry.register("ARRAY_LENGTH", PostgresqlFunctions::arrayLength);
        registry.register("ARRAY_APPEND", PostgresqlFunctions::arrayAppend);
        registry.register("ARRAY_CAT", PostgresqlFunctions::arrayCat);
        registry.register("REGEXP_MATCHES", PostgresqlFunctions::regexpMatches);
        registry.register("REGEXP_REPLACE", PostgresqlFunctions::regexpReplace);
        registry.register("MD5", PostgresqlFunctions::md5);
        registry.register("INITCAP", PostgresqlFunctions::initcap);
        registry.register("CONCAT_WS", PostgresqlFunctions::concatWs);
        registry.register("FORMAT", PostgresqlFunctions::format);
        registry.register("PG_TYPEOF", PostgresqlFunctions::pgTypeof);
        registry.register("LEFT", PostgresqlFunctions::left);
        registry.register("RIGHT", PostgresqlFunctions::right);
        registry.register("LENGTH", PostgresqlFunctions::length);
    }

    /**
     * COALESCE(val1, val2, ...) - returns first non-null value.
     */
    private static Object coalesce(List<Object> args) {
        for (Object arg : args) {
            if (arg != null) return arg;
        }
        return null;
    }

    /**
     * NULLIF(val1, val2) - returns null if val1 = val2.
     */
    private static Object nullif(List<Object> args) {
        if (args.size() < 2) return null;
        Object a = args.get(0);
        Object b = args.get(1);
        if (a == null && b == null) return null;
        if (a != null && a.equals(b)) return null;
        if (a != null && b != null && a.toString().equals(b.toString())) return null;
        return a;
    }

    /**
     * STRING_AGG(values, delimiter) - concatenates with delimiter.
     */
    private static Object stringAgg(List<Object> args) {
        if (args.size() < 2) return null;
        String delimiter = args.get(1) != null ? args.get(1).toString() : ",";
        // In simplified form, the first argument contains the values
        // For function registry testing, join remaining args
        if (args.size() == 2) {
            Object first = args.get(0);
            if (first instanceof List<?> list) {
                return list.stream()
                        .filter(v -> v != null)
                        .map(Object::toString)
                        .collect(Collectors.joining(delimiter));
            }
            return first != null ? first.toString() : null;
        }
        // Multiple values passed
        return args.subList(0, args.size() - 1).stream()
                .filter(v -> v != null)
                .map(Object::toString)
                .collect(Collectors.joining(delimiter));
    }

    /**
     * ARRAY_AGG(values...) - aggregates into array.
     */
    private static Object arrayAgg(List<Object> args) {
        return new ArrayList<>(args);
    }

    /**
     * NOW() / CURRENT_TIMESTAMP - returns current timestamp.
     */
    private static Object now(List<Object> args) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * AGE(timestamp) - returns interval from timestamp to now (simplified as string).
     */
    private static Object age(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        try {
            String dateStr = args.get(0).toString().substring(0, 10);
            LocalDateTime date = LocalDateTime.parse(dateStr + "T00:00:00");
            LocalDateTime now = LocalDateTime.now();
            long years = java.time.Period.between(date.toLocalDate(), now.toLocalDate()).getYears();
            long months = java.time.Period.between(date.toLocalDate(), now.toLocalDate()).getMonths();
            long days = java.time.Period.between(date.toLocalDate(), now.toLocalDate()).getDays();
            return years + " years " + months + " mons " + days + " days";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * DATE_TRUNC(field, source) - truncates to specified precision.
     */
    private static Object dateTrunc(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return null;
        String field = args.get(0).toString().toLowerCase();
        String source = args.get(1).toString();
        try {
            // Parse as date or timestamp
            LocalDateTime dt;
            if (source.length() <= 10) {
                dt = LocalDateTime.parse(source + "T00:00:00");
            } else {
                dt = LocalDateTime.parse(source.replace(" ", "T"));
            }
            return switch (field) {
                case "year" -> dt.withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                case "month" -> dt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                case "day" -> dt.withHour(0).withMinute(0).withSecond(0)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                case "hour" -> dt.withMinute(0).withSecond(0)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> source;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * EXTRACT(field FROM source) - extracts a date/time field.
     * Simplified: args are [field, source].
     */
    private static Object extract(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return null;
        String field = args.get(0).toString().toLowerCase();
        String source = args.get(1).toString();
        try {
            LocalDateTime dt;
            if (source.length() <= 10) {
                dt = LocalDateTime.parse(source + "T00:00:00");
            } else {
                dt = LocalDateTime.parse(source.replace(" ", "T"));
            }
            return switch (field) {
                case "year" -> (long) dt.getYear();
                case "month" -> (long) dt.getMonthValue();
                case "day" -> (long) dt.getDayOfMonth();
                case "hour" -> (long) dt.getHour();
                case "minute" -> (long) dt.getMinute();
                case "second" -> (long) dt.getSecond();
                case "dow", "dayofweek" -> (long) dt.getDayOfWeek().getValue();
                case "doy", "dayofyear" -> (long) dt.getDayOfYear();
                case "quarter" -> (long) ((dt.getMonthValue() - 1) / 3 + 1);
                case "week" -> (long) dt.get(ChronoField.ALIGNED_WEEK_OF_YEAR);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * TO_CHAR(value, format) - format a value as string.
     */
    private static Object toChar(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return null;
        Object value = args.get(0);
        String format = args.get(1) != null ? args.get(1).toString() : "";
        if (value instanceof Number n) {
            // Simple numeric formatting
            return String.format("%." + countDecimalPlaces(format) + "f", n.doubleValue());
        }
        try {
            String source = value.toString();
            LocalDateTime dt;
            if (source.length() <= 10) {
                dt = LocalDateTime.parse(source + "T00:00:00");
            } else {
                dt = LocalDateTime.parse(source.replace(" ", "T"));
            }
            String javaFormat = format
                    .replace("YYYY", "yyyy")
                    .replace("MM", "MM")
                    .replace("DD", "dd")
                    .replace("HH24", "HH")
                    .replace("MI", "mm")
                    .replace("SS", "ss");
            return dt.format(DateTimeFormatter.ofPattern(javaFormat));
        } catch (Exception e) {
            return value.toString();
        }
    }

    /**
     * TO_TIMESTAMP(epoch) - convert epoch seconds to timestamp string.
     */
    private static Object toTimestamp(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        if (args.get(0) instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue())
                    .atZone(ZoneOffset.UTC).toLocalDateTime().toString();
        }
        return args.get(0).toString();
    }

    /**
     * GENERATE_SERIES(start, stop[, step]) - returns list of values.
     */
    private static Object generateSeries(List<Object> args) {
        if (args.size() < 2) return List.of();
        long start = args.get(0) instanceof Number n ? n.longValue() : 0;
        long stop = args.get(1) instanceof Number n ? n.longValue() : 0;
        long step = args.size() >= 3 && args.get(2) instanceof Number n ? n.longValue() : 1;
        List<Long> series = new ArrayList<>();
        if (step > 0) {
            for (long i = start; i <= stop; i += step) series.add(i);
        } else if (step < 0) {
            for (long i = start; i >= stop; i += step) series.add(i);
        }
        return series;
    }

    /**
     * ARRAY_LENGTH(array, dimension) - length of array.
     */
    private static Object arrayLength(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        if (args.get(0) instanceof List<?> list) {
            return (long) list.size();
        }
        return null;
    }

    /**
     * ARRAY_APPEND(array, element) - append element to array.
     */
    @SuppressWarnings("unchecked")
    private static Object arrayAppend(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return null;
        if (args.get(0) instanceof List<?> list) {
            var result = new ArrayList<>((List<Object>) list);
            result.add(args.get(1));
            return result;
        }
        return null;
    }

    /**
     * ARRAY_CAT(array1, array2) - concatenate two arrays.
     */
    @SuppressWarnings("unchecked")
    private static Object arrayCat(List<Object> args) {
        if (args.size() < 2) return null;
        List<Object> result = new ArrayList<>();
        if (args.get(0) instanceof List<?> list1) {
            result.addAll((List<Object>) list1);
        }
        if (args.get(1) instanceof List<?> list2) {
            result.addAll((List<Object>) list2);
        }
        return result;
    }

    /**
     * REGEXP_MATCHES(string, pattern) - returns matching substrings.
     */
    private static Object regexpMatches(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) return List.of();
        String input = args.get(0).toString();
        String pattern = args.get(1).toString();
        try {
            Matcher m = Pattern.compile(pattern).matcher(input);
            List<String> matches = new ArrayList<>();
            while (m.find()) {
                matches.add(m.group(0));
            }
            return matches;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * REGEXP_REPLACE(string, pattern, replacement) - regex replace.
     */
    private static Object regexpReplace(List<Object> args) {
        if (args.size() < 3 || args.get(0) == null) return null;
        String input = args.get(0).toString();
        String pattern = args.get(1) != null ? args.get(1).toString() : "";
        String replacement = args.get(2) != null ? args.get(2).toString() : "";
        try {
            // Check for 'g' flag (global replace)
            if (args.size() >= 4 && "g".equals(args.get(3) != null ? args.get(3).toString() : "")) {
                return input.replaceAll(pattern, replacement);
            }
            return input.replaceFirst(pattern, replacement);
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * MD5(string) - returns MD5 hash as hex string.
     */
    private static Object md5(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(args.get(0).toString().getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * INITCAP(string) - capitalize first letter of each word.
     */
    private static Object initcap(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        String str = args.get(0).toString();
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : str.toCharArray()) {
            if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * CONCAT_WS(separator, val1, val2, ...) - concatenate with separator, skipping nulls.
     */
    private static Object concatWs(List<Object> args) {
        if (args.isEmpty()) return null;
        String separator = args.get(0) != null ? args.get(0).toString() : "";
        return args.subList(1, args.size()).stream()
                .filter(v -> v != null)
                .map(Object::toString)
                .collect(Collectors.joining(separator));
    }

    /**
     * FORMAT(format_string, args...) - simple string formatting.
     */
    private static Object format(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        String fmt = args.get(0).toString();
        // Replace %s with corresponding args
        StringBuilder result = new StringBuilder();
        int argIdx = 1;
        for (int i = 0; i < fmt.length(); i++) {
            if (i < fmt.length() - 1 && fmt.charAt(i) == '%' && fmt.charAt(i + 1) == 's') {
                if (argIdx < args.size() && args.get(argIdx) != null) {
                    result.append(args.get(argIdx));
                }
                argIdx++;
                i++; // skip 's'
            } else {
                result.append(fmt.charAt(i));
            }
        }
        return result.toString();
    }

    /**
     * PG_TYPEOF(value) - returns the type name of a value.
     */
    private static Object pgTypeof(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return "null";
        Object val = args.get(0);
        if (val instanceof Long || val instanceof Integer) return "integer";
        if (val instanceof Double || val instanceof Float) return "double precision";
        if (val instanceof Boolean) return "boolean";
        if (val instanceof String) return "text";
        if (val instanceof List<?>) return "array";
        return "unknown";
    }

    /**
     * LEFT(string, n) - returns first n characters.
     */
    private static Object left(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return null;
        String str = args.get(0).toString();
        int n = args.get(1) instanceof Number num ? num.intValue() : 0;
        if (n < 0) return str.substring(0, Math.max(0, str.length() + n));
        return str.substring(0, Math.min(n, str.length()));
    }

    /**
     * RIGHT(string, n) - returns last n characters.
     */
    private static Object right(List<Object> args) {
        if (args.size() < 2 || args.get(0) == null) return null;
        String str = args.get(0).toString();
        int n = args.get(1) instanceof Number num ? num.intValue() : 0;
        if (n < 0) return str.substring(Math.min(-n, str.length()));
        return str.substring(Math.max(0, str.length() - n));
    }

    /**
     * LENGTH(string) - returns string length.
     */
    private static Object length(List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) return null;
        return (long) args.get(0).toString().length();
    }

    private static int countDecimalPlaces(String format) {
        int dotIdx = format.indexOf('.');
        if (dotIdx < 0) return 0;
        int count = 0;
        for (int i = dotIdx + 1; i < format.length(); i++) {
            if (format.charAt(i) == '0' || format.charAt(i) == '9' || format.charAt(i) == '#') {
                count++;
            } else {
                break;
            }
        }
        return Math.max(count, 0);
    }
}

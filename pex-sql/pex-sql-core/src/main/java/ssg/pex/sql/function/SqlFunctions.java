package ssg.pex.sql.function;

import ssg.pex.exec.NativeFunction;
import ssg.pex.result.Result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registers SQL built-in functions as NativeFunctions.
 */
public final class SqlFunctions {

    private SqlFunctions() {}

    public static Map<String, NativeFunction> all() {
        return Map.ofEntries(
                Map.entry("UPPER", SqlFunctions::upper),
                Map.entry("LOWER", SqlFunctions::lower),
                Map.entry("TRIM", SqlFunctions::trim),
                Map.entry("LTRIM", SqlFunctions::ltrim),
                Map.entry("RTRIM", SqlFunctions::rtrim),
                Map.entry("SUBSTRING", SqlFunctions::substring),
                Map.entry("LENGTH", SqlFunctions::length),
                Map.entry("CONCAT", SqlFunctions::concat),
                Map.entry("REPLACE", SqlFunctions::replace),
                Map.entry("COALESCE", SqlFunctions::coalesce),
                Map.entry("NULLIF", SqlFunctions::nullif),
                Map.entry("CAST", SqlFunctions::cast),
                Map.entry("ABS", SqlFunctions::abs),
                Map.entry("ROUND", SqlFunctions::round),
                Map.entry("CEIL", SqlFunctions::ceil),
                Map.entry("FLOOR", SqlFunctions::floor),
                Map.entry("NOW", SqlFunctions::now),
                Map.entry("CURRENT_DATE", SqlFunctions::currentDate),
                Map.entry("CURRENT_TIMESTAMP", SqlFunctions::currentTimestamp)
        );
    }

    private static Result<Object> upper(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success(args.getFirst().toString().toUpperCase());
    }

    private static Result<Object> lower(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success(args.getFirst().toString().toLowerCase());
    }

    private static Result<Object> trim(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success(args.getFirst().toString().trim());
    }

    private static Result<Object> ltrim(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success(args.getFirst().toString().stripLeading());
    }

    private static Result<Object> rtrim(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success(args.getFirst().toString().stripTrailing());
    }

    private static Result<Object> substring(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.size() < 2 || args.getFirst() == null) return Result.success(null);
        String str = args.getFirst().toString();
        int start = ((Number) args.get(1)).intValue() - 1; // SQL is 1-based
        if (start < 0) start = 0;
        if (args.size() >= 3) {
            int len = ((Number) args.get(2)).intValue();
            return Result.success(str.substring(start, Math.min(start + len, str.length())));
        }
        return Result.success(str.substring(start));
    }

    private static Result<Object> length(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success((long) args.getFirst().toString().length());
    }

    private static Result<Object> concat(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        var sb = new StringBuilder();
        for (Object arg : args) {
            if (arg != null) sb.append(arg);
        }
        return Result.success(sb.toString());
    }

    private static Result<Object> replace(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.size() < 3 || args.getFirst() == null) return Result.success(null);
        return Result.success(args.getFirst().toString()
                .replace(args.get(1).toString(), args.get(2).toString()));
    }

    private static Result<Object> coalesce(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        for (Object arg : args) {
            if (arg != null) return Result.success(arg);
        }
        return Result.success(null);
    }

    private static Result<Object> nullif(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.size() < 2) return Result.success(null);
        return Result.success(Objects.equals(args.get(0), args.get(1)) ? null : args.get(0));
    }

    private static Result<Object> cast(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty()) return Result.success(null);
        return Result.success(args.getFirst()); // simplified
    }

    private static Result<Object> abs(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        if (args.getFirst() instanceof Long l) return Result.success(Math.abs(l));
        if (args.getFirst() instanceof Double d) return Result.success(Math.abs(d));
        if (args.getFirst() instanceof Number n) return Result.success(Math.abs(n.doubleValue()));
        return Result.success(null);
    }

    private static Result<Object> round(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        double val = ((Number) args.getFirst()).doubleValue();
        int scale = args.size() >= 2 ? ((Number) args.get(1)).intValue() : 0;
        double factor = Math.pow(10, scale);
        return Result.success(Math.round(val * factor) / factor);
    }

    private static Result<Object> ceil(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success(Math.ceil(((Number) args.getFirst()).doubleValue()));
    }

    private static Result<Object> floor(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        if (args.isEmpty() || args.getFirst() == null) return Result.success(null);
        return Result.success(Math.floor(((Number) args.getFirst()).doubleValue()));
    }

    private static Result<Object> now(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        return Result.success(Instant.now().toString());
    }

    private static Result<Object> currentDate(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        return Result.success(LocalDate.now().toString());
    }

    private static Result<Object> currentTimestamp(List<Object> args, ssg.pex.exec.ExecutionContext ctx) {
        return Result.success(Instant.now().toString());
    }
}

package ssg.pex.sql.dialects.engines;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.QueryResult;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Time-series optimized storage engine.
 * Supports time_bucket grouping, downsampling, and retention policies.
 */
public class TimeSeriesEngine {

    private static final Pattern TIME_BUCKET_PATTERN = Pattern.compile(
            "(?i)time_bucket\\s*\\(\\s*'(\\w+)'\\s*,\\s*(\\w+)\\s*\\)");
    private static final Pattern RETENTION_PATTERN = Pattern.compile(
            "(?i)retention\\s*=\\s*'(\\w+)'");

    public record RetentionPolicy(String tableName, Duration retention) {}

    private final Map<String, RetentionPolicy> retentionPolicies = new ConcurrentHashMap<>();

    public Result<Object> execute(String sql, InMemoryDatabase db) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        // CREATE TABLE with TIMESERIES engine
        if (upper.startsWith("CREATE TABLE") && upper.contains("ENGINE=TIMESERIES")) {
            return createTimeSeriesTable(trimmed, db);
        }

        // time_bucket queries
        if (upper.contains("TIME_BUCKET")) {
            return executeTimeBucketQuery(trimmed, db);
        }

        // Standard delegation
        return db.execute(trimmed);
    }

    private Result<Object> createTimeSeriesTable(String sql, InMemoryDatabase db) {
        // Extract retention policy if present
        Matcher retentionMatcher = RETENTION_PATTERN.matcher(sql);
        Duration retention = null;
        if (retentionMatcher.find()) {
            retention = parseDuration(retentionMatcher.group(1));
        }

        // Remove ENGINE and WITH clauses, create as normal table
        String cleaned = sql.replaceAll("(?i)\\s*ENGINE\\s*=\\s*TIMESERIES\\s*", " ")
                           .replaceAll("(?i)\\s*WITH\\s*\\([^)]*\\)\\s*", " ")
                           .trim();
        Result<Object> result = db.execute(cleaned);

        if (result.isSuccess() && retention != null) {
            // Extract table name
            String upper = cleaned.toUpperCase();
            int tableIdx = upper.indexOf("TABLE ") + 6;
            String rest = cleaned.substring(tableIdx).trim();
            // handle IF NOT EXISTS
            if (rest.toUpperCase().startsWith("IF NOT EXISTS ")) {
                rest = rest.substring(14).trim();
            }
            String tableName = rest.split("[\\s(]")[0];
            retentionPolicies.put(tableName.toLowerCase(), new RetentionPolicy(tableName, retention));
        }

        return result;
    }

    private Result<Object> executeTimeBucketQuery(String sql, InMemoryDatabase db) {
        String upper = sql.toUpperCase();

        // Parse time_bucket(interval, column)
        Matcher tbMatcher = TIME_BUCKET_PATTERN.matcher(sql);
        if (!tbMatcher.find()) {
            return Result.failure("TIMESERIES_ERROR", "Invalid time_bucket syntax");
        }

        String interval = tbMatcher.group(1);
        String tsColumn = tbMatcher.group(2);

        // Extract table name
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) return Result.failure("TIMESERIES_ERROR", "Missing FROM clause");
        String afterFrom = sql.substring(fromIdx + 6).trim();
        String tableName = afterFrom.split("[\\s;]")[0];

        // Apply retention policy if applicable
        applyRetentionPolicy(tableName, tsColumn, db);

        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) {
            return Result.failure("TIMESERIES_ERROR", "Table not found: " + tableName);
        }

        int tsColIdx = table.getColumnIndex(tsColumn);
        if (tsColIdx < 0) {
            return Result.failure("TIMESERIES_ERROR", "Column not found: " + tsColumn);
        }

        // Parse aggregate expressions
        int selectIdx = upper.indexOf("SELECT ") + 7;
        String selectPart = sql.substring(selectIdx, sql.toUpperCase().indexOf(" FROM ")).trim();

        // Group rows by time bucket
        Duration bucketSize = parseDuration(interval);
        Map<String, List<Row>> buckets = new LinkedHashMap<>();
        for (Row row : table.scan()) {
            Object tsVal = row.getValue(tsColIdx);
            if (tsVal == null) continue;
            String bucket = computeBucket(tsVal.toString(), bucketSize);
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(row);
        }

        // Parse and compute aggregates for each bucket
        String[] selectExprs = splitSelectExprs(selectPart);
        List<String> colNames = new ArrayList<>();
        for (String expr : selectExprs) {
            colNames.add(expr.trim());
        }

        List<Row> resultRows = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            String bucket = entry.getKey();
            List<Row> bucketRows = entry.getValue();
            Object[] vals = new Object[selectExprs.length];

            for (int i = 0; i < selectExprs.length; i++) {
                String expr = selectExprs[i].trim().toUpperCase();
                if (expr.contains("TIME_BUCKET")) {
                    vals[i] = bucket;
                } else if (expr.startsWith("AVG(")) {
                    String col = extractColFromAgg(selectExprs[i].trim());
                    vals[i] = computeAgg("AVG", col, bucketRows, table);
                } else if (expr.startsWith("MIN(")) {
                    String col = extractColFromAgg(selectExprs[i].trim());
                    vals[i] = computeAgg("MIN", col, bucketRows, table);
                } else if (expr.startsWith("MAX(")) {
                    String col = extractColFromAgg(selectExprs[i].trim());
                    vals[i] = computeAgg("MAX", col, bucketRows, table);
                } else if (expr.startsWith("FIRST(")) {
                    String col = extractColFromAgg(selectExprs[i].trim());
                    vals[i] = computeAgg("FIRST", col, bucketRows, table);
                } else if (expr.startsWith("LAST(")) {
                    String col = extractColFromAgg(selectExprs[i].trim());
                    vals[i] = computeAgg("LAST", col, bucketRows, table);
                } else if (expr.startsWith("COUNT(")) {
                    vals[i] = (long) bucketRows.size();
                } else if (expr.startsWith("SUM(")) {
                    String col = extractColFromAgg(selectExprs[i].trim());
                    vals[i] = computeAgg("SUM", col, bucketRows, table);
                }
            }
            resultRows.add(new Row(vals));
        }

        return Result.success(new QueryResult(colNames, resultRows, 0));
    }

    private void applyRetentionPolicy(String tableName, String tsColumn, InMemoryDatabase db) {
        RetentionPolicy policy = retentionPolicies.get(tableName.toLowerCase());
        if (policy == null) return;

        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) return;

        int tsColIdx = table.getColumnIndex(tsColumn);
        if (tsColIdx < 0) return;

        Instant cutoff = Instant.now().minus(policy.retention());
        List<Integer> toRemove = new ArrayList<>();
        List<Row> rows = table.scan();

        for (int i = 0; i < rows.size(); i++) {
            Object val = rows.get(i).getValue(tsColIdx);
            if (val != null) {
                try {
                    Instant ts = Instant.parse(val.toString());
                    if (ts.isBefore(cutoff)) {
                        toRemove.add(i);
                    }
                } catch (Exception ignored) {
                    // Try LocalDateTime
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(val.toString());
                        Instant ts = ldt.toInstant(ZoneOffset.UTC);
                        if (ts.isBefore(cutoff)) {
                            toRemove.add(i);
                        }
                    } catch (Exception ignored2) {}
                }
            }
        }

        // Remove in reverse
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            table.removeRow(toRemove.get(i));
        }
    }

    private String computeBucket(String timestamp, Duration bucketSize) {
        try {
            // Try to parse as ISO instant or datetime
            Instant ts;
            try {
                ts = Instant.parse(timestamp);
            } catch (Exception e) {
                LocalDateTime ldt = LocalDateTime.parse(timestamp);
                ts = ldt.toInstant(ZoneOffset.UTC);
            }

            long epochSeconds = ts.getEpochSecond();
            long bucketSeconds = bucketSize.getSeconds();
            long bucketStart = (epochSeconds / bucketSeconds) * bucketSeconds;
            return Instant.ofEpochSecond(bucketStart).toString();
        } catch (Exception e) {
            return timestamp;
        }
    }

    private Object computeAgg(String func, String column, List<Row> rows, Table table) {
        int colIdx = table.getColumnIndex(column);
        if (colIdx < 0) return null;

        List<Object> values = rows.stream()
                .map(r -> r.getValue(colIdx))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (values.isEmpty()) return null;

        return switch (func.toUpperCase()) {
            case "AVG" -> values.stream()
                    .filter(v -> v instanceof Number)
                    .mapToDouble(v -> ((Number) v).doubleValue())
                    .average().orElse(0.0);
            case "MIN" -> values.stream()
                    .filter(v -> v instanceof Number)
                    .mapToDouble(v -> ((Number) v).doubleValue())
                    .min().orElse(0.0);
            case "MAX" -> values.stream()
                    .filter(v -> v instanceof Number)
                    .mapToDouble(v -> ((Number) v).doubleValue())
                    .max().orElse(0.0);
            case "SUM" -> values.stream()
                    .filter(v -> v instanceof Number)
                    .mapToDouble(v -> ((Number) v).doubleValue())
                    .sum();
            case "FIRST" -> values.getFirst();
            case "LAST" -> values.getLast();
            case "COUNT" -> (long) values.size();
            default -> null;
        };
    }

    private String extractColFromAgg(String expr) {
        int open = expr.indexOf('(');
        int close = expr.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return expr.substring(open + 1, close).trim();
        }
        return expr;
    }

    private String[] splitSelectExprs(String selectPart) {
        List<String> exprs = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < selectPart.length(); i++) {
            char c = selectPart.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                exprs.add(selectPart.substring(start, i).trim());
                start = i + 1;
            }
        }
        exprs.add(selectPart.substring(start).trim());
        return exprs.toArray(new String[0]);
    }

    private Duration parseDuration(String str) {
        str = str.trim().toLowerCase();
        if (str.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(str.replace("s", "")));
        } else if (str.endsWith("m") && !str.endsWith("min")) {
            return Duration.ofMinutes(Long.parseLong(str.replace("m", "")));
        } else if (str.endsWith("min")) {
            return Duration.ofMinutes(Long.parseLong(str.replace("min", "")));
        } else if (str.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(str.replace("h", "")));
        } else if (str.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(str.replace("d", "")));
        }
        // Default: try to parse as seconds
        try {
            return Duration.ofSeconds(Long.parseLong(str));
        } catch (NumberFormatException e) {
            return Duration.ofHours(1); // default 1 hour
        }
    }

    public Map<String, RetentionPolicy> retentionPolicies() {
        return Collections.unmodifiableMap(retentionPolicies);
    }
}

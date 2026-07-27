package ssg.pex.sql.dialects.engines;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic spatial engine supporting POINT type, ST_DISTANCE, and ST_WITHIN.
 */
public class SpatialEngine {

    private static final Pattern POINT_PATTERN = Pattern.compile(
            "(?i)POINT\\s*\\(\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*\\)");
    private static final Pattern ST_DISTANCE_PATTERN = Pattern.compile(
            "(?i)ST_DISTANCE\\s*\\(\\s*(\\w+)\\s*,\\s*POINT\\s*\\(\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*\\)\\s*\\)");
    private static final Pattern ST_WITHIN_PATTERN = Pattern.compile(
            "(?i)ST_WITHIN\\s*\\(\\s*(\\w+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*\\)");

    /**
     * Represents a 2D point.
     */
    public record Point(double x, double y) {
        @Override
        public String toString() {
            return "POINT(" + x + ", " + y + ")";
        }
    }

    public Result<Object> execute(String sql, InMemoryDatabase db) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        // Handle POINT in INSERT
        if (upper.startsWith("INSERT") && upper.contains("POINT")) {
            return executeInsertWithPoint(trimmed, db);
        }

        // Handle ST_DISTANCE in SELECT
        if (upper.contains("ST_DISTANCE")) {
            return executeStDistance(trimmed, db);
        }

        // Handle ST_WITHIN in WHERE
        if (upper.contains("ST_WITHIN")) {
            return executeStWithin(trimmed, db);
        }

        return db.execute(trimmed);
    }

    private Result<Object> executeInsertWithPoint(String sql, InMemoryDatabase db) {
        // Replace POINT(x, y) with string representation for storage
        String processed = sql;
        Matcher m = POINT_PATTERN.matcher(sql);
        while (m.find()) {
            double x = Double.parseDouble(m.group(1));
            double y = Double.parseDouble(m.group(2));
            String pointStr = "POINT(" + x + ", " + y + ")";
            processed = processed.replace(m.group(), "'" + pointStr + "'");
        }
        return db.execute(processed);
    }

    private Result<Object> executeStDistance(String sql, InMemoryDatabase db) {
        String upper = sql.toUpperCase();

        // Parse SELECT ST_DISTANCE(col, POINT(x,y)) FROM table
        Matcher distMatcher = ST_DISTANCE_PATTERN.matcher(sql);
        if (!distMatcher.find()) {
            return Result.failure("SPATIAL_ERROR", "Invalid ST_DISTANCE syntax");
        }

        String pointCol = distMatcher.group(1);
        double targetX = Double.parseDouble(distMatcher.group(2));
        double targetY = Double.parseDouble(distMatcher.group(3));

        // Extract table name
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) return Result.failure("SPATIAL_ERROR", "Missing FROM clause");
        String afterFrom = sql.substring(fromIdx + 6).trim();
        String tableName = afterFrom.split("[\\s;]")[0];

        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) return Result.failure("SPATIAL_ERROR", "Table not found: " + tableName);

        int colIdx = table.getColumnIndex(pointCol);
        if (colIdx < 0) return Result.failure("SPATIAL_ERROR", "Column not found: " + pointCol);

        // Check what other columns are selected
        int selectIdx = upper.indexOf("SELECT ") + 7;
        int fromIdxForSelect = upper.indexOf(" FROM ");
        String selectPart = sql.substring(selectIdx, fromIdxForSelect).trim();

        // Build result with distances
        List<String> colNames = new ArrayList<>();
        List<Row> resultRows = new ArrayList<>();

        // Simple case: SELECT *, ST_DISTANCE(...) or just ST_DISTANCE(...)
        boolean hasOtherCols = !selectPart.toUpperCase().startsWith("ST_DISTANCE");
        if (hasOtherCols) {
            for (Column col : table.columns()) colNames.add(col.name());
        }
        colNames.add("distance");

        for (Row row : table.scan()) {
            Object val = row.getValue(colIdx);
            Point point = parsePoint(val);
            if (point == null) continue;

            double dist = euclideanDistance(point.x(), point.y(), targetX, targetY);

            if (hasOtherCols) {
                Object[] vals = new Object[row.columnCount() + 1];
                for (int i = 0; i < row.columnCount(); i++) vals[i] = row.getValue(i);
                vals[row.columnCount()] = dist;
                resultRows.add(new Row(vals));
            } else {
                resultRows.add(new Row(new Object[]{dist}));
            }
        }

        return Result.success(new QueryResult(colNames, resultRows, 0));
    }

    private Result<Object> executeStWithin(String sql, InMemoryDatabase db) {
        String upper = sql.toUpperCase();

        Matcher withinMatcher = ST_WITHIN_PATTERN.matcher(sql);
        if (!withinMatcher.find()) {
            return Result.failure("SPATIAL_ERROR", "Invalid ST_WITHIN syntax");
        }

        String pointCol = withinMatcher.group(1);
        double centerX = Double.parseDouble(withinMatcher.group(2));
        double centerY = Double.parseDouble(withinMatcher.group(3));
        double radius = Double.parseDouble(withinMatcher.group(4));

        // Extract table name
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) return Result.failure("SPATIAL_ERROR", "Missing FROM clause");
        String afterFrom = sql.substring(fromIdx + 6).trim();
        String tableName = afterFrom.split("[\\s;]")[0];

        // Remove WHERE ST_WITHIN and everything after WHERE
        // We'll filter ourselves
        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) return Result.failure("SPATIAL_ERROR", "Table not found: " + tableName);

        int colIdx = table.getColumnIndex(pointCol);
        if (colIdx < 0) return Result.failure("SPATIAL_ERROR", "Column not found: " + pointCol);

        List<String> colNames = new ArrayList<>();
        for (Column col : table.columns()) colNames.add(col.name());

        List<Row> resultRows = new ArrayList<>();
        for (Row row : table.scan()) {
            Object val = row.getValue(colIdx);
            Point point = parsePoint(val);
            if (point == null) continue;

            double dist = euclideanDistance(point.x(), point.y(), centerX, centerY);
            if (dist <= radius) {
                resultRows.add(row);
            }
        }

        return Result.success(new QueryResult(colNames, resultRows, 0));
    }

    /**
     * Parse a POINT(x, y) string representation into a Point.
     */
    public static Point parsePoint(Object val) {
        if (val == null) return null;
        if (val instanceof Point p) return p;
        String str = val.toString();
        Matcher m = POINT_PATTERN.matcher(str);
        if (m.find()) {
            return new Point(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
        }
        return null;
    }

    /**
     * Euclidean distance between two points.
     */
    public static double euclideanDistance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }
}

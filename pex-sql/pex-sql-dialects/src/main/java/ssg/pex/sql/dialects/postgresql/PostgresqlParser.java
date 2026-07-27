package ssg.pex.sql.dialects.postgresql;

import ssg.pex.sql.ast.SqlSupport.SetClause;
import ssg.pex.sql.grammar.SqlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles PostgreSQL-specific SQL syntax:
 * Type casting, ILIKE, ON CONFLICT, RETURNING, DISTINCT ON, GENERATE_SERIES,
 * ARRAY literals, SERIAL/BIGSERIAL, DO blocks, SIMILAR TO.
 */
public class PostgresqlParser {

    private static final Pattern TYPE_CAST_PATTERN = Pattern.compile(
            "(?i)SELECT\\s+'([^']*)'\\s*::\\s*(\\w+)\\s*;?\\s*$");
    private static final Pattern TYPE_CAST_NUM_PATTERN = Pattern.compile(
            "(?i)SELECT\\s+([\\d.]+)\\s*::\\s*(\\w+)\\s*;?\\s*$");
    private static final Pattern ILIKE_PATTERN = Pattern.compile(
            "(?i)\\bILIKE\\b");
    private static final Pattern ON_CONFLICT_DO_UPDATE_PATTERN = Pattern.compile(
            "(?i)ON\\s+CONFLICT\\s*\\(([^)]+)\\)\\s*DO\\s+UPDATE\\s+SET\\s+(.+?)\\s*(?:RETURNING\\b.*)?;?\\s*$");
    private static final Pattern ON_CONFLICT_DO_NOTHING_PATTERN = Pattern.compile(
            "(?i)ON\\s+CONFLICT\\s*\\(([^)]+)\\)\\s*DO\\s+NOTHING\\s*(?:RETURNING\\b.*)?;?\\s*$");
    private static final Pattern RETURNING_PATTERN = Pattern.compile(
            "(?i)\\bRETURNING\\s+(.+?)\\s*;?\\s*$");
    private static final Pattern DISTINCT_ON_PATTERN = Pattern.compile(
            "(?i)SELECT\\s+DISTINCT\\s+ON\\s*\\(([^)]+)\\)");
    private static final Pattern GENERATE_SERIES_PATTERN = Pattern.compile(
            "(?i)GENERATE_SERIES\\s*\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)(?:\\s*,\\s*(-?\\d+))?\\s*\\)");
    private static final Pattern ARRAY_LITERAL_PATTERN = Pattern.compile(
            "(?i)ARRAY\\s*\\[([^\\]]*)]");
    private static final Pattern SERIAL_PATTERN = Pattern.compile(
            "(?i)\\b(BIG)?SERIAL\\b");
    private static final Pattern DO_BLOCK_PATTERN = Pattern.compile(
            "(?i)DO\\s+\\$\\$\\s*(?:BEGIN\\s+)?(.+?)(?:\\s+END)?\\s*\\$\\$\\s*;?\\s*$",
            Pattern.DOTALL);
    private static final Pattern SIMILAR_TO_PATTERN = Pattern.compile(
            "(?i)\\bSIMILAR\\s+TO\\b");

    private final SqlParser coreParser;

    public PostgresqlParser() {
        this.coreParser = new SqlParser();
    }

    // ---- Type casting ----

    public boolean hasTypeCast(String sql) {
        return sql.contains("::");
    }

    public PostgresqlNodes.TypeCastExpr parseTypeCast(String sql) {
        Matcher m = TYPE_CAST_PATTERN.matcher(sql.trim());
        if (m.find()) {
            return new PostgresqlNodes.TypeCastExpr(m.group(1), m.group(2).toUpperCase());
        }
        Matcher m2 = TYPE_CAST_NUM_PATTERN.matcher(sql.trim());
        if (m2.find()) {
            return new PostgresqlNodes.TypeCastExpr(m2.group(1), m2.group(2).toUpperCase());
        }
        return null;
    }

    // ---- ILIKE ----

    public boolean hasILike(String sql) {
        return ILIKE_PATTERN.matcher(sql).find();
    }

    public String rewriteILikeToLike(String sql) {
        return ILIKE_PATTERN.matcher(sql).replaceAll("LIKE");
    }

    // ---- ON CONFLICT ----

    public boolean hasOnConflict(String sql) {
        return sql.toUpperCase().contains("ON CONFLICT");
    }

    public PostgresqlNodes.OnConflictClause parseOnConflict(String sql) {
        Matcher doUpdate = ON_CONFLICT_DO_UPDATE_PATTERN.matcher(sql);
        if (doUpdate.find()) {
            List<String> cols = parseCsvList(doUpdate.group(1));
            List<SetClause> updates = parseSetClauses(doUpdate.group(2));
            return new PostgresqlNodes.OnConflictClause(cols,
                    PostgresqlNodes.OnConflictClause.OnConflictAction.DO_UPDATE, updates);
        }
        Matcher doNothing = ON_CONFLICT_DO_NOTHING_PATTERN.matcher(sql);
        if (doNothing.find()) {
            List<String> cols = parseCsvList(doNothing.group(1));
            return new PostgresqlNodes.OnConflictClause(cols,
                    PostgresqlNodes.OnConflictClause.OnConflictAction.DO_NOTHING, List.of());
        }
        return null;
    }

    public String extractInsertBeforeOnConflict(String sql) {
        int idx = sql.toUpperCase().indexOf("ON CONFLICT");
        return idx > 0 ? sql.substring(0, idx).trim() : sql;
    }

    // ---- RETURNING ----

    public boolean hasReturning(String sql) {
        return RETURNING_PATTERN.matcher(sql).find();
    }

    public PostgresqlNodes.ReturningClause parseReturning(String sql) {
        Matcher m = RETURNING_PATTERN.matcher(sql);
        if (m.find()) {
            return new PostgresqlNodes.ReturningClause(parseCsvList(m.group(1)));
        }
        return null;
    }

    public String removeReturningClause(String sql) {
        return sql.replaceAll("(?i)\\s+RETURNING\\s+.+?\\s*;?\\s*$", "");
    }

    // ---- DISTINCT ON ----

    public boolean hasDistinctOn(String sql) {
        return DISTINCT_ON_PATTERN.matcher(sql).find();
    }

    public PostgresqlNodes.DistinctOnClause parseDistinctOn(String sql) {
        Matcher m = DISTINCT_ON_PATTERN.matcher(sql);
        if (m.find()) {
            return new PostgresqlNodes.DistinctOnClause(parseCsvList(m.group(1)));
        }
        return null;
    }

    public String rewriteDistinctOnToSelect(String sql) {
        return DISTINCT_ON_PATTERN.matcher(sql).replaceFirst("SELECT");
    }

    // ---- GENERATE_SERIES ----

    public boolean hasGenerateSeries(String sql) {
        return GENERATE_SERIES_PATTERN.matcher(sql).find();
    }

    public PostgresqlNodes.GenerateSeriesNode parseGenerateSeries(String sql) {
        Matcher m = GENERATE_SERIES_PATTERN.matcher(sql);
        if (m.find()) {
            long start = Long.parseLong(m.group(1));
            long stop = Long.parseLong(m.group(2));
            long step = m.group(3) != null ? Long.parseLong(m.group(3)) : 1;
            return new PostgresqlNodes.GenerateSeriesNode(start, stop, step);
        }
        return null;
    }

    // ---- ARRAY ----

    public boolean hasArrayLiteral(String sql) {
        return ARRAY_LITERAL_PATTERN.matcher(sql).find();
    }

    public PostgresqlNodes.ArrayLiteral parseArrayLiteral(String sql) {
        Matcher m = ARRAY_LITERAL_PATTERN.matcher(sql);
        if (m.find()) {
            String content = m.group(1).trim();
            if (content.isEmpty()) {
                return new PostgresqlNodes.ArrayLiteral(List.of());
            }
            List<Object> elements = new ArrayList<>();
            for (String elem : content.split(",")) {
                elements.add(parseLiteralValue(elem.trim()));
            }
            return new PostgresqlNodes.ArrayLiteral(elements);
        }
        return null;
    }

    // ---- SERIAL/BIGSERIAL ----

    public boolean hasSerial(String sql) {
        return SERIAL_PATTERN.matcher(sql).find();
    }

    public String rewriteSerialToAutoIncrement(String sql) {
        // BIGSERIAL -> BIGINT AUTO_INCREMENT, SERIAL -> INTEGER AUTO_INCREMENT
        String result = sql.replaceAll("(?i)\\bBIGSERIAL\\b", "BIGINT AUTO_INCREMENT");
        result = result.replaceAll("(?i)\\bSERIAL\\b", "INTEGER AUTO_INCREMENT");
        return result;
    }

    // ---- DO blocks ----

    public boolean isDoBlock(String sql) {
        return sql.trim().toUpperCase().startsWith("DO ");
    }

    public PostgresqlNodes.DoBlockNode parseDoBlock(String sql) {
        Matcher m = DO_BLOCK_PATTERN.matcher(sql.trim());
        if (m.find()) {
            return new PostgresqlNodes.DoBlockNode(m.group(1).trim());
        }
        return null;
    }

    // ---- SIMILAR TO ----

    public boolean hasSimilarTo(String sql) {
        return SIMILAR_TO_PATTERN.matcher(sql).find();
    }

    public String rewriteSimilarToToLike(String sql) {
        return sql.replaceAll("(?i)\\bSIMILAR\\s+TO\\b", "LIKE");
    }

    // ---- Helpers ----

    private List<String> parseCsvList(String csv) {
        List<String> items = new ArrayList<>();
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    private List<SetClause> parseSetClauses(String setPart) {
        List<SetClause> clauses = new ArrayList<>();
        String[] parts = setPart.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String col = kv[0].trim();
                String val = kv[1].trim();
                Object value = parseLiteralValue(val);
                clauses.add(new SetClause(col, value));
            }
        }
        return clauses;
    }

    private Object parseLiteralValue(String val) {
        val = val.trim();
        if (val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        if (val.equalsIgnoreCase("NULL")) return null;
        if (val.equalsIgnoreCase("TRUE")) return true;
        if (val.equalsIgnoreCase("FALSE")) return false;
        try { return Long.parseLong(val); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}
        return val;
    }

    public SqlParser coreParser() {
        return coreParser;
    }
}

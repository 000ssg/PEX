package ssg.pex.sql.dialects.mysql;

import ssg.pex.sql.ast.SqlSupport.SetClause;
import ssg.pex.sql.grammar.SqlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles MySQL-specific SQL syntax:
 * ON DUPLICATE KEY UPDATE, REPLACE INTO, SHOW, DESCRIBE.
 */
public class MysqlParser {

    private static final Pattern REPLACE_PATTERN = Pattern.compile(
            "(?i)REPLACE\\s+INTO\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*(.+?)\\s*;?\\s*$",
            Pattern.DOTALL);
    private static final Pattern SHOW_TABLES_PATTERN = Pattern.compile("(?i)SHOW\\s+TABLES\\s*;?\\s*$");
    private static final Pattern SHOW_DATABASES_PATTERN = Pattern.compile("(?i)SHOW\\s+DATABASES\\s*;?\\s*$");
    private static final Pattern SHOW_COLUMNS_PATTERN = Pattern.compile("(?i)SHOW\\s+COLUMNS\\s+FROM\\s+(\\w+)\\s*;?\\s*$");
    private static final Pattern DESCRIBE_PATTERN = Pattern.compile("(?i)(?:DESCRIBE|DESC)\\s+(\\w+)\\s*;?\\s*$");
    private static final Pattern ON_DUP_KEY_PATTERN = Pattern.compile(
            "(?i)ON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\s+(.+?)\\s*;?\\s*$");
    private static final Pattern INSERT_VALUES_PATTERN = Pattern.compile(
            "(?i)INSERT\\s+(?:INTO\\s+)?(\\w+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*(.+?)\\s*;?\\s*$",
            Pattern.DOTALL);

    private final SqlParser coreParser;

    public MysqlParser() {
        this.coreParser = new SqlParser();
    }

    public boolean isReplaceInto(String sql) {
        return sql.trim().toUpperCase().startsWith("REPLACE ");
    }

    public MysqlNodes.ReplaceIntoNode parseReplaceInto(String sql) {
        Matcher m = REPLACE_PATTERN.matcher(sql);
        if (m.find()) {
            String table = m.group(1);
            String[] cols = m.group(2).split(",");
            List<String> columns = new ArrayList<>();
            for (String c : cols) columns.add(c.trim());

            String valuesStr = m.group(3).trim();
            List<List<Object>> values = parseValueRows(valuesStr);
            return new MysqlNodes.ReplaceIntoNode(table, columns, values);
        }
        return null;
    }

    public boolean isShowStatement(String sql) {
        String upper = sql.trim().toUpperCase();
        return upper.startsWith("SHOW ");
    }

    public MysqlNodes.ShowStatement parseShow(String sql) {
        if (SHOW_TABLES_PATTERN.matcher(sql).matches()) {
            return new MysqlNodes.ShowStatement(MysqlNodes.ShowType.TABLES, null);
        }
        if (SHOW_DATABASES_PATTERN.matcher(sql).matches()) {
            return new MysqlNodes.ShowStatement(MysqlNodes.ShowType.DATABASES, null);
        }
        Matcher colMatcher = SHOW_COLUMNS_PATTERN.matcher(sql);
        if (colMatcher.matches()) {
            return new MysqlNodes.ShowStatement(MysqlNodes.ShowType.COLUMNS, colMatcher.group(1));
        }
        return null;
    }

    public boolean isDescribe(String sql) {
        String upper = sql.trim().toUpperCase();
        return upper.startsWith("DESCRIBE ") || upper.startsWith("DESC ");
    }

    public MysqlNodes.DescribeStatement parseDescribe(String sql) {
        Matcher m = DESCRIBE_PATTERN.matcher(sql);
        if (m.matches()) {
            return new MysqlNodes.DescribeStatement(m.group(1));
        }
        return null;
    }

    public boolean hasOnDuplicateKey(String sql) {
        return sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE");
    }

    public String extractInsertPart(String sql) {
        int idx = sql.toUpperCase().indexOf("ON DUPLICATE KEY UPDATE");
        return idx > 0 ? sql.substring(0, idx).trim() : sql;
    }

    /** Parse an INSERT INTO table (cols) VALUES (...) statement into a ReplaceIntoNode for duplicate detection. */
    public MysqlNodes.ReplaceIntoNode parseInsertValues(String sql) {
        Matcher m = INSERT_VALUES_PATTERN.matcher(sql);
        if (m.find()) {
            String table = m.group(1);
            String[] cols = m.group(2).split(",");
            List<String> columns = new ArrayList<>();
            for (String c : cols) columns.add(c.trim());
            String valuesStr = m.group(3).trim();
            List<List<Object>> values = parseValueRows(valuesStr);
            return new MysqlNodes.ReplaceIntoNode(table, columns, values);
        }
        return null;
    }

    public List<SetClause> parseOnDuplicateKeyUpdate(String sql) {
        Matcher m = ON_DUP_KEY_PATTERN.matcher(sql);
        if (m.find()) {
            String updatePart = m.group(1);
            return parseSetClauses(updatePart);
        }
        return List.of();
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

    private List<List<Object>> parseValueRows(String valuesStr) {
        List<List<Object>> rows = new ArrayList<>();
        // Split on ),(
        int depth = 0;
        int start = -1;
        for (int i = 0; i < valuesStr.length(); i++) {
            char c = valuesStr.charAt(i);
            if (c == '(') {
                if (depth == 0) start = i + 1;
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String row = valuesStr.substring(start, i);
                    rows.add(parseRow(row));
                    start = -1;
                }
            }
        }
        return rows;
    }

    private List<Object> parseRow(String rowStr) {
        List<Object> values = new ArrayList<>();
        String[] parts = rowStr.split(",");
        for (String part : parts) {
            values.add(parseLiteralValue(part.trim()));
        }
        return values;
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

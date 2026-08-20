package ssg.pex.sql.dialects.mssql;

import ssg.pex.sql.grammar.SqlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles MSSQL-specific SQL syntax:
 * TOP N, @variables, TRY/CATCH, OUTPUT clause, table hints.
 */
public class MssqlParser {

    private static final Pattern TOP_PATTERN = Pattern.compile(
            "(?i)SELECT\\s+TOP\\s+(\\d+)\\s*(PERCENT)?\\s*(WITH\\s+TIES)?");
    private static final Pattern DECLARE_PATTERN = Pattern.compile(
            "(?i)DECLARE\\s+(@\\w+)\\s+(\\w+)");
    private static final Pattern SET_PATTERN = Pattern.compile(
            "(?i)SET\\s+(@\\w+)\\s*=\\s*(.+?)\\s*(?:;|$)");
    private static final Pattern PRINT_PATTERN = Pattern.compile(
            "(?i)PRINT\\s+(@\\w+|'.+?'|\\d+)\\s*(?:;|$)");
    private static final Pattern TRY_PATTERN = Pattern.compile(
            "(?i)BEGIN\\s+TRY\\s+(.+?)\\s+END\\s+TRY\\s+BEGIN\\s+CATCH\\s+(.+?)\\s+END\\s+CATCH",
            Pattern.DOTALL);
    private final SqlParser coreParser;

    public MssqlParser() {
        this.coreParser = new SqlParser();
    }

    public boolean hasTop(String sql) {
        return TOP_PATTERN.matcher(sql).find();
    }

    public MssqlNodes.TopClause parseTop(String sql) {
        Matcher m = TOP_PATTERN.matcher(sql);
        if (m.find()) {
            int count = Integer.parseInt(m.group(1));
            boolean percent = m.group(2) != null;
            boolean withTies = m.group(3) != null;
            return new MssqlNodes.TopClause(count, percent, withTies);
        }
        return null;
    }

    /**
     * Remove TOP clause from SQL to delegate to core parser.
     */
    public String removeTopClause(String sql) {
        return sql.replaceFirst("(?i)TOP\\s+\\d+\\s*(PERCENT)?\\s*(WITH\\s+TIES)?\\s*", "");
    }

    public boolean isDeclare(String sql) {
        return sql.trim().toUpperCase().startsWith("DECLARE");
    }

    public MssqlNodes.VariableDecl parseDeclare(String sql) {
        Matcher m = DECLARE_PATTERN.matcher(sql);
        if (m.find()) {
            return new MssqlNodes.VariableDecl(m.group(1), parseDataType(m.group(2)));
        }
        return null;
    }

    public boolean isSetVariable(String sql) {
        return SET_PATTERN.matcher(sql.trim()).find();
    }

    public MssqlNodes.SetVariable parseSetVariable(String sql) {
        Matcher m = SET_PATTERN.matcher(sql);
        if (m.find()) {
            String name = m.group(1);
            String valStr = m.group(2).trim();
            Object value = parseLiteralValue(valStr);
            return new MssqlNodes.SetVariable(name, value);
        }
        return null;
    }

    public boolean isPrint(String sql) {
        return sql.trim().toUpperCase().startsWith("PRINT");
    }

    public MssqlNodes.PrintStatement parsePrint(String sql) {
        Matcher m = PRINT_PATTERN.matcher(sql);
        if (m.find()) {
            String valStr = m.group(1).trim();
            if (valStr.startsWith("@")) {
                return new MssqlNodes.PrintStatement(valStr); // variable reference
            }
            return new MssqlNodes.PrintStatement(parseLiteralValue(valStr));
        }
        return null;
    }

    public boolean isTryCatch(String sql) {
        return sql.trim().toUpperCase().startsWith("BEGIN TRY");
    }

    public MssqlNodes.TryCatchNode parseTryCatch(String sql) {
        Matcher m = TRY_PATTERN.matcher(sql);
        if (m.find()) {
            String tryBody = m.group(1).trim();
            String catchBody = m.group(2).trim();
            return new MssqlNodes.TryCatchNode(
                    splitStatements(tryBody),
                    splitStatements(catchBody));
        }
        return null;
    }

    public boolean hasNolock(String sql) {
        return sql.toUpperCase().contains("WITH (NOLOCK)");
    }

    public String removeNolock(String sql) {
        return sql.replaceAll("(?i)\\s*WITH\\s*\\(NOLOCK\\)\\s*", " ");
    }

    public boolean hasOutputClause(String sql) {
        return sql.toUpperCase().contains("OUTPUT ");
    }

    /**
     * Returns true if the SQL contains a MSSQL {@code #tableName} reference.
     */
    public boolean hasHashTable(String sql) {
        return sql.contains("#");
    }

    /**
     * Rewrites MSSQL {@code #tableName} references:
     * <ul>
     *   <li>{@code CREATE TABLE #t (...)} → {@code CREATE TEMP TABLE t (...)}</li>
     *   <li>All other {@code #t} references → {@code t} (the table is already registered
     *       as a temp table so lookups will find it by the plain name).</li>
     * </ul>
     */
    public String rewriteHashTableToTemp(String sql) {
        // Rewrite CREATE TABLE #name -> CREATE TEMP TABLE name
        // Group 1 = "CREATE " (without TABLE), group 2 = name
        String result = sql.replaceAll(
                "(?i)(CREATE\\s+)TABLE\\s+#(\\w+)",
                "$1TEMP TABLE $2");
        // Rewrite all other #name references (INSERT INTO, FROM, JOIN, UPDATE, DROP TABLE)
        result = result.replaceAll("#(\\w+)", "$1");
        return result;
    }

    private List<String> splitStatements(String body) {
        List<String> stmts = new ArrayList<>();
        for (String s : body.split(";")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                stmts.add(trimmed);
            }
        }
        return stmts;
    }

    private Object parseLiteralValue(String val) {
        val = val.trim();
        if (val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        try { return Long.parseLong(val); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}
        if (val.equalsIgnoreCase("NULL")) return null;
        return val;
    }

    private ssg.pex.sql.ast.SqlSupport.SqlDataType parseDataType(String type) {
        return switch (type.toUpperCase()) {
            case "INT", "INTEGER" -> ssg.pex.sql.ast.SqlSupport.SqlDataType.INTEGER;
            case "BIGINT" -> ssg.pex.sql.ast.SqlSupport.SqlDataType.BIGINT;
            case "FLOAT", "REAL" -> ssg.pex.sql.ast.SqlSupport.SqlDataType.FLOAT;
            case "VARCHAR", "NVARCHAR", "CHAR", "NCHAR" -> ssg.pex.sql.ast.SqlSupport.SqlDataType.VARCHAR;
            case "DATE" -> ssg.pex.sql.ast.SqlSupport.SqlDataType.DATE;
            case "DATETIME", "DATETIME2" -> ssg.pex.sql.ast.SqlSupport.SqlDataType.TIMESTAMP;
            case "BIT" -> ssg.pex.sql.ast.SqlSupport.SqlDataType.BOOLEAN;
            default -> ssg.pex.sql.ast.SqlSupport.SqlDataType.VARCHAR;
        };
    }

    public SqlParser coreParser() {
        return coreParser;
    }
}

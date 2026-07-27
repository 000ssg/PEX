package ssg.pex.sql.dialects.oracle;

import ssg.pex.sql.grammar.SqlParser;

/**
 * Extends SQL parsing to handle Oracle-specific syntax:
 * CONNECT BY / START WITH, ROWNUM, MINUS, DUAL, sequences.
 *
 * This parser wraps SqlParser and provides Oracle-specific SQL preprocessing.
 */
public class OracleParser {

    private final SqlParser coreParser;

    public OracleParser() {
        this.coreParser = new SqlParser();
    }

    /**
     * Pre-process Oracle-specific SQL syntax before delegating to the core parser.
     * Converts Oracle-specific constructs to standard SQL where possible.
     */
    public String preprocess(String sql) {
        String processed = sql.trim();
        // Replace MINUS with EXCEPT (Oracle synonym)
        processed = processed.replaceAll("(?i)\\bMINUS\\b", "EXCEPT");
        // Oracle GTT: CREATE GLOBAL TEMPORARY TABLE t (...) ON COMMIT DELETE ROWS
        //          -> the SqlParser now handles GLOBAL + TEMPORARY keywords natively,
        //             so no rewriting is needed here; just pass through.
        return processed;
    }

    /**
     * Returns true if the SQL is an Oracle {@code CREATE GLOBAL TEMPORARY TABLE} statement.
     */
    public boolean isGlobalTemporaryTable(String sql) {
        String upper = sql.trim().toUpperCase();
        return upper.startsWith("CREATE GLOBAL TEMPORARY TABLE")
                || upper.startsWith("CREATE GLOBAL TEMP TABLE");
    }

    public SqlParser coreParser() {
        return coreParser;
    }

    /**
     * Check if SQL contains Oracle CONNECT BY syntax.
     */
    public boolean hasConnectBy(String sql) {
        return sql.toUpperCase().contains("CONNECT BY");
    }

    /**
     * Check if SQL references ROWNUM.
     */
    public boolean hasRowNum(String sql) {
        return sql.toUpperCase().contains("ROWNUM");
    }

    /**
     * Check if SQL references DUAL table.
     */
    public boolean isDualQuery(String sql) {
        return sql.toUpperCase().contains("FROM DUAL");
    }

    /**
     * Check if SQL contains sequence operations.
     */
    public boolean hasSequenceOp(String sql) {
        String upper = sql.toUpperCase();
        return upper.contains(".NEXTVAL") || upper.contains(".CURRVAL");
    }

    /**
     * Check if SQL is a CREATE SEQUENCE statement.
     */
    public boolean isCreateSequence(String sql) {
        return sql.trim().toUpperCase().startsWith("CREATE SEQUENCE");
    }

    /**
     * Parse CREATE SEQUENCE statement.
     */
    public OracleNodes.CreateSequenceNode parseCreateSequence(String sql) {
        String upper = sql.trim().toUpperCase();
        String remainder = sql.trim().substring("CREATE SEQUENCE".length()).trim();
        if (remainder.endsWith(";")) {
            remainder = remainder.substring(0, remainder.length() - 1).trim();
        }

        String[] parts = remainder.split("\\s+");
        String name = parts[0];
        long startWith = 1;
        long incrementBy = 1;

        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("START") && i + 2 < parts.length && parts[i + 1].equalsIgnoreCase("WITH")) {
                startWith = Long.parseLong(parts[i + 2]);
                i += 2;
            } else if (parts[i].equalsIgnoreCase("INCREMENT") && i + 2 < parts.length && parts[i + 1].equalsIgnoreCase("BY")) {
                incrementBy = Long.parseLong(parts[i + 2]);
                i += 2;
            }
        }

        return new OracleNodes.CreateSequenceNode(name, startWith, incrementBy);
    }
}

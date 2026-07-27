package ssg.pex.sql.dialects.mysql;

import ssg.pex.sql.ast.SqlSupport.SetClause;

import java.util.List;

/**
 * MySQL-specific AST nodes.
 */
public final class MysqlNodes {

    private MysqlNodes() {}

    /**
     * ON DUPLICATE KEY UPDATE clause.
     */
    public record OnDuplicateKeyUpdate(List<SetClause> updates) {}

    /**
     * REPLACE INTO statement.
     */
    public record ReplaceIntoNode(String table, List<String> columns, List<List<Object>> values) {}

    /**
     * SHOW statement (SHOW TABLES, SHOW DATABASES, SHOW COLUMNS).
     */
    public record ShowStatement(ShowType type, String argument) {}

    public enum ShowType {
        TABLES, DATABASES, COLUMNS
    }

    /**
     * DESCRIBE table statement.
     */
    public record DescribeStatement(String tableName) {}
}

package ssg.pex.sql.grammar;

import java.util.Set;

/**
 * Defines SQL dialect extensions.
 * Provides dialect-specific behavior for ANSI, MySQL, and PostgreSQL.
 */
public final class SqlDialects {

    private SqlDialects() {}

    public enum Dialect {
        ANSI, MYSQL, POSTGRESQL
    }

    /**
     * Returns the set of keywords specific to the given dialect.
     */
    public static Set<String> dialectKeywords(Dialect dialect) {
        return switch (dialect) {
            case ANSI -> Set.of();
            case MYSQL -> Set.of("AUTO_INCREMENT", "ENGINE", "CHARSET", "COLLATE",
                    "UNSIGNED", "ZEROFILL", "TINYINT", "MEDIUMINT", "LONGTEXT",
                    "MEDIUMTEXT", "TINYTEXT", "ENUM", "LIMIT");
            case POSTGRESQL -> Set.of("SERIAL", "BIGSERIAL", "ILIKE", "RETURNING",
                    "INHERITS", "TABLESPACE", "EXTENSION", "SCHEMA", "SEQUENCE",
                    "OWNED", "GENERATED", "ALWAYS", "STORED");
        };
    }

    /**
     * Returns whether the given dialect supports backtick-quoted identifiers.
     */
    public static boolean supportsBacktickIdentifiers(Dialect dialect) {
        return dialect == Dialect.MYSQL;
    }

    /**
     * Returns whether the given dialect supports :: type cast syntax.
     */
    public static boolean supportsTypeCast(Dialect dialect) {
        return dialect == Dialect.POSTGRESQL;
    }

    /**
     * Returns whether the given dialect supports ILIKE (case-insensitive LIKE).
     */
    public static boolean supportsILike(Dialect dialect) {
        return dialect == Dialect.POSTGRESQL;
    }

    /**
     * Returns whether the given dialect supports RETURNING clause.
     */
    public static boolean supportsReturning(Dialect dialect) {
        return dialect == Dialect.POSTGRESQL;
    }

    /**
     * Returns whether the given dialect supports AUTO_INCREMENT keyword.
     */
    public static boolean supportsAutoIncrement(Dialect dialect) {
        return dialect == Dialect.MYSQL;
    }

    /**
     * Returns whether the given dialect supports SERIAL type.
     */
    public static boolean supportsSerial(Dialect dialect) {
        return dialect == Dialect.POSTGRESQL;
    }
}

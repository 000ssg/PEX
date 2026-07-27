package ssg.pex.sql.dialects.postgresql;

import ssg.pex.sql.ast.SqlSupport.SetClause;

import java.util.List;

/**
 * PostgreSQL-specific AST nodes.
 */
public final class PostgresqlNodes {

    private PostgresqlNodes() {}

    /**
     * ON CONFLICT (cols) DO UPDATE SET ... / DO NOTHING clause.
     */
    public record OnConflictClause(List<String> conflictColumns, OnConflictAction action,
                                   List<SetClause> updates) {
        public enum OnConflictAction { DO_UPDATE, DO_NOTHING }
    }

    /**
     * RETURNING col1, col2 clause on INSERT/UPDATE/DELETE.
     */
    public record ReturningClause(List<String> columns) {}

    /**
     * Type cast expression: expr::type (e.g., '42'::INTEGER).
     */
    public record TypeCastExpr(String expression, String targetType) {}

    /**
     * DO $$ BEGIN ... END $$ anonymous block.
     */
    public record DoBlockNode(String body) {}

    /**
     * DISTINCT ON (cols) in SELECT.
     */
    public record DistinctOnClause(List<String> columns) {}

    /**
     * GENERATE_SERIES(start, stop[, step]).
     */
    public record GenerateSeriesNode(long start, long stop, long step) {}

    /**
     * ARRAY[1,2,3] literal.
     */
    public record ArrayLiteral(List<Object> elements) {}

    /**
     * ILIKE expression (case-insensitive LIKE).
     */
    public record ILikeExpr(String column, String pattern) {}
}

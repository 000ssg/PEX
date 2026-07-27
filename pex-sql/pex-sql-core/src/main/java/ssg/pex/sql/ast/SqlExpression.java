package ssg.pex.sql.ast;

import java.util.List;

/**
 * Sealed interface for SQL expressions used in WHERE, HAVING, CHECK, etc.
 */
public sealed interface SqlExpression permits
        SqlExpression.ColumnRef,
        SqlExpression.LiteralExpr,
        SqlExpression.BinaryExpr,
        SqlExpression.UnaryExpr,
        SqlExpression.FunctionExpr,
        SqlExpression.InExpr,
        SqlExpression.BetweenExpr,
        SqlExpression.LikeExpr,
        SqlExpression.IsNullExpr,
        SqlExpression.ExistsExpr,
        SqlExpression.SubqueryExpr,
        SqlExpression.CaseExpr,
        SqlExpression.AggregateExpr,
        SqlExpression.StarExpr {

    record ColumnRef(String table, String column) implements SqlExpression {}

    record LiteralExpr(Object value) implements SqlExpression {}

    record BinaryExpr(SqlExpression left, String operator, SqlExpression right) implements SqlExpression {}

    record UnaryExpr(String operator, SqlExpression operand) implements SqlExpression {}

    record FunctionExpr(String name, List<SqlExpression> args) implements SqlExpression {}

    record InExpr(SqlExpression value, List<SqlExpression> list, boolean negated) implements SqlExpression {}

    record BetweenExpr(SqlExpression value, SqlExpression low, SqlExpression high, boolean negated) implements SqlExpression {}

    record LikeExpr(SqlExpression value, String pattern, boolean negated) implements SqlExpression {}

    record IsNullExpr(SqlExpression value, boolean negated) implements SqlExpression {}

    record ExistsExpr(SelectNode subquery) implements SqlExpression {}

    record SubqueryExpr(SelectNode subquery) implements SqlExpression {}

    record CaseExpr(SqlExpression operand, List<WhenClause> whenClauses, SqlExpression elseExpr) implements SqlExpression {}

    record AggregateExpr(AggregateFunction func, SqlExpression arg, boolean distinct) implements SqlExpression {}

    record StarExpr() implements SqlExpression {}

    record WhenClause(SqlExpression condition, SqlExpression result) {}

    enum AggregateFunction {
        COUNT, SUM, AVG, MIN, MAX, GROUP_CONCAT
    }
}

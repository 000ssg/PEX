package ssg.pex.sql.ast;

import java.util.List;

/**
 * Supporting record and enum types used by SQL AST nodes.
 */
public final class SqlSupport {

    private SqlSupport() {}

    public record SelectItem(String expression, String alias, boolean star) {}

    public record FromClause(List<TableRef> tables) {}

    public record TableRef(String tableName, String alias, JoinClause join) {}

    public record JoinClause(JoinType type, String tableName, String alias, SqlExpression on) {}

    public enum JoinType {
        INNER, LEFT, RIGHT, FULL, CROSS
    }

    public record WhereClause(SqlExpression condition) {}

    public record GroupByClause(List<String> columns) {}

    public record HavingClause(SqlExpression condition) {}

    public record OrderByClause(List<OrderByItem> items) {}

    public record OrderByItem(String column, boolean ascending, boolean nullsFirst) {}

    public record LimitClause(int limit, int offset) {}

    public record SetClause(String column, Object value) {}

    public record ColumnDef(String name, SqlDataType dataType, boolean nullable,
                            Object defaultValue, boolean autoIncrement, boolean primaryKey) {}

    public enum SqlDataType {
        INTEGER, BIGINT, FLOAT, DOUBLE, DECIMAL, VARCHAR, TEXT, BOOLEAN, DATE, TIMESTAMP, BLOB
    }

    public sealed interface TableConstraint permits
            PrimaryKeyConstraint, ForeignKeyConstraint, UniqueConstraint, CheckConstraint {}

    public record PrimaryKeyConstraint(List<String> columns) implements TableConstraint {}

    public record ForeignKeyConstraint(List<String> columns, String refTable,
                                       List<String> refColumns) implements TableConstraint {}

    public record UniqueConstraint(String name, List<String> columns) implements TableConstraint {}

    public record CheckConstraint(String name, SqlExpression expression) implements TableConstraint {}

    public record ProcedureParam(String name, SqlDataType type, ParamMode mode) {}

    public enum ParamMode {
        IN, OUT, INOUT
    }

    public enum TriggerTiming {
        BEFORE, AFTER
    }

    public enum TriggerEvent {
        INSERT, UPDATE, DELETE
    }

    public sealed interface AlterAction permits
            AlterAction.AddColumn, AlterAction.DropColumn, AlterAction.RenameColumn, AlterAction.AddConstraint {

        record AddColumn(ColumnDef column) implements AlterAction {}
        record DropColumn(String columnName) implements AlterAction {}
        record RenameColumn(String oldName, String newName) implements AlterAction {}
        record AddConstraint(TableConstraint constraint) implements AlterAction {}
    }
}

package ssg.pex.sql.dialects.mssql;

import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlNode;
import ssg.pex.sql.ast.SqlSupport.SqlDataType;

import java.util.List;

/**
 * MSSQL-specific AST nodes.
 */
public final class MssqlNodes {

    private MssqlNodes() {}

    /**
     * TOP N [PERCENT] [WITH TIES] clause.
     */
    public record TopClause(int count, boolean percent, boolean withTies) {}

    /**
     * DECLARE @var TYPE statement.
     */
    public record VariableDecl(String name, SqlDataType type) {}

    /**
     * SET @var = value statement.
     */
    public record SetVariable(String name, Object value) {}

    /**
     * PRINT @var statement.
     */
    public record PrintStatement(Object value) {}

    /**
     * BEGIN TRY ... END TRY BEGIN CATCH ... END CATCH.
     */
    public record TryCatchNode(List<String> tryBody, List<String> catchBody) {}

    /**
     * OUTPUT clause on INSERT/UPDATE/DELETE.
     */
    public record OutputClause(List<String> columns, String intoTable) {}
}

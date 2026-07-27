package ssg.pex.sql.dialects.oracle;

import ssg.pex.sql.ast.SqlExpression;

import java.util.List;

/**
 * Oracle-specific AST nodes.
 */
public final class OracleNodes {

    private OracleNodes() {}

    /**
     * CONNECT BY / START WITH hierarchical query clause.
     */
    public record ConnectByClause(SqlExpression connectBy, SqlExpression startWith) {}

    /**
     * ROWNUM pseudo-column expression.
     */
    public record RowNumExpr(int value) {}

    /**
     * Sequence operation expression (NEXTVAL or CURRVAL).
     */
    public record SequenceExpr(String seqName, SequenceOp op) {}

    public enum SequenceOp {
        NEXTVAL, CURRVAL
    }

    /**
     * CREATE SEQUENCE statement.
     */
    public record CreateSequenceNode(String name, long startWith, long incrementBy) {}
}

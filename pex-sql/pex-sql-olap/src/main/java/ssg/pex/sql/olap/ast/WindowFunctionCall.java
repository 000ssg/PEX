package ssg.pex.sql.olap.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlExpression;

import java.util.List;

/**
 * A window function call, e.g. ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC).
 */
public record WindowFunctionCall(
        String functionName,
        List<SqlExpression> args,
        OverClause overClause,
        SourceLocation location
) implements OlapNode {

    public WindowFunctionCall {
        args = args != null ? List.copyOf(args) : List.of();
    }
}

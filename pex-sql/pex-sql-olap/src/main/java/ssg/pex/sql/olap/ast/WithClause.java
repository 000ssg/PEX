package ssg.pex.sql.olap.ast;

import ssg.pex.ast.SourceLocation;

import java.util.List;

/**
 * WITH [RECURSIVE] cte1 AS (...), cte2 AS (...) SELECT ...
 */
public record WithClause(
        List<CteDefinition> ctes,
        boolean recursive,
        SourceLocation location
) implements OlapNode {

    public WithClause {
        ctes = List.copyOf(ctes);
    }
}

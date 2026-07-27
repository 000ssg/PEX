package ssg.pex.sql.olap.ast;

import ssg.pex.ast.SourceLocation;

import java.util.List;

/**
 * PIVOT (aggregate FOR column IN (value1, value2, ...))
 */
public record PivotClause(
        String aggregateFunction,
        String aggregateColumn,
        String forColumn,
        List<Object> inValues,
        SourceLocation location
) implements OlapNode {

    public PivotClause {
        inValues = List.copyOf(inValues);
    }
}

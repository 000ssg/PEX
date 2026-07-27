package ssg.pex.sql.olap.ast;

import ssg.pex.ast.SourceLocation;

import java.util.List;

/**
 * UNPIVOT (valueColumn FOR nameColumn IN (col1, col2, ...))
 */
public record UnpivotClause(
        String valueColumn,
        String nameColumn,
        List<String> sourceColumns,
        SourceLocation location
) implements OlapNode {

    public UnpivotClause {
        sourceColumns = List.copyOf(sourceColumns);
    }
}

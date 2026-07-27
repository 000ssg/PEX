package ssg.pex.sql.olap.ast;

import ssg.pex.sql.ast.SelectNode;

import java.util.List;

/**
 * A single CTE definition: name [(col1, col2)] AS (SELECT ...)
 */
public record CteDefinition(
        String name,
        List<String> columnAliases,
        SelectNode query
) {
    public CteDefinition {
        columnAliases = columnAliases != null ? List.copyOf(columnAliases) : List.of();
    }
}

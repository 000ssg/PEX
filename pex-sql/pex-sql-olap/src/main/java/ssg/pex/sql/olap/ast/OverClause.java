package ssg.pex.sql.olap.ast;

import ssg.pex.sql.ast.SqlSupport.OrderByClause;

import java.util.List;

/**
 * OVER (PARTITION BY ... ORDER BY ... frame)
 */
public record OverClause(
        List<String> partitionBy,
        OrderByClause orderBy,
        FrameSpec frame
) {
    public OverClause {
        partitionBy = partitionBy != null ? List.copyOf(partitionBy) : List.of();
    }
}

package ssg.pex.sql.olap.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlExpression;

import java.util.List;

/**
 * MERGE INTO target USING source ON condition WHEN MATCHED THEN ... WHEN NOT MATCHED THEN ...
 */
public record MergeNode(
        String targetTable,
        String sourceTable,
        String targetAlias,
        String sourceAlias,
        SqlExpression onCondition,
        List<MergeAction> actions,
        SourceLocation location
) implements OlapNode {

    public MergeNode {
        actions = List.copyOf(actions);
    }
}

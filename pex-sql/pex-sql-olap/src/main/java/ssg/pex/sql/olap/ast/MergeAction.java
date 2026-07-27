package ssg.pex.sql.olap.ast;

import ssg.pex.sql.ast.SqlExpression;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface for MERGE actions.
 */
public sealed interface MergeAction permits
        MergeAction.WhenMatchedUpdate,
        MergeAction.WhenMatchedDelete,
        MergeAction.WhenNotMatchedInsert {

    record WhenMatchedUpdate(Map<String, SqlExpression> setColumns) implements MergeAction {}

    record WhenMatchedDelete() implements MergeAction {}

    record WhenNotMatchedInsert(List<String> columns, List<SqlExpression> values) implements MergeAction {
        public WhenNotMatchedInsert {
            columns = List.copyOf(columns);
            values = List.copyOf(values);
        }
    }
}

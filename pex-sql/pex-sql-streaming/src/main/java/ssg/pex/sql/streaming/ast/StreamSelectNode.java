package ssg.pex.sql.streaming.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.GroupByClause;
import ssg.pex.sql.ast.SqlSupport.SelectItem;
import ssg.pex.sql.ast.SqlSupport.WhereClause;

import java.util.List;

/**
 * AST node for streaming SELECT ... FROM stream WINDOW ... EMIT ...
 */
public record StreamSelectNode(
        List<SelectItem> selectItems,
        String fromStream,
        WindowSpec window,
        WhereClause where,
        GroupByClause groupBy,
        EmitStrategy emit,
        SourceLocation location
) implements StreamNode {}

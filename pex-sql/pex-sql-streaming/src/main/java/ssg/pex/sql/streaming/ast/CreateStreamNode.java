package ssg.pex.sql.streaming.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.ColumnDef;

import java.util.List;
import java.util.Map;

/**
 * AST node for CREATE STREAM statements.
 */
public record CreateStreamNode(
        String streamName,
        List<ColumnDef> columns,
        Map<String, String> properties,
        SourceLocation location
) implements StreamNode {}

package ssg.pex.sql.streaming.ast;

import ssg.pex.ast.SourceLocation;

/**
 * AST node for DROP STREAM statements.
 */
public record DropStreamNode(
        String streamName,
        boolean ifExists,
        SourceLocation location
) implements StreamNode {}

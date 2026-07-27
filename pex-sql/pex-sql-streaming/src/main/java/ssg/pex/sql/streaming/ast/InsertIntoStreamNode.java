package ssg.pex.sql.streaming.ast;

import ssg.pex.ast.SourceLocation;

/**
 * AST node for INSERT INTO stream SELECT ... FROM ...
 */
public record InsertIntoStreamNode(
        String targetStream,
        StreamSelectNode query,
        SourceLocation location
) implements StreamNode {}

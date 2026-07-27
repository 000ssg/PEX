package ssg.pex.sql.streaming.ast;

import ssg.pex.ast.SourceLocation;

/**
 * Sealed interface: common base for all streaming SQL AST nodes.
 */
public sealed interface StreamNode permits
        CreateStreamNode, DropStreamNode, StreamSelectNode, InsertIntoStreamNode {

    SourceLocation location();
}

package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;

import java.util.List;

public record CallNode(
        String procedureName,
        List<Object> arguments,
        SourceLocation location
) implements SqlNode {}

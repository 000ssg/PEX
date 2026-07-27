package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;

public record CreateViewNode(
        String viewName,
        SelectNode query,
        boolean orReplace,
        SourceLocation location
) implements SqlNode {}

package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;

public record DropTableNode(
        String tableName,
        boolean ifExists,
        SourceLocation location
) implements SqlNode {}

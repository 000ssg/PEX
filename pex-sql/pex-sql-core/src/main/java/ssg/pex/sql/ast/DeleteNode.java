package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.*;

public record DeleteNode(
        String tableName,
        WhereClause where,
        SourceLocation location
) implements SqlNode {}

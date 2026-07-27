package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.*;

import java.util.List;

public record UpdateNode(
        String tableName,
        List<SetClause> setClauses,
        WhereClause where,
        SourceLocation location
) implements SqlNode {}

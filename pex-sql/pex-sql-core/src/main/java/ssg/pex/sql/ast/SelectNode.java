package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.*;

import java.util.List;

public record SelectNode(
        List<SelectItem> selectItems,
        FromClause from,
        WhereClause where,
        GroupByClause groupBy,
        HavingClause having,
        OrderByClause orderBy,
        LimitClause limit,
        boolean distinct,
        SourceLocation location
) implements SqlNode {}

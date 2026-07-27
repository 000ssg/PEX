package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;

import java.util.List;

public record InsertNode(
        String tableName,
        List<String> columns,
        List<List<Object>> valueRows,
        SelectNode subquery,
        SourceLocation location
) implements SqlNode {}

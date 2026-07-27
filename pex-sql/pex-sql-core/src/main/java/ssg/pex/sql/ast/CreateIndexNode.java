package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;

import java.util.List;

public record CreateIndexNode(
        String indexName,
        String tableName,
        List<String> columns,
        boolean unique,
        SourceLocation location
) implements SqlNode {}

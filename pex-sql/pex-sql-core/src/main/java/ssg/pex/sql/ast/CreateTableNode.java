package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.*;

import java.util.List;

public record CreateTableNode(
        String tableName,
        List<ColumnDef> columns,
        List<TableConstraint> constraints,
        boolean ifNotExists,
        boolean isTemporary,
        SourceLocation location
) implements SqlNode {

    /** Convenience constructor for non-temporary tables (preserves backward compatibility). */
    public CreateTableNode(String tableName, List<ColumnDef> columns, List<TableConstraint> constraints,
                           boolean ifNotExists, SourceLocation location) {
        this(tableName, columns, constraints, ifNotExists, false, location);
    }
}

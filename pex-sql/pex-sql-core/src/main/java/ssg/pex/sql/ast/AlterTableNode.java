package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.*;

import java.util.List;

public record AlterTableNode(
        String tableName,
        List<AlterAction> actions,
        SourceLocation location
) implements SqlNode {}

package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.*;

import java.util.List;

public record CreateProcedureNode(
        String procedureName,
        List<ProcedureParam> params,
        List<SqlNode> body,
        SourceLocation location
) implements SqlNode {}

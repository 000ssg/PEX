package ssg.pex.sql.dbms;

import ssg.pex.sql.ast.SqlNode;
import ssg.pex.sql.ast.SqlSupport.ProcedureParam;

import java.util.List;

public record StoredProcedure(String name, List<ProcedureParam> params, List<SqlNode> body,
                              Object compiledExecutable) {
}

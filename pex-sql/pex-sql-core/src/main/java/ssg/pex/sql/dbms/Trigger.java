package ssg.pex.sql.dbms;

import ssg.pex.sql.ast.SqlNode;
import ssg.pex.sql.ast.SqlSupport.TriggerEvent;
import ssg.pex.sql.ast.SqlSupport.TriggerTiming;

import java.util.List;

public record Trigger(String name, TriggerTiming timing, TriggerEvent event,
                      String tableName, List<SqlNode> body) {
}

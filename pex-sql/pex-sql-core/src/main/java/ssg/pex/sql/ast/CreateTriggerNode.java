package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;
import ssg.pex.sql.ast.SqlSupport.*;

import java.util.List;

public record CreateTriggerNode(
        String triggerName,
        TriggerTiming timing,
        TriggerEvent event,
        String tableName,
        List<SqlNode> body,
        SourceLocation location
) implements SqlNode {}

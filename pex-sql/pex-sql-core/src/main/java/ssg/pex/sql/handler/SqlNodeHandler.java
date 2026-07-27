package ssg.pex.sql.handler;

import ssg.pex.ast.node.ExtensionNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;
import ssg.pex.sql.ast.SqlNode;
import ssg.pex.sql.dbms.InMemoryDatabase;

/**
 * NodeHandler that handles ExtensionNode wrapping SqlNode instances.
 * Delegates execution to an InMemoryDatabase.
 */
public class SqlNodeHandler implements NodeHandler {

    private final InMemoryDatabase database;

    public SqlNodeHandler(InMemoryDatabase database) {
        this.database = database;
    }

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (node instanceof ExtensionNode ext && ext.wrappedNode() instanceof SqlNode sqlNode) {
            return database.execute(sqlNode);
        }
        return Result.failure("SQL_HANDLER_ERROR", "Expected ExtensionNode wrapping SqlNode, got: " +
                (node != null ? node.getClass().getSimpleName() : "null"));
    }
}

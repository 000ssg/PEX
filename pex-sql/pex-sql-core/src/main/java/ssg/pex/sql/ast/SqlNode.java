package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;

/**
 * Sealed interface: common base for all SQL-specific nodes.
 */
public sealed interface SqlNode permits
        SelectNode, InsertNode, UpdateNode, DeleteNode,
        CreateTableNode, DropTableNode, AlterTableNode,
        CreateIndexNode, CreateViewNode, CreateTriggerNode,
        CreateProcedureNode, TransactionNode, CallNode {

    SourceLocation location();
}

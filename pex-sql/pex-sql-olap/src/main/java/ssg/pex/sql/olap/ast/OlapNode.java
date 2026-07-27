package ssg.pex.sql.olap.ast;

/**
 * Sealed interface for OLAP-specific AST nodes.
 * Since SqlNode is sealed in pex-sql-core, OLAP nodes are independent records
 * that OlapDatabase wraps and handles.
 */
public sealed interface OlapNode permits
        WindowFunctionCall, WithClause, MergeNode, GroupingSetSpec,
        PivotClause, UnpivotClause {
}

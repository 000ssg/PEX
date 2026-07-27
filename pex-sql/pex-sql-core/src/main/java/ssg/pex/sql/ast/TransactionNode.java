package ssg.pex.sql.ast;

import ssg.pex.ast.SourceLocation;

public record TransactionNode(
        TransactionAction action,
        String savepointName,
        SourceLocation location
) implements SqlNode {

    public enum TransactionAction {
        BEGIN, COMMIT, ROLLBACK, SAVEPOINT, RELEASE_SAVEPOINT
    }
}

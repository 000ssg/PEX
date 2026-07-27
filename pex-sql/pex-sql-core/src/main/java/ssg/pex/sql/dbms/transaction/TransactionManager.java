package ssg.pex.sql.dbms.transaction;

import ssg.pex.sql.dbms.Schema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages transactions per session.
 */
public class TransactionManager {

    private final Map<String, Transaction> activeTransactions = new ConcurrentHashMap<>();
    private final Schema schema;

    public TransactionManager(Schema schema) {
        this.schema = schema;
    }

    public Transaction begin(String sessionId, IsolationLevel level) {
        if (activeTransactions.containsKey(sessionId)) {
            throw new IllegalStateException("Transaction already active for session: " + sessionId);
        }
        var txn = new Transaction(sessionId, level);
        activeTransactions.put(sessionId, txn);
        return txn;
    }

    public Transaction begin(String sessionId) {
        return begin(sessionId, IsolationLevel.READ_COMMITTED);
    }

    public void commit(String sessionId) {
        var txn = activeTransactions.remove(sessionId);
        if (txn == null) {
            throw new IllegalStateException("No active transaction for session: " + sessionId);
        }
        // Changes are already applied, just remove the transaction
    }

    public void rollback(String sessionId) {
        var txn = activeTransactions.remove(sessionId);
        if (txn == null) {
            throw new IllegalStateException("No active transaction for session: " + sessionId);
        }
        txn.changeLog().undo(schema);
    }

    public void savepoint(String sessionId, String name) {
        var txn = getTransaction(sessionId);
        txn.savepoint(name);
    }

    public void rollbackToSavepoint(String sessionId, String name) {
        var txn = getTransaction(sessionId);
        int position = txn.getSavepointPosition(name);
        txn.changeLog().undoTo(position, schema);
    }

    public void releaseSavepoint(String sessionId, String name) {
        var txn = getTransaction(sessionId);
        txn.releaseSavepoint(name);
    }

    public boolean hasActiveTransaction(String sessionId) {
        return activeTransactions.containsKey(sessionId);
    }

    public Transaction getTransaction(String sessionId) {
        var txn = activeTransactions.get(sessionId);
        if (txn == null) {
            throw new IllegalStateException("No active transaction for session: " + sessionId);
        }
        return txn;
    }
}

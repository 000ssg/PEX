package ssg.pex.sql.dbms.transaction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds transaction state.
 */
public class Transaction {

    private final String sessionId;
    private final IsolationLevel isolationLevel;
    private final ChangeLog changeLog;
    private final Map<String, Integer> savepoints; // name -> changeLog position

    public Transaction(String sessionId, IsolationLevel isolationLevel) {
        this.sessionId = sessionId;
        this.isolationLevel = isolationLevel;
        this.changeLog = new ChangeLog();
        this.savepoints = new LinkedHashMap<>();
    }

    public String sessionId() {
        return sessionId;
    }

    public IsolationLevel isolationLevel() {
        return isolationLevel;
    }

    public ChangeLog changeLog() {
        return changeLog;
    }

    public void savepoint(String name) {
        savepoints.put(name, changeLog.size());
    }

    public int getSavepointPosition(String name) {
        Integer pos = savepoints.get(name);
        if (pos == null) {
            throw new IllegalArgumentException("Savepoint not found: " + name);
        }
        return pos;
    }

    public boolean hasSavepoint(String name) {
        return savepoints.containsKey(name);
    }

    public void releaseSavepoint(String name) {
        savepoints.remove(name);
    }
}

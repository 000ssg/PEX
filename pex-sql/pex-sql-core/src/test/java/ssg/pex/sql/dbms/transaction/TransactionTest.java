package ssg.pex.sql.dbms.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
        db.execute("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name VARCHAR(100), balance DOUBLE)");
        db.execute("INSERT INTO accounts (id, name, balance) VALUES (1, 'Alice', 1000.0)");
        db.execute("INSERT INTO accounts (id, name, balance) VALUES (2, 'Bob', 500.0)");
    }

    private QueryResult query(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess()).isTrue();
        return (QueryResult) result.value();
    }

    @Test
    void beginTransaction() {
        var result = db.execute("BEGIN");
        assertThat(result.isSuccess()).isTrue();
        assertThat(db.transactionManager().hasActiveTransaction("default")).isTrue();
    }

    @Test
    void commitTransaction() {
        db.execute("BEGIN");
        db.execute("INSERT INTO accounts (id, name, balance) VALUES (3, 'Charlie', 200.0)");
        var result = db.execute("COMMIT");
        assertThat(result.isSuccess()).isTrue();
        assertThat(query("SELECT * FROM accounts").rowCount()).isEqualTo(3);
    }

    @Test
    void rollbackTransaction() {
        db.execute("BEGIN");
        // Record the insert in the change log manually
        var txn = db.transactionManager().getTransaction("default");
        int rowCount = db.defaultSchema().getTable("accounts").rowCount();
        db.execute("INSERT INTO accounts (id, name, balance) VALUES (3, 'Charlie', 200.0)");
        txn.changeLog().recordInsert("accounts", rowCount);
        db.execute("ROLLBACK");
        assertThat(query("SELECT * FROM accounts").rowCount()).isEqualTo(2);
    }

    @Test
    void savepointCreate() {
        db.execute("BEGIN");
        db.execute("SAVEPOINT sp1");
        var txn = db.transactionManager().getTransaction("default");
        assertThat(txn.hasSavepoint("sp1")).isTrue();
        db.execute("COMMIT");
    }

    @Test
    void rollbackToSavepoint() {
        db.execute("BEGIN");
        var txn = db.transactionManager().getTransaction("default");
        db.execute("SAVEPOINT sp1");

        int rowCount = db.defaultSchema().getTable("accounts").rowCount();
        db.execute("INSERT INTO accounts (id, name, balance) VALUES (3, 'Charlie', 200.0)");
        txn.changeLog().recordInsert("accounts", rowCount);

        db.execute("ROLLBACK TO SAVEPOINT sp1");
        assertThat(query("SELECT * FROM accounts").rowCount()).isEqualTo(2);
        // Transaction still active
        assertThat(db.transactionManager().hasActiveTransaction("default")).isTrue();
        db.execute("COMMIT");
    }

    @Test
    void releaseSavepoint() {
        db.execute("BEGIN");
        db.execute("SAVEPOINT sp1");
        db.execute("RELEASE SAVEPOINT sp1");
        var txn = db.transactionManager().getTransaction("default");
        assertThat(txn.hasSavepoint("sp1")).isFalse();
        db.execute("COMMIT");
    }

    @Test
    void multipleSavepoints() {
        db.execute("BEGIN");
        var txn = db.transactionManager().getTransaction("default");
        db.execute("SAVEPOINT sp1");

        int rowCount1 = db.defaultSchema().getTable("accounts").rowCount();
        db.execute("INSERT INTO accounts (id, name, balance) VALUES (3, 'Charlie', 200.0)");
        txn.changeLog().recordInsert("accounts", rowCount1);

        db.execute("SAVEPOINT sp2");

        int rowCount2 = db.defaultSchema().getTable("accounts").rowCount();
        db.execute("INSERT INTO accounts (id, name, balance) VALUES (4, 'Diana', 300.0)");
        txn.changeLog().recordInsert("accounts", rowCount2);

        // Rollback to sp2 - should remove Diana but keep Charlie
        db.execute("ROLLBACK TO SAVEPOINT sp2");
        assertThat(query("SELECT * FROM accounts").rowCount()).isEqualTo(3);

        db.execute("COMMIT");
    }

    @Test
    void beginWhenTransactionAlreadyActive() {
        db.execute("BEGIN");
        var result = db.execute("BEGIN");
        assertThat(result.isFailure()).isTrue();
        db.execute("COMMIT");
    }

    @Test
    void commitWithoutTransaction() {
        var result = db.execute("COMMIT");
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void rollbackWithoutTransaction() {
        var result = db.execute("ROLLBACK");
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void transactionManagerBeginAndCommit() {
        var tm = db.transactionManager();
        tm.begin("session1");
        assertThat(tm.hasActiveTransaction("session1")).isTrue();
        tm.commit("session1");
        assertThat(tm.hasActiveTransaction("session1")).isFalse();
    }

    @Test
    void transactionManagerBeginAndRollback() {
        var tm = db.transactionManager();
        tm.begin("session1");
        assertThat(tm.hasActiveTransaction("session1")).isTrue();
        tm.rollback("session1");
        assertThat(tm.hasActiveTransaction("session1")).isFalse();
    }

    @Test
    void transactionManagerDuplicateBeginThrows() {
        var tm = db.transactionManager();
        tm.begin("session1");
        assertThatThrownBy(() -> tm.begin("session1"))
                .isInstanceOf(IllegalStateException.class);
        tm.rollback("session1");
    }

    @Test
    void transactionIsolationLevel() {
        var tm = db.transactionManager();
        var txn = tm.begin("session1", IsolationLevel.SERIALIZABLE);
        assertThat(txn.isolationLevel()).isEqualTo(IsolationLevel.SERIALIZABLE);
        tm.commit("session1");
    }

    @Test
    void changeLogRecordsInsert() {
        var log = new ChangeLog();
        log.recordInsert("users", 0);
        assertThat(log.size()).isEqualTo(1);
    }

    @Test
    void changeLogRecordsUpdate() {
        var log = new ChangeLog();
        var row = new ssg.pex.sql.dbms.Row(new Object[]{"Alice", 30});
        log.recordUpdate("users", 0, row);
        assertThat(log.size()).isEqualTo(1);
    }

    @Test
    void changeLogRecordsDelete() {
        var log = new ChangeLog();
        var row = new ssg.pex.sql.dbms.Row(new Object[]{"Alice", 30});
        log.recordDelete("users", 0, row);
        assertThat(log.size()).isEqualTo(1);
    }

    @Test
    void transactionSessionId() {
        var txn = new Transaction("test-session", IsolationLevel.READ_COMMITTED);
        assertThat(txn.sessionId()).isEqualTo("test-session");
    }

    @Test
    void savepointPositionTracking() {
        var txn = new Transaction("test", IsolationLevel.READ_COMMITTED);
        txn.changeLog().recordInsert("t1", 0);
        txn.savepoint("sp1");
        txn.changeLog().recordInsert("t1", 1);
        txn.changeLog().recordInsert("t1", 2);
        assertThat(txn.getSavepointPosition("sp1")).isEqualTo(1);
    }
}

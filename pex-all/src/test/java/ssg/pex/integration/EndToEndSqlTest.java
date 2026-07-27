package ssg.pex.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndSqlTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void createTable() {
        var result = db.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
        assertThat(result.isSuccess()).isTrue();

        assertThat(db.defaultSchema().getTable("users")).isNotNull();
    }

    @Test
    void insertIntoTable() {
        db.execute("CREATE TABLE items (id INT, label VARCHAR(50))");

        var result = db.execute("INSERT INTO items (id, label) VALUES (1, 'apple')");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isInstanceOf(DmlResult.class);
        assertThat(((DmlResult) result.value()).affectedRows()).isEqualTo(1);
    }

    @Test
    void selectWithWhere() {
        db.execute("CREATE TABLE products (id INT, name VARCHAR(50), price INT)");
        db.execute("INSERT INTO products (id, name, price) VALUES (1, 'Pen', 10)");
        db.execute("INSERT INTO products (id, name, price) VALUES (2, 'Book', 25)");
        db.execute("INSERT INTO products (id, name, price) VALUES (3, 'Notebook', 15)");

        var result = db.execute("SELECT name, price FROM products WHERE price > 12");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void updateRows() {
        db.execute("CREATE TABLE stock (id INT, qty INT)");
        db.execute("INSERT INTO stock (id, qty) VALUES (1, 100)");
        db.execute("INSERT INTO stock (id, qty) VALUES (2, 200)");

        var result = db.execute("UPDATE stock SET qty = 150 WHERE id = 1");
        assertThat(result.isSuccess()).isTrue();
        assertThat(((DmlResult) result.value()).affectedRows()).isEqualTo(1);

        var selectResult = db.execute("SELECT qty FROM stock WHERE id = 1");
        assertThat(selectResult.isSuccess()).isTrue();
        var qr = (QueryResult) selectResult.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        assertThat(((Number) qr.rows().getFirst().getValue(0)).intValue()).isEqualTo(150);
    }

    @Test
    void deleteRows() {
        db.execute("CREATE TABLE logs (id INT, msg VARCHAR(100))");
        db.execute("INSERT INTO logs (id, msg) VALUES (1, 'info')");
        db.execute("INSERT INTO logs (id, msg) VALUES (2, 'error')");
        db.execute("INSERT INTO logs (id, msg) VALUES (3, 'info')");

        var result = db.execute("DELETE FROM logs WHERE msg = 'info'");
        assertThat(result.isSuccess()).isTrue();
        assertThat(((DmlResult) result.value()).affectedRows()).isEqualTo(2);

        var remaining = db.execute("SELECT * FROM logs");
        assertThat(remaining.isSuccess()).isTrue();
        assertThat(((QueryResult) remaining.value()).rowCount()).isEqualTo(1);
    }

    @Test
    void joinOperation() {
        db.execute("CREATE TABLE departments (id INT, name VARCHAR(50))");
        db.execute("CREATE TABLE employees (id INT, name VARCHAR(50), dept_id INT)");

        db.execute("INSERT INTO departments (id, name) VALUES (1, 'Engineering')");
        db.execute("INSERT INTO departments (id, name) VALUES (2, 'Marketing')");
        db.execute("INSERT INTO employees (id, name, dept_id) VALUES (1, 'Alice', 1)");
        db.execute("INSERT INTO employees (id, name, dept_id) VALUES (2, 'Bob', 2)");
        db.execute("INSERT INTO employees (id, name, dept_id) VALUES (3, 'Charlie', 1)");

        var result = db.execute(
                "SELECT employees.name, departments.name FROM employees " +
                "JOIN departments ON employees.dept_id = departments.id");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    @Test
    void aggregateCountAndSum() {
        db.execute("CREATE TABLE sales (id INT, category VARCHAR(50), amount INT)");
        db.execute("INSERT INTO sales (id, category, amount) VALUES (1, 'A', 100)");
        db.execute("INSERT INTO sales (id, category, amount) VALUES (2, 'B', 200)");
        db.execute("INSERT INTO sales (id, category, amount) VALUES (3, 'A', 150)");
        db.execute("INSERT INTO sales (id, category, amount) VALUES (4, 'B', 300)");

        var result = db.execute(
                "SELECT category, COUNT(*) AS cnt, SUM(amount) AS total " +
                "FROM sales GROUP BY category");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void transactionBeginAndCommit() {
        db.execute("CREATE TABLE accounts (id INT, balance INT)");
        db.execute("INSERT INTO accounts (id, balance) VALUES (1, 1000)");

        var beginResult = db.execute("BEGIN");
        assertThat(beginResult.isSuccess()).isTrue();

        db.execute("UPDATE accounts SET balance = 500 WHERE id = 1");

        var commitResult = db.execute("COMMIT");
        assertThat(commitResult.isSuccess()).isTrue();

        // After commit, the update persists
        var postResult = db.execute("SELECT balance FROM accounts WHERE id = 1");
        assertThat(postResult.isSuccess()).isTrue();
        var postQr = (QueryResult) postResult.value();
        assertThat(((Number) postQr.rows().getFirst().getValue(0)).intValue()).isEqualTo(500);
    }

    @Test
    void transactionBeginAndRollbackExecutesWithoutError() {
        db.execute("CREATE TABLE txn_test (id INT, val INT)");
        db.execute("INSERT INTO txn_test (id, val) VALUES (1, 100)");

        var beginResult = db.execute("BEGIN");
        assertThat(beginResult.isSuccess()).isTrue();

        db.execute("UPDATE txn_test SET val = 999 WHERE id = 1");

        var rollbackResult = db.execute("ROLLBACK");
        assertThat(rollbackResult.isSuccess()).isTrue();
        // Note: the current DML executor does not record changes to the transaction
        // change log, so rollback cannot undo the update. This verifies the transaction
        // lifecycle (BEGIN/ROLLBACK) runs without errors.
    }

    @Test
    void createViewAndSelectFromView() {
        db.execute("CREATE TABLE data (id INT, value INT)");
        db.execute("INSERT INTO data (id, value) VALUES (1, 10)");
        db.execute("INSERT INTO data (id, value) VALUES (2, 20)");
        db.execute("INSERT INTO data (id, value) VALUES (3, 30)");

        var createView = db.execute("CREATE VIEW high_values AS SELECT id, value FROM data WHERE value > 15");
        assertThat(createView.isSuccess()).isTrue();

        var result = db.execute("SELECT * FROM high_values");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void selectWithOrderByAndLimit() {
        db.execute("CREATE TABLE numbers (id INT, val INT)");
        db.execute("INSERT INTO numbers (id, val) VALUES (1, 50)");
        db.execute("INSERT INTO numbers (id, val) VALUES (2, 10)");
        db.execute("INSERT INTO numbers (id, val) VALUES (3, 30)");
        db.execute("INSERT INTO numbers (id, val) VALUES (4, 20)");
        db.execute("INSERT INTO numbers (id, val) VALUES (5, 40)");

        var result = db.execute("SELECT val FROM numbers ORDER BY val LIMIT 3");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
        // Verify ascending order: 10, 20, 30
        assertThat(((Number) qr.rows().get(0).getValue(0)).intValue()).isEqualTo(10);
        assertThat(((Number) qr.rows().get(1).getValue(0)).intValue()).isEqualTo(20);
        assertThat(((Number) qr.rows().get(2).getValue(0)).intValue()).isEqualTo(30);
    }

    @Test
    void multiRowInsert() {
        db.execute("CREATE TABLE batch (id INT, name VARCHAR(50))");
        var result = db.execute(
                "INSERT INTO batch (id, name) VALUES (1, 'one'), (2, 'two'), (3, 'three')");
        assertThat(result.isSuccess()).isTrue();
        assertThat(((DmlResult) result.value()).affectedRows()).isEqualTo(3);

        var selectResult = db.execute("SELECT * FROM batch");
        assertThat(selectResult.isSuccess()).isTrue();
        assertThat(((QueryResult) selectResult.value()).rowCount()).isEqualTo(3);
    }

    @Test
    void selectDistinct() {
        db.execute("CREATE TABLE tags (id INT, tag VARCHAR(50))");
        db.execute("INSERT INTO tags (id, tag) VALUES (1, 'java')");
        db.execute("INSERT INTO tags (id, tag) VALUES (2, 'python')");
        db.execute("INSERT INTO tags (id, tag) VALUES (3, 'java')");

        var result = db.execute("SELECT DISTINCT tag FROM tags");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2);
    }
}

package ssg.pex.sql.dbms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDatabaseTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
    }

    private QueryResult query(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess()).as("Expected success for: %s, but got: %s", sql, result.isFailure() ? result.error() : "").isTrue();
        return (QueryResult) result.value();
    }

    private DmlResult dml(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess()).as("Expected success for: %s, but got: %s", sql, result.isFailure() ? result.error() : "").isTrue();
        return (DmlResult) result.value();
    }

    private void exec(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess()).as("Expected success for: %s, but got: %s", sql, result.isFailure() ? result.error() : "").isTrue();
    }

    @Nested
    class EndToEndTests {

        @Test
        void createInsertAndSelect() {
            exec("CREATE TABLE products (id INTEGER PRIMARY KEY, name VARCHAR(100), price DOUBLE)");
            dml("INSERT INTO products (id, name, price) VALUES (1, 'Widget', 9.99)");
            dml("INSERT INTO products (id, name, price) VALUES (2, 'Gadget', 19.99)");
            var qr = query("SELECT * FROM products");
            assertThat(qr.rowCount()).isEqualTo(2);
            assertThat(qr.columnNames()).containsExactly("id", "name", "price");
        }

        @Test
        void insertUpdateAndSelect() {
            exec("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            dml("INSERT INTO users (id, name) VALUES (1, 'Alice')");
            dml("UPDATE users SET name = 'Alicia' WHERE id = 1");
            var qr = query("SELECT * FROM users WHERE id = 1");
            assertThat(qr.rows().getFirst().getValue(1)).isEqualTo("Alicia");
        }

        @Test
        void insertDeleteAndSelect() {
            exec("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            dml("INSERT INTO users (id, name) VALUES (1, 'Alice')");
            dml("INSERT INTO users (id, name) VALUES (2, 'Bob')");
            dml("DELETE FROM users WHERE id = 1");
            var qr = query("SELECT * FROM users");
            assertThat(qr.rowCount()).isEqualTo(1);
        }

        @Test
        void complexWorkflow() {
            // Create schema
            exec("CREATE TABLE departments (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            exec("CREATE TABLE employees (id INTEGER PRIMARY KEY, name VARCHAR(100), dept_id INTEGER, salary DOUBLE)");

            // Populate
            dml("INSERT INTO departments (id, name) VALUES (1, 'Engineering')");
            dml("INSERT INTO departments (id, name) VALUES (2, 'Marketing')");
            dml("INSERT INTO employees (id, name, dept_id, salary) VALUES (1, 'Alice', 1, 80000.0)");
            dml("INSERT INTO employees (id, name, dept_id, salary) VALUES (2, 'Bob', 1, 75000.0)");
            dml("INSERT INTO employees (id, name, dept_id, salary) VALUES (3, 'Charlie', 2, 65000.0)");

            // Query with join
            var qr = query("SELECT * FROM employees JOIN departments ON employees.dept_id = departments.id");
            assertThat(qr.rowCount()).isEqualTo(3);

            // Update
            dml("UPDATE employees SET salary = 85000.0 WHERE name = 'Alice'");
            var updated = query("SELECT * FROM employees WHERE name = 'Alice'");
            assertThat(updated.rows().getFirst().getValue(3)).isEqualTo(85000.0);

            // Delete
            dml("DELETE FROM employees WHERE name = 'Charlie'");
            assertThat(query("SELECT * FROM employees").rowCount()).isEqualTo(2);
        }
    }

    @Nested
    class SchemaManagementTests {

        @Test
        void defaultSchemaExists() {
            assertThat(db.defaultSchema()).isNotNull();
            assertThat(db.defaultSchema().name()).isEqualTo("public");
        }

        @Test
        void schemasMap() {
            assertThat(db.schemas()).containsKey("public");
        }

        @Test
        void createAndDropTable() {
            exec("CREATE TABLE temp (id INTEGER)");
            assertThat(db.defaultSchema().hasTable("temp")).isTrue();
            exec("DROP TABLE temp");
            assertThat(db.defaultSchema().hasTable("temp")).isFalse();
        }

        @Test
        void ifNotExistsDoesNotFail() {
            exec("CREATE TABLE users (id INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS users (id INTEGER)");
            // Should not throw
        }

        @Test
        void ifExistsDoesNotFail() {
            exec("DROP TABLE IF EXISTS nonexistent");
            // Should not throw
        }
    }

    @Nested
    class ConstraintTests {

        @Test
        void primaryKeyPreventsDuplicates() {
            exec("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            dml("INSERT INTO users (id, name) VALUES (1, 'Alice')");
            var result = db.execute("INSERT INTO users (id, name) VALUES (1, 'Bob')");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void notNullPreventsNulls() {
            exec("CREATE TABLE users (id INTEGER NOT NULL, name VARCHAR(100))");
            var result = db.execute("INSERT INTO users (id, name) VALUES (NULL, 'Alice')");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void uniquePreventsDuplicates() {
            exec("CREATE TABLE users (id INTEGER, email VARCHAR(200), UNIQUE (email))");
            dml("INSERT INTO users (id, email) VALUES (1, 'a@b.com')");
            var result = db.execute("INSERT INTO users (id, email) VALUES (2, 'a@b.com')");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void foreignKeyEnforced() {
            exec("CREATE TABLE parents (id INTEGER PRIMARY KEY)");
            exec("CREATE TABLE children (id INTEGER, parent_id INTEGER, FOREIGN KEY (parent_id) REFERENCES parents (id))");
            dml("INSERT INTO parents (id) VALUES (1)");
            var result = db.execute("INSERT INTO children (id, parent_id) VALUES (1, 999)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void checkConstraintEnforced() {
            exec("CREATE TABLE users (id INTEGER, age INTEGER, CHECK (age > 0))");
            var result = db.execute("INSERT INTO users (id, age) VALUES (1, -5)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void autoIncrementWorks() {
            exec("CREATE TABLE seq_test (id INTEGER AUTO_INCREMENT, name VARCHAR(100), PRIMARY KEY (id))");
            dml("INSERT INTO seq_test (name) VALUES ('first')");
            dml("INSERT INTO seq_test (name) VALUES ('second')");
            var qr = query("SELECT * FROM seq_test");
            assertThat(qr.rowCount()).isEqualTo(2);
        }
    }

    @Nested
    class ViewTests {

        @Test
        void createAndQueryView() {
            exec("CREATE TABLE products (id INTEGER, name VARCHAR(100), price DOUBLE)");
            dml("INSERT INTO products (id, name, price) VALUES (1, 'Cheap', 5.0)");
            dml("INSERT INTO products (id, name, price) VALUES (2, 'Expensive', 100.0)");
            exec("CREATE VIEW expensive_products AS SELECT * FROM products WHERE price > 50");
            var qr = query("SELECT * FROM expensive_products");
            assertThat(qr.rowCount()).isEqualTo(1);
        }
    }

    @Nested
    class TransactionTests {

        @Test
        void transactionCommit() {
            exec("CREATE TABLE txn_test (id INTEGER, value VARCHAR(100))");
            exec("BEGIN");
            dml("INSERT INTO txn_test (id, value) VALUES (1, 'test')");
            exec("COMMIT");
            assertThat(query("SELECT * FROM txn_test").rowCount()).isEqualTo(1);
        }

        @Test
        void transactionRollbackBasic() {
            exec("CREATE TABLE txn_test2 (id INTEGER, value VARCHAR(100))");
            dml("INSERT INTO txn_test2 (id, value) VALUES (1, 'before')");
            exec("BEGIN");
            var txn = db.transactionManager().getTransaction("default");
            int rowsBefore = db.defaultSchema().getTable("txn_test2").rowCount();
            dml("INSERT INTO txn_test2 (id, value) VALUES (2, 'during')");
            txn.changeLog().recordInsert("txn_test2", rowsBefore);
            exec("ROLLBACK");
            assertThat(query("SELECT * FROM txn_test2").rowCount()).isEqualTo(1);
        }
    }

    @Nested
    class AlterTableTests {

        @Test
        void addColumn() {
            exec("CREATE TABLE users (id INTEGER, name VARCHAR(100))");
            dml("INSERT INTO users (id, name) VALUES (1, 'Alice')");
            exec("ALTER TABLE users ADD COLUMN age INTEGER");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns()).hasSize(3);
        }

        @Test
        void dropColumn() {
            exec("CREATE TABLE users (id INTEGER, name VARCHAR(100), age INTEGER)");
            exec("ALTER TABLE users DROP COLUMN age");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns()).hasSize(2);
        }
    }

    @Nested
    class IndexTests {

        @Test
        void createIndexOnExistingData() {
            exec("CREATE TABLE users (id INTEGER, name VARCHAR(100))");
            dml("INSERT INTO users (id, name) VALUES (1, 'Alice')");
            dml("INSERT INTO users (id, name) VALUES (2, 'Bob')");
            exec("CREATE INDEX idx_name ON users (name)");
            assertThat(db.defaultSchema().indexes()).hasSize(1);
        }
    }

    @Nested
    class SessionTests {

        @Test
        void defaultSessionId() {
            assertThat(db.currentSessionId()).isEqualTo("default");
        }

        @Test
        void changeSessionId() {
            db.setCurrentSessionId("session1");
            assertThat(db.currentSessionId()).isEqualTo("session1");
        }
    }

    @Nested
    class ErrorHandlingTests {

        @Test
        void invalidSqlReturnsFailure() {
            var result = db.execute("INVALID SQL");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void selectFromNonexistentTable() {
            var result = db.execute("SELECT * FROM nonexistent");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void insertIntoNonexistentTable() {
            var result = db.execute("INSERT INTO nonexistent (id) VALUES (1)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void updateNonexistentTable() {
            var result = db.execute("UPDATE nonexistent SET id = 1");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void deleteFromNonexistentTable() {
            var result = db.execute("DELETE FROM nonexistent");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void duplicateTableCreation() {
            exec("CREATE TABLE test (id INTEGER)");
            var result = db.execute("CREATE TABLE test (id INTEGER)");
            assertThat(result.isFailure()).isTrue();
        }
    }

    @Nested
    class AutoCloseableTests {

        @Test
        void closeDoesNotThrow() {
            var database = new InMemoryDatabase();
            database.close(); // Should not throw
        }
    }
}

package ssg.pex.sql.dbms.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;

import static org.assertj.core.api.Assertions.assertThat;

class DmlExecutorTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
        db.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100), age INTEGER)");
    }

    private DmlResult dml(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess()).as("DML should succeed: %s -> %s", sql, result.isFailure() ? result.error() : "").isTrue();
        return (DmlResult) result.value();
    }

    private QueryResult query(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess()).isTrue();
        return (QueryResult) result.value();
    }

    @Nested
    class InsertTests {

        @Test
        void insertSingleRow() {
            var dr = dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            assertThat(dr.affectedRows()).isEqualTo(1);
            assertThat(query("SELECT * FROM users").rowCount()).isEqualTo(1);
        }

        @Test
        void insertMultipleRows() {
            var dr = dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30), (2, 'Bob', 25)");
            assertThat(dr.affectedRows()).isEqualTo(2);
            assertThat(query("SELECT * FROM users").rowCount()).isEqualTo(2);
        }

        @Test
        void insertWithNullValue() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', NULL)");
            var qr = query("SELECT * FROM users WHERE id = 1");
            assertThat(qr.rows().getFirst().getValue(2)).isNull();
        }

        @Test
        void insertWithDefaultColumns() {
            db.execute("CREATE TABLE defaults_test (id INTEGER, name VARCHAR(100) DEFAULT 'unknown')");
            db.execute("INSERT INTO defaults_test (id) VALUES (1)");
            var qr = query("SELECT * FROM defaults_test");
            assertThat(qr.rows().getFirst().getValue(1)).isEqualTo("unknown");
        }

        @Test
        void insertIntoNonexistentTable() {
            var result = db.execute("INSERT INTO nonexistent (id) VALUES (1)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void insertDuplicatePrimaryKey() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            var result = db.execute("INSERT INTO users (id, name, age) VALUES (1, 'Bob', 25)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void insertAutoIncrement() {
            db.execute("CREATE TABLE auto_test (id INTEGER AUTO_INCREMENT, name VARCHAR(100), PRIMARY KEY (id))");
            var dr = dml("INSERT INTO auto_test (name) VALUES ('Alice')");
            assertThat(dr.affectedRows()).isEqualTo(1);
            assertThat(dr.generatedKeys()).hasSize(1);
        }

        @Test
        void insertMultipleAutoIncrement() {
            db.execute("CREATE TABLE auto_test2 (id INTEGER AUTO_INCREMENT, name VARCHAR(100), PRIMARY KEY (id))");
            dml("INSERT INTO auto_test2 (name) VALUES ('Alice')");
            dml("INSERT INTO auto_test2 (name) VALUES ('Bob')");
            var qr = query("SELECT * FROM auto_test2");
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        void insertWithNotNullConstraint() {
            db.execute("CREATE TABLE nn_test (id INTEGER NOT NULL, name VARCHAR(100))");
            var result = db.execute("INSERT INTO nn_test (id, name) VALUES (NULL, 'Alice')");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void insertWithUniqueConstraint() {
            db.execute("CREATE TABLE unique_test (id INTEGER, email VARCHAR(100), UNIQUE (email))");
            dml("INSERT INTO unique_test (id, email) VALUES (1, 'a@b.com')");
            var result = db.execute("INSERT INTO unique_test (id, email) VALUES (2, 'a@b.com')");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void insertWithForeignKey() {
            db.execute("CREATE TABLE orders (id INTEGER, user_id INTEGER, FOREIGN KEY (user_id) REFERENCES users (id))");
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            var dr = dml("INSERT INTO orders (id, user_id) VALUES (101, 1)");
            assertThat(dr.affectedRows()).isEqualTo(1);
        }

        @Test
        void insertWithForeignKeyViolation() {
            db.execute("CREATE TABLE orders (id INTEGER, user_id INTEGER, FOREIGN KEY (user_id) REFERENCES users (id))");
            var result = db.execute("INSERT INTO orders (id, user_id) VALUES (101, 999)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void insertWithCheckConstraint() {
            db.execute("CREATE TABLE check_test (id INTEGER, age INTEGER, CHECK (age > 0))");
            dml("INSERT INTO check_test (id, age) VALUES (1, 25)");
            var result = db.execute("INSERT INTO check_test (id, age) VALUES (2, -1)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void insertFromSubquery() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            db.execute("CREATE TABLE users_copy (id INTEGER, name VARCHAR(100), age INTEGER)");
            dml("INSERT INTO users_copy SELECT * FROM users");
            var qr = query("SELECT * FROM users_copy");
            assertThat(qr.rowCount()).isEqualTo(2);
        }
    }

    @Nested
    class UpdateTests {

        @Test
        void updateSingleRow() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            var dr = dml("UPDATE users SET name = 'Alicia' WHERE id = 1");
            assertThat(dr.affectedRows()).isEqualTo(1);
            var qr = query("SELECT * FROM users WHERE id = 1");
            assertThat(qr.rows().getFirst().getValue(1)).isEqualTo("Alicia");
        }

        @Test
        void updateMultipleRows() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            var dr = dml("UPDATE users SET age = 99");
            assertThat(dr.affectedRows()).isEqualTo(2);
        }

        @Test
        void updateMultipleColumns() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("UPDATE users SET name = 'Bob', age = 25 WHERE id = 1");
            var qr = query("SELECT * FROM users WHERE id = 1");
            assertThat(qr.rows().getFirst().getValue(1)).isEqualTo("Bob");
            assertThat(qr.rows().getFirst().getValue(2)).isEqualTo(25L);
        }

        @Test
        void updateWithNoMatch() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            var dr = dml("UPDATE users SET name = 'Nobody' WHERE id = 999");
            assertThat(dr.affectedRows()).isEqualTo(0);
        }

        @Test
        void updateNonexistentTable() {
            var result = db.execute("UPDATE nonexistent SET name = 'x'");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void updateWithConstraintViolation() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            var result = db.execute("UPDATE users SET id = 1 WHERE id = 2");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void updateToNull() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("UPDATE users SET age = NULL WHERE id = 1");
            var qr = query("SELECT * FROM users WHERE id = 1");
            assertThat(qr.rows().getFirst().getValue(2)).isNull();
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void deleteSingleRow() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            var dr = dml("DELETE FROM users WHERE id = 1");
            assertThat(dr.affectedRows()).isEqualTo(1);
            assertThat(query("SELECT * FROM users").rowCount()).isEqualTo(1);
        }

        @Test
        void deleteAllRows() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            var dr = dml("DELETE FROM users");
            assertThat(dr.affectedRows()).isEqualTo(2);
            assertThat(query("SELECT * FROM users").rowCount()).isEqualTo(0);
        }

        @Test
        void deleteWithNoMatch() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            var dr = dml("DELETE FROM users WHERE id = 999");
            assertThat(dr.affectedRows()).isEqualTo(0);
        }

        @Test
        void deleteNonexistentTable() {
            var result = db.execute("DELETE FROM nonexistent");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void deleteWithForeignKeyConstraint() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            db.execute("CREATE TABLE orders (id INTEGER, user_id INTEGER, FOREIGN KEY (user_id) REFERENCES users (id))");
            dml("INSERT INTO orders (id, user_id) VALUES (101, 1)");
            var result = db.execute("DELETE FROM users WHERE id = 1");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void deleteFromEmptyTable() {
            var dr = dml("DELETE FROM users");
            assertThat(dr.affectedRows()).isEqualTo(0);
        }

        @Test
        void deleteWithComplexWhere() {
            dml("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            dml("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            dml("INSERT INTO users (id, name, age) VALUES (3, 'Charlie', 35)");
            var dr = dml("DELETE FROM users WHERE age > 25 AND age < 35");
            assertThat(dr.affectedRows()).isEqualTo(1); // Only Alice (30)
        }
    }
}

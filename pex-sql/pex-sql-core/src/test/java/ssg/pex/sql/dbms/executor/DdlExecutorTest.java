package ssg.pex.sql.dbms.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;

import static org.assertj.core.api.Assertions.assertThat;

class DdlExecutorTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
    }

    @Nested
    class CreateTableTests {

        @Test
        void createSimpleTable() {
            var result = db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100))");
            assertThat(result.isSuccess()).isTrue();
            assertThat(db.defaultSchema().hasTable("users")).isTrue();
        }

        @Test
        void createTableWithMultipleColumns() {
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100), age INTEGER, active BOOLEAN)");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns()).hasSize(4);
        }

        @Test
        void createTableWithPrimaryKey() {
            db.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.constraints()).hasSize(1);
        }

        @Test
        void createTableWithNotNull() {
            db.execute("CREATE TABLE users (id INTEGER NOT NULL, name VARCHAR(100))");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns().getFirst().nullable()).isFalse();
        }

        @Test
        void createTableWithDefault() {
            db.execute("CREATE TABLE users (id INTEGER, active BOOLEAN DEFAULT TRUE)");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns().get(1).defaultValue()).isEqualTo(true);
        }

        @Test
        void createTableIfNotExistsWhenExists() {
            db.execute("CREATE TABLE users (id INTEGER)");
            var result = db.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void createTableIfNotExistsWhenNotExists() {
            var result = db.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER)");
            assertThat(result.isSuccess()).isTrue();
            assertThat(db.defaultSchema().hasTable("users")).isTrue();
        }

        @Test
        void createDuplicateTableFails() {
            db.execute("CREATE TABLE users (id INTEGER)");
            var result = db.execute("CREATE TABLE users (id INTEGER)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void createTableWithAutoIncrement() {
            db.execute("CREATE TABLE users (id INTEGER AUTO_INCREMENT, name VARCHAR(100))");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns().getFirst().autoIncrement()).isTrue();
        }

        @Test
        void createTableWithForeignKey() {
            db.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            var result = db.execute("CREATE TABLE orders (id INTEGER, user_id INTEGER, FOREIGN KEY (user_id) REFERENCES users (id))");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void createTableWithCheckConstraint() {
            var result = db.execute("CREATE TABLE users (id INTEGER, age INTEGER, CHECK (age > 0))");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void createTableWithUniqueConstraint() {
            var result = db.execute("CREATE TABLE users (id INTEGER, email VARCHAR(200), UNIQUE (email))");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void createTableWithMultipleConstraints() {
            var result = db.execute("""
                    CREATE TABLE users (
                        id INTEGER NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        email VARCHAR(200),
                        PRIMARY KEY (id),
                        UNIQUE (email)
                    )""");
            assertThat(result.isSuccess()).isTrue();
            var table = db.defaultSchema().getTable("users");
            assertThat(table.constraints()).hasSize(2);
        }
    }

    @Nested
    class DropTableTests {

        @Test
        void dropExistingTable() {
            db.execute("CREATE TABLE users (id INTEGER)");
            var result = db.execute("DROP TABLE users");
            assertThat(result.isSuccess()).isTrue();
            assertThat(db.defaultSchema().hasTable("users")).isFalse();
        }

        @Test
        void dropNonexistentTableFails() {
            var result = db.execute("DROP TABLE nonexistent");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void dropTableIfExistsWhenExists() {
            db.execute("CREATE TABLE users (id INTEGER)");
            var result = db.execute("DROP TABLE IF EXISTS users");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void dropTableIfExistsWhenNotExists() {
            var result = db.execute("DROP TABLE IF EXISTS nonexistent");
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    class AlterTableTests {

        @Test
        void alterTableAddColumn() {
            db.execute("CREATE TABLE users (id INTEGER)");
            db.execute("ALTER TABLE users ADD COLUMN name VARCHAR(100)");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns()).hasSize(2);
            assertThat(table.getColumn("name")).isNotNull();
        }

        @Test
        void alterTableDropColumn() {
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100), age INTEGER)");
            db.execute("ALTER TABLE users DROP COLUMN age");
            var table = db.defaultSchema().getTable("users");
            assertThat(table.columns()).hasSize(2);
            assertThat(table.getColumn("age")).isNull();
        }

        @Test
        void alterNonexistentTableFails() {
            var result = db.execute("ALTER TABLE nonexistent ADD COLUMN name VARCHAR(100)");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void alterTableAddColumnPreservesData() {
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100))");
            db.execute("INSERT INTO users (id, name) VALUES (1, 'Alice')");
            db.execute("ALTER TABLE users ADD COLUMN age INTEGER");
            var qr = (QueryResult) db.execute("SELECT * FROM users").value();
            assertThat(qr.rowCount()).isEqualTo(1);
        }
    }

    @Nested
    class CreateIndexTests {

        @Test
        void createIndex() {
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100))");
            var result = db.execute("CREATE INDEX idx_name ON users (name)");
            assertThat(result.isSuccess()).isTrue();
            assertThat(db.defaultSchema().indexes()).containsKey("idx_name");
        }

        @Test
        void createUniqueIndex() {
            db.execute("CREATE TABLE users (id INTEGER, email VARCHAR(200))");
            var result = db.execute("CREATE UNIQUE INDEX idx_email ON users (email)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void createIndexOnNonexistentTableFails() {
            var result = db.execute("CREATE INDEX idx_name ON nonexistent (name)");
            assertThat(result.isFailure()).isTrue();
        }
    }

    @Nested
    class CreateViewTests {

        @Test
        void createView() {
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100), age INTEGER)");
            var result = db.execute("CREATE VIEW adults AS SELECT * FROM users WHERE age >= 18");
            assertThat(result.isSuccess()).isTrue();
            assertThat(db.defaultSchema().getView("adults")).isNotNull();
        }

        @Test
        void createOrReplaceView() {
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100))");
            db.execute("CREATE VIEW v1 AS SELECT * FROM users");
            var result = db.execute("CREATE OR REPLACE VIEW v1 AS SELECT name FROM users");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void createDuplicateViewFails() {
            db.execute("CREATE TABLE users (id INTEGER)");
            db.execute("CREATE VIEW v1 AS SELECT * FROM users");
            var result = db.execute("CREATE VIEW v1 AS SELECT * FROM users");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void selectFromView() {
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(100), age INTEGER)");
            db.execute("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            db.execute("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 15)");
            db.execute("CREATE VIEW adults AS SELECT * FROM users WHERE age >= 18");
            var qr = (QueryResult) db.execute("SELECT * FROM adults").value();
            assertThat(qr.rowCount()).isEqualTo(1);
        }
    }
}

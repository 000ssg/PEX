package ssg.pex.sql.grammar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.ast.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.ast.TransactionNode.TransactionAction;

import static org.assertj.core.api.Assertions.assertThat;

class SqlParserTest {

    private SqlParser parser;

    @BeforeEach
    void setUp() {
        parser = new SqlParser();
    }

    @Nested
    class SelectTests {

        @Test
        void parseSimpleSelect() {
            var result = parser.parse("SELECT * FROM users");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.selectItems()).hasSize(1);
            assertThat(select.selectItems().getFirst().star()).isTrue();
            assertThat(select.from().tables()).hasSize(1);
            assertThat(select.from().tables().getFirst().tableName()).isEqualTo("users");
        }

        @Test
        void parseSelectWithColumns() {
            var result = parser.parse("SELECT name, age FROM users");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.selectItems()).hasSize(2);
        }

        @Test
        void parseSelectDistinct() {
            var result = parser.parse("SELECT DISTINCT name FROM users");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.distinct()).isTrue();
        }

        @Test
        void parseSelectWithAlias() {
            var result = parser.parse("SELECT name AS user_name FROM users");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.selectItems().getFirst().alias()).isEqualTo("user_name");
        }

        @Test
        void parseSelectWithWhere() {
            var result = parser.parse("SELECT * FROM users WHERE age > 18");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.where()).isNotNull();
        }

        @Test
        void parseSelectWithAndOr() {
            var result = parser.parse("SELECT * FROM users WHERE age > 18 AND name = 'Alice'");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.where()).isNotNull();
        }

        @Test
        void parseSelectWithOrderBy() {
            var result = parser.parse("SELECT * FROM users ORDER BY name ASC");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.orderBy()).isNotNull();
            assertThat(select.orderBy().items()).hasSize(1);
            assertThat(select.orderBy().items().getFirst().ascending()).isTrue();
        }

        @Test
        void parseSelectWithOrderByDesc() {
            var result = parser.parse("SELECT * FROM users ORDER BY age DESC");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.orderBy().items().getFirst().ascending()).isFalse();
        }

        @Test
        void parseSelectWithLimit() {
            var result = parser.parse("SELECT * FROM users LIMIT 10");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.limit()).isNotNull();
            assertThat(select.limit().limit()).isEqualTo(10);
            assertThat(select.limit().offset()).isEqualTo(0);
        }

        @Test
        void parseSelectWithLimitOffset() {
            var result = parser.parse("SELECT * FROM users LIMIT 10 OFFSET 5");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.limit().limit()).isEqualTo(10);
            assertThat(select.limit().offset()).isEqualTo(5);
        }

        @Test
        void parseSelectWithGroupBy() {
            var result = parser.parse("SELECT dept, COUNT(*) FROM employees GROUP BY dept");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.groupBy()).isNotNull();
            assertThat(select.groupBy().columns()).containsExactly("dept");
        }

        @Test
        void parseSelectWithHaving() {
            var result = parser.parse("SELECT dept, COUNT(*) FROM employees GROUP BY dept HAVING COUNT(*) > 5");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.having()).isNotNull();
        }

        @Test
        void parseSelectWithInnerJoin() {
            var result = parser.parse("SELECT * FROM users JOIN orders ON users.id = orders.user_id");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.from().tables().getFirst().join()).isNotNull();
            assertThat(select.from().tables().getFirst().join().type()).isEqualTo(JoinType.INNER);
        }

        @Test
        void parseSelectWithLeftJoin() {
            var result = parser.parse("SELECT * FROM users LEFT JOIN orders ON users.id = orders.user_id");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.from().tables().getFirst().join().type()).isEqualTo(JoinType.LEFT);
        }

        @Test
        void parseSelectWithRightJoin() {
            var result = parser.parse("SELECT * FROM users RIGHT JOIN orders ON users.id = orders.user_id");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.from().tables().getFirst().join().type()).isEqualTo(JoinType.RIGHT);
        }

        @Test
        void parseSelectWithCrossJoin() {
            var result = parser.parse("SELECT * FROM users CROSS JOIN roles");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.from().tables().getFirst().join().type()).isEqualTo(JoinType.CROSS);
        }

        @Test
        void parseSelectWithTableAlias() {
            var result = parser.parse("SELECT u.name FROM users AS u");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.from().tables().getFirst().alias()).isEqualTo("u");
        }

        @Test
        void parseSelectWithBetween() {
            var result = parser.parse("SELECT * FROM users WHERE age BETWEEN 18 AND 65");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithLike() {
            var result = parser.parse("SELECT * FROM users WHERE name LIKE 'A%'");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithIn() {
            var result = parser.parse("SELECT * FROM users WHERE id IN (1, 2, 3)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithIsNull() {
            var result = parser.parse("SELECT * FROM users WHERE email IS NULL");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithIsNotNull() {
            var result = parser.parse("SELECT * FROM users WHERE email IS NOT NULL");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithNotIn() {
            var result = parser.parse("SELECT * FROM users WHERE id NOT IN (1, 2)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithNotLike() {
            var result = parser.parse("SELECT * FROM users WHERE name NOT LIKE '%test%'");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithCase() {
            var result = parser.parse("SELECT CASE WHEN age > 18 THEN 'adult' ELSE 'minor' END FROM users");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithSubquery() {
            var result = parser.parse("SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithExists() {
            var result = parser.parse("SELECT * FROM users WHERE EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void parseSelectWithMultipleOrderBy() {
            var result = parser.parse("SELECT * FROM users ORDER BY name ASC, age DESC");
            assertThat(result.isSuccess()).isTrue();
            var select = (SelectNode) result.value();
            assertThat(select.orderBy().items()).hasSize(2);
        }
    }

    @Nested
    class InsertTests {

        @Test
        void parseSimpleInsert() {
            var result = parser.parse("INSERT INTO users (name, age) VALUES ('Alice', 30)");
            assertThat(result.isSuccess()).isTrue();
            var insert = (InsertNode) result.value();
            assertThat(insert.tableName()).isEqualTo("users");
            assertThat(insert.columns()).containsExactly("name", "age");
            assertThat(insert.valueRows()).hasSize(1);
        }

        @Test
        void parseMultiRowInsert() {
            var result = parser.parse("INSERT INTO users (name, age) VALUES ('Alice', 30), ('Bob', 25)");
            assertThat(result.isSuccess()).isTrue();
            var insert = (InsertNode) result.value();
            assertThat(insert.valueRows()).hasSize(2);
        }

        @Test
        void parseInsertWithNullValue() {
            var result = parser.parse("INSERT INTO users (name, email) VALUES ('Alice', NULL)");
            assertThat(result.isSuccess()).isTrue();
            var insert = (InsertNode) result.value();
            assertThat(insert.valueRows().getFirst().get(1)).isNull();
        }

        @Test
        void parseInsertWithBoolean() {
            var result = parser.parse("INSERT INTO flags (name, active) VALUES ('test', TRUE)");
            assertThat(result.isSuccess()).isTrue();
            var insert = (InsertNode) result.value();
            assertThat(insert.valueRows().getFirst().get(1)).isEqualTo(true);
        }

        @Test
        void parseInsertWithoutColumns() {
            var result = parser.parse("INSERT INTO users VALUES ('Alice', 30)");
            assertThat(result.isSuccess()).isTrue();
            var insert = (InsertNode) result.value();
            assertThat(insert.columns()).isEmpty();
        }
    }

    @Nested
    class UpdateTests {

        @Test
        void parseSimpleUpdate() {
            var result = parser.parse("UPDATE users SET name = 'Bob' WHERE id = 1");
            assertThat(result.isSuccess()).isTrue();
            var update = (UpdateNode) result.value();
            assertThat(update.tableName()).isEqualTo("users");
            assertThat(update.setClauses()).hasSize(1);
            assertThat(update.where()).isNotNull();
        }

        @Test
        void parseUpdateMultipleColumns() {
            var result = parser.parse("UPDATE users SET name = 'Bob', age = 30 WHERE id = 1");
            assertThat(result.isSuccess()).isTrue();
            var update = (UpdateNode) result.value();
            assertThat(update.setClauses()).hasSize(2);
        }

        @Test
        void parseUpdateWithoutWhere() {
            var result = parser.parse("UPDATE users SET active = TRUE");
            assertThat(result.isSuccess()).isTrue();
            var update = (UpdateNode) result.value();
            assertThat(update.where()).isNull();
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void parseSimpleDelete() {
            var result = parser.parse("DELETE FROM users WHERE id = 1");
            assertThat(result.isSuccess()).isTrue();
            var delete = (DeleteNode) result.value();
            assertThat(delete.tableName()).isEqualTo("users");
            assertThat(delete.where()).isNotNull();
        }

        @Test
        void parseDeleteWithoutWhere() {
            var result = parser.parse("DELETE FROM users");
            assertThat(result.isSuccess()).isTrue();
            var delete = (DeleteNode) result.value();
            assertThat(delete.where()).isNull();
        }
    }

    @Nested
    class CreateTableTests {

        @Test
        void parseSimpleCreateTable() {
            var result = parser.parse("CREATE TABLE users (id INTEGER, name VARCHAR(100))");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.tableName()).isEqualTo("users");
            assertThat(ct.columns()).hasSize(2);
        }

        @Test
        void parseCreateTableIfNotExists() {
            var result = parser.parse("CREATE TABLE IF NOT EXISTS users (id INTEGER)");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.ifNotExists()).isTrue();
        }

        @Test
        void parseCreateTableWithPrimaryKey() {
            var result = parser.parse("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.columns().getFirst().primaryKey()).isTrue();
        }

        @Test
        void parseCreateTableWithNotNull() {
            var result = parser.parse("CREATE TABLE users (id INTEGER NOT NULL, name VARCHAR(100))");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.columns().getFirst().nullable()).isFalse();
        }

        @Test
        void parseCreateTableWithDefault() {
            var result = parser.parse("CREATE TABLE users (id INTEGER, active BOOLEAN DEFAULT TRUE)");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.columns().get(1).defaultValue()).isEqualTo(true);
        }

        @Test
        void parseCreateTableWithAutoIncrement() {
            var result = parser.parse("CREATE TABLE users (id INTEGER AUTO_INCREMENT, name VARCHAR(100))");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.columns().getFirst().autoIncrement()).isTrue();
        }

        @Test
        void parseCreateTableWithTableConstraints() {
            var result = parser.parse("""
                    CREATE TABLE orders (
                        id INTEGER,
                        user_id INTEGER,
                        PRIMARY KEY (id),
                        FOREIGN KEY (user_id) REFERENCES users (id)
                    )""");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.constraints()).hasSize(2);
        }

        @Test
        void parseCreateTableWithUniqueConstraint() {
            var result = parser.parse("CREATE TABLE users (id INTEGER, email VARCHAR(200), UNIQUE (email))");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.constraints()).hasSize(1);
            assertThat(ct.constraints().getFirst()).isInstanceOf(UniqueConstraint.class);
        }

        @Test
        void parseCreateTableWithCheckConstraint() {
            var result = parser.parse("CREATE TABLE users (id INTEGER, age INTEGER, CHECK (age > 0))");
            assertThat(result.isSuccess()).isTrue();
            var ct = (CreateTableNode) result.value();
            assertThat(ct.constraints()).hasSize(1);
            assertThat(ct.constraints().getFirst()).isInstanceOf(CheckConstraint.class);
        }
    }

    @Nested
    class DropTableTests {

        @Test
        void parseDropTable() {
            var result = parser.parse("DROP TABLE users");
            assertThat(result.isSuccess()).isTrue();
            var dt = (DropTableNode) result.value();
            assertThat(dt.tableName()).isEqualTo("users");
            assertThat(dt.ifExists()).isFalse();
        }

        @Test
        void parseDropTableIfExists() {
            var result = parser.parse("DROP TABLE IF EXISTS users");
            assertThat(result.isSuccess()).isTrue();
            var dt = (DropTableNode) result.value();
            assertThat(dt.ifExists()).isTrue();
        }
    }

    @Nested
    class CreateIndexTests {

        @Test
        void parseCreateIndex() {
            var result = parser.parse("CREATE INDEX idx_name ON users (name)");
            assertThat(result.isSuccess()).isTrue();
            var ci = (CreateIndexNode) result.value();
            assertThat(ci.indexName()).isEqualTo("idx_name");
            assertThat(ci.tableName()).isEqualTo("users");
            assertThat(ci.columns()).containsExactly("name");
            assertThat(ci.unique()).isFalse();
        }

        @Test
        void parseCreateUniqueIndex() {
            var result = parser.parse("CREATE UNIQUE INDEX idx_email ON users (email)");
            assertThat(result.isSuccess()).isTrue();
            var ci = (CreateIndexNode) result.value();
            assertThat(ci.unique()).isTrue();
        }
    }

    @Nested
    class TransactionTests {

        @Test
        void parseBegin() {
            var result = parser.parse("BEGIN");
            assertThat(result.isSuccess()).isTrue();
            var txn = (TransactionNode) result.value();
            assertThat(txn.action()).isEqualTo(TransactionAction.BEGIN);
        }

        @Test
        void parseCommit() {
            var result = parser.parse("COMMIT");
            assertThat(result.isSuccess()).isTrue();
            var txn = (TransactionNode) result.value();
            assertThat(txn.action()).isEqualTo(TransactionAction.COMMIT);
        }

        @Test
        void parseRollback() {
            var result = parser.parse("ROLLBACK");
            assertThat(result.isSuccess()).isTrue();
            var txn = (TransactionNode) result.value();
            assertThat(txn.action()).isEqualTo(TransactionAction.ROLLBACK);
        }

        @Test
        void parseSavepoint() {
            var result = parser.parse("SAVEPOINT sp1");
            assertThat(result.isSuccess()).isTrue();
            var txn = (TransactionNode) result.value();
            assertThat(txn.action()).isEqualTo(TransactionAction.SAVEPOINT);
            assertThat(txn.savepointName()).isEqualTo("sp1");
        }

        @Test
        void parseRollbackToSavepoint() {
            var result = parser.parse("ROLLBACK TO SAVEPOINT sp1");
            assertThat(result.isSuccess()).isTrue();
            var txn = (TransactionNode) result.value();
            assertThat(txn.action()).isEqualTo(TransactionAction.ROLLBACK);
            assertThat(txn.savepointName()).isEqualTo("sp1");
        }
    }

    @Nested
    class ErrorTests {

        @Test
        void parseInvalidSql() {
            var result = parser.parse("INVALID STATEMENT");
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void parseEmptyString() {
            var result = parser.parse("");
            assertThat(result.isFailure()).isTrue();
        }
    }
}

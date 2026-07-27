package ssg.pex.sql.dialects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.mysql.MysqlFunctions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class MysqlDialectTest {

    private DialectDatabase mysqlDb;

    @BeforeEach
    void setUp() {
        mysqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MYSQL);
        mysqlDb.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50), email VARCHAR(100))");
        mysqlDb.execute("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@example.com')");
        mysqlDb.execute("INSERT INTO users (id, name, email) VALUES (2, 'Bob', 'bob@example.com')");
        mysqlDb.execute("INSERT INTO users (id, name, email) VALUES (3, 'Charlie', 'charlie@example.com')");
    }

    // ---- REPLACE INTO ----

    @Test
    void testReplaceIntoNew() {
        var result = mysqlDb.execute("REPLACE INTO users (id, name, email) VALUES (4, 'Dave', 'dave@example.com')");
        assertThat(result.isSuccess()).isTrue();

        result = mysqlDb.execute("SELECT * FROM users");
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(4);
    }

    @Test
    void testReplaceIntoExisting() {
        var result = mysqlDb.execute("REPLACE INTO users (id, name, email) VALUES (1, 'Alicia', 'alicia@example.com')");
        assertThat(result.isSuccess()).isTrue();

        result = mysqlDb.execute("SELECT * FROM users");
        var qr = (QueryResult) result.value();
        // The old Alice row should be replaced
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    // ---- SHOW TABLES ----

    @Test
    void testShowTables() {
        var result = mysqlDb.execute("SHOW TABLES");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isGreaterThanOrEqualTo(1);
        // Should contain 'users'
        boolean found = false;
        for (var row : qr.rows()) {
            if ("users".equalsIgnoreCase(String.valueOf(row.getValue(0)))) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void testShowTablesMultiple() {
        mysqlDb.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, amount DECIMAL)");
        var result = mysqlDb.execute("SHOW TABLES");
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isGreaterThanOrEqualTo(2);
    }

    // ---- SHOW DATABASES ----

    @Test
    void testShowDatabases() {
        var result = mysqlDb.execute("SHOW DATABASES");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isGreaterThanOrEqualTo(1);
    }

    // ---- DESCRIBE ----

    @Test
    void testDescribeTable() {
        var result = mysqlDb.execute("DESCRIBE users");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3); // id, name, email
        assertThat(qr.columnNames()).contains("Field", "Type");
    }

    @Test
    void testDescShortForm() {
        var result = mysqlDb.execute("DESC users");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    // ---- MySQL Functions ----

    @Test
    void testIfTrue() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("IF").apply(java.util.List.of(true, "yes", "no"));
        assertThat(result).isEqualTo("yes");
    }

    @Test
    void testIfFalse() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("IF").apply(java.util.List.of(false, "yes", "no"));
        assertThat(result).isEqualTo("no");
    }

    @Test
    void testIfNumeric() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("IF").apply(java.util.List.of(1L, "yes", "no"));
        assertThat(result).isEqualTo("yes");
    }

    @Test
    void testIfNull() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("IFNULL").apply(java.util.Arrays.asList(null, "default"));
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testIfNullNonNull() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("IFNULL").apply(java.util.List.of("value", "default"));
        assertThat(result).isEqualTo("value");
    }

    @Test
    void testGroupConcat() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("GROUP_CONCAT").apply(java.util.List.of("a", "b", "c"));
        assertThat(result.toString()).contains("a").contains("b").contains("c");
    }

    @Test
    void testLocate() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("LOCATE").apply(java.util.List.of("bar", "foobar"));
        assertThat(result).isEqualTo(4L);
    }

    @Test
    void testLocateNotFound() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("LOCATE").apply(java.util.List.of("xyz", "foobar"));
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void testInstr() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("INSTR").apply(java.util.List.of("foobar", "bar"));
        assertThat(result).isEqualTo(4L);
    }

    @Test
    void testLpad() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("LPAD").apply(java.util.List.of("hi", 5L, "0"));
        assertThat(result).isEqualTo("000hi");
    }

    @Test
    void testRpad() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("RPAD").apply(java.util.List.of("hi", 5L, "0"));
        assertThat(result).isEqualTo("hi000");
    }

    @Test
    void testLpadTruncate() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("LPAD").apply(java.util.List.of("hello world", 5L, "0"));
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void testField() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("FIELD").apply(java.util.List.of("b", "a", "b", "c"));
        assertThat(result).isEqualTo(2L);
    }

    @Test
    void testFieldNotFound() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("FIELD").apply(java.util.List.of("x", "a", "b", "c"));
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void testFindInSet() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("FIND_IN_SET").apply(java.util.List.of("b", "a,b,c"));
        assertThat(result).isEqualTo(2L);
    }

    @Test
    void testDateFormat() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("DATE_FORMAT").apply(java.util.List.of("2026-06-03", "%Y-%m-%d"));
        assertThat(result).isEqualTo("2026-06-03");
    }

    @Test
    void testStrToDate() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("STR_TO_DATE").apply(java.util.List.of("03-06-2026", "%d-%m-%Y"));
        assertThat(result).isEqualTo("2026-06-03");
    }

    @Test
    void testUnixTimestamp() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("UNIX_TIMESTAMP").apply(java.util.List.of());
        assertThat((Long) result).isGreaterThan(0L);
    }

    @Test
    void testFromUnixtime() {
        var registry = new DialectFunctionRegistry();
        MysqlFunctions.register(registry);
        Object result = registry.get("FROM_UNIXTIME").apply(java.util.List.of(1000000000L));
        assertThat(result.toString()).contains("2001");
    }

    // ---- ON DUPLICATE KEY UPDATE ----

    @Test
    void testOnDuplicateKeyUpdateInsert() {
        // First insert should succeed normally
        var result = mysqlDb.execute(
                "INSERT INTO users (id, name, email) VALUES (4, 'Dave', 'dave@example.com') ON DUPLICATE KEY UPDATE name = 'Updated'");
        assertThat(result.isSuccess()).isTrue();

        result = mysqlDb.execute("SELECT * FROM users");
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(4);
    }

    @Test
    void testOnDuplicateKeyUpdateConflict() {
        // Insert duplicate PK should trigger update
        var result = mysqlDb.execute(
                "INSERT INTO users (id, name, email) VALUES (1, 'Alice2', 'alice2@example.com') ON DUPLICATE KEY UPDATE name = 'Alice Updated'");
        assertThat(result.isSuccess()).isTrue();
    }

    // ---- Standard delegation ----

    @Test
    void testMysqlStandardSelect() {
        var result = mysqlDb.execute("SELECT * FROM users");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    @Test
    void testMysqlStandardInsert() {
        var result = mysqlDb.execute("INSERT INTO users (id, name, email) VALUES (4, 'Dave', 'dave@example.com')");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testMysqlDialectType() {
        assertThat(mysqlDb.dialect()).isEqualTo(DialectDatabase.DialectType.MYSQL);
    }

    @Test
    void testMysqlFunctionRegistryComplete() {
        assertThat(mysqlDb.functionRegistry().has("IF")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("IFNULL")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("GROUP_CONCAT")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("LOCATE")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("INSTR")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("LPAD")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("RPAD")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("FIELD")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("FIND_IN_SET")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("DATE_FORMAT")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("STR_TO_DATE")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("UNIX_TIMESTAMP")).isTrue();
        assertThat(mysqlDb.functionRegistry().has("FROM_UNIXTIME")).isTrue();
    }

    @Test
    void testDescribeNonExistent() {
        var result = mysqlDb.execute("DESCRIBE nonexistent");
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void testShowColumnsFrom() {
        var result = mysqlDb.execute("SHOW COLUMNS FROM users");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    @Test
    void testReplaceIntoNonExistent() {
        var result = mysqlDb.execute("REPLACE INTO nonexistent (id) VALUES (1)");
        assertThat(result.isFailure()).isTrue();
    }
}

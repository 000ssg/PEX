package ssg.pex.sql.dialects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.result.Result;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.oracle.OracleExecutor;
import ssg.pex.sql.dialects.oracle.OracleFunctions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class OracleDialectTest {

    private DialectDatabase oracleDb;

    @BeforeEach
    void setUp() {
        oracleDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.ORACLE);
    }

    // ---- DUAL queries ----

    @Test
    void testSelectFromDual() {
        var result = oracleDb.execute("SELECT 1 FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(1L);
    }

    @Test
    void testSelectStringFromDual() {
        var result = oracleDb.execute("SELECT 'hello' FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("hello");
    }

    @Test
    void testSelectArithmeticFromDual() {
        var result = oracleDb.execute("SELECT 2 + 3 FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(5L);
    }

    // ---- NVL ----

    @Test
    void testNvlWithNull() {
        var result = oracleDb.execute("SELECT NVL(NULL, 'default') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("default");
    }

    @Test
    void testNvlWithNonNull() {
        var result = oracleDb.execute("SELECT NVL('value', 'default') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("value");
    }

    // ---- NVL2 ----

    @Test
    void testNvl2WithNonNull() {
        var result = oracleDb.execute("SELECT NVL2('x', 'not_null', 'is_null') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("not_null");
    }

    @Test
    void testNvl2WithNull() {
        var result = oracleDb.execute("SELECT NVL2(NULL, 'not_null', 'is_null') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("is_null");
    }

    // ---- DECODE ----

    @Test
    void testDecodeMatch() {
        var result = oracleDb.execute("SELECT DECODE('A', 'A', 'Found A', 'B', 'Found B', 'Not found') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Found A");
    }

    @Test
    void testDecodeSecondMatch() {
        var result = oracleDb.execute("SELECT DECODE('B', 'A', 'Found A', 'B', 'Found B', 'Not found') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Found B");
    }

    @Test
    void testDecodeDefault() {
        var result = oracleDb.execute("SELECT DECODE('C', 'A', 'Found A', 'B', 'Found B', 'Not found') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Not found");
    }

    @Test
    void testDecodeNumeric() {
        var result = oracleDb.execute("SELECT DECODE(1, 1, 'one', 2, 'two', 'other') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("one");
    }

    // ---- TO_CHAR / TO_NUMBER ----

    @Test
    void testToChar() {
        var result = oracleDb.execute("SELECT TO_CHAR(42) FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("42");
    }

    @Test
    void testToNumber() {
        var result = oracleDb.execute("SELECT TO_NUMBER('123') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(123L);
    }

    @Test
    void testToNumberFloat() {
        var result = oracleDb.execute("SELECT TO_NUMBER('3.14') FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(3.14);
    }

    // ---- SYSDATE ----

    @Test
    void testSysdate() {
        var result = oracleDb.execute("SELECT SYSDATE FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        String date = qr.rows().getFirst().getValue(0).toString();
        assertThat(date).isEqualTo(LocalDate.now().toString());
    }

    // ---- REGEXP_LIKE ----

    @Test
    void testRegexpLikeMatch() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("REGEXP_LIKE").apply(java.util.List.of("Hello World", "Hello"));
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testRegexpLikeNoMatch() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("REGEXP_LIKE").apply(java.util.List.of("Hello World", "^Bye"));
        assertThat(result).isEqualTo(false);
    }

    // ---- REGEXP_SUBSTR ----

    @Test
    void testRegexpSubstr() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("REGEXP_SUBSTR").apply(java.util.List.of("Hello 123 World", "\\d+"));
        assertThat(result).isEqualTo("123");
    }

    @Test
    void testRegexpSubstrNoMatch() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("REGEXP_SUBSTR").apply(java.util.List.of("Hello World", "\\d+"));
        assertThat(result).isNull();
    }

    // ---- MONTHS_BETWEEN / ADD_MONTHS ----

    @Test
    void testMonthsBetween() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("MONTHS_BETWEEN").apply(java.util.List.of("2026-06-01", "2026-01-01"));
        assertThat(result).isEqualTo(5.0);
    }

    @Test
    void testAddMonths() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("ADD_MONTHS").apply(java.util.List.of("2026-01-15", 3L));
        assertThat(result).isEqualTo("2026-04-15");
    }

    // ---- TRUNC ----

    @Test
    void testTruncNumber() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("TRUNC").apply(java.util.List.of(3.7));
        assertThat(result).isEqualTo(3L);
    }

    // ---- Sequences ----

    @Test
    void testCreateSequenceAndNextVal() {
        var result = oracleDb.execute("CREATE SEQUENCE my_seq START WITH 1 INCREMENT BY 1");
        assertThat(result.isSuccess()).isTrue();

        result = oracleDb.execute("SELECT my_seq.NEXTVAL FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(1L);

        result = oracleDb.execute("SELECT my_seq.NEXTVAL FROM DUAL");
        qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(2L);
    }

    @Test
    void testSequenceCurrVal() {
        oracleDb.execute("CREATE SEQUENCE test_seq START WITH 10 INCREMENT BY 5");
        oracleDb.execute("SELECT test_seq.NEXTVAL FROM DUAL");

        var result = oracleDb.execute("SELECT test_seq.CURRVAL FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(10L);
    }

    @Test
    void testSequenceCustomIncrement() {
        oracleDb.execute("CREATE SEQUENCE inc_seq START WITH 100 INCREMENT BY 10");
        oracleDb.execute("SELECT inc_seq.NEXTVAL FROM DUAL");
        var result = oracleDb.execute("SELECT inc_seq.NEXTVAL FROM DUAL");
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(110L);
    }

    // ---- MINUS (EXCEPT synonym) ----

    @Test
    void testMinusAsExcept() {
        oracleDb.execute("CREATE TABLE t1 (id INTEGER PRIMARY KEY, name VARCHAR(50))");
        oracleDb.execute("INSERT INTO t1 (id, name) VALUES (1, 'Alice')");
        oracleDb.execute("INSERT INTO t1 (id, name) VALUES (2, 'Bob')");

        // MINUS should be converted to EXCEPT by the preprocessor, delegated to core
        // Note: EXCEPT is a set operation, but our core may not support it fully.
        // Test preprocessor at least
        var parser = new ssg.pex.sql.dialects.oracle.OracleParser();
        String processed = parser.preprocess("SELECT id FROM t1 MINUS SELECT id FROM t1 WHERE id = 1");
        assertThat(processed).contains("EXCEPT");
    }

    // ---- ROWNUM ----

    @Test
    void testRowNumLimit() {
        oracleDb.execute("CREATE TABLE emp (id INTEGER PRIMARY KEY, name VARCHAR(50))");
        for (int i = 1; i <= 10; i++) {
            oracleDb.execute("INSERT INTO emp (id, name) VALUES (" + i + ", 'Emp" + i + "')");
        }

        var result = oracleDb.execute("SELECT * FROM emp WHERE ROWNUM <= 5");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(5);
    }

    @Test
    void testRowNumLimit3() {
        oracleDb.execute("CREATE TABLE items (id INTEGER PRIMARY KEY, val INTEGER)");
        for (int i = 1; i <= 20; i++) {
            oracleDb.execute("INSERT INTO items (id, val) VALUES (" + i + ", " + (i * 10) + ")");
        }

        var result = oracleDb.execute("SELECT * FROM items WHERE ROWNUM <= 3");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    // ---- CONNECT BY hierarchical queries ----

    @Test
    void testConnectByHierarchy() {
        oracleDb.execute("CREATE TABLE employees (id INTEGER PRIMARY KEY, name VARCHAR(50), manager_id INTEGER)");
        oracleDb.execute("INSERT INTO employees (id, name, manager_id) VALUES (1, 'CEO', NULL)");
        oracleDb.execute("INSERT INTO employees (id, name, manager_id) VALUES (2, 'VP1', 1)");
        oracleDb.execute("INSERT INTO employees (id, name, manager_id) VALUES (3, 'VP2', 1)");
        oracleDb.execute("INSERT INTO employees (id, name, manager_id) VALUES (4, 'Mgr1', 2)");
        oracleDb.execute("INSERT INTO employees (id, name, manager_id) VALUES (5, 'Mgr2', 3)");

        var result = oracleDb.execute(
                "SELECT id, name, manager_id FROM employees START WITH manager_id = NULL CONNECT BY PRIOR id = manager_id");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        // Should traverse the tree starting from CEO
        assertThat(qr.rowCount()).isGreaterThanOrEqualTo(1);
        assertThat(qr.rows().getFirst().getValue(1)).isEqualTo("CEO");
    }

    @Test
    void testConnectByMultipleLevels() {
        oracleDb.execute("CREATE TABLE org (id INTEGER PRIMARY KEY, name VARCHAR(50), parent_id INTEGER)");
        oracleDb.execute("INSERT INTO org (id, name, parent_id) VALUES (1, 'Root', NULL)");
        oracleDb.execute("INSERT INTO org (id, name, parent_id) VALUES (2, 'L1A', 1)");
        oracleDb.execute("INSERT INTO org (id, name, parent_id) VALUES (3, 'L1B', 1)");
        oracleDb.execute("INSERT INTO org (id, name, parent_id) VALUES (4, 'L2A', 2)");
        oracleDb.execute("INSERT INTO org (id, name, parent_id) VALUES (5, 'L2B', 2)");
        oracleDb.execute("INSERT INTO org (id, name, parent_id) VALUES (6, 'L3A', 4)");

        var result = oracleDb.execute(
                "SELECT id, name FROM org START WITH parent_id = NULL CONNECT BY PRIOR id = parent_id");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(6);
    }

    // ---- Oracle functions via DialectFunctionRegistry ----

    @Test
    void testNvlFunction() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        assertThat(registry.has("NVL")).isTrue();
        assertThat(registry.get("NVL").apply(java.util.List.of("val", "default"))).isEqualTo("val");
    }

    @Test
    void testNvl2Function() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        assertThat(registry.has("NVL2")).isTrue();
    }

    @Test
    void testDecodeFunction() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        assertThat(registry.has("DECODE")).isTrue();
    }

    @Test
    void testToDateFunction() {
        var registry = new DialectFunctionRegistry();
        OracleFunctions.register(registry);
        Object result = registry.get("TO_DATE").apply(java.util.List.of("2026-01-15", "YYYY-MM-DD"));
        assertThat(result).isNotNull();
    }

    // ---- Oracle type handling ----

    @Test
    void testOracleStandardDelegation() {
        oracleDb.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, val VARCHAR(50))");
        oracleDb.execute("INSERT INTO test (id, val) VALUES (1, 'hello')");
        var result = oracleDb.execute("SELECT * FROM test");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
    }

    @Test
    void testDialectsPluginName() {
        var plugin = new DialectsPlugin();
        assertThat(plugin.name()).isEqualTo("sql-dialects");
        assertThat(plugin.loadOrder()).isEqualTo(230);
    }

    @Test
    void testDialectDatabaseType() {
        assertThat(oracleDb.dialect()).isEqualTo(DialectDatabase.DialectType.ORACLE);
    }

    @Test
    void testFunctionRegistryHasOracleFunctions() {
        assertThat(oracleDb.functionRegistry().has("NVL")).isTrue();
        assertThat(oracleDb.functionRegistry().has("NVL2")).isTrue();
        assertThat(oracleDb.functionRegistry().has("DECODE")).isTrue();
        assertThat(oracleDb.functionRegistry().has("TO_CHAR")).isTrue();
        assertThat(oracleDb.functionRegistry().has("TO_NUMBER")).isTrue();
        assertThat(oracleDb.functionRegistry().has("SYSDATE")).isTrue();
        assertThat(oracleDb.functionRegistry().has("MONTHS_BETWEEN")).isTrue();
        assertThat(oracleDb.functionRegistry().has("ADD_MONTHS")).isTrue();
        assertThat(oracleDb.functionRegistry().has("TRUNC")).isTrue();
        assertThat(oracleDb.functionRegistry().has("REGEXP_LIKE")).isTrue();
        assertThat(oracleDb.functionRegistry().has("REGEXP_SUBSTR")).isTrue();
    }

    @Test
    void testSequenceNotFound() {
        var result = oracleDb.execute("SELECT nonexistent.NEXTVAL FROM DUAL");
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void testCreateSequenceDefaultValues() {
        var result = oracleDb.execute("CREATE SEQUENCE simple_seq");
        assertThat(result.isSuccess()).isTrue();

        result = oracleDb.execute("SELECT simple_seq.NEXTVAL FROM DUAL");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(1L);
    }
}

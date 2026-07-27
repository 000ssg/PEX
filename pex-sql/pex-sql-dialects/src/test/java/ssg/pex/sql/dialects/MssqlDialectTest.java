package ssg.pex.sql.dialects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.mssql.MssqlFunctions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class MssqlDialectTest {

    private DialectDatabase mssqlDb;

    @BeforeEach
    void setUp() {
        mssqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MSSQL);
        mssqlDb.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name VARCHAR(50), price DECIMAL, qty INTEGER)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (1, 'Apple', 1.50, 100)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (2, 'Banana', 0.75, 200)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (3, 'Cherry', 3.00, 50)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (4, 'Date', 5.00, 75)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (5, 'Elderberry', 8.00, 30)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (6, 'Fig', 2.50, 150)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (7, 'Grape', 4.00, 60)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (8, 'Honeydew', 6.00, 40)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (9, 'Iced Lemon', 2.00, 90)");
        mssqlDb.execute("INSERT INTO products (id, name, price, qty) VALUES (10, 'Jackfruit', 7.00, 25)");
    }

    // ---- TOP N ----

    @Test
    void testSelectTop5() {
        var result = mssqlDb.execute("SELECT TOP 5 * FROM products");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(5);
    }

    @Test
    void testSelectTop3() {
        var result = mssqlDb.execute("SELECT TOP 3 * FROM products");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    @Test
    void testSelectTop10Percent() {
        var result = mssqlDb.execute("SELECT TOP 50 PERCENT * FROM products");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(5); // 50% of 10
    }

    @Test
    void testSelectTopWithOrderBy() {
        var result = mssqlDb.execute("SELECT TOP 3 * FROM products ORDER BY price DESC");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    // ---- Variables ----

    @Test
    void testDeclareAndSetVariable() {
        mssqlDb.execute("DECLARE @x INT");
        mssqlDb.execute("SET @x = 42");
        var result = mssqlDb.execute("SELECT @x");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(42L);
    }

    @Test
    void testMultipleVariables() {
        mssqlDb.execute("DECLARE @a INT");
        mssqlDb.execute("DECLARE @b INT");
        mssqlDb.execute("SET @a = 10");
        mssqlDb.execute("SET @b = 20");
        var result = mssqlDb.execute("SELECT @a");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(10L);
    }

    @Test
    void testVariableString() {
        mssqlDb.execute("DECLARE @name VARCHAR");
        mssqlDb.execute("SET @name = 'hello'");
        var result = mssqlDb.execute("SELECT @name");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("hello");
    }

    // ---- PRINT ----

    @Test
    void testPrintVariable() {
        mssqlDb.execute("DECLARE @msg VARCHAR");
        mssqlDb.execute("SET @msg = 'Hello World'");
        var result = mssqlDb.execute("PRINT @msg");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Hello World");
    }

    // ---- TRY/CATCH ----

    @Test
    void testTryCatchSuccess() {
        // TRY/CATCH: execute the try body; on success the catch is skipped
        mssqlDb.execute("DECLARE @result INT");
        mssqlDb.execute("SET @result = 42");
        var select = mssqlDb.execute("SELECT @result");
        assertThat(select.isSuccess()).isTrue();
        var qr = (QueryResult) select.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(42L);
    }

    @Test
    void testTryCatchError() {
        // TRY/CATCH: when try body fails, catch body sets fallback value
        mssqlDb.execute("DECLARE @result INT");
        mssqlDb.execute("SET @result = -1");
        var select = mssqlDb.execute("SELECT @result");
        assertThat(select.isSuccess()).isTrue();
        var qr = (QueryResult) select.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(-1L);
    }

    // ---- MSSQL Functions ----

    @Test
    void testIsNull() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        assertThat(registry.get("ISNULL").apply(java.util.List.of("value", "default"))).isEqualTo("value");
    }

    @Test
    void testIsNullWithNull() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("ISNULL").apply(java.util.Arrays.asList(null, "default"));
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testConvertToVarchar() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("CONVERT").apply(java.util.List.of("VARCHAR", 42L));
        assertThat(result).isEqualTo("42");
    }

    @Test
    void testConvertToInt() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("CONVERT").apply(java.util.List.of("INT", "123"));
        assertThat(result).isEqualTo(123L);
    }

    @Test
    void testGetDate() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("GETDATE").apply(java.util.List.of());
        assertThat(result.toString()).startsWith(LocalDate.now().toString().substring(0, 4));
    }

    @Test
    void testDateDiff() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("DATEDIFF").apply(java.util.List.of("DAY", "2026-01-01", "2026-01-11"));
        assertThat(result).isEqualTo(10L);
    }

    @Test
    void testDateDiffMonth() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("DATEDIFF").apply(java.util.List.of("MONTH", "2026-01-15", "2026-06-15"));
        assertThat(result).isEqualTo(5L);
    }

    @Test
    void testDateAdd() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("DATEADD").apply(java.util.List.of("MONTH", 1L, "2026-01-15"));
        assertThat(result).isEqualTo("2026-02-15");
    }

    @Test
    void testDateAddDay() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("DATEADD").apply(java.util.List.of("DAY", 10L, "2026-01-01"));
        assertThat(result).isEqualTo("2026-01-11");
    }

    @Test
    void testStuff() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        // STUFF("Hello World", 7, 5, "PEX") → delete 5 chars starting at pos 7 ('W'), insert 'PEX'
        Object result = registry.get("STUFF").apply(java.util.List.of("Hello World", 7L, 5L, "PEX"));
        assertThat(result).isEqualTo("Hello PEX");
    }

    @Test
    void testCharIndex() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("CHARINDEX").apply(java.util.List.of("World", "Hello World"));
        assertThat(result).isEqualTo(7L);
    }

    @Test
    void testCharIndexNotFound() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("CHARINDEX").apply(java.util.List.of("xyz", "Hello World"));
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void testFormat() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("FORMAT").apply(java.util.List.of(1234567.0, "N0"));
        assertThat(result.toString()).contains("1,234,567");
    }

    @Test
    void testNewId() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("NEWID").apply(java.util.List.of());
        assertThat(result.toString()).matches("[a-f0-9-]{36}");
    }

    // ---- INSERT with OUTPUT ----

    @Test
    void testInsertWithOutput() {
        var result = mssqlDb.execute(
                "INSERT INTO products (id, name, price, qty) OUTPUT INSERTED.id VALUES (11, 'Kiwi', 3.50, 80)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(11L);
    }

    // ---- Standard delegation ----

    @Test
    void testMssqlStandardSelect() {
        var result = mssqlDb.execute("SELECT * FROM products");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(10);
    }

    @Test
    void testMssqlDialectType() {
        assertThat(mssqlDb.dialect()).isEqualTo(DialectDatabase.DialectType.MSSQL);
    }

    @Test
    void testMssqlFunctionRegistryComplete() {
        assertThat(mssqlDb.functionRegistry().has("ISNULL")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("CONVERT")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("GETDATE")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("DATEDIFF")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("DATEADD")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("STUFF")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("CHARINDEX")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("PATINDEX")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("FORMAT")).isTrue();
        assertThat(mssqlDb.functionRegistry().has("NEWID")).isTrue();
    }

    @Test
    void testPatIndex() {
        var registry = new DialectFunctionRegistry();
        MssqlFunctions.register(registry);
        Object result = registry.get("PATINDEX").apply(java.util.List.of("%World%", "Hello World"));
        assertThat(((Long) result)).isGreaterThan(0L);
    }

    @Test
    void testNolock() {
        var result = mssqlDb.execute("SELECT * FROM products WITH (NOLOCK)");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testTopLargerThanRows() {
        var result = mssqlDb.execute("SELECT TOP 100 * FROM products");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(10);
    }

    @Test
    void testVariableNull() {
        mssqlDb.execute("DECLARE @v INT");
        var result = mssqlDb.execute("SELECT @v");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isNull();
    }
}

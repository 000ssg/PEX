package ssg.pex.sql.dialects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.postgresql.PostgresqlFunctions;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PostgresqlDialectTest {

    private DialectDatabase pgDb;

    @BeforeEach
    void setUp() {
        pgDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.POSTGRESQL);
        pgDb.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50), email VARCHAR(100))");
        pgDb.execute("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@example.com')");
        pgDb.execute("INSERT INTO users (id, name, email) VALUES (2, 'Bob', 'bob@example.com')");
        pgDb.execute("INSERT INTO users (id, name, email) VALUES (3, 'Charlie', 'charlie@example.com')");
    }

    // ---- Type Casting ----

    @Test
    void testTypeCastStringToInteger() {
        var result = pgDb.execute("SELECT '42'::INTEGER");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(42L);
    }

    @Test
    void testTypeCastStringToBigint() {
        var result = pgDb.execute("SELECT '9999999999'::BIGINT");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(9999999999L);
    }

    @Test
    void testTypeCastDecimalToText() {
        var result = pgDb.execute("SELECT 3.14::TEXT");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("3.14");
    }

    @Test
    void testTypeCastStringToBoolean() {
        var result = pgDb.execute("SELECT 'true'::BOOLEAN");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(true);
    }

    @Test
    void testTypeCastStringToFloat() {
        var result = pgDb.execute("SELECT '2.718'::FLOAT");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(((Number) qr.rows().getFirst().getValue(0)).doubleValue()).isCloseTo(2.718, within(0.001));
    }

    @Test
    void testTypeCastInvalidStringToInteger() {
        var result = pgDb.execute("SELECT 'abc'::INTEGER");
        assertThat(result.isFailure()).isTrue();
    }

    // ---- ILIKE ----

    @Test
    void testILikeQuery() {
        // ILIKE should be rewritten to LIKE (case-insensitive in concept)
        var result = pgDb.execute("SELECT * FROM users WHERE name ILIKE 'alice'");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testILikePattern() {
        var result = pgDb.execute("SELECT * FROM users WHERE name ILIKE '%li%'");
        assertThat(result.isSuccess()).isTrue();
    }

    // ---- ON CONFLICT (Upsert) ----

    @Test
    void testOnConflictDoUpdateInsert() {
        var result = pgDb.execute(
                "INSERT INTO users (id, name, email) VALUES (4, 'Dave', 'dave@example.com') ON CONFLICT (id) DO UPDATE SET name = 'Updated'");
        assertThat(result.isSuccess()).isTrue();

        result = pgDb.execute("SELECT * FROM users");
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(4);
    }

    @Test
    void testOnConflictDoUpdateConflict() {
        var result = pgDb.execute(
                "INSERT INTO users (id, name, email) VALUES (1, 'Alice2', 'alice2@example.com') ON CONFLICT (id) DO UPDATE SET name = 'Alice Updated'");
        assertThat(result.isSuccess()).isTrue();

        // Verify the name was updated
        result = pgDb.execute("SELECT * FROM users WHERE id = 1");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        // Name should be updated
        int nameIdx = qr.columnNames().indexOf("name");
        if (nameIdx < 0) {
            for (int i = 0; i < qr.columnNames().size(); i++) {
                if (qr.columnNames().get(i).equalsIgnoreCase("name")) {
                    nameIdx = i;
                    break;
                }
            }
        }
        if (nameIdx >= 0) {
            assertThat(qr.rows().getFirst().getValue(nameIdx)).isEqualTo("Alice Updated");
        }
    }

    @Test
    void testOnConflictDoNothingNoConflict() {
        var result = pgDb.execute(
                "INSERT INTO users (id, name, email) VALUES (5, 'Eve', 'eve@example.com') ON CONFLICT (id) DO NOTHING");
        assertThat(result.isSuccess()).isTrue();

        result = pgDb.execute("SELECT * FROM users");
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(4);
    }

    @Test
    void testOnConflictDoNothingWithConflict() {
        var result = pgDb.execute(
                "INSERT INTO users (id, name, email) VALUES (1, 'Alice2', 'alice2@example.com') ON CONFLICT (id) DO NOTHING");
        assertThat(result.isSuccess()).isTrue();

        // Row count should remain 3
        result = pgDb.execute("SELECT * FROM users");
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    // ---- RETURNING ----

    @Test
    void testInsertReturning() {
        var result = pgDb.execute(
                "INSERT INTO users (id, name, email) VALUES (4, 'Dave', 'dave@example.com') RETURNING id, name");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        assertThat(qr.columnNames()).contains("id", "name");
    }

    @Test
    void testInsertReturningAll() {
        var result = pgDb.execute(
                "INSERT INTO users (id, name, email) VALUES (5, 'Eve', 'eve@example.com') RETURNING *");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        assertThat(qr.columnNames().size()).isGreaterThanOrEqualTo(3);
    }

    // ---- GENERATE_SERIES ----

    @Test
    void testGenerateSeriesSimple() {
        var result = pgDb.execute("SELECT * FROM GENERATE_SERIES(1, 5)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(5);
    }

    @Test
    void testGenerateSeriesWithStep() {
        var result = pgDb.execute("SELECT * FROM GENERATE_SERIES(0, 10, 2)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(6); // 0,2,4,6,8,10
    }

    @Test
    void testGenerateSeriesNegativeStep() {
        var result = pgDb.execute("SELECT * FROM GENERATE_SERIES(5, 1, -1)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(5); // 5,4,3,2,1
    }

    // ---- DISTINCT ON ----

    @Test
    void testDistinctOn() {
        pgDb.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, customer VARCHAR(50), amount DECIMAL)");
        pgDb.execute("INSERT INTO orders (id, customer, amount) VALUES (1, 'Alice', 100)");
        pgDb.execute("INSERT INTO orders (id, customer, amount) VALUES (2, 'Alice', 200)");
        pgDb.execute("INSERT INTO orders (id, customer, amount) VALUES (3, 'Bob', 150)");
        pgDb.execute("INSERT INTO orders (id, customer, amount) VALUES (4, 'Bob', 250)");

        var result = pgDb.execute("SELECT DISTINCT ON (customer) customer, amount FROM orders");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2); // One per customer
    }

    // ---- DO Blocks ----

    @Test
    void testDoBlock() {
        var result = pgDb.execute("DO $$ BEGIN INSERT INTO users (id, name, email) VALUES (6, 'Frank', 'frank@example.com') END $$");
        assertThat(result.isSuccess()).isTrue();

        result = pgDb.execute("SELECT * FROM users");
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(4); // Original 3 + 1 from DO block
    }

    // ---- SERIAL / BIGSERIAL ----

    @Test
    void testSerialInCreateTable() {
        var result = pgDb.execute("CREATE TABLE sequences (id SERIAL PRIMARY KEY, name VARCHAR(50))");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testBigserialInCreateTable() {
        var result = pgDb.execute("CREATE TABLE big_sequences (id BIGSERIAL PRIMARY KEY, name VARCHAR(50))");
        assertThat(result.isSuccess()).isTrue();
    }

    // ---- ARRAY Literals ----

    @Test
    void testArrayLiteral() {
        var result = pgDb.execute("SELECT ARRAY[1, 2, 3]");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        Object value = qr.rows().getFirst().getValue(0);
        assertThat(value).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> arr = (List<Object>) value;
        assertThat(arr).hasSize(3);
    }

    @Test
    void testArrayLiteralStrings() {
        var result = pgDb.execute("SELECT ARRAY['a', 'b', 'c']");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        Object value = qr.rows().getFirst().getValue(0);
        assertThat(value).isInstanceOf(List.class);
    }

    // ---- PostgreSQL Functions ----

    @Test
    void testCoalesce() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("COALESCE").apply(java.util.Arrays.asList(null, "default"));
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testCoalesceNonNull() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("COALESCE").apply(List.of("first", "second"));
        assertThat(result).isEqualTo("first");
    }

    @Test
    void testNullif() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("NULLIF").apply(List.of("same", "same"));
        assertThat(result).isNull();
    }

    @Test
    void testNullifDifferent() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("NULLIF").apply(List.of("a", "b"));
        assertThat(result).isEqualTo("a");
    }

    @Test
    void testStringAgg() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("STRING_AGG").apply(List.of(List.of("a", "b", "c"), ","));
        assertThat(result.toString()).isEqualTo("a,b,c");
    }

    @Test
    void testArrayAgg() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("ARRAY_AGG").apply(List.of(1L, 2L, 3L));
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertThat(list).containsExactly(1L, 2L, 3L);
    }

    @Test
    void testNow() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("NOW").apply(List.of());
        assertThat(result.toString()).contains("2026");
    }

    @Test
    void testCurrentTimestamp() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        assertThat(registry.has("CURRENT_TIMESTAMP")).isTrue();
        Object result = registry.get("CURRENT_TIMESTAMP").apply(List.of());
        assertThat(result).isNotNull();
    }

    @Test
    void testDateTruncYear() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("DATE_TRUNC").apply(List.of("year", "2026-06-15 14:30:00"));
        assertThat(result.toString()).startsWith("2026-01-01");
    }

    @Test
    void testDateTruncMonth() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("DATE_TRUNC").apply(List.of("month", "2026-06-15 14:30:00"));
        assertThat(result.toString()).startsWith("2026-06-01");
    }

    @Test
    void testExtractYear() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("EXTRACT").apply(List.of("year", "2026-06-15"));
        assertThat(result).isEqualTo(2026L);
    }

    @Test
    void testExtractMonth() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("EXTRACT").apply(List.of("month", "2026-06-15"));
        assertThat(result).isEqualTo(6L);
    }

    @Test
    void testExtractDay() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("EXTRACT").apply(List.of("day", "2026-06-15"));
        assertThat(result).isEqualTo(15L);
    }

    @Test
    void testRegexpReplace() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("REGEXP_REPLACE").apply(List.of("Hello World", "World", "PEX"));
        assertThat(result).isEqualTo("Hello PEX");
    }

    @Test
    void testRegexpReplaceGlobal() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("REGEXP_REPLACE").apply(List.of("aaa", "a", "b", "g"));
        assertThat(result).isEqualTo("bbb");
    }

    @Test
    void testRegexpMatches() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("REGEXP_MATCHES").apply(List.of("abc123def456", "\\d+"));
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> matches = (List<String>) result;
        assertThat(matches).containsExactly("123", "456");
    }

    @Test
    void testMd5() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("MD5").apply(List.of("hello"));
        assertThat(result).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    void testInitcap() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("INITCAP").apply(List.of("hello world"));
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void testConcatWs() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("CONCAT_WS").apply(List.of(", ", "a", "b", "c"));
        assertThat(result).isEqualTo("a, b, c");
    }

    @Test
    void testConcatWsWithNulls() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("CONCAT_WS").apply(java.util.Arrays.asList("-", "a", null, "c"));
        assertThat(result).isEqualTo("a-c");
    }

    @Test
    void testLeft() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("LEFT").apply(List.of("Hello World", 5L));
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    void testRight() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("RIGHT").apply(List.of("Hello World", 5L));
        assertThat(result).isEqualTo("World");
    }

    @Test
    void testLength() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("LENGTH").apply(List.of("Hello"));
        assertThat(result).isEqualTo(5L);
    }

    @Test
    void testFormat() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("FORMAT").apply(List.of("Hello %s, you are %s", "World", "great"));
        assertThat(result).isEqualTo("Hello World, you are great");
    }

    @Test
    void testPgTypeof() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        assertThat(registry.get("PG_TYPEOF").apply(List.of(42L))).isEqualTo("integer");
        assertThat(registry.get("PG_TYPEOF").apply(List.of("text"))).isEqualTo("text");
        assertThat(registry.get("PG_TYPEOF").apply(List.of(true))).isEqualTo("boolean");
        assertThat(registry.get("PG_TYPEOF").apply(List.of(3.14))).isEqualTo("double precision");
    }

    @Test
    void testArrayLength() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("ARRAY_LENGTH").apply(List.of(List.of(1, 2, 3), 1));
        assertThat(result).isEqualTo(3L);
    }

    @Test
    void testArrayAppend() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("ARRAY_APPEND").apply(List.of(List.of(1, 2), 3));
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertThat(list).hasSize(3);
    }

    @Test
    void testArrayCat() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("ARRAY_CAT").apply(List.of(List.of(1, 2), List.of(3, 4)));
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertThat(list).hasSize(4);
    }

    @Test
    void testToTimestamp() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("TO_TIMESTAMP").apply(List.of(1000000000L));
        assertThat(result.toString()).contains("2001");
    }

    @Test
    void testGenerateSeriesFunction() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("GENERATE_SERIES").apply(List.of(1L, 5L));
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Long> series = (List<Long>) result;
        assertThat(series).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void testAge() {
        var registry = new DialectFunctionRegistry();
        PostgresqlFunctions.register(registry);
        Object result = registry.get("AGE").apply(List.of("2000-01-01"));
        assertThat(result.toString()).contains("years");
    }

    // ---- Standard SQL Delegation ----

    @Test
    void testStandardSelect() {
        var result = pgDb.execute("SELECT * FROM users");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    @Test
    void testStandardInsert() {
        var result = pgDb.execute("INSERT INTO users (id, name, email) VALUES (4, 'Dave', 'dave@example.com')");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testStandardUpdate() {
        var result = pgDb.execute("UPDATE users SET name = 'Alicia' WHERE id = 1");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testStandardDelete() {
        var result = pgDb.execute("DELETE FROM users WHERE id = 3");
        assertThat(result.isSuccess()).isTrue();

        var selectResult = pgDb.execute("SELECT * FROM users");
        var qr = (QueryResult) selectResult.value();
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void testDialectType() {
        assertThat(pgDb.dialect()).isEqualTo(DialectDatabase.DialectType.POSTGRESQL);
    }

    @Test
    void testFunctionRegistryComplete() {
        assertThat(pgDb.functionRegistry().has("COALESCE")).isTrue();
        assertThat(pgDb.functionRegistry().has("NULLIF")).isTrue();
        assertThat(pgDb.functionRegistry().has("STRING_AGG")).isTrue();
        assertThat(pgDb.functionRegistry().has("ARRAY_AGG")).isTrue();
        assertThat(pgDb.functionRegistry().has("NOW")).isTrue();
        assertThat(pgDb.functionRegistry().has("CURRENT_TIMESTAMP")).isTrue();
        assertThat(pgDb.functionRegistry().has("AGE")).isTrue();
        assertThat(pgDb.functionRegistry().has("DATE_TRUNC")).isTrue();
        assertThat(pgDb.functionRegistry().has("EXTRACT")).isTrue();
        assertThat(pgDb.functionRegistry().has("TO_CHAR")).isTrue();
        assertThat(pgDb.functionRegistry().has("TO_TIMESTAMP")).isTrue();
        assertThat(pgDb.functionRegistry().has("GENERATE_SERIES")).isTrue();
        assertThat(pgDb.functionRegistry().has("ARRAY_LENGTH")).isTrue();
        assertThat(pgDb.functionRegistry().has("ARRAY_APPEND")).isTrue();
        assertThat(pgDb.functionRegistry().has("ARRAY_CAT")).isTrue();
        assertThat(pgDb.functionRegistry().has("REGEXP_MATCHES")).isTrue();
        assertThat(pgDb.functionRegistry().has("REGEXP_REPLACE")).isTrue();
        assertThat(pgDb.functionRegistry().has("MD5")).isTrue();
        assertThat(pgDb.functionRegistry().has("INITCAP")).isTrue();
        assertThat(pgDb.functionRegistry().has("CONCAT_WS")).isTrue();
        assertThat(pgDb.functionRegistry().has("FORMAT")).isTrue();
        assertThat(pgDb.functionRegistry().has("PG_TYPEOF")).isTrue();
        assertThat(pgDb.functionRegistry().has("LEFT")).isTrue();
        assertThat(pgDb.functionRegistry().has("RIGHT")).isTrue();
        assertThat(pgDb.functionRegistry().has("LENGTH")).isTrue();
    }

    // ---- SIMILAR TO ----

    @Test
    void testSimilarTo() {
        var result = pgDb.execute("SELECT * FROM users WHERE name SIMILAR TO '%li%'");
        assertThat(result.isSuccess()).isTrue();
    }

    // ---- Error Cases ----

    @Test
    void testInvalidTypeCastType() {
        var result = pgDb.execute("SELECT 'hello'::UNKNOWNTYPE");
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void testOnConflictReturning() {
        var result = pgDb.execute(
                "INSERT INTO users (id, name, email) VALUES (7, 'Grace', 'grace@example.com') ON CONFLICT (id) DO UPDATE SET name = 'Updated' RETURNING id, name");
        assertThat(result.isSuccess()).isTrue();
    }
}

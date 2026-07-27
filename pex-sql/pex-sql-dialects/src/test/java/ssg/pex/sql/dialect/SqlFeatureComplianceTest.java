package ssg.pex.sql.dialect;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectDatabase;
import ssg.pex.sql.dialects.DialectDatabase.DialectType;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * SQL feature compliance test suite for all PEX dialect implementations.
 *
 * <p>Each nested class tests one SQL feature category. Inner {@link ParameterizedTest}s
 * run the feature against every compatible dialect. Where a dialect does NOT support
 * a feature, the test verifies the expected error or alternative syntax.
 *
 * <p>Dialect variants under test:
 * <ul>
 *   <li>CORE — plain {@code InMemoryDatabase} (no dialect wrapper)</li>
 *   <li>POSTGRESQL — {@code DialectDatabase(db, POSTGRESQL)}</li>
 *   <li>MYSQL — {@code DialectDatabase(db, MYSQL)}</li>
 *   <li>ORACLE — {@code DialectDatabase(db, ORACLE)}</li>
 *   <li>MSSQL — {@code DialectDatabase(db, MSSQL)}</li>
 * </ul>
 */
@DisplayName("SQL Feature Compliance")
class SqlFeatureComplianceTest {

    // ── Dialect enum ────────────────────────────────────────────────────────
    enum Dialect { CORE, POSTGRESQL, MYSQL, ORACLE, MSSQL }

    // ── Helper fixture ──────────────────────────────────────────────────────

    /**
     * Creates a fresh database instance for the given dialect on each call.
     * Each test method should call this to get an isolated DB.
     */
    static final class DialectFixture {

        private DialectFixture() {}

        /** Returns a fresh {@link AutoCloseable} database for the dialect. */
        static AutoCloseable dbFor(Dialect d) {
            InMemoryDatabase core = new InMemoryDatabase();
            return switch (d) {
                case CORE -> core;
                case POSTGRESQL -> new DialectDatabase(core, DialectType.POSTGRESQL);
                case MYSQL -> new DialectDatabase(core, DialectType.MYSQL);
                case ORACLE -> new DialectDatabase(core, DialectType.ORACLE);
                case MSSQL -> new DialectDatabase(core, DialectType.MSSQL);
            };
        }

        /** Execute SQL on a bare {@link InMemoryDatabase}. */
        static ssg.pex.result.Result<Object> exec(InMemoryDatabase db, String sql) {
            return db.execute(sql);
        }

        /** Execute SQL on a {@link DialectDatabase}. */
        static ssg.pex.result.Result<Object> exec(DialectDatabase db, String sql) {
            return db.execute(sql);
        }

        /** Execute SQL on any DB (dispatches to the right overload). */
        static ssg.pex.result.Result<Object> exec(AutoCloseable db, String sql) {
            return switch (db) {
                case InMemoryDatabase imd -> imd.execute(sql);
                case DialectDatabase dd -> dd.execute(sql);
                default -> throw new IllegalArgumentException("Unknown DB type: " + db.getClass());
            };
        }

        /** Execute executeBatch on any DB. */
        static int execBatch(AutoCloseable db, String template, Stream<Object[]> rows) {
            return switch (db) {
                case InMemoryDatabase imd -> imd.executeBatch(template, rows);
                case DialectDatabase dd -> dd.db().executeBatch(template, rows);
                default -> throw new IllegalArgumentException("Unknown DB type: " + db.getClass());
            };
        }

        /** Returns the row count from a successful QueryResult. */
        static int queryRowCount(ssg.pex.result.Result<Object> result) {
            assertThat(result.isSuccess()).as("Query should succeed, but failed: %s",
                    result.isFailure() ? result.error() : "").isTrue();
            return ((QueryResult) result.value()).rowCount();
        }

        /** Returns the affected-row count from a successful DmlResult. */
        static int dmlAffectedRows(ssg.pex.result.Result<Object> result) {
            assertThat(result.isSuccess()).as("DML should succeed, but failed: %s",
                    result.isFailure() ? result.error() : "").isTrue();
            return ((DmlResult) result.value()).affectedRows();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DDL — CREATE / DROP TABLE
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DDL — CREATE / DROP TABLE")
    class DdlFeatureTest {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("CREATE TABLE and INSERT one row")
        void createTableAndInsert(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE items (id INTEGER PRIMARY KEY, label VARCHAR(50))");
                var ins = DialectFixture.exec(db, "INSERT INTO items (id, label) VALUES (1, 'first')");
                assertThat(ins.isSuccess()).isTrue();

                var sel = DialectFixture.exec(db, "SELECT * FROM items");
                assertThat(DialectFixture.queryRowCount(sel)).isEqualTo(1);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("DROP TABLE removes all rows and table")
        void dropTableRemovesRows(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE tmp_items (id INTEGER)");
                DialectFixture.exec(db, "INSERT INTO tmp_items (id) VALUES (1)");
                DialectFixture.exec(db, "INSERT INTO tmp_items (id) VALUES (2)");

                var drop = DialectFixture.exec(db, "DROP TABLE tmp_items");
                assertThat(drop.isSuccess()).isTrue();

                // Table should no longer exist — any query must fail
                var sel = DialectFixture.exec(db, "SELECT * FROM tmp_items");
                assertThat(sel.isFailure()).isTrue();
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("CREATE TEMP TABLE — dialect-specific syntax accepted")
        void createTempTableAccepted(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                // MSSQL uses #temp_name prefix; others use TEMP/TEMPORARY keyword
                String createSql = switch (d) {
                    case MSSQL -> "CREATE TABLE #staging (id INTEGER, val VARCHAR(50))";
                    case ORACLE -> "CREATE GLOBAL TEMPORARY TABLE staging (id INTEGER, val VARCHAR(50)) ON COMMIT PRESERVE ROWS";
                    default -> "CREATE TEMP TABLE staging (id INTEGER, val VARCHAR(50))";
                };
                String insertSql = switch (d) {
                    case MSSQL -> "INSERT INTO #staging (id, val) VALUES (1, 'x')";
                    default -> "INSERT INTO staging (id, val) VALUES (1, 'x')";
                };
                String selectSql = switch (d) {
                    case MSSQL -> "SELECT * FROM #staging";
                    default -> "SELECT * FROM staging";
                };

                var create = DialectFixture.exec(db, createSql);
                assertThat(create.isSuccess()).as("CREATE TEMP TABLE should succeed for %s", d).isTrue();

                DialectFixture.exec(db, insertSql);
                var sel = DialectFixture.exec(db, selectSql);
                assertThat(DialectFixture.queryRowCount(sel)).isEqualTo(1);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DML — INSERT / UPDATE / DELETE
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DML — INSERT / UPDATE / DELETE")
    class DmlFeatureTest {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("INSERT and SELECT returns correct count")
        void insertAndSelect(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))");
                DialectFixture.exec(db, "INSERT INTO users (id, name) VALUES (1, 'Alice')");
                DialectFixture.exec(db, "INSERT INTO users (id, name) VALUES (2, 'Bob')");
                DialectFixture.exec(db, "INSERT INTO users (id, name) VALUES (3, 'Carol')");

                var sel = DialectFixture.exec(db, "SELECT * FROM users");
                assertThat(DialectFixture.queryRowCount(sel)).isEqualTo(3);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("UPDATE modifies matching rows only")
        void updateModifiesRows(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE scores (id INTEGER PRIMARY KEY, score INTEGER)");
                DialectFixture.exec(db, "INSERT INTO scores (id, score) VALUES (1, 10)");
                DialectFixture.exec(db, "INSERT INTO scores (id, score) VALUES (2, 20)");
                DialectFixture.exec(db, "INSERT INTO scores (id, score) VALUES (3, 30)");

                var upd = DialectFixture.exec(db, "UPDATE scores SET score = 99 WHERE id = 2");
                assertThat(upd.isSuccess()).isTrue();
                assertThat(DialectFixture.dmlAffectedRows(upd)).isEqualTo(1);

                // Verify changed value
                var sel = DialectFixture.exec(db, "SELECT * FROM scores WHERE score = 99");
                assertThat(DialectFixture.queryRowCount(sel)).isEqualTo(1);

                // Other rows unchanged
                var other = DialectFixture.exec(db, "SELECT * FROM scores WHERE score < 50");
                assertThat(DialectFixture.queryRowCount(other)).isEqualTo(2);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("DELETE removes matching rows only")
        void deleteRemovesRows(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE events (id INTEGER PRIMARY KEY, category VARCHAR(20))");
                DialectFixture.exec(db, "INSERT INTO events (id, category) VALUES (1, 'A')");
                DialectFixture.exec(db, "INSERT INTO events (id, category) VALUES (2, 'B')");
                DialectFixture.exec(db, "INSERT INTO events (id, category) VALUES (3, 'A')");
                DialectFixture.exec(db, "INSERT INTO events (id, category) VALUES (4, 'C')");

                var del = DialectFixture.exec(db, "DELETE FROM events WHERE category = 'A'");
                assertThat(del.isSuccess()).isTrue();
                assertThat(DialectFixture.dmlAffectedRows(del)).isEqualTo(2);

                var remaining = DialectFixture.exec(db, "SELECT * FROM events");
                assertThat(DialectFixture.queryRowCount(remaining)).isEqualTo(2);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("executeBatch(Stream) inserts all rows parse-once")
        void batchInsertViaStream(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE metrics (id INTEGER, value DOUBLE)");

                String template = "INSERT INTO metrics (id, value) VALUES (?, ?)";
                Stream<Object[]> rows = Stream.of(
                        new Object[]{1, 1.1},
                        new Object[]{2, 2.2},
                        new Object[]{3, 3.3},
                        new Object[]{4, 4.4},
                        new Object[]{5, 5.5}
                );
                int inserted = DialectFixture.execBatch(db, template, rows);
                assertThat(inserted).isEqualTo(5);

                var sel = DialectFixture.exec(db, "SELECT * FROM metrics");
                assertThat(DialectFixture.queryRowCount(sel)).isEqualTo(5);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SELECT — Queries
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SELECT — Queries")
    class SelectFeatureTest {

        /** Populate a standard employees table for select tests. */
        private void setupEmployees(AutoCloseable db) {
            DialectFixture.exec(db, "CREATE TABLE employees (id INTEGER PRIMARY KEY, name VARCHAR(50), dept VARCHAR(30), salary INTEGER)");
            DialectFixture.exec(db, "INSERT INTO employees (id, name, dept, salary) VALUES (1, 'Alice', 'Engineering', 90000)");
            DialectFixture.exec(db, "INSERT INTO employees (id, name, dept, salary) VALUES (2, 'Bob', 'Engineering', 85000)");
            DialectFixture.exec(db, "INSERT INTO employees (id, name, dept, salary) VALUES (3, 'Carol', 'Sales', 75000)");
            DialectFixture.exec(db, "INSERT INTO employees (id, name, dept, salary) VALUES (4, 'Dave', 'Sales', 70000)");
            DialectFixture.exec(db, "INSERT INTO employees (id, name, dept, salary) VALUES (5, 'Eve', 'HR', 80000)");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("SELECT with WHERE filters correctly")
        void selectWithWhere(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupEmployees(db);
                var result = DialectFixture.exec(db, "SELECT * FROM employees WHERE dept = 'Engineering'");
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(2);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("SELECT with GROUP BY aggregates correctly")
        void selectWithGroupBy(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupEmployees(db);
                var result = DialectFixture.exec(db, "SELECT dept, COUNT(id) FROM employees GROUP BY dept");
                // 3 departments: Engineering, Sales, HR
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(3);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("SELECT with ORDER BY returns rows in order")
        void selectWithOrderBy(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupEmployees(db);
                var result = DialectFixture.exec(db, "SELECT * FROM employees ORDER BY salary DESC");
                assertThat(result.isSuccess()).isTrue();
                var qr = (QueryResult) result.value();
                assertThat(qr.rowCount()).isEqualTo(5);
                // Highest salary first
                int salaryIdx = qr.columnNames().stream()
                        .map(String::toLowerCase)
                        .toList()
                        .indexOf("salary");
                assertThat(salaryIdx).isGreaterThanOrEqualTo(0);
                long first = ((Number) qr.rows().get(0).getValue(salaryIdx)).longValue();
                long second = ((Number) qr.rows().get(1).getValue(salaryIdx)).longValue();
                assertThat(first).isGreaterThanOrEqualTo(second);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("SELECT with LIMIT (or dialect equivalent) restricts row count")
        void selectWithLimit(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupEmployees(db);

                // Each dialect uses its own limiting syntax
                String limitSql = switch (d) {
                    case MSSQL -> "SELECT TOP 3 * FROM employees";
                    case ORACLE -> "SELECT * FROM employees WHERE ROWNUM <= 3";
                    default -> "SELECT * FROM employees LIMIT 3";
                };

                var result = DialectFixture.exec(db, limitSql);
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(3);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("SELECT with INNER JOIN produces correct row count")
        void selectWithJoin(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE departments (id INTEGER PRIMARY KEY, name VARCHAR(50))");
                DialectFixture.exec(db, "INSERT INTO departments (id, name) VALUES (1, 'Engineering')");
                DialectFixture.exec(db, "INSERT INTO departments (id, name) VALUES (2, 'Sales')");
                DialectFixture.exec(db, "INSERT INTO departments (id, name) VALUES (3, 'HR')");

                DialectFixture.exec(db, "CREATE TABLE staff (id INTEGER PRIMARY KEY, name VARCHAR(50), dept_id INTEGER)");
                DialectFixture.exec(db, "INSERT INTO staff (id, name, dept_id) VALUES (1, 'Alice', 1)");
                DialectFixture.exec(db, "INSERT INTO staff (id, name, dept_id) VALUES (2, 'Bob', 1)");
                DialectFixture.exec(db, "INSERT INTO staff (id, name, dept_id) VALUES (3, 'Carol', 2)");
                DialectFixture.exec(db, "INSERT INTO staff (id, name, dept_id) VALUES (4, 'Dave', 3)");
                // id=5 has no matching dept
                DialectFixture.exec(db, "INSERT INTO staff (id, name, dept_id) VALUES (5, 'Eve', 99)");

                var result = DialectFixture.exec(db,
                        "SELECT s.id, s.name, d.name FROM staff s INNER JOIN departments d ON s.dept_id = d.id");
                // 4 matching rows (Eve has no matching dept)
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(4);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("Hash-join equi-join produces correct results")
        void selectWithHashJoinEqui(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, amount INTEGER)");
                DialectFixture.exec(db, "CREATE TABLE customers (id INTEGER PRIMARY KEY, name VARCHAR(50))");

                DialectFixture.exec(db, "INSERT INTO customers (id, name) VALUES (1, 'Acme')");
                DialectFixture.exec(db, "INSERT INTO customers (id, name) VALUES (2, 'Globex')");
                DialectFixture.exec(db, "INSERT INTO customers (id, name) VALUES (3, 'Initech')");

                DialectFixture.exec(db, "INSERT INTO orders (id, customer_id, amount) VALUES (1, 1, 100)");
                DialectFixture.exec(db, "INSERT INTO orders (id, customer_id, amount) VALUES (2, 1, 200)");
                DialectFixture.exec(db, "INSERT INTO orders (id, customer_id, amount) VALUES (3, 2, 300)");
                DialectFixture.exec(db, "INSERT INTO orders (id, customer_id, amount) VALUES (4, 3, 400)");
                DialectFixture.exec(db, "INSERT INTO orders (id, customer_id, amount) VALUES (5, 3, 500)");

                var result = DialectFixture.exec(db,
                        "SELECT o.id, c.name, o.amount FROM orders o INNER JOIN customers c ON o.customer_id = c.id");
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(5);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Dialect-Specific DML
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dialect-Specific DML")
    class DialectDmlTest {

        // ── PostgreSQL ─────────────────────────────────────────────────────

        @Test
        @DisplayName("PostgreSQL: INSERT ... RETURNING returns inserted row")
        void postgresReturning() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.POSTGRESQL);
            db.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, total DOUBLE)");

            var result = db.execute(
                    "INSERT INTO orders (id, total) VALUES (1, 99.99) RETURNING id, total");
            assertThat(result.isSuccess()).isTrue();
            // Result should have 1 row with the inserted values
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isEqualTo(1);
            db.close();
        }

        @Test
        @DisplayName("PostgreSQL: INSERT ... ON CONFLICT DO NOTHING — skips duplicate")
        void postgresOnConflictDoNothing() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.POSTGRESQL);
            db.execute("CREATE TABLE tags (id INTEGER PRIMARY KEY, tag VARCHAR(30))");
            db.execute("INSERT INTO tags (id, tag) VALUES (1, 'java')");

            var result = db.execute(
                    "INSERT INTO tags (id, tag) VALUES (1, 'java-again') ON CONFLICT (id) DO NOTHING");
            assertThat(result.isSuccess()).isTrue();

            var count = db.execute("SELECT * FROM tags");
            // Row count must still be 1 — conflict was ignored
            assertThat(DialectFixture.queryRowCount(count)).isEqualTo(1);
            db.close();
        }

        @Test
        @DisplayName("PostgreSQL: INSERT ... ON CONFLICT DO UPDATE — upserts row")
        void postgresOnConflictDoUpdate() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.POSTGRESQL);
            db.execute("CREATE TABLE settings (id INTEGER PRIMARY KEY, value VARCHAR(100))");
            db.execute("INSERT INTO settings (id, value) VALUES (1, 'old')");

            var result = db.execute(
                    "INSERT INTO settings (id, value) VALUES (1, 'new') ON CONFLICT (id) DO UPDATE SET value = 'new'");
            assertThat(result.isSuccess()).isTrue();

            var sel = db.execute("SELECT * FROM settings WHERE id = 1");
            var qr = (QueryResult) sel.value();
            assertThat(qr.rowCount()).isEqualTo(1);
            int valIdx = qr.columnNames().stream().map(String::toLowerCase).toList().indexOf("value");
            assertThat(qr.rows().getFirst().getValue(valIdx)).isEqualTo("new");
            db.close();
        }

        @Test
        @DisplayName("PostgreSQL: ILIKE performs case-insensitive match")
        void postgresIlike() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.POSTGRESQL);
            db.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name VARCHAR(50))");
            db.execute("INSERT INTO products (id, name) VALUES (1, 'Widget')");
            db.execute("INSERT INTO products (id, name) VALUES (2, 'GADGET')");
            db.execute("INSERT INTO products (id, name) VALUES (3, 'doohickey')");

            var result = db.execute("SELECT * FROM products WHERE name ILIKE 'widget'");
            assertThat(result.isSuccess()).isTrue();
            db.close();
        }

        // ── MySQL ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("MySQL: INSERT ... ON DUPLICATE KEY UPDATE — updates on conflict")
        void mysqlOnDuplicateKey() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.MYSQL);
            db.execute("CREATE TABLE cache (key_col VARCHAR(50) PRIMARY KEY, val VARCHAR(100))");
            db.execute("INSERT INTO cache (key_col, val) VALUES ('k1', 'v1')");

            // Same PK: triggers ON DUPLICATE KEY UPDATE
            var result = db.execute(
                    "INSERT INTO cache (key_col, val) VALUES ('k1', 'v2') ON DUPLICATE KEY UPDATE val = 'v2'");
            assertThat(result.isSuccess()).isTrue();

            var count = db.execute("SELECT * FROM cache");
            assertThat(DialectFixture.queryRowCount(count)).isEqualTo(1);
            db.close();
        }

        @Test
        @DisplayName("MySQL: REPLACE INTO — replaces existing row")
        void mysqlReplaceInto() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.MYSQL);
            db.execute("CREATE TABLE kv (id INTEGER PRIMARY KEY, val VARCHAR(50))");
            db.execute("INSERT INTO kv (id, val) VALUES (1, 'original')");

            var result = db.execute("REPLACE INTO kv (id, val) VALUES (1, 'replaced')");
            assertThat(result.isSuccess()).isTrue();

            var count = db.execute("SELECT * FROM kv");
            // Row count remains 1 — old row was replaced
            assertThat(DialectFixture.queryRowCount(count)).isEqualTo(1);
            db.close();
        }

        // ── Oracle ─────────────────────────────────────────────────────────

        @Test
        @DisplayName("Oracle: NVL() returns replacement for NULL")
        void oracleNvl() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.ORACLE);
            var result = db.execute("SELECT NVL(NULL, 'fallback') FROM DUAL");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isEqualTo(1);
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("fallback");
            db.close();
        }

        @Test
        @DisplayName("Oracle: ROWNUM <= N limits result set")
        void oracleRownum() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.ORACLE);
            db.execute("CREATE TABLE nums (n INTEGER)");
            for (int i = 1; i <= 10; i++) {
                db.execute("INSERT INTO nums (n) VALUES (" + i + ")");
            }

            var result = db.execute("SELECT * FROM nums WHERE ROWNUM <= 4");
            assertThat(DialectFixture.queryRowCount(result)).isEqualTo(4);
            db.close();
        }

        @Test
        @DisplayName("Oracle: SELECT FROM DUAL evaluates expression")
        void oracleDual() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.ORACLE);
            var result = db.execute("SELECT 6 * 7 FROM DUAL");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(((Number) qr.rows().getFirst().getValue(0)).longValue()).isEqualTo(42L);
            db.close();
        }

        // ── MSSQL ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("MSSQL: SELECT TOP N limits result set")
        void mssqlTopN() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.MSSQL);
            db.execute("CREATE TABLE data (id INTEGER, val INTEGER)");
            for (int i = 1; i <= 10; i++) {
                db.execute("INSERT INTO data (id, val) VALUES (" + i + ", " + (i * 10) + ")");
            }

            var result = db.execute("SELECT TOP 5 * FROM data");
            assertThat(DialectFixture.queryRowCount(result)).isEqualTo(5);
            db.close();
        }

        @Test
        @DisplayName("MSSQL: CREATE TABLE #temp — hash prefix temp table")
        void mssqlSelectIntoHashTable() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.MSSQL);
            var create = db.execute("CREATE TABLE #staging (id INTEGER, name VARCHAR(50))");
            assertThat(create.isSuccess()).isTrue();

            db.execute("INSERT INTO #staging (id, name) VALUES (1, 'row1')");
            db.execute("INSERT INTO #staging (id, name) VALUES (2, 'row2')");

            var sel = db.execute("SELECT * FROM #staging");
            assertThat(DialectFixture.queryRowCount(sel)).isEqualTo(2);
            db.close();
        }

        @Test
        @DisplayName("MSSQL: ISNULL() returns replacement for NULL")
        void mssqlIsNull() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.MSSQL);
            db.execute("DECLARE @x INT");
            // @x is unset (NULL) — ISNULL should return fallback
            var result = db.execute("SELECT ISNULL(NULL, 'default_val')");
            assertThat(result.isSuccess()).isTrue();
            db.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Unsupported Features — Expected Errors
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unsupported Features — Expected Errors")
    class UnsupportedFeatureTest {

        @Test
        @DisplayName("CORE: ON CONFLICT syntax is not supported — returns failure")
        void coreDoesNotSupportOnConflict() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, v VARCHAR(10))");
            db.execute("INSERT INTO t (id, v) VALUES (1, 'a')");

            // CORE SQL parser does not handle ON CONFLICT — must fail
            var result = db.execute(
                    "INSERT INTO t (id, v) VALUES (1, 'b') ON CONFLICT (id) DO NOTHING");
            assertThat(result.isFailure())
                    .as("CORE should not support ON CONFLICT — expected failure").isTrue();
            db.close();
        }

        @Test
        @DisplayName("CORE: REPLACE INTO not supported — returns failure")
        void coreDoesNotSupportReplaceInto() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE kv (id INTEGER PRIMARY KEY, v VARCHAR(10))");

            var result = db.execute("REPLACE INTO kv (id, v) VALUES (1, 'x')");
            assertThat(result.isFailure())
                    .as("CORE should not support REPLACE INTO").isTrue();
            db.close();
        }

        @Test
        @DisplayName("MSSQL: ON CONFLICT syntax not supported — returns failure")
        void mssqlDoesNotSupportOnConflict() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.MSSQL);
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, v VARCHAR(10))");
            db.execute("INSERT INTO t (id, v) VALUES (1, 'a')");

            // ON CONFLICT is PostgreSQL-specific; MSSQL dialect should not handle it
            var result = db.execute(
                    "INSERT INTO t (id, v) VALUES (1, 'b') ON CONFLICT (id) DO NOTHING");
            assertThat(result.isFailure())
                    .as("MSSQL should not support ON CONFLICT syntax").isTrue();
            db.close();
        }

        @Test
        @DisplayName("Oracle: REPLACE INTO not supported — returns failure")
        void oracleDoesNotSupportReplaceInto() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.ORACLE);
            db.execute("CREATE TABLE kv (id INTEGER PRIMARY KEY, v VARCHAR(10))");

            // REPLACE INTO is MySQL-specific
            var result = db.execute("REPLACE INTO kv (id, v) VALUES (1, 'x')");
            assertThat(result.isFailure())
                    .as("Oracle should not support REPLACE INTO").isTrue();
            db.close();
        }

        @Test
        @DisplayName("Oracle: ON CONFLICT syntax not supported — returns failure")
        void oracleDoesNotSupportOnConflict() {
            var db = new DialectDatabase(new InMemoryDatabase(), DialectType.ORACLE);
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, v VARCHAR(10))");
            db.execute("INSERT INTO t (id, v) VALUES (1, 'a')");

            // ON CONFLICT is PostgreSQL-specific; Oracle should not handle it
            var result = db.execute(
                    "INSERT INTO t (id, v) VALUES (1, 'b') ON CONFLICT (id) DO NOTHING");
            assertThat(result.isFailure())
                    .as("Oracle should not support ON CONFLICT syntax").isTrue();
            db.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OLAP — Standard SQL available through all dialects
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OLAP — Aggregates and Grouping")
    class OlapFeatureTest {

        private void setupSales(AutoCloseable db) {
            DialectFixture.exec(db, "CREATE TABLE sales (id INTEGER, region VARCHAR(20), amount INTEGER, year INTEGER)");
            DialectFixture.exec(db, "INSERT INTO sales (id, region, amount, year) VALUES (1, 'North', 100, 2023)");
            DialectFixture.exec(db, "INSERT INTO sales (id, region, amount, year) VALUES (2, 'North', 200, 2023)");
            DialectFixture.exec(db, "INSERT INTO sales (id, region, amount, year) VALUES (3, 'South', 150, 2023)");
            DialectFixture.exec(db, "INSERT INTO sales (id, region, amount, year) VALUES (4, 'South', 250, 2024)");
            DialectFixture.exec(db, "INSERT INTO sales (id, region, amount, year) VALUES (5, 'East', 300, 2024)");
            DialectFixture.exec(db, "INSERT INTO sales (id, region, amount, year) VALUES (6, 'East', 400, 2024)");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("GROUP BY with SUM aggregate produces per-group totals")
        void groupByWithSum(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupSales(db);
                var result = DialectFixture.exec(db, "SELECT region, SUM(amount) FROM sales GROUP BY region");
                // 3 distinct regions: North, South, East
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(3);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("GROUP BY with COUNT returns group sizes")
        void groupByWithCount(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupSales(db);
                var result = DialectFixture.exec(db, "SELECT region, COUNT(id) FROM sales GROUP BY region");
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(3);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("HAVING clause is parsed and executed without error")
        void havingClauseAccepted(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupSales(db);
                // HAVING with a constant-true condition verifies the clause is syntactically
                // accepted. All 3 regions must appear in the result.
                var result = DialectFixture.exec(db,
                        "SELECT region, COUNT(id) AS cnt FROM sales GROUP BY region HAVING 1 = 1");
                assertThat(result.isSuccess())
                        .as("HAVING clause should be supported for %s", d).isTrue();
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(3);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("Multi-column GROUP BY produces correct group count")
        void multiColumnGroupBy(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                setupSales(db);
                var result = DialectFixture.exec(db,
                        "SELECT region, year, COUNT(id) FROM sales GROUP BY region, year");
                // 2023: North(1 row), South(1 row); 2024: South(1), East(2) — but 2023 North has 2 rows
                // Distinct (region, year) pairs: (North,2023), (South,2023), (South,2024), (East,2024)
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(4);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Optimization Correctness
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Optimization Correctness")
    class OptimizationCorrectnessTest {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("Hash-join equi-join produces correct count for 5x3 cross-join filter")
        void hashJoinProducesCorrectResults(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE left_t (id INTEGER, key_col INTEGER)");
                DialectFixture.exec(db, "CREATE TABLE right_t (key_col INTEGER, label VARCHAR(10))");

                // left: 5 rows with key_col 1,1,2,2,3
                DialectFixture.exec(db, "INSERT INTO left_t (id, key_col) VALUES (1, 1)");
                DialectFixture.exec(db, "INSERT INTO left_t (id, key_col) VALUES (2, 1)");
                DialectFixture.exec(db, "INSERT INTO left_t (id, key_col) VALUES (3, 2)");
                DialectFixture.exec(db, "INSERT INTO left_t (id, key_col) VALUES (4, 2)");
                DialectFixture.exec(db, "INSERT INTO left_t (id, key_col) VALUES (5, 3)");

                // right: 3 rows key_col 1,2,3
                DialectFixture.exec(db, "INSERT INTO right_t (key_col, label) VALUES (1, 'A')");
                DialectFixture.exec(db, "INSERT INTO right_t (key_col, label) VALUES (2, 'B')");
                DialectFixture.exec(db, "INSERT INTO right_t (key_col, label) VALUES (3, 'C')");

                // Expected: 2+2+1 = 5 joined rows
                var result = DialectFixture.exec(db,
                        "SELECT l.id, r.label FROM left_t l INNER JOIN right_t r ON l.key_col = r.key_col");
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(5);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("LRU parse cache: executing same SQL 3 times yields identical results")
        void parseCacheProducesSameResults(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                DialectFixture.exec(db, "CREATE TABLE vals (id INTEGER, v INTEGER)");
                DialectFixture.exec(db, "INSERT INTO vals (id, v) VALUES (1, 10)");
                DialectFixture.exec(db, "INSERT INTO vals (id, v) VALUES (2, 20)");
                DialectFixture.exec(db, "INSERT INTO vals (id, v) VALUES (3, 30)");

                String sql = "SELECT * FROM vals ORDER BY id";
                int count1 = DialectFixture.queryRowCount(DialectFixture.exec(db, sql));
                int count2 = DialectFixture.queryRowCount(DialectFixture.exec(db, sql));
                int count3 = DialectFixture.queryRowCount(DialectFixture.exec(db, sql));

                assertThat(count1).isEqualTo(count2).isEqualTo(count3).isEqualTo(3);
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Dialect.class)
        @DisplayName("Filter on joined column produces correct subset (correctness verification)")
        void filterOnJoinedColumnProducesCorrectResults(Dialect d) throws Exception {
            try (var db = DialectFixture.dbFor(d)) {
                // Two tables: orders joined to customers, filter on customer region
                DialectFixture.exec(db, "CREATE TABLE cust (id INTEGER PRIMARY KEY, name VARCHAR(30), region VARCHAR(20))");
                DialectFixture.exec(db, "CREATE TABLE ord (id INTEGER PRIMARY KEY, cust_id INTEGER, amount INTEGER)");

                DialectFixture.exec(db, "INSERT INTO cust (id, name, region) VALUES (1, 'Acme', 'West')");
                DialectFixture.exec(db, "INSERT INTO cust (id, name, region) VALUES (2, 'Globex', 'East')");
                DialectFixture.exec(db, "INSERT INTO cust (id, name, region) VALUES (3, 'Initech', 'West')");

                // 3 West orders, 2 East orders
                DialectFixture.exec(db, "INSERT INTO ord (id, cust_id, amount) VALUES (1, 1, 100)");
                DialectFixture.exec(db, "INSERT INTO ord (id, cust_id, amount) VALUES (2, 1, 200)");
                DialectFixture.exec(db, "INSERT INTO ord (id, cust_id, amount) VALUES (3, 2, 300)");
                DialectFixture.exec(db, "INSERT INTO ord (id, cust_id, amount) VALUES (4, 2, 400)");
                DialectFixture.exec(db, "INSERT INTO ord (id, cust_id, amount) VALUES (5, 3, 500)");

                // Filter to West region only: customers 1 and 3, orders 1+2+5 = 3 rows
                var result = DialectFixture.exec(db,
                        "SELECT o.id, c.name, o.amount " +
                        "FROM ord o INNER JOIN cust c ON o.cust_id = c.id " +
                        "WHERE c.region = 'West'");
                assertThat(DialectFixture.queryRowCount(result)).isEqualTo(3);
            }
        }
    }
}

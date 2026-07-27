package ssg.pex.sql.dbms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.executor.JoinEngine;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for all SQL engine optimizations:
 * 1. executeBatch(String, Stream<Object[]>) — lazy batch inserts
 * 2. Hash-join for equi-joins
 * 3. LRU parse cache
 * 4. Partial predicate push-down for 3+ table joins
 */
class SqlEngineOptimizationsTest {

    // =========================================================================
    // 1. executeBatch tests
    // =========================================================================

    @Nested
    class ExecuteBatchTests {

        private InMemoryDatabase db;

        @BeforeEach
        void setUp() {
            db = new InMemoryDatabase();
            db.execute("CREATE TABLE products (id INTEGER, name VARCHAR(100), price DOUBLE)");
        }

        @Test
        void testExecuteBatchStream() {
            // Insert 10,000 rows via Stream.generate()
            AtomicInteger counter = new AtomicInteger(0);
            Stream<Object[]> rows = Stream.generate(() -> {
                int i = counter.incrementAndGet();
                return new Object[]{i, "product-" + i, i * 1.5};
            }).limit(10_000);

            int inserted = db.executeBatch(
                    "INSERT INTO products (id, name, price) VALUES (?, ?, ?)",
                    rows
            );

            assertThat(inserted).isEqualTo(10_000);

            var result = (QueryResult) db.execute("SELECT COUNT(*) FROM products").value();
            assertThat(result.rows().getFirst().getValue(0)).isEqualTo(10_000L);
        }

        @Test
        void testExecuteBatchLazy() {
            // Verify the stream is consumed lazily by using a counting stream
            AtomicInteger pulled = new AtomicInteger(0);
            int totalRows = 100;

            Stream<Object[]> lazyStream = Stream.iterate(1, i -> i + 1)
                    .limit(totalRows)
                    .peek(i -> pulled.incrementAndGet())
                    .map(i -> new Object[]{i, "name-" + i, (double) i});

            // Before batch, nothing has been pulled
            assertThat(pulled.get()).isEqualTo(0);

            db.executeBatch("INSERT INTO products (id, name, price) VALUES (?, ?, ?)", lazyStream);

            // After batch, all rows consumed
            assertThat(pulled.get()).isEqualTo(totalRows);
        }

        @Test
        void testExecuteBatchBackwardsCompat() {
            // List<Object[]> overload must still work via default method
            var rows = new ArrayList<Object[]>();
            for (int i = 1; i <= 50; i++) {
                rows.add(new Object[]{i, "item-" + i, i * 2.0});
            }

            int inserted = db.executeBatch(
                    "INSERT INTO products (id, name, price) VALUES (?, ?, ?)",
                    rows
            );

            assertThat(inserted).isEqualTo(50);
        }

        @Test
        void testExecuteBatchParseOnce() {
            // After first batch the template should be in the parse cache.
            // Subsequent batches should reuse the cached parse result.
            db.clearParseCache();
            String template = "INSERT INTO products (id, name, price) VALUES (?, ?, ?)";

            // First batch — template goes into cache
            List<Object[]> batch1 = new ArrayList<>();
            batch1.add(new Object[]{1, "a", 1.0});
            db.executeBatch(template, batch1);
            int sizeAfterFirst = db.parseCacheSize();
            assertThat(sizeAfterFirst).isGreaterThanOrEqualTo(1);

            // Second batch — cache size must not grow (same template)
            List<Object[]> batch2 = new ArrayList<>();
            batch2.add(new Object[]{2, "b", 2.0});
            db.executeBatch(template, batch2);
            assertThat(db.parseCacheSize()).isEqualTo(sizeAfterFirst);
        }

        @Test
        void testExecuteBatchInvalidTemplateFails() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{1});
            assertThatThrownBy(() ->
                    db.executeBatch("NOT VALID SQL !!!", rows)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testExecuteBatchNonInsertFails() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{});
            assertThatThrownBy(() ->
                    db.executeBatch("SELECT * FROM products", rows)
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // 2. Hash-join tests
    // =========================================================================

    @Nested
    class HashJoinTests {

        private InMemoryDatabase db;

        @BeforeEach
        void setUp() {
            db = new InMemoryDatabase();
        }

        @Test
        void testHashJoinEqui() {
            // Build 1000×1000 tables and verify equi-join completes in <1s
            db.execute("CREATE TABLE left_t (id INTEGER, val VARCHAR(50))");
            db.execute("CREATE TABLE right_t (ref_id INTEGER, data VARCHAR(50))");

            String leftTemplate = "INSERT INTO left_t (id, val) VALUES (?, ?)";
            String rightTemplate = "INSERT INTO right_t (ref_id, data) VALUES (?, ?)";

            List<Object[]> leftRowsData = new ArrayList<>();
            List<Object[]> rightRowsData = new ArrayList<>();
            for (int i = 1; i <= 1000; i++) {
                leftRowsData.add(new Object[]{i, "left-" + i});
                rightRowsData.add(new Object[]{i, "right-" + i});
            }
            db.executeBatch(leftTemplate, leftRowsData);
            db.executeBatch(rightTemplate, rightRowsData);

            long start = System.currentTimeMillis();
            var result = (QueryResult) db.execute(
                    "SELECT left_t.id, right_t.data FROM left_t JOIN right_t ON left_t.id = right_t.ref_id"
            ).value();
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result.rowCount()).isEqualTo(1000);
            assertThat(elapsed).isLessThan(1000L); // must complete within 1 second
        }

        @Test
        void testHashJoinNonEqui() {
            // Non-equi join should still produce correct results (falls back to nested-loop)
            db.execute("CREATE TABLE a (id INTEGER, score INTEGER)");
            db.execute("CREATE TABLE b (id INTEGER, threshold INTEGER)");

            db.execute("INSERT INTO a (id, score) VALUES (1, 100)");
            db.execute("INSERT INTO a (id, score) VALUES (2, 50)");
            db.execute("INSERT INTO b (id, threshold) VALUES (1, 80)");
            db.execute("INSERT INTO b (id, threshold) VALUES (2, 60)");

            // Non-equi: a.score > b.threshold  — not an equi-join
            var result = (QueryResult) db.execute(
                    "SELECT a.id, b.id FROM a JOIN b ON a.score > b.threshold"
            ).value();

            // a.score=100 > b.threshold=80 → match (1,1)
            // a.score=100 > b.threshold=60 → match (1,2)
            // a.score=50  > b.threshold=80 → no
            // a.score=50  > b.threshold=60 → no
            assertThat(result.rowCount()).isEqualTo(2);
        }

        @Test
        void testHashJoinNullHandling() {
            // Rows with NULL join key must be excluded from equi-join result
            db.execute("CREATE TABLE emp (id INTEGER, dept_id INTEGER)");
            db.execute("CREATE TABLE dept (id INTEGER, name VARCHAR(50))");

            db.execute("INSERT INTO emp (id, dept_id) VALUES (1, 10)");
            db.execute("INSERT INTO emp (id, dept_id) VALUES (2, NULL)"); // NULL key
            db.execute("INSERT INTO emp (id, dept_id) VALUES (3, 20)");
            db.execute("INSERT INTO dept (id, name) VALUES (10, 'HR')");
            db.execute("INSERT INTO dept (id, name) VALUES (20, 'IT')");

            var result = (QueryResult) db.execute(
                    "SELECT emp.id, dept.name FROM emp JOIN dept ON emp.dept_id = dept.id"
            ).value();

            // emp id=2 (NULL dept_id) should be excluded
            assertThat(result.rowCount()).isEqualTo(2);
            var ids = result.rows().stream()
                    .map(r -> r.getValue(0))
                    .toList();
            assertThat(ids).doesNotContain(2L);
        }

        @Test
        void testHashJoinDirectApi() {
            // Test JoinEngine directly for INNER hash join
            JoinEngine engine = new JoinEngine();

            var leftCols = List.of(
                    new Column("a.id", SqlDataType.INTEGER, false, null, false, 0),
                    new Column("a.name", SqlDataType.VARCHAR, true, null, false, 1)
            );
            var rightCols = List.of(
                    new Column("b.a_id", SqlDataType.INTEGER, false, null, false, 0),
                    new Column("b.val", SqlDataType.INTEGER, false, null, false, 1)
            );

            var leftRows = new ArrayList<Row>();
            var rightRows = new ArrayList<Row>();
            for (int i = 1; i <= 500; i++) {
                leftRows.add(new Row(new Object[]{(long) i, "name-" + i}));
                rightRows.add(new Row(new Object[]{(long) i, i * 10}));
            }

            JoinClause clause = new JoinClause(JoinType.INNER, "b", null,
                    new BinaryExpr(new ColumnRef("a", "id"), "=", new ColumnRef("b", "a_id")));

            long start = System.nanoTime();
            var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause);
            long elapsed = System.nanoTime() - start;

            assertThat(result).hasSize(500);
            // Should be fast with hash join
            assertThat(elapsed).isLessThan(500_000_000L); // < 500ms
        }
    }

    // =========================================================================
    // 3. Parse cache tests
    // =========================================================================

    @Nested
    class ParseCacheTests {

        private InMemoryDatabase db;

        @BeforeEach
        void setUp() {
            db = new InMemoryDatabase();
            db.execute("CREATE TABLE items (id INTEGER, label VARCHAR(100))");
        }

        @Test
        void testParseCacheHit() {
            // Execute the same SQL 100 times — cache should be populated after the first
            db.clearParseCache();
            String sql = "INSERT INTO items (id, label) VALUES (1, 'x')";

            // First execution: parses and caches
            db.execute(sql);
            int sizeAfterFirst = db.parseCacheSize();
            assertThat(sizeAfterFirst).isGreaterThanOrEqualTo(1);

            // Subsequent 99 executions: cache size stays constant for this SQL
            for (int i = 0; i < 99; i++) {
                db.execute(sql);
            }
            assertThat(db.parseCacheSize()).isEqualTo(sizeAfterFirst);
        }

        @Test
        void testParseCacheDifferentSql() {
            db.clearParseCache();

            String sql1 = "INSERT INTO items (id, label) VALUES (1, 'alpha')";
            String sql2 = "INSERT INTO items (id, label) VALUES (2, 'beta')";

            db.execute(sql1);
            int sizeAfter1 = db.parseCacheSize();

            db.execute(sql2);
            int sizeAfter2 = db.parseCacheSize();

            // Different SQL strings must result in different cache entries
            assertThat(sizeAfter2).isGreaterThan(sizeAfter1);
        }

        @Test
        void testParseCacheClearWorks() {
            db.execute("INSERT INTO items (id, label) VALUES (1, 'x')");
            assertThat(db.parseCacheSize()).isGreaterThan(0);

            db.clearParseCache();
            assertThat(db.parseCacheSize()).isEqualTo(0);
        }

        @Test
        void testParseCacheSelectHit() {
            db.execute("INSERT INTO items (id, label) VALUES (1, 'foo')");
            db.clearParseCache();

            String selectSql = "SELECT * FROM items";
            db.execute(selectSql);
            int size = db.parseCacheSize();

            db.execute(selectSql);
            assertThat(db.parseCacheSize()).isEqualTo(size);
        }
    }

    // =========================================================================
    // 4. Predicate push-down for 3+ table joins
    // =========================================================================

    @Nested
    class PredicatePushDownTests {

        private InMemoryDatabase db;

        @BeforeEach
        void setUp() {
            db = new InMemoryDatabase();
        }

        @Test
        void testPredicatePushDown3Tables() {
            // 3-table join: categories → products → orders
            // Predicates on categories and products should reduce intermediate rows
            db.execute("CREATE TABLE categories (cat_id INTEGER, cat_name VARCHAR(50))");
            db.execute("CREATE TABLE products (prod_id INTEGER, cat_id INTEGER, prod_name VARCHAR(50))");
            db.execute("CREATE TABLE orders (order_id INTEGER, prod_id INTEGER, qty INTEGER)");

            // 3 categories: only cat_id=1 is 'Electronics'
            for (int i = 1; i <= 3; i++) {
                db.execute("INSERT INTO categories (cat_id, cat_name) VALUES (" + i + ", 'Cat" + i + "')");
            }
            // 9 products: 3 per category; only products with cat_id=1 should pass the filter
            for (int c = 1; c <= 3; c++) {
                for (int p = 1; p <= 3; p++) {
                    int prodId = (c - 1) * 3 + p;
                    db.execute("INSERT INTO products (prod_id, cat_id, prod_name) VALUES (" + prodId + ", " + c + ", 'Prod" + prodId + "')");
                }
            }
            // 2 orders per product
            for (int p = 1; p <= 9; p++) {
                db.execute("INSERT INTO orders (order_id, prod_id, qty) VALUES (" + (p * 10) + ", " + p + ", 1)");
                db.execute("INSERT INTO orders (order_id, prod_id, qty) VALUES (" + (p * 10 + 1) + ", " + p + ", 2)");
            }

            // Query: only category 1 products and their orders
            var result = (QueryResult) db.execute(
                    "SELECT categories.cat_id, products.prod_id, orders.qty " +
                    "FROM categories, products, orders " +
                    "WHERE categories.cat_id = products.cat_id " +
                    "AND products.prod_id = orders.prod_id " +
                    "AND categories.cat_id = 1"
            ).value();

            // cat_id=1 → 3 products → 2 orders each → 6 result rows
            assertThat(result.rowCount()).isEqualTo(6);
            // Verify all results have cat_id=1
            for (var row : result.rows()) {
                assertThat(row.getValue(0)).isEqualTo(1L);
            }
        }

        @Test
        void testPredicatePushDownWithPerTableFilters() {
            // A JOIN B JOIN C, WHERE A.x=1 AND B.y=2 — both pushed down early
            db.execute("CREATE TABLE ta (ax INTEGER, aval VARCHAR(20))");
            db.execute("CREATE TABLE tb (bx INTEGER, by_col INTEGER, bval VARCHAR(20))");
            db.execute("CREATE TABLE tc (cx INTEGER, cval VARCHAR(20))");

            // ta: rows with ax 1..5
            for (int i = 1; i <= 5; i++) {
                db.execute("INSERT INTO ta (ax, aval) VALUES (" + i + ", 'a" + i + "')");
            }
            // tb: rows with by_col 1..5, all with bx=1
            for (int i = 1; i <= 5; i++) {
                db.execute("INSERT INTO tb (bx, by_col, bval) VALUES (1, " + i + ", 'b" + i + "')");
            }
            // tc: 3 rows
            for (int i = 1; i <= 3; i++) {
                db.execute("INSERT INTO tc (cx, cval) VALUES (" + i + ", 'c" + i + "')");
            }

            // ax=1 AND by_col=2 → only 1 row from ta, 1 row from tb, 3 rows from tc → 3 results
            var result = (QueryResult) db.execute(
                    "SELECT ta.ax, tb.by_col, tc.cx " +
                    "FROM ta, tb, tc " +
                    "WHERE ta.ax = 1 AND tb.by_col = 2"
            ).value();

            assertThat(result.rowCount()).isEqualTo(3);
            for (var row : result.rows()) {
                assertThat(row.getValue(0)).isEqualTo(1L);
                assertThat(row.getValue(1)).isEqualTo(2L);
            }
        }
    }
}

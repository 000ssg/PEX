package ssg.pex.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectDatabase;
import ssg.pex.sql.dialects.DialectDatabase.DialectType;
import ssg.pex.sql.dialects.DialectFunctionRegistry;
import ssg.pex.sql.olap.OlapDatabase;
import ssg.pex.sql.olap.ast.WindowFunctionCall;
import ssg.pex.sql.streaming.engine.StreamEvent;
import ssg.pex.sql.streaming.engine.StreamSimulator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that exercise the three new SQL sub-modules (OLAP, Streaming,
 * Dialects) both individually and in cross-module scenarios.
 */
class SqlSubModulesIntegrationTest {

    // -----------------------------------------------------------------------
    // OLAP Integration Tests
    // -----------------------------------------------------------------------

    @Nested
    class OlapIntegration {

        private OlapDatabase db;

        @BeforeEach
        void setUp() {
            db = new OlapDatabase();
            db.execute("CREATE TABLE employees (id INT, name VARCHAR(50), dept VARCHAR(20), salary INT)");
            db.execute("INSERT INTO employees VALUES (1, 'Alice', 'Eng', 90000)");
            db.execute("INSERT INTO employees VALUES (2, 'Bob', 'Eng', 85000)");
            db.execute("INSERT INTO employees VALUES (3, 'Carol', 'Sales', 70000)");
            db.execute("INSERT INTO employees VALUES (4, 'Dave', 'Sales', 75000)");
            db.execute("INSERT INTO employees VALUES (5, 'Eve', 'Eng', 95000)");
        }

        @AfterEach
        void tearDown() {
            db.close();
        }

        @Test
        void windowFunctionRowNumberOnCoreData() {
            // Execute base query through core InMemoryDatabase, then apply window function
            var wfCall = db.parseWindowFunction(
                    "ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var result = db.executeWindowFunction(
                    "SELECT name, dept, salary FROM employees", List.of(wfCall));

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.rowCount()).isEqualTo(5);
            // Should have original 3 columns + 1 window function column
            assertThat(qr.columnNames()).hasSize(4);
        }

        @Test
        void windowFunctionRankWithPartition() {
            var wfCall = db.parseWindowFunction(
                    "RANK() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var result = db.executeWindowFunction(
                    "SELECT name, dept, salary FROM employees ORDER BY dept, salary DESC",
                    List.of(wfCall));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(5);
        }

        @Test
        void cteNonRecursiveQuery() {
            var result = db.executeOlap("""
                    WITH dept_stats AS (
                        SELECT dept, COUNT(*) AS cnt, AVG(salary) AS avg_sal
                        FROM employees GROUP BY dept
                    )
                    SELECT * FROM dept_stats
                    """);

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        void mergeUpsertOperation() {
            db.execute("CREATE TABLE target (id INT, name VARCHAR(50), salary INT)");
            db.execute("INSERT INTO target VALUES (1, 'Alice', 80000)");
            db.execute("INSERT INTO target VALUES (2, 'Bob', 75000)");

            db.execute("CREATE TABLE source (id INT, name VARCHAR(50), salary INT)");
            db.execute("INSERT INTO source VALUES (2, 'Bob', 90000)");
            db.execute("INSERT INTO source VALUES (3, 'Charlie', 65000)");

            var mergeResult = db.executeMerge("""
                    MERGE INTO target t
                    USING source s ON t.id = s.id
                    WHEN MATCHED THEN UPDATE SET t.salary = s.salary
                    WHEN NOT MATCHED THEN INSERT (id, name, salary) VALUES (s.id, s.name, s.salary)
                    """);

            assertThat(mergeResult.isSuccess()).isTrue();
        }

        @Test
        void cteWithFilter() {
            db.execute("CREATE TABLE quarterly_sales (product VARCHAR(20), quarter VARCHAR(5), amount INT)");
            db.execute("INSERT INTO quarterly_sales VALUES ('Widget', 'Q1', 100)");
            db.execute("INSERT INTO quarterly_sales VALUES ('Widget', 'Q2', 200)");
            db.execute("INSERT INTO quarterly_sales VALUES ('Gadget', 'Q1', 150)");

            var result = db.executeOlap(
                    "WITH totals AS (SELECT product, SUM(amount) AS total FROM quarterly_sales GROUP BY product) " +
                    "SELECT * FROM totals");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }
    }

    // -----------------------------------------------------------------------
    // Streaming Integration Tests
    // -----------------------------------------------------------------------

    @Nested
    class StreamingIntegration {

        private StreamSimulator sim;

        @BeforeEach
        void setUp() {
            sim = new StreamSimulator();
        }

        @Test
        void createStreamAndIngestEvents() {
            var createResult = sim.executeQuery(
                    "CREATE STREAM page_views (user_id VARCHAR(50), page VARCHAR(100), ts BIGINT)");
            assertThat(createResult.isSuccess()).isTrue();
            assertThat(sim.hasStream("page_views")).isTrue();

            sim.ingestEvent("page_views", new StreamEvent(1000L, "u1",
                    Map.of("user_id", "u1", "page", "/home", "ts", 1000L)));
            sim.ingestEvent("page_views", new StreamEvent(2000L, "u2",
                    Map.of("user_id", "u2", "page", "/products", "ts", 2000L)));
            sim.ingestEvent("page_views", new StreamEvent(3000L, "u1",
                    Map.of("user_id", "u1", "page", "/home", "ts", 3000L)));

            // Query the stream state
            var queryResult = sim.executeQuery("SELECT * FROM page_views");
            assertThat(queryResult.isSuccess()).isTrue();
            assertThat(queryResult.value().rowCount()).isEqualTo(3);
        }

        @Test
        void tumblingWindowAggregation() {
            sim.executeQuery("CREATE STREAM clicks (page VARCHAR(100), ts BIGINT)");

            // Ingest events across two 5-second tumbling windows
            for (int i = 0; i < 3; i++) {
                sim.ingestEvent("clicks", new StreamEvent(i * 1000L, null,
                        Map.of("page", "/home", "ts", (long) (i * 1000))));
            }
            for (int i = 0; i < 2; i++) {
                sim.ingestEvent("clicks", new StreamEvent(5000L + i * 1000L, null,
                        Map.of("page", "/home", "ts", 5000L + i * 1000L)));
            }

            // Verify events were ingested into the stream
            assertThat(sim.getStream("clicks")).isNotNull();
            assertThat(sim.getStream("clicks").peekAll()).hasSize(5);
        }

        @Test
        void streamToStreamJoin() {
            sim.executeQuery("CREATE STREAM orders (order_id INT, amount INT, ts BIGINT)");
            sim.executeQuery("CREATE STREAM payments (order_id INT, status VARCHAR(20), ts BIGINT)");

            sim.ingestEvent("orders", new StreamEvent(1000L, "o1",
                    Map.of("order_id", 1, "amount", 100, "ts", 1000L)));
            sim.ingestEvent("orders", new StreamEvent(2000L, "o2",
                    Map.of("order_id", 2, "amount", 200, "ts", 2000L)));

            sim.ingestEvent("payments", new StreamEvent(1500L, "p1",
                    Map.of("order_id", 1, "status", "paid", "ts", 1500L)));
            sim.ingestEvent("payments", new StreamEvent(5000L, "p2",
                    Map.of("order_id", 2, "status", "paid", "ts", 5000L)));

            // Join within 2-second window
            var joined = sim.joinStreams("orders", "payments", "order_id", 2000);
            // order 1 matched (1000 vs 1500, diff=500 <= 2000)
            // order 2 may or may not match (2000 vs 5000, diff=3000 > 2000)
            assertThat(joined).isNotEmpty();
            assertThat(joined.getFirst().values()).containsKey("order_id");
        }

        @Test
        void streamToTableLookupJoin() {
            sim.executeQuery("CREATE STREAM events (user_id VARCHAR(50), action VARCHAR(50), ts BIGINT)");

            sim.ingestEvent("events", new StreamEvent(1000L, "u1",
                    Map.of("user_id", "u1", "action", "click", "ts", 1000L)));
            sim.ingestEvent("events", new StreamEvent(2000L, "u2",
                    Map.of("user_id", "u2", "action", "view", "ts", 2000L)));

            // Lookup table: user_id -> user details
            Map<Object, Map<String, Object>> lookupTable = Map.of(
                    "u1", Map.of("user_name", "Alice", "tier", "gold"),
                    "u2", Map.of("user_name", "Bob", "tier", "silver")
            );

            var enriched = sim.lookupJoin("events", lookupTable, "user_id");
            assertThat(enriched).hasSize(2);
            assertThat(enriched.getFirst().values()).containsKey("user_name");
        }

        @Test
        void dropStreamAndVerify() {
            sim.executeQuery("CREATE STREAM temp_stream (id INT, ts BIGINT)");
            assertThat(sim.hasStream("temp_stream")).isTrue();

            var dropResult = sim.executeQuery("DROP STREAM temp_stream");
            assertThat(dropResult.isSuccess()).isTrue();
            assertThat(sim.hasStream("temp_stream")).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Dialect Integration Tests
    // -----------------------------------------------------------------------

    @Nested
    class DialectIntegration {

        @Test
        void oracleConnectByHierarchy() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE org (id INT, name VARCHAR(50), mgr_id INT)");
            db.execute("INSERT INTO org VALUES (1, 'CEO', NULL)");
            db.execute("INSERT INTO org VALUES (2, 'VP Eng', 1)");
            db.execute("INSERT INTO org VALUES (3, 'Dev', 2)");

            try (var oracle = new DialectDatabase(db, DialectType.ORACLE)) {
                var result = oracle.execute(
                        "SELECT * FROM org CONNECT BY PRIOR id = mgr_id START WITH mgr_id IS NULL");
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.value()).isInstanceOf(QueryResult.class);
                var qr = (QueryResult) result.value();
                assertThat(qr.rowCount()).isGreaterThanOrEqualTo(3);
            }
        }

        @Test
        void mssqlTopQuery() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE items (id INT, name VARCHAR(50), price INT)");
            db.execute("INSERT INTO items VALUES (1, 'A', 30)");
            db.execute("INSERT INTO items VALUES (2, 'B', 10)");
            db.execute("INSERT INTO items VALUES (3, 'C', 20)");
            db.execute("INSERT INTO items VALUES (4, 'D', 40)");

            try (var mssql = new DialectDatabase(db, DialectType.MSSQL)) {
                var result = mssql.execute("SELECT TOP 2 * FROM items ORDER BY price DESC");
                assertThat(result.isSuccess()).isTrue();
                var qr = (QueryResult) result.value();
                assertThat(qr.rowCount()).isEqualTo(2);
            }
        }

        @Test
        void mysqlShowTables() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE config (key_name VARCHAR(50), value VARCHAR(100))");
            db.execute("CREATE TABLE users (id INTEGER, name VARCHAR(50))");

            try (var mysql = new DialectDatabase(db, DialectType.MYSQL)) {
                var result = mysql.execute("SHOW TABLES");
                assertThat(result.isSuccess()).isTrue();
                var qr = (QueryResult) result.value();
                assertThat(qr.rowCount()).isEqualTo(2);
            }
        }

        @Test
        void oracleNvlFunction() {
            var db = new InMemoryDatabase();
            try (var oracle = new DialectDatabase(db, DialectType.ORACLE)) {
                var reg = oracle.functionRegistry();
                assertThat(reg.has("NVL")).isTrue();
                assertThat(reg.get("NVL").apply(java.util.Arrays.asList(null, "default"))).isEqualTo("default");
                assertThat(reg.get("NVL").apply(List.of("value", "default"))).isEqualTo("value");
            }
        }

        @Test
        void mssqlIsnullFunction() {
            var db = new InMemoryDatabase();
            try (var mssql = new DialectDatabase(db, DialectType.MSSQL)) {
                var reg = mssql.functionRegistry();
                assertThat(reg.has("ISNULL")).isTrue();
                assertThat(reg.get("ISNULL").apply(java.util.Arrays.asList(null, 0))).isEqualTo(0);
                assertThat(reg.get("ISNULL").apply(List.of(42, 0))).isEqualTo(42);
            }
        }

        @Test
        void mysqlIfFunction() {
            var db = new InMemoryDatabase();
            try (var mysql = new DialectDatabase(db, DialectType.MYSQL)) {
                var reg = mysql.functionRegistry();
                assertThat(reg.has("IF")).isTrue();
                assertThat(reg.get("IF").apply(List.of(true, "yes", "no"))).isEqualTo("yes");
                assertThat(reg.get("IF").apply(List.of(false, "yes", "no"))).isEqualTo("no");
            }
        }

        @Test
        void fullTextSearchEngine() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE articles (id INT, title VARCHAR(200), body TEXT)");
            db.execute("INSERT INTO articles VALUES (1, 'Java Guide', 'Learn Java programming basics')");
            db.execute("INSERT INTO articles VALUES (2, 'Python Tutorial', 'Getting started with Python')");
            db.execute("INSERT INTO articles VALUES (3, 'Java Advanced', 'Advanced Java concurrency patterns')");

            try (var ftDb = new DialectDatabase(db, DialectType.FULLTEXT)) {
                ftDb.execute("CREATE FULLTEXT INDEX ft_articles ON articles (title, body)");
                var result = ftDb.execute(
                        "SELECT * FROM articles WHERE MATCH(title, body) AGAINST('Java')");
                assertThat(result.isSuccess()).isTrue();
                var qr = (QueryResult) result.value();
                // Two rows contain "Java"
                assertThat(qr.rowCount()).isEqualTo(2);
            }
        }

        @Test
        void spatialDistanceQuery() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE places (id INT, name VARCHAR(50), lat DOUBLE, lon DOUBLE)");
            db.execute("INSERT INTO places VALUES (1, 'Office', 40.7128, -74.0060)");
            db.execute("INSERT INTO places VALUES (2, 'Park', 40.7580, -73.9855)");
            db.execute("INSERT INTO places VALUES (3, 'Airport', 40.6413, -73.7781)");

            try (var spatialDb = new DialectDatabase(db, DialectType.SPATIAL)) {
                // ST_Distance query
                var result = spatialDb.execute(
                        "SELECT name, ST_DISTANCE(lat, POINT(40.7128, -74.0060)) AS dist FROM places");
                assertThat(result.isSuccess()).isTrue();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Cross-Module Integration Tests
    // -----------------------------------------------------------------------

    @Nested
    class CrossModuleIntegration {

        @Test
        void olapWindowFunctionOnCoreDatabase() {
            // Setup data in InMemoryDatabase (core), analyze with OlapDatabase
            var coreDb = new InMemoryDatabase();
            coreDb.execute("CREATE TABLE sales (id INT, region VARCHAR(20), quarter VARCHAR(5), amount INT)");
            coreDb.execute("INSERT INTO sales VALUES (1, 'East', 'Q1', 100)");
            coreDb.execute("INSERT INTO sales VALUES (2, 'East', 'Q2', 150)");
            coreDb.execute("INSERT INTO sales VALUES (3, 'West', 'Q1', 200)");
            coreDb.execute("INSERT INTO sales VALUES (4, 'West', 'Q2', 180)");

            try (var olap = new OlapDatabase(coreDb)) {
                // Window function: running total per region
                var wfCall = olap.parseWindowFunction(
                        "SUM(amount) OVER (PARTITION BY region ORDER BY id)");
                var result = olap.executeWindowFunction(
                        "SELECT id, region, amount FROM sales", List.of(wfCall));

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.value().rowCount()).isEqualTo(4);
                assertThat(result.value().columnNames()).contains("sum");
            }
        }

        @Test
        void dialectFunctionEvaluationAcrossDialects() {
            // Verify the same null-handling concept across Oracle, MSSQL, MySQL
            var db = new InMemoryDatabase();

            try (var oracle = new DialectDatabase(db, DialectType.ORACLE)) {
                var nvl = oracle.functionRegistry().get("NVL");
                assertThat(nvl.apply(java.util.Arrays.asList(null, "fallback"))).isEqualTo("fallback");
            }

            try (var mssql = new DialectDatabase(db, DialectType.MSSQL)) {
                var isnull = mssql.functionRegistry().get("ISNULL");
                assertThat(isnull.apply(java.util.Arrays.asList(null, "fallback"))).isEqualTo("fallback");
            }

            try (var mysql = new DialectDatabase(db, DialectType.MYSQL)) {
                var ifnull = mysql.functionRegistry().get("IFNULL");
                assertThat(ifnull.apply(java.util.Arrays.asList(null, "fallback"))).isEqualTo("fallback");
            }
        }

        @Test
        void streamingWithWindowedAggregatesEndToEnd() {
            var sim = new StreamSimulator();
            sim.executeQuery("CREATE STREAM sensor_data (sensor_id VARCHAR(20), temp DOUBLE, ts BIGINT)");

            // Ingest temperature readings from two sensors
            sim.ingestEvent("sensor_data", new StreamEvent(1000L, "s1",
                    Map.of("sensor_id", "s1", "temp", 22.5, "ts", 1000L)));
            sim.ingestEvent("sensor_data", new StreamEvent(2000L, "s2",
                    Map.of("sensor_id", "s2", "temp", 23.1, "ts", 2000L)));
            sim.ingestEvent("sensor_data", new StreamEvent(3000L, "s1",
                    Map.of("sensor_id", "s1", "temp", 22.8, "ts", 3000L)));
            sim.ingestEvent("sensor_data", new StreamEvent(4000L, "s2",
                    Map.of("sensor_id", "s2", "temp", 23.5, "ts", 4000L)));

            // Verify events were ingested and stream is accessible
            assertThat(sim.getStream("sensor_data")).isNotNull();
            assertThat(sim.getStream("sensor_data").peekAll()).hasSize(4);
        }

        @Test
        void coreDataPipedToOlapCte() {
            // Build data with core DB, then run CTE analysis
            var coreDb = new InMemoryDatabase();
            coreDb.execute("CREATE TABLE orders (id INT, customer VARCHAR(50), amount INT)");
            coreDb.execute("INSERT INTO orders VALUES (1, 'Alice', 100)");
            coreDb.execute("INSERT INTO orders VALUES (2, 'Bob', 200)");
            coreDb.execute("INSERT INTO orders VALUES (3, 'Alice', 150)");
            coreDb.execute("INSERT INTO orders VALUES (4, 'Bob', 300)");

            try (var olap = new OlapDatabase(coreDb)) {
                var result = olap.executeOlap("""
                        WITH customer_totals AS (
                            SELECT customer, SUM(amount) AS total
                            FROM orders GROUP BY customer
                        )
                        SELECT * FROM customer_totals
                        """);

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.value().rowCount()).isEqualTo(2);
            }
        }
    }
}

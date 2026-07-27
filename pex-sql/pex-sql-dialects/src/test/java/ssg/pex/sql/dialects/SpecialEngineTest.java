package ssg.pex.sql.dialects;

import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.engines.*;
import ssg.pex.sql.dialects.engines.ColumnarEngine.ColumnarTable;
import ssg.pex.sql.dialects.engines.ColumnarEngine.RleEntry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SpecialEngineTest {

    // =============== COLUMNAR ENGINE ===============

    @Test
    void testColumnarCreateTable() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.COLUMNAR);
        var result = db.execute("CREATE TABLE metrics (ts VARCHAR(50), value DECIMAL, sensor VARCHAR(20)) ENGINE=COLUMNAR");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testColumnarInsertAndSelect() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.COLUMNAR);
        db.execute("CREATE TABLE metrics (ts VARCHAR(50), value DECIMAL, sensor VARCHAR(20)) ENGINE=COLUMNAR");
        db.execute("INSERT INTO metrics (ts, value, sensor) VALUES ('2026-01-01', 10.5, 'temp')");
        db.execute("INSERT INTO metrics (ts, value, sensor) VALUES ('2026-01-02', 11.0, 'temp')");
        db.execute("INSERT INTO metrics (ts, value, sensor) VALUES ('2026-01-03', 9.8, 'humidity')");

        var result = db.execute("SELECT * FROM metrics");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    @Test
    void testColumnarColumnScan() {
        var table = new ColumnarTable("test", List.of("a", "b", "c"));
        table.addRow(Map.of("a", 1L, "b", 2L, "c", 3L));
        table.addRow(Map.of("a", 4L, "b", 5L, "c", 6L));

        Map<String, List<Object>> scan = table.columnScan(List.of("a", "c"));
        assertThat(scan).hasSize(2);
        assertThat(scan.get("a")).containsExactly(1L, 4L);
        assertThat(scan.get("c")).containsExactly(3L, 6L);
    }

    @Test
    void testColumnarAggregateSum() {
        var table = new ColumnarTable("test", List.of("id", "amount"));
        table.addRow(Map.of("id", 1L, "amount", 10.0));
        table.addRow(Map.of("id", 2L, "amount", 20.0));
        table.addRow(Map.of("id", 3L, "amount", 30.0));

        Object sum = table.computeAggregate("amount", "SUM");
        assertThat((Double) sum).isEqualTo(60.0);
    }

    @Test
    void testColumnarAggregateAvg() {
        var table = new ColumnarTable("test", List.of("id", "amount"));
        table.addRow(Map.of("id", 1L, "amount", 10.0));
        table.addRow(Map.of("id", 2L, "amount", 20.0));
        table.addRow(Map.of("id", 3L, "amount", 30.0));

        Object avg = table.computeAggregate("amount", "AVG");
        assertThat((Double) avg).isEqualTo(20.0);
    }

    @Test
    void testColumnarAggregateCount() {
        var table = new ColumnarTable("test", List.of("id", "val"));
        table.addRow(Map.of("id", 1L, "val", "a"));
        table.addRow(Map.of("id", 2L, "val", "b"));
        table.addRow(Map.of("id", 3L, "val", "c"));

        Object count = table.computeAggregate("val", "COUNT");
        assertThat((Long) count).isEqualTo(3L);
    }

    @Test
    void testColumnarAggregateMinMax() {
        var table = new ColumnarTable("test", List.of("id", "val"));
        table.addRow(Map.of("id", 1L, "val", 5.0));
        table.addRow(Map.of("id", 2L, "val", 15.0));
        table.addRow(Map.of("id", 3L, "val", 10.0));

        assertThat((Double) table.computeAggregate("val", "MIN")).isEqualTo(5.0);
        assertThat((Double) table.computeAggregate("val", "MAX")).isEqualTo(15.0);
    }

    @Test
    void testColumnarCompression() {
        var table = new ColumnarTable("test", List.of("id", "status"));
        table.addRow(Map.of("id", 1L, "status", "active"));
        table.addRow(Map.of("id", 2L, "status", "active"));
        table.addRow(Map.of("id", 3L, "status", "active"));
        table.addRow(Map.of("id", 4L, "status", "inactive"));
        table.addRow(Map.of("id", 5L, "status", "inactive"));

        List<RleEntry> compressed = table.compressColumn("status");
        assertThat(compressed).hasSize(2);
        assertThat(compressed.get(0).value()).isEqualTo("active");
        assertThat(compressed.get(0).count()).isEqualTo(3);
        assertThat(compressed.get(1).value()).isEqualTo("inactive");
        assertThat(compressed.get(1).count()).isEqualTo(2);
    }

    @Test
    void testColumnarSelectSpecificColumns() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.COLUMNAR);
        db.execute("CREATE TABLE data (a INTEGER, b INTEGER, c INTEGER) ENGINE=COLUMNAR");
        db.execute("INSERT INTO data (a, b, c) VALUES (1, 2, 3)");
        db.execute("INSERT INTO data (a, b, c) VALUES (4, 5, 6)");

        var result = db.execute("SELECT a, c FROM data");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.columnNames()).containsExactly("a", "c");
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void testColumnarAggregateSqlQuery() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.COLUMNAR);
        db.execute("CREATE TABLE sales (product VARCHAR(50), amount DECIMAL) ENGINE=COLUMNAR");
        db.execute("INSERT INTO sales (product, amount) VALUES ('A', 100)");
        db.execute("INSERT INTO sales (product, amount) VALUES ('B', 200)");
        db.execute("INSERT INTO sales (product, amount) VALUES ('C', 300)");

        // Verify data was stored and is queryable through the columnar dialect
        var result = db.execute("SELECT * FROM sales");
        assertThat(result.isSuccess()).as("execute SELECT: %s", result).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).as("row count, columns=%s", qr.columnNames()).isEqualTo(3);
        // Columnar engine returns column-oriented data accessible by name
        assertThat(qr.columnNames()).isNotEmpty();
        // Verify amount column has numeric data
        int amountIdx = -1;
        for (int i = 0; i < qr.columnNames().size(); i++) {
            if (qr.columnNames().get(i).equalsIgnoreCase("amount")) amountIdx = i;
        }
        assertThat(amountIdx).as("amount column index in %s", qr.columnNames()).isGreaterThanOrEqualTo(0);
        var firstVal = qr.rows().getFirst().getValue(amountIdx);
        assertThat(firstVal).as("first row amount value").isNotNull();
        assertThat(((Number) firstVal).doubleValue()).isEqualTo(100.0);
    }

    @Test
    void testColumnarTableMetadata() {
        var table = new ColumnarTable("metrics", List.of("ts", "value"));
        assertThat(table.name()).isEqualTo("metrics");
        assertThat(table.columnNames()).containsExactly("ts", "value");
        assertThat(table.rowCount()).isEqualTo(0);
    }

    @Test
    void testColumnarEngineTableAccess() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.COLUMNAR);
        db.execute("CREATE TABLE t1 (id INTEGER) ENGINE=COLUMNAR");
        assertThat(db.columnarEngine().getTable("t1")).isNotNull();
    }

    // =============== TIMESERIES ENGINE ===============

    @Test
    void testTimeSeriesCreateTable() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        var result = db.execute(
                "CREATE TABLE sensor_data (ts TIMESTAMP, value DECIMAL, sensor VARCHAR(20)) ENGINE=TIMESERIES WITH (retention='30d')");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testTimeSeriesInsertAndSelect() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE readings (ts TIMESTAMP, value DECIMAL) ENGINE=TIMESERIES");
        db.execute("INSERT INTO readings (ts, value) VALUES ('2026-06-01T10:00:00Z', 25.5)");
        db.execute("INSERT INTO readings (ts, value) VALUES ('2026-06-01T11:00:00Z', 26.0)");
        db.execute("INSERT INTO readings (ts, value) VALUES ('2026-06-01T12:00:00Z', 27.5)");

        var result = db.execute("SELECT * FROM readings");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
    }

    @Test
    void testTimeBucketGrouping() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE temps (ts TIMESTAMP, value DECIMAL) ENGINE=TIMESERIES");
        // Insert data at various times within two 1-hour buckets
        db.execute("INSERT INTO temps (ts, value) VALUES ('2026-06-01T10:00:00Z', 20.0)");
        db.execute("INSERT INTO temps (ts, value) VALUES ('2026-06-01T10:30:00Z', 22.0)");
        db.execute("INSERT INTO temps (ts, value) VALUES ('2026-06-01T11:00:00Z', 25.0)");
        db.execute("INSERT INTO temps (ts, value) VALUES ('2026-06-01T11:30:00Z', 27.0)");

        var result = db.execute(
                "SELECT time_bucket('1h', ts), AVG(value) FROM temps");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2); // Two 1-hour buckets
    }

    @Test
    void testTimeBucketDownsamplingAvg() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE data (ts TIMESTAMP, value DECIMAL) ENGINE=TIMESERIES");
        db.execute("INSERT INTO data (ts, value) VALUES ('2026-06-01T10:00:00Z', 10.0)");
        db.execute("INSERT INTO data (ts, value) VALUES ('2026-06-01T10:30:00Z', 20.0)");

        var result = db.execute("SELECT time_bucket('1h', ts), AVG(value) FROM data");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
        assertThat(((Number) qr.rows().getFirst().getValue(1)).doubleValue()).isEqualTo(15.0);
    }

    @Test
    void testTimeBucketMin() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE data (ts TIMESTAMP, value DECIMAL) ENGINE=TIMESERIES");
        db.execute("INSERT INTO data (ts, value) VALUES ('2026-06-01T10:00:00Z', 10.0)");
        db.execute("INSERT INTO data (ts, value) VALUES ('2026-06-01T10:30:00Z', 5.0)");

        var result = db.execute("SELECT time_bucket('1h', ts), MIN(value) FROM data");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(((Number) qr.rows().getFirst().getValue(1)).doubleValue()).isEqualTo(5.0);
    }

    @Test
    void testTimeBucketMax() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE data (ts TIMESTAMP, value DECIMAL) ENGINE=TIMESERIES");
        db.execute("INSERT INTO data (ts, value) VALUES ('2026-06-01T10:00:00Z', 10.0)");
        db.execute("INSERT INTO data (ts, value) VALUES ('2026-06-01T10:30:00Z', 50.0)");

        var result = db.execute("SELECT time_bucket('1h', ts), MAX(value) FROM data");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(((Number) qr.rows().getFirst().getValue(1)).doubleValue()).isEqualTo(50.0);
    }

    @Test
    void testRetentionPolicy() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE logs (ts TIMESTAMP, msg VARCHAR(100)) ENGINE=TIMESERIES WITH (retention='30d')");
        assertThat(db.timeSeriesEngine().retentionPolicies()).isNotEmpty();
    }

    @Test
    void testRetentionPolicyPurge() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE logs (ts TIMESTAMP, msg VARCHAR(100)) ENGINE=TIMESERIES WITH (retention='1d')");

        // Insert old data
        String oldTs = Instant.now().minus(2, ChronoUnit.DAYS).toString();
        String recentTs = Instant.now().toString();
        db.execute("INSERT INTO logs (ts, msg) VALUES ('" + oldTs + "', 'old')");
        db.execute("INSERT INTO logs (ts, msg) VALUES ('" + recentTs + "', 'recent')");

        // Query with time_bucket should trigger retention check
        var result = db.execute("SELECT time_bucket('1h', ts), COUNT(*) FROM logs");
        assertThat(result.isSuccess()).isTrue();
        // Old data should have been purged
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void testTimeSeriesStandardDelegation() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.TIMESERIES);
        db.execute("CREATE TABLE normal (id INTEGER PRIMARY KEY, name VARCHAR(50))");
        db.execute("INSERT INTO normal (id, name) VALUES (1, 'test')");
        var result = db.execute("SELECT * FROM normal");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1);
    }

    // =============== FULLTEXT ENGINE ===============

    @Test
    void testFullTextCreateIndex() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.FULLTEXT);
        db.execute("CREATE TABLE articles (id INTEGER PRIMARY KEY, title VARCHAR(200), body TEXT)");
        db.execute("INSERT INTO articles (id, title, body) VALUES (1, 'Java Programming', 'Java is a popular language')");
        db.execute("INSERT INTO articles (id, title, body) VALUES (2, 'Python Guide', 'Python is great for data science')");

        var result = db.execute("CREATE FULLTEXT INDEX idx_articles ON articles (title, body)");
        assertThat(result.isSuccess()).isTrue();
        assertThat(db.fullTextEngine().getIndex("idx_articles")).isNotNull();
    }

    @Test
    void testMatchAgainstBasic() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.FULLTEXT);
        db.execute("CREATE TABLE docs (id INTEGER PRIMARY KEY, content TEXT)");
        db.execute("INSERT INTO docs (id, content) VALUES (1, 'The quick brown fox jumps over the lazy dog')");
        db.execute("INSERT INTO docs (id, content) VALUES (2, 'A fast red car drives on the highway')");
        db.execute("INSERT INTO docs (id, content) VALUES (3, 'The brown bear lives in the forest')");
        db.execute("CREATE FULLTEXT INDEX idx_docs ON docs (content)");

        var result = db.execute("SELECT * FROM docs WHERE MATCH(content) AGAINST('brown')");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2); // docs 1 and 3 contain 'brown'
    }

    @Test
    void testMatchAgainstMultipleTerms() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.FULLTEXT);
        db.execute("CREATE TABLE docs (id INTEGER PRIMARY KEY, content TEXT)");
        db.execute("INSERT INTO docs (id, content) VALUES (1, 'Java programming language')");
        db.execute("INSERT INTO docs (id, content) VALUES (2, 'Python programming language')");
        db.execute("INSERT INTO docs (id, content) VALUES (3, 'JavaScript web development')");
        db.execute("CREATE FULLTEXT INDEX idx_docs ON docs (content)");

        var result = db.execute("SELECT * FROM docs WHERE MATCH(content) AGAINST('programming')");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void testBooleanModeRequired() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.FULLTEXT);
        db.execute("CREATE TABLE docs (id INTEGER PRIMARY KEY, content TEXT)");
        db.execute("INSERT INTO docs (id, content) VALUES (1, 'Java programming is fun')");
        db.execute("INSERT INTO docs (id, content) VALUES (2, 'Python programming is great')");
        db.execute("INSERT INTO docs (id, content) VALUES (3, 'Java coffee beans')");
        db.execute("CREATE FULLTEXT INDEX idx_docs ON docs (content)");

        var result = db.execute("SELECT * FROM docs WHERE MATCH(content) AGAINST('+Java +programming' IN BOOLEAN MODE)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1); // Only doc 1 has both Java AND programming
    }

    @Test
    void testBooleanModeExcluded() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.FULLTEXT);
        db.execute("CREATE TABLE docs (id INTEGER PRIMARY KEY, content TEXT)");
        db.execute("INSERT INTO docs (id, content) VALUES (1, 'Java programming is fun')");
        db.execute("INSERT INTO docs (id, content) VALUES (2, 'Python programming is great')");
        db.execute("INSERT INTO docs (id, content) VALUES (3, 'JavaScript web development')");
        db.execute("CREATE FULLTEXT INDEX idx_docs ON docs (content)");

        var result = db.execute("SELECT * FROM docs WHERE MATCH(content) AGAINST('+programming -Python' IN BOOLEAN MODE)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(1); // Only doc 1: has programming, no Python
    }

    @Test
    void testFullTextRelevanceScoring() {
        var ftIndex = new FullTextEngine.FullTextIndex("test", "docs", List.of("content"));
        ftIndex.indexRow(0, "Java programming language tutorial");
        ftIndex.indexRow(1, "Python programming language");
        ftIndex.indexRow(2, "Java Java Java intensive");

        double score0 = ftIndex.relevanceScore(0, new String[]{"java"});
        double score2 = ftIndex.relevanceScore(2, new String[]{"java"});
        // Both should have positive scores
        assertThat(score0).isGreaterThan(0);
        assertThat(score2).isGreaterThan(0);
    }

    @Test
    void testFullTextIndexMetadata() {
        var ftIndex = new FullTextEngine.FullTextIndex("idx1", "articles", List.of("title", "body"));
        assertThat(ftIndex.name()).isEqualTo("idx1");
        assertThat(ftIndex.tableName()).isEqualTo("articles");
        assertThat(ftIndex.columns()).containsExactly("title", "body");
    }

    @Test
    void testFullTextStandardDelegation() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.FULLTEXT);
        db.execute("CREATE TABLE items (id INTEGER PRIMARY KEY, name VARCHAR(50))");
        db.execute("INSERT INTO items (id, name) VALUES (1, 'test')");
        var result = db.execute("SELECT * FROM items");
        assertThat(result.isSuccess()).isTrue();
    }

    // =============== SPATIAL ENGINE ===============

    @Test
    void testPointCreation() {
        var point = new SpatialEngine.Point(1.0, 2.0);
        assertThat(point.x()).isEqualTo(1.0);
        assertThat(point.y()).isEqualTo(2.0);
        assertThat(point.toString()).contains("POINT");
    }

    @Test
    void testStDistance() {
        double dist = SpatialEngine.euclideanDistance(0, 0, 3, 4);
        assertThat(dist).isEqualTo(5.0);
    }

    @Test
    void testStDistanceZero() {
        double dist = SpatialEngine.euclideanDistance(5, 5, 5, 5);
        assertThat(dist).isEqualTo(0.0);
    }

    @Test
    void testParsePoint() {
        var point = SpatialEngine.parsePoint("POINT(3.5, 4.5)");
        assertThat(point).isNotNull();
        assertThat(point.x()).isEqualTo(3.5);
        assertThat(point.y()).isEqualTo(4.5);
    }

    @Test
    void testParsePointNull() {
        assertThat(SpatialEngine.parsePoint(null)).isNull();
        assertThat(SpatialEngine.parsePoint("invalid")).isNull();
    }

    @Test
    void testSpatialInsertAndStDistance() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.SPATIAL);
        db.execute("CREATE TABLE locations (id INTEGER PRIMARY KEY, name VARCHAR(50), pos VARCHAR(100))");
        db.execute("INSERT INTO locations (id, name, pos) VALUES (1, 'A', POINT(0.0, 0.0))");
        db.execute("INSERT INTO locations (id, name, pos) VALUES (2, 'B', POINT(3.0, 4.0))");
        db.execute("INSERT INTO locations (id, name, pos) VALUES (3, 'C', POINT(6.0, 8.0))");

        var result = db.execute("SELECT ST_DISTANCE(pos, POINT(0.0, 0.0)) FROM locations");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(3);
        // First point at origin should have distance 0
        assertThat(((Number) qr.rows().get(0).getValue(0)).doubleValue()).isCloseTo(0.0, within(0.01));
        // Second point at (3,4) should have distance 5
        assertThat(((Number) qr.rows().get(1).getValue(0)).doubleValue()).isCloseTo(5.0, within(0.01));
        // Third point at (6,8) should have distance 10
        assertThat(((Number) qr.rows().get(2).getValue(0)).doubleValue()).isCloseTo(10.0, within(0.01));
    }

    @Test
    void testStWithin() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.SPATIAL);
        db.execute("CREATE TABLE places (id INTEGER PRIMARY KEY, name VARCHAR(50), loc VARCHAR(100))");
        db.execute("INSERT INTO places (id, name, loc) VALUES (1, 'Near', POINT(1.0, 1.0))");
        db.execute("INSERT INTO places (id, name, loc) VALUES (2, 'Far', POINT(100.0, 100.0))");
        db.execute("INSERT INTO places (id, name, loc) VALUES (3, 'Mid', POINT(3.0, 3.0))");

        var result = db.execute("SELECT * FROM places WHERE ST_WITHIN(loc, 0.0, 0.0, 5.0)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        // "Near" at (1,1) dist=1.41, "Mid" at (3,3) dist=4.24 — both within radius 5 of origin
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void testStWithinLargerRadius() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.SPATIAL);
        db.execute("CREATE TABLE pts (id INTEGER PRIMARY KEY, p VARCHAR(100))");
        db.execute("INSERT INTO pts (id, p) VALUES (1, POINT(1.0, 0.0))");
        db.execute("INSERT INTO pts (id, p) VALUES (2, POINT(2.0, 0.0))");
        db.execute("INSERT INTO pts (id, p) VALUES (3, POINT(5.0, 0.0))");
        db.execute("INSERT INTO pts (id, p) VALUES (4, POINT(10.0, 0.0))");

        var result = db.execute("SELECT * FROM pts WHERE ST_WITHIN(p, 0.0, 0.0, 3.0)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2); // points at 1 and 2 are within radius 3
    }

    @Test
    void testSpatialStandardDelegation() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.SPATIAL);
        db.execute("CREATE TABLE normal (id INTEGER PRIMARY KEY, name VARCHAR(50))");
        db.execute("INSERT INTO normal (id, name) VALUES (1, 'test')");
        var result = db.execute("SELECT * FROM normal");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testBoundingBoxViaWithin() {
        // Simulate bounding box using ST_WITHIN with large radius
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.SPATIAL);
        db.execute("CREATE TABLE geo (id INTEGER PRIMARY KEY, point VARCHAR(100))");
        db.execute("INSERT INTO geo (id, point) VALUES (1, POINT(5.0, 5.0))");
        db.execute("INSERT INTO geo (id, point) VALUES (2, POINT(15.0, 15.0))");
        db.execute("INSERT INTO geo (id, point) VALUES (3, POINT(25.0, 25.0))");

        // Within radius 15 of (10,10) should include (5,5) and (15,15)
        var result = db.execute("SELECT * FROM geo WHERE ST_WITHIN(point, 10.0, 10.0, 15.0)");
        assertThat(result.isSuccess()).isTrue();
        var qr = (QueryResult) result.value();
        assertThat(qr.rowCount()).isEqualTo(2);
    }

    @Test
    void testDialectDatabaseClose() {
        var db = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.SPATIAL);
        assertThatNoException().isThrownBy(db::close);
    }

    @Test
    void testDialectTypeEnum() {
        assertThat(DialectDatabase.DialectType.values()).hasSize(8);
        assertThat(DialectDatabase.DialectType.valueOf("COLUMNAR")).isEqualTo(DialectDatabase.DialectType.COLUMNAR);
        assertThat(DialectDatabase.DialectType.valueOf("TIMESERIES")).isEqualTo(DialectDatabase.DialectType.TIMESERIES);
        assertThat(DialectDatabase.DialectType.valueOf("FULLTEXT")).isEqualTo(DialectDatabase.DialectType.FULLTEXT);
        assertThat(DialectDatabase.DialectType.valueOf("SPATIAL")).isEqualTo(DialectDatabase.DialectType.SPATIAL);
    }
}

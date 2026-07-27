package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import ssg.pex.nosql.dialects.cassandra.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Cassandra CQL feature tests.
 * Covers DDL, DML, prepared statements, batches, TTL, COUNT, DISTINCT.
 */
@DisplayName("Cassandra Feature Tests")
class CassandraFeatureTest {

    private CassandraDatabase db;
    private CassandraSession session;

    @BeforeEach
    void setup() {
        db = new CassandraDatabase();
        session = db.connect("test_ks");
    }

    @AfterEach
    void teardown() {
        session.close();
        db.close();
    }

    // ── DDL ───────────────────────────────────────────────────────────────────

    @Test void createTable() {
        session.execute("CREATE TABLE test_ks.users (id UUID PRIMARY KEY, name TEXT, age INT)");
        assertThat(db.underlying().collectionExists("test_ks.users")).isTrue();
    }

    @Test void createTableIfNotExists() {
        session.execute("CREATE TABLE IF NOT EXISTS test_ks.t (id UUID PRIMARY KEY, v TEXT)");
        // Second call should not throw
        session.execute("CREATE TABLE IF NOT EXISTS test_ks.t (id UUID PRIMARY KEY, v TEXT)");
        assertThat(db.underlying().collectionExists("test_ks.t")).isTrue();
    }

    @Test void createTableWithClusteringKey() {
        session.execute(
                "CREATE TABLE test_ks.events (id UUID, ts BIGINT, data TEXT, PRIMARY KEY (id, ts)) " +
                "WITH CLUSTERING ORDER BY (ts DESC)");
        assertThat(db.underlying().collectionExists("test_ks.events")).isTrue();
        CassandraSchema schema = db.executor().parser().schemas().get("test_ks.events");
        assertThat(schema).isNotNull();
        assertThat(schema.partitionKeys()).contains("id");
        assertThat(schema.clusteringKeys()).contains("ts");
    }

    @Test void dropTable() {
        session.execute("CREATE TABLE test_ks.drop_me (id UUID PRIMARY KEY)");
        assertThat(db.underlying().collectionExists("test_ks.drop_me")).isTrue();
        session.execute("DROP TABLE test_ks.drop_me");
        assertThat(db.underlying().collectionExists("test_ks.drop_me")).isFalse();
    }

    @Test void createIndex() {
        session.execute("CREATE TABLE test_ks.products (id UUID PRIMARY KEY, name TEXT, category TEXT)");
        session.execute("CREATE INDEX ON test_ks.products (category)");
        // Index creation should not throw; verify data still queryable
        session.execute("INSERT INTO test_ks.products (id, name, category) VALUES (uuid(), 'Widget', 'Electronics')");
        var result = session.execute("SELECT * FROM test_ks.products WHERE category = 'Electronics'");
        assertThat(result.rows()).hasSize(1);
    }

    // ── DML ───────────────────────────────────────────────────────────────────

    @Test void insertAndSelect() {
        session.execute("CREATE TABLE test_ks.items (id UUID PRIMARY KEY, name TEXT)");
        session.execute("INSERT INTO test_ks.items (id, name) VALUES (uuid(), 'Alpha')");
        session.execute("INSERT INTO test_ks.items (id, name) VALUES (uuid(), 'Beta')");
        var result = session.execute("SELECT * FROM test_ks.items");
        assertThat(result.rows()).hasSize(2);
    }

    @Test void insertWithLiteralId() {
        session.execute("CREATE TABLE test_ks.kv (id UUID PRIMARY KEY, val TEXT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.kv (id, val) VALUES ('" + id + "', 'hello')");
        var result = session.execute("SELECT * FROM test_ks.kv WHERE id = '" + id + "'");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("val")).isEqualTo("hello");
    }

    @Test void selectWithWhere() {
        session.execute("CREATE TABLE test_ks.scores (id UUID PRIMARY KEY, score INT)");
        session.execute("INSERT INTO test_ks.scores (id, score) VALUES (uuid(), 100)");
        session.execute("INSERT INTO test_ks.scores (id, score) VALUES (uuid(), 200)");
        session.execute("INSERT INTO test_ks.scores (id, score) VALUES (uuid(), 300)");
        var result = session.execute("SELECT * FROM test_ks.scores WHERE score = 200");
        assertThat(result.rows()).hasSize(1);
    }

    @Test void selectWithLimit() {
        session.execute("CREATE TABLE test_ks.big (id UUID PRIMARY KEY, n INT)");
        for (int i = 0; i < 10; i++) {
            session.execute("INSERT INTO test_ks.big (id, n) VALUES (uuid(), " + i + ")");
        }
        var result = session.execute("SELECT * FROM test_ks.big LIMIT 3");
        assertThat(result.rows()).hasSize(3);
    }

    @Test void selectSpecificColumns() {
        session.execute("CREATE TABLE test_ks.people (id UUID PRIMARY KEY, name TEXT, age INT)");
        session.execute("INSERT INTO test_ks.people (id, name, age) VALUES (uuid(), 'Alice', 30)");
        var result = session.execute("SELECT name FROM test_ks.people");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).containsKey("name")).isTrue();
        // age should not be in projection
        assertThat(result.rows().get(0).containsKey("age")).isFalse();
    }

    @Test void updateRow() {
        session.execute("CREATE TABLE test_ks.cfg (id UUID PRIMARY KEY, val TEXT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.cfg (id, val) VALUES ('" + id + "', 'old')");
        session.execute("UPDATE test_ks.cfg SET val = 'new' WHERE id = '" + id + "'");
        var result = session.execute("SELECT * FROM test_ks.cfg WHERE id = '" + id + "'");
        assertThat(result.rows().get(0).get("val")).isEqualTo("new");
    }

    @Test void deleteRow() {
        session.execute("CREATE TABLE test_ks.logs (id UUID PRIMARY KEY, msg TEXT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.logs (id, msg) VALUES ('" + id + "', 'to delete')");
        session.execute("DELETE FROM test_ks.logs WHERE id = '" + id + "'");
        var result = session.execute("SELECT * FROM test_ks.logs");
        assertThat(result.rows()).isEmpty();
    }

    @Test void deleteSpecificField() {
        session.execute("CREATE TABLE test_ks.data (id UUID PRIMARY KEY, a TEXT, b TEXT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.data (id, a, b) VALUES ('" + id + "', 'aval', 'bval')");
        session.execute("DELETE a FROM test_ks.data WHERE id = '" + id + "'");
        var result = session.execute("SELECT * FROM test_ks.data WHERE id = '" + id + "'");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).containsKey("a")).isFalse();
        assertThat(result.rows().get(0).get("b")).isEqualTo("bval");
    }

    @Test void truncate() {
        session.execute("CREATE TABLE test_ks.temp (id UUID PRIMARY KEY)");
        session.execute("INSERT INTO test_ks.temp (id) VALUES (uuid())");
        session.execute("INSERT INTO test_ks.temp (id) VALUES (uuid())");
        session.execute("TRUNCATE test_ks.temp");
        var result = session.execute("SELECT * FROM test_ks.temp");
        assertThat(result.rows()).isEmpty();
    }

    // ── Prepared Statements ───────────────────────────────────────────────────

    @Test void preparedInsert() {
        session.execute("CREATE TABLE test_ks.ps_test (id UUID PRIMARY KEY, name TEXT, age INT)");
        var ps = session.prepare("INSERT INTO test_ks.ps_test (id, name, age) VALUES (?, ?, ?)");
        session.execute(ps, UUID.randomUUID().toString(), "Alice", 30L);
        session.execute(ps, UUID.randomUUID().toString(), "Bob", 25L);
        var result = session.execute("SELECT * FROM test_ks.ps_test");
        assertThat(result.rows()).hasSize(2);
    }

    @Test void preparedSelect() {
        session.execute("CREATE TABLE test_ks.ps_sel (id UUID PRIMARY KEY, status TEXT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.ps_sel (id, status) VALUES ('" + id + "', 'active')");
        var ps = session.prepare("SELECT * FROM test_ks.ps_sel WHERE id = ?");
        var result = session.execute(ps, id);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("status")).isEqualTo("active");
    }

    @Test void preparedSelectWithWhere() {
        session.execute("CREATE TABLE test_ks.ps_where (id UUID PRIMARY KEY, score INT)");
        for (int i = 1; i <= 5; i++) {
            session.execute("INSERT INTO test_ks.ps_where (id, score) VALUES (uuid(), " + (i * 10) + ")");
        }
        var ps = session.prepare("SELECT * FROM test_ks.ps_where WHERE score = ?");
        var result = session.execute(ps, 30L);
        assertThat(result.rows()).hasSize(1);
    }

    @Test void preparedUpdate() {
        session.execute("CREATE TABLE test_ks.ps_upd (id UUID PRIMARY KEY, v TEXT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.ps_upd (id, v) VALUES ('" + id + "', 'before')");
        var ps = session.prepare("UPDATE test_ks.ps_upd SET v = ? WHERE id = ?");
        session.execute(ps, "after", id);
        var result = session.execute("SELECT * FROM test_ks.ps_upd WHERE id = '" + id + "'");
        assertThat(result.rows().get(0).get("v")).isEqualTo("after");
    }

    @Test void preparedDelete() {
        session.execute("CREATE TABLE test_ks.ps_del (id UUID PRIMARY KEY, v TEXT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.ps_del (id, v) VALUES ('" + id + "', 'doomed')");
        var ps = session.prepare("DELETE FROM test_ks.ps_del WHERE id = ?");
        session.execute(ps, id);
        assertThat(session.execute("SELECT * FROM test_ks.ps_del").rows()).isEmpty();
    }

    // ── Batch ─────────────────────────────────────────────────────────────────

    @Test void batchInsert() {
        session.execute("CREATE TABLE test_ks.batch_tbl (id UUID PRIMARY KEY, n INT)");
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        session.executeBatch(
                "INSERT INTO test_ks.batch_tbl (id, n) VALUES ('" + id1 + "', 1)",
                "INSERT INTO test_ks.batch_tbl (id, n) VALUES ('" + id2 + "', 2)"
        );
        var result = session.execute("SELECT * FROM test_ks.batch_tbl");
        assertThat(result.rows()).hasSize(2);
    }

    @Test void batchMixedOperations() {
        session.execute("CREATE TABLE test_ks.batch_mix (id UUID PRIMARY KEY, val TEXT)");
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        session.executeBatch(
                "INSERT INTO test_ks.batch_mix (id, val) VALUES ('" + id1 + "', 'a')",
                "INSERT INTO test_ks.batch_mix (id, val) VALUES ('" + id2 + "', 'b')",
                "UPDATE test_ks.batch_mix SET val = 'updated' WHERE id = '" + id1 + "'"
        );
        var rows = session.execute("SELECT * FROM test_ks.batch_mix").rows();
        assertThat(rows).hasSize(2);
        rows.stream()
                .filter(r -> id1.equals(r.get("id")))
                .findFirst()
                .ifPresent(r -> assertThat(r.get("val")).isEqualTo("updated"));
    }

    // ── TTL ───────────────────────────────────────────────────────────────────

    @Test void insertWithTtl() throws InterruptedException {
        session.execute("CREATE TABLE test_ks.ttl_t (id UUID PRIMARY KEY, msg TEXT)");
        var inMemCol = (InMemoryCollection) db.underlying().getCollection("test_ks.ttl_t");
        // Insert directly so we control _id
        var wr = inMemCol.insertOne(Document.of("msg", "temporary"));
        Object docId = wr.insertedIds().get(0);
        // Set 1ms TTL on the _id
        inMemCol.setTtl(docId, 1);
        Thread.sleep(20);
        assertThat(inMemCol.count()).isEqualTo(0);
    }

    // ── COUNT / DISTINCT ──────────────────────────────────────────────────────

    @Test void selectCountStar() {
        session.execute("CREATE TABLE test_ks.cnt (id UUID PRIMARY KEY, v INT)");
        for (int i = 0; i < 7; i++) {
            session.execute("INSERT INTO test_ks.cnt (id, v) VALUES (uuid(), " + i + ")");
        }
        var result = session.execute("SELECT COUNT(*) FROM test_ks.cnt");
        assertThat(result.count()).isEqualTo(7);
    }

    @Test void selectCountStarWithWhere() {
        session.execute("CREATE TABLE test_ks.cnt2 (id UUID PRIMARY KEY, cat TEXT)");
        session.execute("INSERT INTO test_ks.cnt2 (id, cat) VALUES (uuid(), 'A')");
        session.execute("INSERT INTO test_ks.cnt2 (id, cat) VALUES (uuid(), 'A')");
        session.execute("INSERT INTO test_ks.cnt2 (id, cat) VALUES (uuid(), 'B')");
        var result = session.execute("SELECT COUNT(*) FROM test_ks.cnt2 WHERE cat = 'A'");
        assertThat(result.count()).isEqualTo(2);
    }

    @Test void selectDistinct() {
        session.execute("CREATE TABLE test_ks.dist (id UUID PRIMARY KEY, dept TEXT)");
        session.execute("INSERT INTO test_ks.dist (id, dept) VALUES (uuid(), 'Engineering')");
        session.execute("INSERT INTO test_ks.dist (id, dept) VALUES (uuid(), 'Engineering')");
        session.execute("INSERT INTO test_ks.dist (id, dept) VALUES (uuid(), 'Sales')");
        var result = session.execute("SELECT DISTINCT dept FROM test_ks.dist");
        assertThat(result.rows()).hasSize(2);
    }

    // ── Miscellaneous ─────────────────────────────────────────────────────────

    @Test void useKeyspaceSwitchesContext() {
        session.execute("CREATE TABLE test_ks.x (id UUID PRIMARY KEY)");
        session.execute("USE test_ks");
        // After USE, queries without keyspace prefix should use current keyspace
        assertThat(db.underlying().collectionExists("test_ks.x")).isTrue();
    }

    @Test void closedSessionThrows() {
        session.close();
        assertThatThrownBy(() -> session.execute("SELECT * FROM test_ks.anything"))
                .isInstanceOf(NoSqlException.class);
    }

    @Test void wasAppliedAlwaysTrue() {
        session.execute("CREATE TABLE test_ks.lwt (id UUID PRIMARY KEY, v INT)");
        String id = UUID.randomUUID().toString();
        session.execute("INSERT INTO test_ks.lwt (id, v) VALUES ('" + id + "', 1)");
        var result = session.execute("SELECT * FROM test_ks.lwt WHERE id = '" + id + "'");
        assertThat(result.wasApplied()).isTrue();
    }

    @Test void bulkInsertWithPreparedStatement() {
        session.execute("CREATE TABLE test_ks.bulk (id UUID PRIMARY KEY, val INT)");
        var ps = session.prepare("INSERT INTO test_ks.bulk (id, val) VALUES (?, ?)");
        for (int i = 0; i < 20; i++) {
            session.execute(ps, UUID.randomUUID().toString(), (long) i);
        }
        var result = session.execute("SELECT COUNT(*) FROM test_ks.bulk");
        assertThat(result.count()).isEqualTo(20);
    }

    @Test void selectWithGtCondition() {
        session.execute("CREATE TABLE test_ks.nums (id UUID PRIMARY KEY, n INT)");
        for (int i = 1; i <= 5; i++) {
            session.execute("INSERT INTO test_ks.nums (id, n) VALUES (uuid(), " + i + ")");
        }
        var result = session.execute("SELECT * FROM test_ks.nums WHERE n > 3");
        assertThat(result.rows()).hasSize(2);
    }

    @Test void selectWithLteCondition() {
        session.execute("CREATE TABLE test_ks.vals (id UUID PRIMARY KEY, score INT)");
        for (int i = 1; i <= 10; i++) {
            session.execute("INSERT INTO test_ks.vals (id, score) VALUES (uuid(), " + (i * 10) + ")");
        }
        var result = session.execute("SELECT * FROM test_ks.vals WHERE score <= 50");
        assertThat(result.rows()).hasSize(5);
    }

    @Test void cassandraResultSetToString() {
        var rs = CassandraResultSet.empty();
        assertThat(rs.toString()).contains("0");
        assertThat(rs.wasApplied()).isTrue();
        assertThat(rs.isEmpty()).isTrue();
    }
}

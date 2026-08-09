package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class InMemoryNoSqlDatabaseTest {

    private InMemoryNoSqlDatabase db = new InMemoryNoSqlDatabase();

    @BeforeEach
    void setUp() {
        db = new InMemoryNoSqlDatabase();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void getCollectionCreatesIfMissing() {
        NoSqlCollection col = db.getCollection("users");
        assertThat(col).isNotNull();
        assertThat(col.name()).isEqualTo("users");
    }

    @Test
    void getCollectionReturnsSameInstance() {
        NoSqlCollection c1 = db.getCollection("users");
        NoSqlCollection c2 = db.getCollection("users");
        assertThat(c1).isSameAs(c2);
    }

    @Test
    void createCollection() {
        NoSqlCollection col = db.createCollection("posts");
        assertThat(col).isNotNull();
        assertThat(db.collectionExists("posts")).isTrue();
    }

    @Test
    void createCollectionDuplicateThrows() {
        db.createCollection("users");
        assertThatThrownBy(() -> db.createCollection("users"))
                .isInstanceOf(NoSqlException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void collectionExists() {
        db.getCollection("users");
        assertThat(db.collectionExists("users")).isTrue();
        assertThat(db.collectionExists("missing")).isFalse();
    }

    @Test
    void dropCollection() {
        db.getCollection("users");
        db.dropCollection("users");
        assertThat(db.collectionExists("users")).isFalse();
    }

    @Test
    void dropCollectionNoop() {
        db.dropCollection("nonexistent");
        // should not throw
        assertThat(db.listCollectionNames()).isEmpty();
    }

    @Test
    void listCollectionNames() {
        db.getCollection("users");
        db.getCollection("posts");
        assertThat(db.listCollectionNames()).containsExactlyInAnyOrder("users", "posts");
    }

    @Test
    void dropAll() {
        db.getCollection("users");
        db.getCollection("posts");
        db.drop();
        assertThat(db.listCollectionNames()).isEmpty();
    }

    @Test
    void close() {
        db.getCollection("users");
        db.close();
        assertThat(db.listCollectionNames()).isEmpty();
    }

    @Test
    void returnsInMemoryCollection() {
        NoSqlCollection col = db.getCollection("test");
        assertThat(col).isInstanceOf(InMemoryCollection.class);
    }

    @Test
    void listCollectionNamesImmutable() {
        db.getCollection("users");
        var names = db.listCollectionNames();
        assertThatThrownBy(() -> names.add("hack"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

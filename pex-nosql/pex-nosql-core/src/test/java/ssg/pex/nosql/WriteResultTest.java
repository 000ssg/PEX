package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class WriteResultTest {

    @Test
    void inserted() {
        WriteResult result = WriteResult.inserted(3, List.of("id1", "id2", "id3"));
        assertThat(result.insertedCount()).isEqualTo(3);
        assertThat(result.modifiedCount()).isZero();
        assertThat(result.deletedCount()).isZero();
        assertThat(result.matchedCount()).isZero();
        assertThat(result.insertedIds()).containsExactly("id1", "id2", "id3");
    }

    @Test
    void modified() {
        WriteResult result = WriteResult.modified(5);
        assertThat(result.matchedCount()).isEqualTo(5);
        assertThat(result.modifiedCount()).isEqualTo(5);
        assertThat(result.insertedCount()).isZero();
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    void deleted() {
        WriteResult result = WriteResult.deleted(2);
        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(result.matchedCount()).isZero();
        assertThat(result.modifiedCount()).isZero();
        assertThat(result.insertedCount()).isZero();
    }

    @Test
    void none() {
        WriteResult result = WriteResult.none();
        assertThat(result.matchedCount()).isZero();
        assertThat(result.modifiedCount()).isZero();
        assertThat(result.insertedCount()).isZero();
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    void wasAcknowledgedInserted() {
        assertThat(WriteResult.inserted(1, List.of("id1")).wasAcknowledged()).isTrue();
    }

    @Test
    void wasAcknowledgedModified() {
        assertThat(WriteResult.modified(1).wasAcknowledged()).isTrue();
    }

    @Test
    void wasAcknowledgedDeleted() {
        assertThat(WriteResult.deleted(1).wasAcknowledged()).isTrue();
    }

    @Test
    void wasAcknowledgedNone() {
        assertThat(WriteResult.none().wasAcknowledged()).isFalse();
    }

    @Test
    void insertedIdsImmutable() {
        List<Object> mutable = new ArrayList<>(List.of("id1"));
        WriteResult result = WriteResult.inserted(1, mutable);
        mutable.add("id2");
        assertThat(result.insertedIds()).hasSize(1);
    }

    @Test
    void recordEquality() {
        WriteResult a = WriteResult.none();
        WriteResult b = WriteResult.none();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}

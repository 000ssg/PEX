package ssg.pex.result;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;

class BatchResultTest {

    @Test
    void fullSuccess() {
        BatchResult<String> result = new BatchResult<>(List.of("a", "b"), List.of(), 2);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.isFullSuccess()).isTrue();
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void withErrors() {
        PexError err = new PexError("ERR", "failed");
        BatchResult<String> result = new BatchResult<>(List.of("a"), List.of(err), 2);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.isFullSuccess()).isFalse();
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errorCount()).isEqualTo(1);
    }

    @Test
    void allErrors() {
        BatchResult<String> result = new BatchResult<>(List.of(), List.of(new PexError("E1", "x"), new PexError("E2", "y")), 2);
        assertThat(result.successCount()).isZero();
        assertThat(result.errorCount()).isEqualTo(2);
    }

    @Test
    void toStringOutput() {
        BatchResult<String> result = new BatchResult<>(List.of("a"), List.of(new PexError("E", "fail")), 2);
        String s = result.toString();
        assertThat(s).contains("success=1");
        assertThat(s).contains("errors=1");
        assertThat(s).contains("total=2");
    }
}

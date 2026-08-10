package ssg.pex.exec;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ExecutionConfigTest {

    @Test
    void defaults() {
        ExecutionConfig cfg = ExecutionConfig.defaults();
        assertThat(cfg.maxRecursionDepth()).isEqualTo(256);
        assertThat(cfg.maxLoopIterations()).isEqualTo(100_000);
        assertThat(cfg.timeoutMillis()).isEqualTo(30_000L);
        assertThat(cfg.errorMode()).isEqualTo(ExecutionConfig.ErrorMode.RESULT);
    }

    @Test
    void customConfig() {
        ExecutionConfig cfg = new ExecutionConfig(100, 50_000, 10_000L, ExecutionConfig.ErrorMode.EXCEPTION);
        assertThat(cfg.maxRecursionDepth()).isEqualTo(100);
        assertThat(cfg.maxLoopIterations()).isEqualTo(50_000);
        assertThat(cfg.timeoutMillis()).isEqualTo(10_000L);
        assertThat(cfg.errorMode()).isEqualTo(ExecutionConfig.ErrorMode.EXCEPTION);
    }

    @Test
    void errorModeValues() {
        assertThat(ExecutionConfig.ErrorMode.values()).hasSize(2);
    }

    @Test
    void recordEquality() {
        ExecutionConfig a = ExecutionConfig.defaults();
        ExecutionConfig b = ExecutionConfig.defaults();
        assertThat(a).isEqualTo(b);
    }
}

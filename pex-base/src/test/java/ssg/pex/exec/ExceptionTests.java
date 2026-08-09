package ssg.pex.exec;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ExceptionTests {

    @Test
    void pexExecutionExceptionMessage() {
        PexExecutionException ex = new PexExecutionException("timeout");
        assertThat(ex.getMessage()).isEqualTo("timeout");
    }

    @Test
    void pexExecutionExceptionWithCause() {
        RuntimeException cause = new RuntimeException("root");
        PexExecutionException ex = new PexExecutionException("fail", cause);
        assertThat(ex.getMessage()).isEqualTo("fail");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void returnExceptionValue() {
        ReturnException ex = new ReturnException(42);
        assertThat(ex.value()).isEqualTo(42);
    }

    @Test
    void returnExceptionNullValue() {
        ReturnException ex = new ReturnException(null);
        assertThat(ex.value()).isNull();
    }

    @Test
    void returnExceptionSuppressedStackTrace() {
        ReturnException ex = new ReturnException("result");
        assertThat(ex.getStackTrace()).isEmpty();
    }
}

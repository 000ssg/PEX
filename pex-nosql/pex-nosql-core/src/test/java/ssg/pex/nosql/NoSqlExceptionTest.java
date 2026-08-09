package ssg.pex.nosql;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class NoSqlExceptionTest {

    @Test
    void basicConstructor() {
        NoSqlException ex = new NoSqlException("TEST_ERR", "something went wrong");
        assertThat(ex.getMessage()).isEqualTo("something went wrong");
        assertThat(ex.errorCode()).isEqualTo("TEST_ERR");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructorWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        NoSqlException ex = new NoSqlException("TEST_ERR", "outer", cause);
        assertThat(ex.getMessage()).isEqualTo("outer");
        assertThat(ex.errorCode()).isEqualTo("TEST_ERR");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    public void toStringFormat() {
        NoSqlException ex = new NoSqlException("MY_CODE", "error msg");
        String s = ex.toString();
        assertThat(s).contains("MY_CODE");
        assertThat(s).contains("error msg");
    }

    @Test
    void isRuntimeException() {
        NoSqlException ex = new NoSqlException("E", "msg");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}

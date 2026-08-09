package ssg.pex.result;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.ast.SourceLocation;

class PexErrorTest {

    @Test
    void basicConstructor() {
        PexError err = new PexError("ERR_CODE", "something failed");
        assertThat(err.code()).isEqualTo("ERR_CODE");
        assertThat(err.message()).isEqualTo("something failed");
        assertThat(err.hasLocation()).isFalse();
        assertThat(err.hasCause()).isFalse();
    }

    @Test
    void withLocation() {
        SourceLocation loc = SourceLocation.of("test.pex", 1, 5);
        PexError err = new PexError("ERR", "fail", loc);
        assertThat(err.hasLocation()).isTrue();
        assertThat(err.location()).isSameAs(loc);
    }

    @Test
    void withCause() {
        RuntimeException cause = new RuntimeException("root");
        PexError err = new PexError("ERR", "fail", cause);
        assertThat(err.hasCause()).isTrue();
        assertThat(err.cause()).isSameAs(cause);
    }

    @Test
    void withAll() {
        SourceLocation loc = SourceLocation.of("test.pex", 1, 5);
        RuntimeException cause = new RuntimeException("root");
        PexError err = new PexError("ERR", "fail", loc, cause);
        assertThat(err.hasLocation()).isTrue();
        assertThat(err.hasCause()).isTrue();
    }

    @Test
    void toStringBasic() {
        PexError err = new PexError("ERR", "message");
        String s = err.toString();
        assertThat(s).contains("ERR");
        assertThat(s).contains("message");
    }

    @Test
    void toStringWithLocation() {
        SourceLocation loc = SourceLocation.of("file.pex", 10, 20);
        PexError err = new PexError("ERR", "msg", loc);
        String s = err.toString();
        assertThat(s).contains("at");
    }

    @Test
    void toStringWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PexError err = new PexError("ERR", "msg", cause);
        String s = err.toString();
        assertThat(s).contains("caused by");
        assertThat(s).contains("RuntimeException");
    }
}

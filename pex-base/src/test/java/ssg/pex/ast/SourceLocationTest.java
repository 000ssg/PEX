package ssg.pex.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SourceLocation")
class SourceLocationTest {

    @Test
    @DisplayName("of(source, line, column) stores all three fields")
    void testOfSourceLineColumn() {
        var loc = SourceLocation.of("main.pex", 10, 5);
        assertThat(loc.source()).isEqualTo("main.pex");
        assertThat(loc.line()).isEqualTo(10);
        assertThat(loc.column()).isEqualTo(5);
        assertThat(loc.offset()).isEqualTo(-1);
        assertThat(loc.length()).isEqualTo(-1);
    }

    @Test
    @DisplayName("of(line, column) stores line and column with null source")
    void testOfLineColumn() {
        var loc = SourceLocation.of(3, 7);
        assertThat(loc.source()).isNull();
        assertThat(loc.line()).isEqualTo(3);
        assertThat(loc.column()).isEqualTo(7);
    }

    @Test
    @DisplayName("at(offset, length) stores offset and length")
    void testAtOffsetLength() {
        var loc = SourceLocation.at(100, 20);
        assertThat(loc.source()).isNull();
        assertThat(loc.line()).isEqualTo(-1);
        assertThat(loc.column()).isEqualTo(-1);
        assertThat(loc.offset()).isEqualTo(100);
        assertThat(loc.length()).isEqualTo(20);
    }

    @Test
    @DisplayName("UNKNOWN has all fields set to -1 and null source")
    void testUnknown() {
        assertThat(SourceLocation.UNKNOWN.source()).isNull();
        assertThat(SourceLocation.UNKNOWN.line()).isEqualTo(-1);
        assertThat(SourceLocation.UNKNOWN.column()).isEqualTo(-1);
        assertThat(SourceLocation.UNKNOWN.offset()).isEqualTo(-1);
        assertThat(SourceLocation.UNKNOWN.length()).isEqualTo(-1);
    }

    @Test
    @DisplayName("toString with source, line and column")
    void testToStringWithSourceLineColumn() {
        var loc = SourceLocation.of("Parser.java", 42, 8);
        assertThat(loc.toString()).isEqualTo("Parser.java:42:8");
    }

    @Test
    @DisplayName("toString with line and column only (no source)")
    void testToStringWithLineColumnNoSource() {
        var loc = SourceLocation.of(5, 3);
        assertThat(loc.toString()).isEqualTo("5:3");
    }

    @Test
    @DisplayName("toString with offset and length")
    void testToStringWithOffsetLength() {
        var loc = SourceLocation.at(50, 10);
        assertThat(loc.toString()).isEqualTo("@50+10");
    }

    @Test
    @DisplayName("toString for UNKNOWN returns '?'")
    void testToStringUnknown() {
        assertThat(SourceLocation.UNKNOWN.toString()).isEqualTo("?");
    }

    @Test
    @DisplayName("equality by record components")
    void testEquality() {
        var a = SourceLocation.of("f.pex", 1, 2);
        var b = SourceLocation.of("f.pex", 1, 2);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("inequality on different fields")
    void testInequality() {
        var a = SourceLocation.of("f.pex", 1, 2);
        var b = SourceLocation.of("g.pex", 1, 2);
        assertThat(a).isNotEqualTo(b);
    }
}

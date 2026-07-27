package ssg.pex.sql.olap.ast;

/**
 * ROWS/RANGE BETWEEN start AND end
 */
public record FrameSpec(FrameType type, FrameBound start, FrameBound end) {

    public enum FrameType {
        ROWS, RANGE
    }
}

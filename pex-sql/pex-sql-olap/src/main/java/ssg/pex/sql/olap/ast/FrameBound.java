package ssg.pex.sql.olap.ast;

/**
 * A frame boundary such as UNBOUNDED PRECEDING, 2 PRECEDING, CURRENT ROW, etc.
 */
public record FrameBound(BoundType type, int offset) {

    public enum BoundType {
        UNBOUNDED_PRECEDING,
        N_PRECEDING,
        CURRENT_ROW,
        N_FOLLOWING,
        UNBOUNDED_FOLLOWING
    }

    public static FrameBound unboundedPreceding() {
        return new FrameBound(BoundType.UNBOUNDED_PRECEDING, 0);
    }

    public static FrameBound currentRow() {
        return new FrameBound(BoundType.CURRENT_ROW, 0);
    }

    public static FrameBound unboundedFollowing() {
        return new FrameBound(BoundType.UNBOUNDED_FOLLOWING, 0);
    }

    public static FrameBound preceding(int n) {
        return new FrameBound(BoundType.N_PRECEDING, n);
    }

    public static FrameBound following(int n) {
        return new FrameBound(BoundType.N_FOLLOWING, n);
    }
}

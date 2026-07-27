package ssg.pex.sql.streaming.ast;

/**
 * Window specification for streaming queries.
 *
 * @param type       the window type (TUMBLING, HOPPING, SLIDING, SESSION)
 * @param durationMs the window size / duration in milliseconds
 * @param advanceMs  the advance interval for hopping windows (0 for others)
 * @param gapMs      the inactivity gap for session windows (0 for others)
 */
public record WindowSpec(WindowType type, long durationMs, long advanceMs, long gapMs) {

    public enum WindowType {
        TUMBLING, HOPPING, SLIDING, SESSION
    }
}

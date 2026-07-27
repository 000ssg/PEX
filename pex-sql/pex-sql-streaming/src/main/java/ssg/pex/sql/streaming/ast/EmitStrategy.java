package ssg.pex.sql.streaming.ast;

/**
 * Emit strategy for streaming queries.
 * CHANGES: emit results as they change (on every event).
 * FINAL: emit results only when a window closes.
 */
public enum EmitStrategy {
    CHANGES, FINAL
}

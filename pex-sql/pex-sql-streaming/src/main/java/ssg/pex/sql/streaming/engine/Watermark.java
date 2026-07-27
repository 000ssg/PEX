package ssg.pex.sql.streaming.engine;

/**
 * Tracks event-time progress for a stream.
 * The watermark indicates that no event with a timestamp less than the watermark
 * is expected to arrive (apart from late events within the allowed lateness).
 */
public class Watermark {

    private long currentWatermark;
    private final long allowedLatenessMs;

    public Watermark() {
        this(0, 0);
    }

    public Watermark(long initialWatermark, long allowedLatenessMs) {
        this.currentWatermark = initialWatermark;
        this.allowedLatenessMs = allowedLatenessMs;
    }

    /**
     * Current watermark value (event time in ms).
     */
    public long currentWatermark() {
        return currentWatermark;
    }

    /**
     * Allowed lateness in ms.
     */
    public long allowedLatenessMs() {
        return allowedLatenessMs;
    }

    /**
     * Advance the watermark based on an incoming event timestamp.
     * The watermark always moves forward.
     */
    public void advance(long eventTimestamp) {
        if (eventTimestamp > currentWatermark) {
            currentWatermark = eventTimestamp;
        }
    }

    /**
     * Check if an event is late (its timestamp is behind the watermark minus allowed lateness).
     */
    public boolean isLate(long eventTimestamp) {
        return eventTimestamp < (currentWatermark - allowedLatenessMs);
    }

    /**
     * Set the watermark to a specific value (for testing / manual control).
     */
    public void set(long watermark) {
        this.currentWatermark = watermark;
    }
}

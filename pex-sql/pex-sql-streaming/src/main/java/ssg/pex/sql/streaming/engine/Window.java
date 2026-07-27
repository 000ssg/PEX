package ssg.pex.sql.streaming.engine;

import java.util.List;

/**
 * Sealed interface for stream processing windows.
 */
public sealed interface Window permits TumblingWindow, HoppingWindow, SlidingWindow, SessionWindow {

    long startTime();

    long endTime();

    List<StreamEvent> events();

    /**
     * Whether this window accepts an event at the given timestamp.
     */
    boolean accepts(long eventTimestamp);

    /**
     * Add an event to this window. Does nothing if the event is not accepted.
     */
    void addEvent(StreamEvent event);

    /**
     * Whether this window is closed given the current watermark.
     */
    boolean isClosed(long currentWatermark);
}

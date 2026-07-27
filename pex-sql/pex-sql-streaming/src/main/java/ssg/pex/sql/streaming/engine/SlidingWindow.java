package ssg.pex.sql.streaming.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A sliding window that covers a fixed duration ending at the given endTime.
 * Events with timestamps in [startTime, endTime) belong to this window.
 * A new sliding window is created for each incoming event, covering [eventTs - durationMs, eventTs + 1).
 */
public final class SlidingWindow implements Window {

    private final long startTime;
    private final long endTime;
    private final List<StreamEvent> events;

    public SlidingWindow(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.events = new ArrayList<>();
    }

    @Override
    public long startTime() {
        return startTime;
    }

    @Override
    public long endTime() {
        return endTime;
    }

    @Override
    public List<StreamEvent> events() {
        return Collections.unmodifiableList(events);
    }

    @Override
    public boolean accepts(long eventTimestamp) {
        return eventTimestamp >= startTime && eventTimestamp < endTime;
    }

    @Override
    public void addEvent(StreamEvent event) {
        if (accepts(event.timestamp())) {
            events.add(event);
        }
    }

    @Override
    public boolean isClosed(long currentWatermark) {
        return currentWatermark >= endTime;
    }
}

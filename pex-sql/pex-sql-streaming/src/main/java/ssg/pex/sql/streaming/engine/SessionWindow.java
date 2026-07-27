package ssg.pex.sql.streaming.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A session window that groups events by activity.
 * A session window stays open as long as events arrive within the gap timeout.
 * It closes when no event arrives within {@code gapMs} of the last event.
 */
public final class SessionWindow implements Window {

    private long startTime;
    private long endTime;
    private final long gapMs;
    private final List<StreamEvent> events;

    public SessionWindow(long firstEventTimestamp, long gapMs) {
        this.startTime = firstEventTimestamp;
        this.endTime = firstEventTimestamp + gapMs;
        this.gapMs = gapMs;
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

    public long gapMs() {
        return gapMs;
    }

    @Override
    public List<StreamEvent> events() {
        return Collections.unmodifiableList(events);
    }

    @Override
    public boolean accepts(long eventTimestamp) {
        // Accepts if within the current session boundary
        return eventTimestamp >= startTime && eventTimestamp < endTime;
    }

    @Override
    public void addEvent(StreamEvent event) {
        if (event.timestamp() >= startTime && event.timestamp() < endTime) {
            events.add(event);
            // Extend the session window end
            long newEnd = event.timestamp() + gapMs;
            if (newEnd > endTime) {
                endTime = newEnd;
            }
        }
    }

    @Override
    public boolean isClosed(long currentWatermark) {
        return currentWatermark >= endTime;
    }

    /**
     * Merge another session window into this one (for overlapping sessions).
     */
    public void merge(SessionWindow other) {
        if (other.startTime < this.startTime) {
            this.startTime = other.startTime;
        }
        if (other.endTime > this.endTime) {
            this.endTime = other.endTime;
        }
        this.events.addAll(other.events);
        this.events.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
    }
}

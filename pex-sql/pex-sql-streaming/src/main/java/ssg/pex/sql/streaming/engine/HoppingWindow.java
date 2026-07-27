package ssg.pex.sql.streaming.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A hopping (overlapping, fixed-size) window.
 * Window size = endTime - startTime; hops advance by advanceMs.
 * An event may belong to multiple hopping windows.
 */
public final class HoppingWindow implements Window {

    private final long startTime;
    private final long endTime;
    private final List<StreamEvent> events;

    public HoppingWindow(long startTime, long durationMs) {
        this.startTime = startTime;
        this.endTime = startTime + durationMs;
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

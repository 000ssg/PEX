package ssg.pex.sql.streaming.engine;

import ssg.pex.sql.streaming.ast.WindowSpec;
import ssg.pex.sql.streaming.ast.WindowSpec.WindowType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages open windows for a streaming query and assigns events to them.
 */
public class WindowManager {

    private final WindowType windowType;
    private final long durationMs;
    private final long advanceMs;
    private final long gapMs;
    private final List<Window> openWindows;

    private WindowManager(WindowType windowType, long durationMs, long advanceMs, long gapMs) {
        this.windowType = windowType;
        this.durationMs = durationMs;
        this.advanceMs = advanceMs;
        this.gapMs = gapMs;
        this.openWindows = new ArrayList<>();
    }

    /**
     * Create a WindowManager from a WindowSpec.
     */
    public static WindowManager from(WindowSpec spec) {
        return new WindowManager(spec.type(), spec.durationMs(), spec.advanceMs(), spec.gapMs());
    }

    /**
     * Factory for tumbling windows.
     */
    public static WindowManager tumbling(long durationMs) {
        return new WindowManager(WindowType.TUMBLING, durationMs, 0, 0);
    }

    /**
     * Factory for hopping windows.
     */
    public static WindowManager hopping(long durationMs, long advanceMs) {
        return new WindowManager(WindowType.HOPPING, durationMs, advanceMs, 0);
    }

    /**
     * Factory for sliding windows.
     */
    public static WindowManager sliding(long durationMs) {
        return new WindowManager(WindowType.SLIDING, durationMs, 0, 0);
    }

    /**
     * Factory for session windows.
     */
    public static WindowManager session(long gapMs) {
        return new WindowManager(WindowType.SESSION, 0, 0, gapMs);
    }

    /**
     * Assign an event to the appropriate window(s). Creates new windows as needed.
     */
    public void assignToWindows(StreamEvent event) {
        long ts = event.timestamp();

        switch (windowType) {
            case TUMBLING -> assignTumbling(event, ts);
            case HOPPING -> assignHopping(event, ts);
            case SLIDING -> assignSliding(event, ts);
            case SESSION -> assignSession(event, ts);
        }
    }

    private void assignTumbling(StreamEvent event, long ts) {
        // Find the window this event belongs to
        long windowStart = (ts / durationMs) * durationMs;
        boolean assigned = false;
        for (Window w : openWindows) {
            if (w.accepts(ts)) {
                w.addEvent(event);
                assigned = true;
                break;
            }
        }
        if (!assigned) {
            var window = new TumblingWindow(windowStart, durationMs);
            window.addEvent(event);
            openWindows.add(window);
        }
    }

    private void assignHopping(StreamEvent event, long ts) {
        // An event can belong to multiple hopping windows.
        // Find all windows that should exist for this event timestamp.
        // Windows start at multiples of advanceMs.
        long firstWindowStart = ((ts / advanceMs) * advanceMs) - durationMs + advanceMs;
        if (firstWindowStart < 0) firstWindowStart = 0;
        // Align firstWindowStart to advanceMs boundary
        firstWindowStart = (firstWindowStart / advanceMs) * advanceMs;

        boolean assigned = false;
        for (Window w : openWindows) {
            if (w.accepts(ts)) {
                w.addEvent(event);
                assigned = true;
            }
        }

        // Create any missing windows
        for (long start = firstWindowStart; start <= ts; start += advanceMs) {
            long end = start + durationMs;
            if (ts >= start && ts < end) {
                // Check if this window already exists
                boolean exists = false;
                for (Window w : openWindows) {
                    if (w.startTime() == start && w.endTime() == end) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    var window = new HoppingWindow(start, durationMs);
                    window.addEvent(event);
                    openWindows.add(window);
                    assigned = true;
                }
            }
        }
    }

    private void assignSliding(StreamEvent event, long ts) {
        // Each event triggers a new sliding window covering [ts - durationMs + 1, ts + 1)
        // But also assign to any existing windows that accept this event
        for (Window w : openWindows) {
            if (w.accepts(ts)) {
                w.addEvent(event);
            }
        }
        var window = new SlidingWindow(Math.max(0, ts - durationMs + 1), ts + 1);
        // Backfill: add prior events that fall within the new window's range
        var seen = new java.util.IdentityHashMap<StreamEvent, Boolean>();
        seen.put(event, Boolean.TRUE);
        for (Window w : openWindows) {
            for (StreamEvent existing : w.events()) {
                if (!seen.containsKey(existing) && window.accepts(existing.timestamp())) {
                    window.addEvent(existing);
                    seen.put(existing, Boolean.TRUE);
                }
            }
        }
        window.addEvent(event);
        openWindows.add(window);
    }

    private void assignSession(StreamEvent event, long ts) {
        // Find a session window that accepts this event
        SessionWindow target = null;
        for (Window w : openWindows) {
            if (w instanceof SessionWindow sw && sw.accepts(ts)) {
                target = sw;
                break;
            }
        }
        if (target != null) {
            target.addEvent(event);
            // Merge overlapping sessions
            mergeSessionWindows();
        } else {
            var session = new SessionWindow(ts, gapMs);
            session.addEvent(event);
            openWindows.add(session);
        }
    }

    private void mergeSessionWindows() {
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < openWindows.size(); i++) {
                if (!(openWindows.get(i) instanceof SessionWindow a)) continue;
                for (int j = i + 1; j < openWindows.size(); j++) {
                    if (!(openWindows.get(j) instanceof SessionWindow b)) continue;
                    // Overlap if one's range intersects the other
                    if (a.startTime() < b.endTime() && b.startTime() < a.endTime()) {
                        a.merge(b);
                        openWindows.remove(j);
                        merged = true;
                        break;
                    }
                }
                if (merged) break;
            }
        }
    }

    /**
     * Return and remove all closed windows given the current watermark.
     */
    public List<Window> getClosedWindows(long watermark) {
        var closed = new ArrayList<Window>();
        Iterator<Window> it = openWindows.iterator();
        while (it.hasNext()) {
            Window w = it.next();
            if (w.isClosed(watermark)) {
                closed.add(w);
                it.remove();
            }
        }
        return closed;
    }

    /**
     * Return all currently open windows (for inspection/testing).
     */
    public List<Window> openWindows() {
        return new ArrayList<>(openWindows);
    }
}

package ssg.pex.sql.streaming.engine;

import ssg.pex.sql.dbms.Column;

import java.util.*;

/**
 * A named event stream with a schema and an internal event buffer.
 */
public class StreamSource {

    private final String name;
    private final List<Column> schema;
    private final LinkedList<StreamEvent> buffer;
    private final Map<String, String> properties;

    public StreamSource(String name, List<Column> schema, Map<String, String> properties) {
        this.name = name;
        this.schema = new ArrayList<>(schema);
        this.buffer = new LinkedList<>();
        this.properties = new LinkedHashMap<>(properties);
    }

    public String name() {
        return name;
    }

    public List<Column> schema() {
        return Collections.unmodifiableList(schema);
    }

    public Map<String, String> properties() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * Emit (enqueue) an event into the stream buffer.
     */
    public void emit(StreamEvent event) {
        buffer.add(event);
    }

    /**
     * Drain up to {@code maxEvents} events from the buffer.
     */
    public List<StreamEvent> poll(int maxEvents) {
        var result = new ArrayList<StreamEvent>(Math.min(maxEvents, buffer.size()));
        for (int i = 0; i < maxEvents && !buffer.isEmpty(); i++) {
            result.add(buffer.poll());
        }
        return result;
    }

    /**
     * Return all buffered events whose timestamp falls within [fromTimestamp, toTimestamp).
     * Events are removed from the buffer.
     */
    public List<StreamEvent> pollWindow(long fromTimestamp, long toTimestamp) {
        var result = new ArrayList<StreamEvent>();
        var it = buffer.iterator();
        while (it.hasNext()) {
            StreamEvent e = it.next();
            if (e.timestamp() >= fromTimestamp && e.timestamp() < toTimestamp) {
                result.add(e);
                it.remove();
            }
        }
        return result;
    }

    /**
     * Peek at all buffered events without removing them.
     */
    public List<StreamEvent> peekAll() {
        return Collections.unmodifiableList(new ArrayList<>(buffer));
    }

    /**
     * Number of buffered events.
     */
    public int bufferedCount() {
        return buffer.size();
    }
}

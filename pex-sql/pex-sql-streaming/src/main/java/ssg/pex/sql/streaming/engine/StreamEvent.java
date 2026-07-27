package ssg.pex.sql.streaming.engine;

import java.util.Map;

/**
 * A single event in a stream.
 *
 * @param timestamp event time in milliseconds
 * @param key       partition key (nullable)
 * @param values    column name to value mapping
 */
public record StreamEvent(long timestamp, String key, Map<String, Object> values) {

    /**
     * Retrieve a typed value by column name.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String column) {
        return (T) values.get(column);
    }
}

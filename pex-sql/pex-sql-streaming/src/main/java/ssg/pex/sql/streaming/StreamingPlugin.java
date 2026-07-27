package ssg.pex.sql.streaming;

import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

/**
 * PEX plugin for streaming SQL: windowed aggregation and event processing.
 */
public class StreamingPlugin implements PexPlugin {

    @Override
    public String name() {
        return "sql-streaming";
    }

    @Override
    public int loadOrder() {
        return 220;
    }

    @Override
    public void initialize(PluginContext ctx) {
        // Streaming engine is self-contained; no handlers or grammar extensions needed at plugin level.
    }
}

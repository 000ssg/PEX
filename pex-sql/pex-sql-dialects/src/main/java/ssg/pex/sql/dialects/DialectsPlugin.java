package ssg.pex.sql.dialects;

import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

/**
 * PEX plugin that registers SQL dialect support for Oracle, MSSQL, MySQL,
 * and special storage engines (columnar, time-series, full-text, spatial).
 */
public class DialectsPlugin implements PexPlugin {

    @Override
    public String name() {
        return "sql-dialects";
    }

    @Override
    public int loadOrder() {
        return 230;
    }

    @Override
    public void initialize(PluginContext ctx) {
        // Dialect-specific functions and parsers are used via DialectDatabase
    }
}

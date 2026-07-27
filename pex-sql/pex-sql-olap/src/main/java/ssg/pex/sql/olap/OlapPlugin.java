package ssg.pex.sql.olap;

import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

/**
 * PEX plugin for SQL OLAP extensions: window functions, CTEs, CUBE/ROLLUP, MERGE, PIVOT/UNPIVOT.
 */
public class OlapPlugin implements PexPlugin {

    @Override
    public String name() {
        return "sql-olap";
    }

    @Override
    public int loadOrder() {
        return 210;
    }

    @Override
    public void initialize(PluginContext ctx) {
        // OLAP functionality is accessed through OlapDatabase
        // which wraps InMemoryDatabase and adds OLAP capabilities.
        // No additional handlers or functions needed at the plugin level
        // since OLAP operations are orchestrated through the OlapDatabase API.
    }
}

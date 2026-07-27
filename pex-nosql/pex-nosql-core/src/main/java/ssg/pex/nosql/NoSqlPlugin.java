package ssg.pex.nosql;

import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

/**
 * PEX plugin for in-memory NoSQL simulation (MongoDB and Cassandra dialects).
 *
 * <p>Registers the pex-nosql-core module with the PEX plugin system.
 * The NoSQL engine does not use AST-based node handlers — collections are
 * accessed directly via {@link InMemoryNoSqlDatabase}. This plugin therefore
 * acts as a declarative registration point that makes the module discoverable
 * via {@code ServiceLoader} with a defined load order.
 *
 * <p>Load order {@code 210} places nosql-core after SQL core ({@code 200}) and
 * before dialect extensions.
 */
public class NoSqlPlugin implements PexPlugin {

    @Override
    public String name() {
        return "nosql";
    }

    @Override
    public int loadOrder() {
        return 210;
    }

    @Override
    public void initialize(PluginContext ctx) {
        // NoSQL does not use AST-based handlers — collections are used directly
        // via InMemoryNoSqlDatabase.getCollection(name).
        // This method intentionally left as a no-op.
    }
}

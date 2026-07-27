package ssg.pex.nosql.dialects;

import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

/**
 * PEX plugin that registers NoSQL dialect support (MongoDB and Cassandra).
 *
 * <p>Dialect-specific APIs ({@link ssg.pex.nosql.dialects.mongodb.MongoDbDatabase},
 * {@link ssg.pex.nosql.dialects.cassandra.CassandraDatabase}) are used directly
 * rather than through AST node handlers, so {@link #initialize(PluginContext)}
 * is a no-op. This plugin exists to make pex-nosql-dialects discoverable via
 * {@code ServiceLoader} with a defined load order.
 *
 * <p>Load order {@code 240} places nosql-dialects after nosql-core ({@code 210})
 * and sql-dialects ({@code 230}).
 */
public class NoSqlDialectsPlugin implements PexPlugin {

    @Override
    public String name() {
        return "nosql-dialects";
    }

    @Override
    public int loadOrder() {
        return 240;
    }

    @Override
    public void initialize(PluginContext ctx) {
        // Dialect-specific databases (MongoDbDatabase, CassandraDatabase) are
        // used via NoSqlDialectDatabase directly — no handler registration needed.
    }
}

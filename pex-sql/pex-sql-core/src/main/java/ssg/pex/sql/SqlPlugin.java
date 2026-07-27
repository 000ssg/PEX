package ssg.pex.sql;

import ssg.pex.ast.node.ExtensionNode;
import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.function.SqlFunctions;
import ssg.pex.sql.handler.SqlNodeHandler;

import java.util.List;

/**
 * PEX plugin for SQL parsing and in-memory database simulation.
 */
public class SqlPlugin implements PexPlugin {

    @Override
    public String name() {
        return "sql";
    }

    @Override
    public int loadOrder() {
        return 200;
    }

    @Override
    public void initialize(PluginContext ctx) {
        var database = new InMemoryDatabase();

        // Register handler for ExtensionNode (which wraps SqlNode)
        ctx.registerHandler(ExtensionNode.class, new SqlNodeHandler(database));

        // Register SQL built-in functions
        SqlFunctions.all().forEach((name, fn) ->
                ctx.registerFunction(name, List.of(), fn));
    }
}

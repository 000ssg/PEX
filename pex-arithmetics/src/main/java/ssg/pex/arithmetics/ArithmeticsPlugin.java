package ssg.pex.arithmetics;

import ssg.pex.arithmetics.function.ConversionFunctions;
import ssg.pex.arithmetics.function.MathFunctions;
import ssg.pex.arithmetics.function.RadixFunctions;
import ssg.pex.arithmetics.grammar.ArithmeticsGrammarProvider;
import ssg.pex.arithmetics.handler.ArithmeticHandlerProvider;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.exec.NodeHandler;
import ssg.pex.spi.GrammarProvider;
import ssg.pex.spi.HandlerProvider;
import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

import java.util.Map;

/**
 * Arithmetic plugin for PEX. Provides numeric operations, math functions,
 * type conversions, and an expression grammar.
 */
public class ArithmeticsPlugin implements PexPlugin, HandlerProvider, GrammarProvider {

    private Map<Class<?>, NodeHandler> handlers;
    private Grammar grammar;

    @Override
    public String name() {
        return "arithmetics";
    }

    @Override
    public int loadOrder() {
        return 100;
    }

    @Override
    public void initialize(PluginContext ctx) {
        // Register all handlers
        handlers = provideHandlers();
        for (var entry : handlers.entrySet()) {
            ctx.registerHandler(entry.getKey(), entry.getValue());
        }

        // Register all functions
        MathFunctions.register(ctx);
        ConversionFunctions.register(ctx);
        RadixFunctions.register(ctx);

        // Set grammar
        grammar = provideGrammar();
        ctx.setGrammar(grammar);
    }

    @Override
    public Map<Class<?>, NodeHandler> provideHandlers() {
        if (handlers == null) {
            handlers = ArithmeticHandlerProvider.createHandlers();
        }
        return handlers;
    }

    @Override
    public String dialectName() {
        return "arithmetics";
    }

    @Override
    public Grammar provideGrammar() {
        if (grammar == null) {
            grammar = ArithmeticsGrammarProvider.createGrammar();
        }
        return grammar;
    }
}

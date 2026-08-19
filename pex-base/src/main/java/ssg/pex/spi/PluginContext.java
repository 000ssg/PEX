package ssg.pex.spi;

import ssg.pex.bnf.dialect.DialectExtension;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.exec.FunctionRegistry;
import ssg.pex.exec.HandlerRegistry;
import ssg.pex.exec.NativeFunction;
import ssg.pex.exec.NodeHandler;

import java.util.List;

public class PluginContext {

    private final HandlerRegistry handlerRegistry;
    private final FunctionRegistry functionRegistry;
    private Grammar grammar;

    public PluginContext(HandlerRegistry handlerRegistry, FunctionRegistry functionRegistry, Grammar grammar) {
        this.handlerRegistry = handlerRegistry;
        this.functionRegistry = functionRegistry;
        this.grammar = grammar;
    }

    public void registerHandler(Class<?> nodeType, NodeHandler handler) {
        handlerRegistry.register(nodeType, handler);
    }

    public void registerFunction(String name, NativeFunction impl) {
        functionRegistry.registerNative(name, List.of(), impl);
    }

    public void registerFunction(String name, List<String> paramNames, NativeFunction impl) {
        functionRegistry.registerNative(name, paramNames, impl);
    }

    public void extendGrammar(DialectExtension ext) {
        if (grammar != null) {
            grammar = grammar.withDialect(ext);
        }
    }

    public void setGrammar(Grammar grammar) {
        this.grammar = grammar;
    }

    public Grammar grammar() {
        return grammar;
    }

    public HandlerRegistry handlerRegistry() {
        return handlerRegistry;
    }

    public FunctionRegistry functionRegistry() {
        return functionRegistry;
    }
}

package ssg.pex.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.ast.node.AstNode;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.result.PexError;
import ssg.pex.result.Result;
import ssg.pex.scope.ScopeTree;
import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;
import ssg.pex.spi.PluginRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ExecutionEngine implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionEngine.class);

    private final Grammar grammar;
    private final RecursiveDescentEngine parser;
    private final HandlerRegistry handlers;
    private final FunctionRegistry functions;
    private final ExecutionConfig config;
    private final List<ExecutionListener> listeners;
    private final ExecutionStatistics statistics;

    private ExecutionEngine(Builder builder) {
        this.grammar = builder.grammar;
        this.parser = new RecursiveDescentEngine();
        this.handlers = builder.handlers;
        this.functions = builder.functions;
        this.config = builder.config != null ? builder.config : ExecutionConfig.defaults();
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
        this.statistics = new ExecutionStatistics();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Result<Object> parseAndExecute(String input) {
        var parseResult = parse(input);
        if (parseResult.isFailure()) {
            return Result.failure(parseResult.error());
        }
        return execute(parseResult.value());
    }

    public Result<AstNode> parse(String input) {
        if (grammar == null) {
            return Result.failure("NO_GRAMMAR", "No grammar configured for this engine");
        }
        var parseResult = parser.parse(input, grammar);
        if (parseResult.isFailure()) {
            return Result.failure(parseResult.error());
        }
        // Stub: AST building from ParseMatch is complex and will be fully implemented later.
        return Result.failure("NOT_IMPLEMENTED",
                "AST building from ParseMatch is not yet implemented. " +
                "Use execute(AstNode) with a manually constructed AST.");
    }

    public Result<Object> execute(AstNode ast) {
        var scopeTree = new ScopeTree();
        var ctx = new ExecutionContext(scopeTree, functions, handlers, statistics, config, listeners, this);
        LOG.debug("Executing AST: {}", ast.getClass().getSimpleName());
        return executeNode(ast, ctx);
    }

    public Result<Object> executeNode(Object node, ExecutionContext ctx) {
        if (node == null) {
            return Result.success(null);
        }

        ctx.checkTimeout();
        statistics.incrementNodeCount();

        for (var listener : ctx.listeners()) {
            listener.onNodeEnter(node, ctx);
        }

        var handler = handlers.getHandler(node.getClass());
        if (handler == null) {
            var error = new PexError("NO_HANDLER",
                    "No handler registered for node type: " + node.getClass().getName());
            statistics.incrementErrors();
            for (var listener : ctx.listeners()) {
                listener.onError(error, ctx);
            }
            return Result.failure(error);
        }

        Result<Object> result;
        try {
            result = handler.handle(node, ctx);
        } catch (ReturnException re) {
            throw re;
        } catch (PexExecutionException e) {
            var error = new PexError("EXECUTION_ERROR", e.getMessage(), e);
            statistics.incrementErrors();
            result = Result.failure(error);
        } catch (Exception e) {
            var error = new PexError("UNEXPECTED_ERROR", e.getMessage(), e);
            statistics.incrementErrors();
            result = Result.failure(error);
        }

        if (result.isFailure()) {
            statistics.incrementErrors();
            for (var listener : ctx.listeners()) {
                listener.onError(result.error(), ctx);
            }
        }

        for (var listener : ctx.listeners()) {
            listener.onNodeExit(node, ctx, result);
        }

        return result;
    }

    @Override
    public void close() {
        LOG.debug("ExecutionEngine closed. Statistics: {}", statistics.snapshot());
    }

    public Grammar grammar() {
        return grammar;
    }

    public HandlerRegistry handlers() {
        return handlers;
    }

    public FunctionRegistry functions() {
        return functions;
    }

    public ExecutionConfig config() {
        return config;
    }

    public ExecutionStatistics statistics() {
        return statistics;
    }

    public static class Builder {

        private Grammar grammar;
        private ExecutionConfig config;
        private final HandlerRegistry handlers = new HandlerRegistry();
        private final FunctionRegistry functions = new FunctionRegistry();
        private final List<ExecutionListener> listeners = new ArrayList<>();
        private final List<PexPlugin> plugins = new ArrayList<>();

        private Builder() {}

        public Builder grammar(Grammar grammar) {
            this.grammar = grammar;
            return this;
        }

        public Builder config(ExecutionConfig config) {
            this.config = config;
            return this;
        }

        public Builder plugin(PexPlugin plugin) {
            this.plugins.add(plugin);
            return this;
        }

        public Builder addListener(ExecutionListener listener) {
            this.listeners.add(listener);
            return this;
        }

        public Builder loadPlugins() {
            var registry = PluginRegistry.getInstance();
            this.plugins.addAll(registry.loadPlugins());
            return this;
        }

        public ExecutionEngine build() {
            var ctx = new PluginContext(handlers, functions, grammar);
            for (var plugin : plugins) {
                LOG.debug("Initializing plugin: {}", plugin.name());
                plugin.initialize(ctx);
            }
            this.grammar = ctx.grammar();
            return new ExecutionEngine(this);
        }
    }
}

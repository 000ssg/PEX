package ssg.pex.exec;

import ssg.pex.result.Result;
import ssg.pex.scope.ScopeTree;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class ExecutionContext {

    private final ScopeTree scopeTree;
    private final FunctionRegistry functions;
    private final HandlerRegistry handlers;
    private final ExecutionStatistics statistics;
    private final ExecutionConfig config;
    private final List<ExecutionListener> listeners;
    private final ExecutionEngine engine;
    private final long startTimeNanos;
    private int recursionDepth;

    public ExecutionContext(
            ScopeTree scopeTree,
            FunctionRegistry functions,
            HandlerRegistry handlers,
            ExecutionStatistics statistics,
            ExecutionConfig config,
            List<ExecutionListener> listeners,
            ExecutionEngine engine
    ) {
        this.scopeTree = scopeTree;
        this.functions = functions;
        this.handlers = handlers;
        this.statistics = statistics;
        this.config = config;
        this.listeners = listeners;
        this.engine = engine;
        this.startTimeNanos = System.nanoTime();
        this.recursionDepth = 0;
    }

    public Result<Object> execute(Object node) {
        return engine.executeNode(node, this);
    }

    public void checkTimeout() {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        if (elapsedMillis > config.timeoutMillis()) {
            throw new PexExecutionException("Execution timed out after " + elapsedMillis + "ms");
        }
    }

    public void checkRecursionDepth() {
        if (recursionDepth > config.maxRecursionDepth()) {
            throw new PexExecutionException(
                    "Maximum recursion depth exceeded: " + recursionDepth + " > " + config.maxRecursionDepth());
        }
    }

    public void enterRecursion() {
        recursionDepth++;
        checkRecursionDepth();
    }

    public void exitRecursion() {
        recursionDepth--;
    }

    public ScopeTree scopeTree() {
        return scopeTree;
    }

    public FunctionRegistry functions() {
        return functions;
    }

    public HandlerRegistry handlers() {
        return handlers;
    }

    public ExecutionConfig config() {
        return config;
    }

    public ExecutionStatistics statistics() {
        return statistics;
    }

    public List<ExecutionListener> listeners() {
        return listeners;
    }

    public int recursionDepth() {
        return recursionDepth;
    }

    public long startTimeNanos() {
        return startTimeNanos;
    }
}

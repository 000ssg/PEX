package ssg.pex.exec;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ExecutionStatistics {

    private final AtomicLong nodeCount = new AtomicLong();
    private final AtomicLong functionCalls = new AtomicLong();
    private final AtomicLong scopeEntries = new AtomicLong();
    private final AtomicLong scopeExits = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public void incrementNodeCount() {
        nodeCount.incrementAndGet();
    }

    public void incrementFunctionCalls() {
        functionCalls.incrementAndGet();
    }

    public void incrementScopeEntries() {
        scopeEntries.incrementAndGet();
    }

    public void incrementScopeExits() {
        scopeExits.incrementAndGet();
    }

    public void incrementErrors() {
        errors.incrementAndGet();
    }

    public long getNodeCount() {
        return nodeCount.get();
    }

    public long getFunctionCalls() {
        return functionCalls.get();
    }

    public long getScopeEntries() {
        return scopeEntries.get();
    }

    public long getScopeExits() {
        return scopeExits.get();
    }

    public long getErrors() {
        return errors.get();
    }

    public Map<String, Long> snapshot() {
        return Map.of(
                "nodeCount", nodeCount.get(),
                "functionCalls", functionCalls.get(),
                "scopeEntries", scopeEntries.get(),
                "scopeExits", scopeExits.get(),
                "errors", errors.get()
        );
    }

    public void reset() {
        nodeCount.set(0);
        functionCalls.set(0);
        scopeEntries.set(0);
        scopeExits.set(0);
        errors.set(0);
    }
}

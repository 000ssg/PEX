package ssg.pex.scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.result.Result;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScopeTree {

    private static final Logger LOG = LoggerFactory.getLogger(ScopeTree.class);

    private final Scope root;
    private Scope current;
    private final ScopeTracer tracer;
    private final CopyOnWriteArrayList<ScopeTreeListener> listeners = new CopyOnWriteArrayList<>();

    public ScopeTree() {
        this.root = new Scope("global", null);
        this.current = root;
        this.tracer = new ScopeTracer();
    }

    public Scope enterScope(String name) {
        var child = new Scope(name, current);
        current.addChild(child);
        current = child;
        LOG.debug("Entered scope: {}", child.path());
        for (var listener : listeners) {
            listener.onScopeEnter(child);
        }
        return child;
    }

    public Scope exitScope() {
        if (current.parent() == null) {
            LOG.warn("Attempted to exit root scope");
            return current;
        }
        var exiting = current;
        current = current.parent();
        LOG.debug("Exited scope: {}", exiting.path());
        for (var listener : listeners) {
            listener.onScopeExit(exiting);
        }
        return current;
    }

    public Result<Void> defineVariable(String name, Variable var) {
        if (current.hasLocal(name)) {
            return Result.failure("DUPLICATE_VARIABLE",
                    "Variable '" + name + "' already defined in scope " + current.path());
        }

        // Check for shadowing in parent scopes
        var scope = current.parent();
        while (scope != null) {
            if (scope.hasLocal(name)) {
                var shadowedVar = scope.getLocal(name);
                var record = new ShadowRecord(
                        name,
                        current.path(),
                        scope.path(),
                        shadowedVar.currentValue(),
                        var.currentValue()
                );
                tracer.recordShadow(record);
                LOG.debug("Variable '{}' in {} shadows {} in {}", name, current.path(), name, scope.path());
                for (var listener : listeners) {
                    listener.onVariableShadow(record);
                }
                break;
            }
            scope = scope.parent();
        }

        current.define(name, var);
        for (var listener : listeners) {
            listener.onVariableDefine(current, var);
        }
        return Result.success(null);
    }

    public Result<Variable> resolveVariable(String name) {
        var scope = current;
        while (scope != null) {
            if (scope.hasLocal(name)) {
                return Result.success(scope.getLocal(name));
            }
            scope = scope.parent();
        }
        return Result.failure("UNDEFINED_VARIABLE",
                "Variable '" + name + "' is not defined in scope " + current.path() + " or any parent scope");
    }

    public Result<Void> updateVariable(String name, Object newValue) {
        var scope = current;
        while (scope != null) {
            if (scope.hasLocal(name)) {
                var existing = scope.getLocal(name);
                if (!existing.mutable()) {
                    return Result.failure("IMMUTABLE_VARIABLE",
                            "Variable '" + name + "' is immutable");
                }
                if (existing instanceof ScalarVariable scalar) {
                    scope.update(name, scalar.withValue(newValue));
                    return Result.success(null);
                }
                return Result.failure("UPDATE_ERROR",
                        "Cannot update non-scalar variable '" + name + "' directly");
            }
            scope = scope.parent();
        }
        return Result.failure("UNDEFINED_VARIABLE",
                "Variable '" + name + "' is not defined");
    }

    public ScopePath currentPath() {
        return current.path();
    }

    public List<ShadowRecord> shadowHistory() {
        return tracer.history();
    }

    public Scope root() {
        return root;
    }

    public Scope current() {
        return current;
    }

    public void addListener(ScopeTreeListener listener) {
        listeners.add(listener);
    }
}

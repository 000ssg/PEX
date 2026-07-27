package ssg.pex.scope;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeDescriptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeTreeTest {

    private ScopeTree tree;

    @BeforeEach
    void setUp() {
        tree = new ScopeTree();
    }

    // --- Construction ---

    @Test
    void newTree_hasGlobalRootScope() {
        assertThat(tree.root()).isNotNull();
        assertThat(tree.root().name()).isEqualTo("global");
        assertThat(tree.root().parent()).isNull();
    }

    @Test
    void newTree_currentIsRoot() {
        assertThat(tree.current()).isSameAs(tree.root());
    }

    // --- enterScope / exitScope ---

    @Test
    void enterScope_createsChild_movesCurrent() {
        var child = tree.enterScope("block");
        assertThat(tree.current()).isSameAs(child);
        assertThat(child.name()).isEqualTo("block");
        assertThat(child.parent()).isSameAs(tree.root());
        assertThat(tree.root().children()).contains(child);
    }

    @Test
    void exitScope_movesToParent() {
        tree.enterScope("block");
        tree.exitScope();
        assertThat(tree.current()).isSameAs(tree.root());
    }

    @Test
    void exitScope_fromRoot_returnsRoot() {
        // The implementation logs a warning and returns current (root)
        var result = tree.exitScope();
        assertThat(result).isSameAs(tree.root());
        assertThat(tree.current()).isSameAs(tree.root());
    }

    // --- defineVariable ---

    @Test
    void defineVariable_inCurrentScope() {
        var v = scalarVar("x", 42);
        var result = tree.defineVariable("x", v);
        assertThat(result.isSuccess()).isTrue();
        assertThat(tree.root().hasLocal("x")).isTrue();
    }

    @Test
    void defineVariable_duplicateInSameScopeFails() {
        tree.defineVariable("x", scalarVar("x", 1));
        var result = tree.defineVariable("x", scalarVar("x", 2));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("DUPLICATE_VARIABLE");
    }

    // --- resolveVariable ---

    @Test
    void resolveVariable_fromCurrentScope() {
        tree.defineVariable("x", scalarVar("x", 10));
        var result = tree.resolveVariable("x");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().currentValue()).isEqualTo(10);
    }

    @Test
    void resolveVariable_walksParentChain() {
        tree.defineVariable("x", scalarVar("x", 100));
        tree.enterScope("inner");
        var result = tree.resolveVariable("x");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().currentValue()).isEqualTo(100);
    }

    @Test
    void resolveVariable_notFound() {
        var result = tree.resolveVariable("nonExistent");
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("UNDEFINED_VARIABLE");
    }

    // --- Variable shadowing ---

    @Test
    void shadowing_innerScopeDefinesSameName() {
        tree.defineVariable("x", scalarVar("x", 1));
        tree.enterScope("inner");
        tree.defineVariable("x", scalarVar("x", 2));

        // Inner scope resolves to shadowed value
        var result = tree.resolveVariable("x");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().currentValue()).isEqualTo(2);

        // Shadow recorded
        assertThat(tree.shadowHistory()).hasSize(1);
        var shadow = tree.shadowHistory().get(0);
        assertThat(shadow.variableName()).isEqualTo("x");
        assertThat(shadow.previousValue()).isEqualTo(1);
        assertThat(shadow.newValue()).isEqualTo(2);
    }

    @Test
    void shadowing_exitScopeRevealsOriginal() {
        tree.defineVariable("x", scalarVar("x", 1));
        tree.enterScope("inner");
        tree.defineVariable("x", scalarVar("x", 2));
        tree.exitScope();
        var result = tree.resolveVariable("x");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().currentValue()).isEqualTo(1);
    }

    // --- updateVariable ---

    @Test
    void updateVariable_scalarUpdate() {
        tree.defineVariable("x", scalarVar("x", 10));
        var result = tree.updateVariable("x", 20);
        assertThat(result.isSuccess()).isTrue();
        assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(20);
    }

    @Test
    void updateVariable_immutableFails() {
        tree.defineVariable("x", new ScalarVariable("x", 10, TypeDescriptor.INT, false));
        var result = tree.updateVariable("x", 20);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("IMMUTABLE_VARIABLE");
    }

    @Test
    void updateVariable_undefinedFails() {
        var result = tree.updateVariable("ghost", 0);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("UNDEFINED_VARIABLE");
    }

    @Test
    void updateVariable_walksParentChain() {
        tree.defineVariable("x", scalarVar("x", 1));
        tree.enterScope("inner");
        var result = tree.updateVariable("x", 99);
        assertThat(result.isSuccess()).isTrue();
        // After exit, the root variable should be updated
        tree.exitScope();
        assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(99);
    }

    // --- ScopePath ---

    @Test
    void scopePath_of() {
        var path = ScopePath.of("a", "b", "c");
        assertThat(path.segments()).containsExactly("a", "b", "c");
    }

    @Test
    void scopePath_append() {
        var path = ScopePath.of("a").append("b");
        assertThat(path.segments()).containsExactly("a", "b");
    }

    @Test
    void scopePath_toString() {
        assertThat(ScopePath.of("global", "block").toString()).isEqualTo("global.block");
    }

    @Test
    void scopePath_isAncestorOf() {
        var parent = ScopePath.of("global");
        var child = ScopePath.of("global", "block");
        assertThat(parent.isAncestorOf(child)).isTrue();
        assertThat(child.isAncestorOf(parent)).isFalse();
        assertThat(parent.isAncestorOf(parent)).isFalse();
    }

    @Test
    void scopePath_depth() {
        assertThat(ScopePath.of("a").depth()).isEqualTo(1);
        assertThat(ScopePath.of("a", "b", "c").depth()).isEqualTo(3);
    }

    // --- ScalarVariable ---

    @Test
    void scalarVariable_creation() {
        var v = new ScalarVariable("x", 42, TypeDescriptor.INT, true);
        assertThat(v.name()).isEqualTo("x");
        assertThat(v.currentValue()).isEqualTo(42);
        assertThat(v.type()).isEqualTo(TypeDescriptor.INT);
        assertThat(v.mutable()).isTrue();
    }

    @Test
    void scalarVariable_withValue() {
        var v = new ScalarVariable("x", 1, TypeDescriptor.INT, true);
        var v2 = v.withValue(2);
        assertThat(v2.currentValue()).isEqualTo(2);
        assertThat(v2.name()).isEqualTo("x");
        assertThat(v.currentValue()).isEqualTo(1); // original unchanged
    }

    // --- NumericIndexStore ---

    @Test
    void numericIndexStore_getSetSize() {
        var store = new NumericIndexStore();
        store.set(0, "a");
        store.set(1, "b");
        assertThat(store.get(0)).isEqualTo("a");
        assertThat(store.get(1)).isEqualTo("b");
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void numericIndexStore_autoExtend() {
        var store = new NumericIndexStore();
        store.set(5, "x");
        assertThat(store.size()).isEqualTo(6);
        assertThat(store.get(0)).isNull();
        assertThat(store.get(5)).isEqualTo("x");
    }

    @Test
    void numericIndexStore_containsKey() {
        var store = new NumericIndexStore();
        store.add("val");
        assertThat(store.containsKey(0)).isTrue();
        assertThat(store.containsKey(1)).isFalse();
    }

    @Test
    void numericIndexStore_outOfBoundsReturnsNull() {
        var store = new NumericIndexStore();
        assertThat(store.get(99)).isNull();
    }

    // --- KeyMappedStore ---

    @Test
    void keyMappedStore_getSetSize() {
        var store = new KeyMappedStore();
        store.set("a", 1);
        store.set("b", 2);
        assertThat(store.get("a")).isEqualTo(1);
        assertThat(store.get("b")).isEqualTo(2);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void keyMappedStore_keys() {
        var store = new KeyMappedStore();
        store.set("x", 1);
        store.set("y", 2);
        assertThat(store.keys()).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    void keyMappedStore_containsKey() {
        var store = new KeyMappedStore();
        store.set("a", 1);
        assertThat(store.containsKey("a")).isTrue();
        assertThat(store.containsKey("b")).isFalse();
    }

    @Test
    void keyMappedStore_nonStringKeyConverted() {
        var store = new KeyMappedStore();
        store.set(42, "val");
        assertThat(store.get("42")).isEqualTo("val");
    }

    // --- IndexedVariable ---

    @Test
    void indexedVariable_currentValueIsStore() {
        var store = new NumericIndexStore();
        store.add("a");
        var v = new IndexedVariable("arr", store, TypeDescriptor.of(PexType.ARRAY), true);
        assertThat(v.currentValue()).isSameAs(store);
        assertThat(v.name()).isEqualTo("arr");
    }

    // --- ScopeTracer ---

    @Test
    void scopeTracer_recordShadow() {
        var tracer = new ScopeTracer();
        var record = new ShadowRecord("x", ScopePath.of("inner"), ScopePath.of("global"), 1, 2);
        tracer.recordShadow(record);
        assertThat(tracer.history()).hasSize(1);
        assertThat(tracer.history().get(0)).isEqualTo(record);
    }

    @Test
    void scopeTracer_historyFor() {
        var tracer = new ScopeTracer();
        tracer.recordShadow(new ShadowRecord("x", ScopePath.of("a"), ScopePath.of("b"), 1, 2));
        tracer.recordShadow(new ShadowRecord("y", ScopePath.of("a"), ScopePath.of("b"), 3, 4));
        tracer.recordShadow(new ShadowRecord("x", ScopePath.of("c"), ScopePath.of("a"), 2, 5));
        assertThat(tracer.historyFor("x")).hasSize(2);
        assertThat(tracer.historyFor("y")).hasSize(1);
        assertThat(tracer.historyFor("z")).isEmpty();
    }

    @Test
    void scopeTracer_clear() {
        var tracer = new ScopeTracer();
        tracer.recordShadow(new ShadowRecord("x", ScopePath.of("a"), ScopePath.of("b"), 1, 2));
        tracer.clear();
        assertThat(tracer.history()).isEmpty();
    }

    // --- Multiple nested scopes ---

    @Test
    void multipleNestedScopes_variableResolution() {
        tree.defineVariable("a", scalarVar("a", 1));
        tree.enterScope("s1");
        tree.defineVariable("b", scalarVar("b", 2));
        tree.enterScope("s2");
        tree.defineVariable("c", scalarVar("c", 3));

        // All three visible from innermost scope
        assertThat(tree.resolveVariable("a").value().currentValue()).isEqualTo(1);
        assertThat(tree.resolveVariable("b").value().currentValue()).isEqualTo(2);
        assertThat(tree.resolveVariable("c").value().currentValue()).isEqualTo(3);

        tree.exitScope(); // back to s1
        assertThat(tree.resolveVariable("c").isFailure()).isTrue();
        assertThat(tree.resolveVariable("b").value().currentValue()).isEqualTo(2);

        tree.exitScope(); // back to global
        assertThat(tree.resolveVariable("b").isFailure()).isTrue();
        assertThat(tree.resolveVariable("a").value().currentValue()).isEqualTo(1);
    }

    @Test
    void currentPath_reflectsPosition() {
        assertThat(tree.currentPath().toString()).isEqualTo("global");
        tree.enterScope("fn");
        assertThat(tree.currentPath().toString()).isEqualTo("global.fn");
        tree.enterScope("loop");
        assertThat(tree.currentPath().toString()).isEqualTo("global.fn.loop");
    }

    // --- ScopeTreeListener ---

    @Test
    void listener_notifiedOnEnterAndExit() {
        var events = new ArrayList<String>();
        tree.addListener(new ScopeTreeListener() {
            @Override public void onScopeEnter(Scope scope) { events.add("enter:" + scope.name()); }
            @Override public void onScopeExit(Scope scope) { events.add("exit:" + scope.name()); }
            @Override public void onVariableDefine(Scope scope, Variable var) { events.add("def:" + var.name()); }
            @Override public void onVariableShadow(ShadowRecord record) { events.add("shadow:" + record.variableName()); }
        });

        tree.enterScope("block");
        tree.defineVariable("x", scalarVar("x", 1));
        tree.exitScope();

        assertThat(events).containsExactly("enter:block", "def:x", "exit:block");
    }

    @Test
    void listener_notifiedOnShadow() {
        var shadows = new ArrayList<String>();
        tree.addListener(new ScopeTreeListener() {
            @Override public void onScopeEnter(Scope scope) {}
            @Override public void onScopeExit(Scope scope) {}
            @Override public void onVariableDefine(Scope scope, Variable var) {}
            @Override public void onVariableShadow(ShadowRecord record) {
                shadows.add(record.variableName() + ":" + record.previousValue() + "->" + record.newValue());
            }
        });

        tree.defineVariable("x", scalarVar("x", 10));
        tree.enterScope("inner");
        tree.defineVariable("x", scalarVar("x", 20));

        assertThat(shadows).containsExactly("x:10->20");
    }

    // --- Shadow history tracks full chain ---

    @Test
    void shadowHistory_tracksFullChain() {
        tree.defineVariable("x", scalarVar("x", 1));
        tree.enterScope("s1");
        tree.defineVariable("x", scalarVar("x", 2));
        tree.enterScope("s2");
        tree.defineVariable("x", scalarVar("x", 3));

        assertThat(tree.shadowHistory()).hasSize(2);
        assertThat(tree.shadowHistory().get(0).previousValue()).isEqualTo(1);
        assertThat(tree.shadowHistory().get(0).newValue()).isEqualTo(2);
        assertThat(tree.shadowHistory().get(1).previousValue()).isEqualTo(2);
        assertThat(tree.shadowHistory().get(1).newValue()).isEqualTo(3);
    }

    // --- Scope variables map is unmodifiable ---

    @Test
    void scope_variablesMap_isUnmodifiable() {
        tree.defineVariable("x", scalarVar("x", 1));
        var vars = tree.root().variables();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> vars.put("y", scalarVar("y", 2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Scope children list is unmodifiable ---

    @Test
    void scope_childrenList_isUnmodifiable() {
        tree.enterScope("child");
        var children = tree.root().children();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> children.add(new Scope("hack", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Helper ---

    private static ScalarVariable scalarVar(String name, Object value) {
        return new ScalarVariable(name, value, TypeDescriptor.of(PexType.INT), true);
    }
}

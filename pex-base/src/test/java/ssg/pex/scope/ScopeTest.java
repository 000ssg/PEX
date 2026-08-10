package ssg.pex.scope;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.type.PexType;
import ssg.pex.type.TypeDescriptor;

class ScopeTest {

    @Test
    void rootScope() {
        Scope root = new Scope("global", null);
        assertThat(root.name()).isEqualTo("global");
        assertThat(root.parent()).isNull();
        assertThat(root.path().segments()).containsExactly("global");
    }

    @Test
    void childScope() {
        Scope root = new Scope("global", null);
        Scope child = new Scope("fn1", root);
        assertThat(child.name()).isEqualTo("fn1");
        assertThat(child.parent()).isSameAs(root);
        assertThat(child.path().segments()).containsExactly("global", "fn1");
    }

    @Test
    void nestedScopePath() {
        Scope root = new Scope("global", null);
        Scope child = new Scope("fn1", root);
        Scope grandchild = new Scope("block", child);
        assertThat(grandchild.path().segments()).containsExactly("global", "fn1", "block");
    }

    @Test
    void defineAndGetLocal() {
        Scope root = new Scope("global", null);
        Variable var = new ScalarVariable("x", 42, TypeDescriptor.of(PexType.MAP), true);
        root.define("x", var);
        assertThat(root.hasLocal("x")).isTrue();
        assertThat(root.getLocal("x")).isSameAs(var);
    }

    @Test
    void getLocalMissing() {
        Scope root = new Scope("global", null);
        assertThat(root.hasLocal("missing")).isFalse();
        assertThat(root.getLocal("missing")).isNull();
    }

    @Test
    void updateExisting() {
        Scope root = new Scope("global", null);
        Variable var1 = new ScalarVariable("x", 1, TypeDescriptor.of(PexType.MAP), true);
        Variable var2 = new ScalarVariable("x", 2, TypeDescriptor.of(PexType.MAP), true);
        root.define("x", var1);
        root.update("x", var2);
        assertThat(root.getLocal("x").currentValue()).isEqualTo(2);
    }

    @Test
    void updateUndefinedThrows() {
        Scope root = new Scope("global", null);
        Variable var = new ScalarVariable("x", 1, TypeDescriptor.of(PexType.MAP), true);
        assertThatThrownBy(() -> root.update("x", var))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not defined");
    }

    @Test
    void addChild() {
        Scope root = new Scope("global", null);
        Scope child = new Scope("fn1", root);
        root.addChild(child);
        assertThat(root.children()).containsExactly(child);
    }

    @Test
    void childrenImmutable() {
        Scope root = new Scope("global", null);
        assertThatThrownBy(() -> root.children().add(new Scope("x", root)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void variablesImmutable() {
        Scope root = new Scope("global", null);
        Variable var = new ScalarVariable("x", 1, TypeDescriptor.of(PexType.MAP), true);
        root.define("x", var);
        assertThatThrownBy(() -> root.variables().put("y", var))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

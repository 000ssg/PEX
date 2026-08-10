package ssg.pex.scope;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.type.PexType;
import ssg.pex.type.TypeDescriptor;

class VariableTest {

    @Test
    void scalarVariable() {
        ScalarVariable var = new ScalarVariable("x", 42, TypeDescriptor.of(PexType.MAP), true);
        assertThat(var.name()).isEqualTo("x");
        assertThat(var.currentValue()).isEqualTo(42);
        assertThat(var.type()).isNotNull();
        assertThat(var.mutable()).isTrue();
    }

    @Test
    void scalarWithNewValue() {
        ScalarVariable var = new ScalarVariable("x", 1, TypeDescriptor.of(PexType.MAP), true);
        ScalarVariable updated = var.withValue(2);
        assertThat(updated.currentValue()).isEqualTo(2);
        assertThat(var.currentValue()).isEqualTo(1); // original unchanged
    }

    @Test
    void indexedVariable() {
        VariableStore store = new KeyMappedStore();
        IndexedVariable var = new IndexedVariable("arr", store, TypeDescriptor.of(PexType.MAP), true);
        assertThat(var.name()).isEqualTo("arr");
        assertThat(var.currentValue()).isSameAs(store);
    }

    @Test
    void scalarIsVariable() {
        Variable var = new ScalarVariable("x", 1, TypeDescriptor.of(PexType.MAP), true);
        assertThat(var).isInstanceOf(ScalarVariable.class);
    }

    @Test
    void indexedIsVariable() {
        Variable var = new IndexedVariable("arr", new KeyMappedStore(), TypeDescriptor.of(PexType.MAP), true);
        assertThat(var).isInstanceOf(IndexedVariable.class);
    }
}

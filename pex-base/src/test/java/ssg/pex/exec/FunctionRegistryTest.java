package ssg.pex.exec;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.result.Result;

import java.util.List;
import java.util.Set;

class FunctionRegistryTest {

    private final FunctionRegistry registry = new FunctionRegistry();

    @Test
    void registerAndLookup() {
        FunctionDef def = new FunctionDef("len", List.of("x"), null, null);
        registry.register("len", def);
        assertThat(registry.lookup("len")).isPresent();
        assertThat(registry.lookup("len").get()).isSameAs(def);
    }

    @Test
    void registerNative() {
        registry.registerNative("echo", List.of("msg"), (args, ctx) -> Result.success(args.get(0)));
        assertThat(registry.hasFunction("echo")).isTrue();
        FunctionDef def = registry.lookup("echo").get();
        assertThat(def.name()).isEqualTo("echo");
        assertThat(def.paramNames()).containsExactly("msg");
        assertThat(def.isNative()).isTrue();
    }

    @Test
    void lookupMissing() {
        assertThat(registry.lookup("nonexistent")).isEmpty();
    }

    @Test
    void hasFunction() {
        registry.register("test", new FunctionDef("test", List.of(), null, null));
        assertThat(registry.hasFunction("test")).isTrue();
        assertThat(registry.hasFunction("missing")).isFalse();
    }

    @Test
    void functionNames() {
        registry.register("a", new FunctionDef("a", List.of(), null, null));
        registry.register("b", new FunctionDef("b", List.of(), null, null));
        Set<String> names = registry.functionNames();
        assertThat(names).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void functionNamesImmutable() {
        registry.register("a", new FunctionDef("a", List.of(), null, null));
        Set<String> names = registry.functionNames();
        assertThatThrownBy(() -> names.add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void overwrite() {
        FunctionDef def1 = new FunctionDef("f", List.of(), null, null);
        FunctionDef def2 = new FunctionDef("f", List.of("x"), null, null);
        registry.register("f", def1);
        registry.register("f", def2);
        assertThat(registry.lookup("f").get()).isSameAs(def2);
    }
}

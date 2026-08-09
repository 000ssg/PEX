package ssg.pex.exec;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.ast.node.AstNode;
import ssg.pex.ast.node.IntLiteral;
import ssg.pex.result.Result;

import java.util.Set;

class HandlerRegistryTest {

    private final HandlerRegistry registry = new HandlerRegistry();

    @Test
    void exactMatch() {
        NodeHandler handler = (node, ctx) -> Result.success("ok");
        registry.register(IntLiteral.class, handler);
        assertThat(registry.getHandler(IntLiteral.class)).isSameAs(handler);
    }

    @Test
    void superclassFallback() {
        NodeHandler handler = (node, ctx) -> Result.success("base");
        registry.register(AstNode.class, handler);
        // IntLiteral extends LiteralNode extends AstNode
        assertThat(registry.getHandler(IntLiteral.class)).isSameAs(handler);
    }

    @Test
    void exactOverSuperclass() {
        NodeHandler baseHandler = (node, ctx) -> Result.success("base");
        NodeHandler specificHandler = (node, ctx) -> Result.success("specific");
        registry.register(AstNode.class, baseHandler);
        registry.register(IntLiteral.class, specificHandler);
        assertThat(registry.getHandler(IntLiteral.class)).isSameAs(specificHandler);
    }

    @Test
    void noHandler() {
        assertThat(registry.getHandler(String.class)).isNull();
    }

    @Test
    void hasHandler() {
        registry.register(IntLiteral.class, (node, ctx) -> Result.success("ok"));
        assertThat(registry.hasHandler(IntLiteral.class)).isTrue();
        assertThat(registry.hasHandler(String.class)).isFalse();
    }

    @Test
    void registeredTypes() {
        registry.register(IntLiteral.class, (node, ctx) -> Result.success("ok"));
        Set<Class<?>> types = registry.registeredTypes();
        assertThat(types).contains(IntLiteral.class);
    }

    @Test
    void registeredTypesImmutable() {
        registry.register(IntLiteral.class, (node, ctx) -> Result.success("ok"));
        Set<Class<?>> types = registry.registeredTypes();
        assertThatThrownBy(() -> types.add(String.class))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void interfaceFallback() {
        NodeHandler handler = (node, ctx) -> Result.success("iface");
        registry.register(AstNode.class, handler);
        // Walk class hierarchy to AstNode
        assertThat(registry.getHandler(IntLiteral.class)).isSameAs(handler);
    }
}

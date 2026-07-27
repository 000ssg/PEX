package ssg.pex.exec;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HandlerRegistry {

    private final ConcurrentHashMap<Class<?>, NodeHandler> handlers = new ConcurrentHashMap<>();

    public void register(Class<?> nodeType, NodeHandler handler) {
        handlers.put(nodeType, handler);
    }

    public NodeHandler getHandler(Class<?> nodeType) {
        // Exact match first
        var handler = handlers.get(nodeType);
        if (handler != null) {
            return handler;
        }

        // Walk class hierarchy
        var current = nodeType.getSuperclass();
        while (current != null && current != Object.class) {
            handler = handlers.get(current);
            if (handler != null) {
                return handler;
            }
            current = current.getSuperclass();
        }

        // Walk interfaces
        for (var iface : getAllInterfaces(nodeType)) {
            handler = handlers.get(iface);
            if (handler != null) {
                return handler;
            }
        }

        return null;
    }

    public boolean hasHandler(Class<?> nodeType) {
        return getHandler(nodeType) != null;
    }

    public Set<Class<?>> registeredTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        var interfaces = ConcurrentHashMap.<Class<?>>newKeySet();
        collectInterfaces(clazz, interfaces);
        return interfaces;
    }

    private static void collectInterfaces(Class<?> clazz, Set<Class<?>> result) {
        if (clazz == null) return;
        for (var iface : clazz.getInterfaces()) {
            result.add(iface);
            collectInterfaces(iface, result);
        }
        collectInterfaces(clazz.getSuperclass(), result);
    }
}

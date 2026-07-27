package ssg.pex.exec;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionRegistry {

    private final ConcurrentHashMap<String, FunctionDef> functions = new ConcurrentHashMap<>();

    public void register(String name, FunctionDef def) {
        functions.put(name, def);
    }

    public void registerNative(String name, List<String> paramNames, NativeFunction impl) {
        functions.put(name, new FunctionDef(name, paramNames, null, impl));
    }

    public Optional<FunctionDef> lookup(String name) {
        return Optional.ofNullable(functions.get(name));
    }

    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public Set<String> functionNames() {
        return Collections.unmodifiableSet(functions.keySet());
    }
}

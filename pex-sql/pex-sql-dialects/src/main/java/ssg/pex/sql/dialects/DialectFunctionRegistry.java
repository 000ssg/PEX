package ssg.pex.sql.dialects;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Registry for dialect-specific SQL functions.
 * Each dialect registers its functions here during initialization.
 */
public class DialectFunctionRegistry {

    @FunctionalInterface
    public interface DialectFunction {
        Object apply(List<Object> args);
    }

    private final Map<String, DialectFunction> functions = new LinkedHashMap<>();

    public void register(String name, DialectFunction function) {
        functions.put(name.toUpperCase(), function);
    }

    public DialectFunction get(String name) {
        return functions.get(name.toUpperCase());
    }

    public boolean has(String name) {
        return functions.containsKey(name.toUpperCase());
    }

    public Map<String, DialectFunction> all() {
        return Map.copyOf(functions);
    }

    public Set<String> functionNames() {
        return functions.keySet();
    }
}

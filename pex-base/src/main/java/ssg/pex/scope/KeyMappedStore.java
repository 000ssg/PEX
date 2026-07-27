package ssg.pex.scope;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class KeyMappedStore implements VariableStore {

    private final LinkedHashMap<String, Object> entries = new LinkedHashMap<>();

    @Override
    public Object get(Object key) {
        return entries.get(toStringKey(key));
    }

    @Override
    public void set(Object key, Object value) {
        entries.put(toStringKey(key), value);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean containsKey(Object key) {
        return entries.containsKey(toStringKey(key));
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(entries);
    }

    private static String toStringKey(Object key) {
        if (key instanceof String s) return s;
        return String.valueOf(key);
    }
}

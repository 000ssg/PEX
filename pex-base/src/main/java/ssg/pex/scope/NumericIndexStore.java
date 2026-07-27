package ssg.pex.scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NumericIndexStore implements VariableStore {

    private final ArrayList<Object> elements = new ArrayList<>();

    @Override
    public Object get(Object key) {
        int index = toIndex(key);
        if (index < 0 || index >= elements.size()) {
            return null;
        }
        return elements.get(index);
    }

    @Override
    public void set(Object key, Object value) {
        int index = toIndex(key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Negative index: " + index);
        }
        while (elements.size() <= index) {
            elements.add(null);
        }
        elements.set(index, value);
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean containsKey(Object key) {
        int index = toIndex(key);
        return index >= 0 && index < elements.size();
    }

    public void add(Object value) {
        elements.add(value);
    }

    public List<Object> toList() {
        return Collections.unmodifiableList(elements);
    }

    private static int toIndex(Object key) {
        if (key instanceof Integer i) return i;
        if (key instanceof Long l) return l.intValue();
        if (key instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("NumericIndexStore requires integer key, got: " + key);
    }
}

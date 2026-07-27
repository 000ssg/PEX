package ssg.pex.scope;

public sealed interface VariableStore permits NumericIndexStore, KeyMappedStore {

    Object get(Object key);

    void set(Object key, Object value);

    int size();

    boolean containsKey(Object key);
}

package ssg.pex.scope;

import ssg.pex.type.TypeDescriptor;

public record IndexedVariable(String name, VariableStore store, TypeDescriptor type, boolean mutable) implements Variable {

    @Override
    public Object currentValue() {
        return store;
    }
}

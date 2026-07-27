package ssg.pex.scope;

import ssg.pex.type.TypeDescriptor;

public record ScalarVariable(String name, Object value, TypeDescriptor type, boolean mutable) implements Variable {

    @Override
    public Object currentValue() {
        return value;
    }

    public ScalarVariable withValue(Object newValue) {
        return new ScalarVariable(name, newValue, type, mutable);
    }
}

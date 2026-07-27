package ssg.pex.scope;

import ssg.pex.type.TypeDescriptor;

public sealed interface Variable permits ScalarVariable, IndexedVariable {

    String name();

    TypeDescriptor type();

    boolean mutable();

    Object currentValue();
}

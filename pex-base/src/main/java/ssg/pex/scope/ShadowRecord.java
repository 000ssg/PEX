package ssg.pex.scope;

public record ShadowRecord(
        String variableName,
        ScopePath definingScope,
        ScopePath shadowedScope,
        Object previousValue,
        Object newValue
) {}

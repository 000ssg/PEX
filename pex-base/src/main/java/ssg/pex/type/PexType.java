package ssg.pex.type;

public enum PexType {

    INT("int", Integer.class),
    LONG("long", Long.class),
    FLOAT("float", Float.class),
    DOUBLE("double", Double.class),
    STRING("string", String.class),
    BOOL("bool", Boolean.class),
    NULL("null", Void.class),
    ARRAY("array", null),
    MAP("map", null),
    FUNCTION("function", null),
    VOID("void", Void.class);

    private final String displayName;
    private final Class<?> javaType;

    PexType(String displayName, Class<?> javaType) {
        this.displayName = displayName;
        this.javaType = javaType;
    }

    public String displayName() {
        return displayName;
    }

    public Class<?> javaType() {
        return javaType;
    }

    public boolean isNumeric() {
        return this == INT || this == LONG || this == FLOAT || this == DOUBLE;
    }

    public boolean isInteger() {
        return this == INT || this == LONG;
    }

    public boolean isFloatingPoint() {
        return this == FLOAT || this == DOUBLE;
    }
}

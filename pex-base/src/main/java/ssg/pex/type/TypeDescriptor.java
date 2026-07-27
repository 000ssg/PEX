package ssg.pex.type;

import ssg.pex.util.Preconditions;

public record TypeDescriptor(PexType type, String name, Class<?> javaType) {

    public static final TypeDescriptor INT = of(PexType.INT);
    public static final TypeDescriptor LONG = of(PexType.LONG);
    public static final TypeDescriptor FLOAT = of(PexType.FLOAT);
    public static final TypeDescriptor DOUBLE = of(PexType.DOUBLE);
    public static final TypeDescriptor STRING = of(PexType.STRING);
    public static final TypeDescriptor BOOL = of(PexType.BOOL);
    public static final TypeDescriptor NULL = of(PexType.NULL);
    public static final TypeDescriptor VOID = of(PexType.VOID);

    public static TypeDescriptor of(PexType type) {
        Preconditions.requireNonNull(type, "type");
        return new TypeDescriptor(type, type.displayName(), type.javaType());
    }

    public static TypeDescriptor custom(String name, Class<?> javaType) {
        Preconditions.requireNotEmpty(name, "name");
        Preconditions.requireNonNull(javaType, "javaType");
        return new TypeDescriptor(null, name, javaType);
    }
}

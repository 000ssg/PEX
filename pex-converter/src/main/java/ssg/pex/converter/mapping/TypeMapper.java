package ssg.pex.converter.mapping;

import ssg.pex.type.PexType;

public abstract class TypeMapper {

    public abstract String mapType(PexType type);

    public String mapType(String customTypeName) {
        return customTypeName;
    }
}

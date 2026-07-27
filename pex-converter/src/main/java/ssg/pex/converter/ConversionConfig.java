package ssg.pex.converter;

public record ConversionConfig(String indentation, String lineEnding, NamingConvention namingConvention) {

    public enum NamingConvention {
        PRESERVE,
        CAMEL_CASE,
        PASCAL_CASE,
        SNAKE_CASE
    }

    public static ConversionConfig defaults() {
        return new ConversionConfig("    ", "\n", NamingConvention.PRESERVE);
    }
}

package ssg.pex.converter.mapping;

import ssg.pex.converter.ConversionConfig.NamingConvention;

import java.util.ArrayList;
import java.util.List;

public final class NamingMapper {

    private NamingMapper() {}

    public static String toCamelCase(String name) {
        var parts = splitParts(name);
        if (parts.isEmpty()) return name;
        var sb = new StringBuilder();
        sb.append(parts.getFirst().toLowerCase());
        for (int i = 1; i < parts.size(); i++) {
            sb.append(capitalize(parts.get(i)));
        }
        return sb.toString();
    }

    public static String toPascalCase(String name) {
        var parts = splitParts(name);
        if (parts.isEmpty()) return name;
        var sb = new StringBuilder();
        for (var part : parts) {
            sb.append(capitalize(part));
        }
        return sb.toString();
    }

    public static String toSnakeCase(String name) {
        var parts = splitParts(name);
        if (parts.isEmpty()) return name;
        var sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append('_');
            sb.append(parts.get(i).toLowerCase());
        }
        return sb.toString();
    }

    public static String convert(String name, NamingConvention convention) {
        return switch (convention) {
            case PRESERVE -> name;
            case CAMEL_CASE -> toCamelCase(name);
            case PASCAL_CASE -> toPascalCase(name);
            case SNAKE_CASE -> toSnakeCase(name);
        };
    }

    private static List<String> splitParts(String name) {
        if (name == null || name.isEmpty()) return List.of();

        var parts = new ArrayList<String>();

        // First split on underscores and hyphens
        var segments = name.split("[_\\-]+");
        for (var segment : segments) {
            if (segment.isEmpty()) continue;
            // Then split on camelCase boundaries
            splitCamelCase(segment, parts);
        }
        return parts;
    }

    private static void splitCamelCase(String segment, List<String> parts) {
        var sb = new StringBuilder();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (Character.isUpperCase(c) && !sb.isEmpty()) {
                parts.add(sb.toString());
                sb.setLength(0);
            }
            sb.append(c);
        }
        if (!sb.isEmpty()) {
            parts.add(sb.toString());
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}

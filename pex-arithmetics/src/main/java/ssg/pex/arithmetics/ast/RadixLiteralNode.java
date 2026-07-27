package ssg.pex.arithmetics.ast;

import ssg.pex.ast.SourceLocation;

import java.util.Map;

/**
 * Represents a numeric literal with a specific radix (hex, binary, octal).
 * <p>
 * Since {@code LiteralNode} in pex-base is sealed, this is a standalone record
 * that can be wrapped in an {@code ExtensionNode} when used in an AST.
 */
public record RadixLiteralNode(long value, int radix, String originalText,
                                SourceLocation location, Map<String, Object> metadata) {

    public RadixLiteralNode(long value, int radix, String originalText) {
        this(value, radix, originalText, SourceLocation.UNKNOWN, Map.of());
    }

    /**
     * Returns the numeric value of this literal.
     */
    public Object literalValue() {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return (int) value;
        }
        return value;
    }

    /**
     * Returns a display string showing the value in its original radix.
     */
    public String toRadixString() {
        return switch (radix) {
            case 2 -> "0b" + Long.toBinaryString(value);
            case 8 -> "0o" + Long.toOctalString(value);
            case 16 -> "0x" + Long.toHexString(value);
            default -> Long.toString(value, radix);
        };
    }

    /**
     * Parses a radix-prefixed string literal.
     *
     * @param text the text to parse (e.g., "0xFF", "0b1010", "0o77")
     * @return the parsed RadixLiteralNode
     * @throws NumberFormatException if the text cannot be parsed
     */
    public static RadixLiteralNode parse(String text) {
        if (text.startsWith("0x") || text.startsWith("0X")) {
            long val = Long.parseUnsignedLong(text.substring(2), 16);
            return new RadixLiteralNode(val, 16, text);
        }
        if (text.startsWith("0b") || text.startsWith("0B")) {
            long val = Long.parseUnsignedLong(text.substring(2), 2);
            return new RadixLiteralNode(val, 2, text);
        }
        if (text.startsWith("0o") || text.startsWith("0O")) {
            long val = Long.parseUnsignedLong(text.substring(2), 8);
            return new RadixLiteralNode(val, 8, text);
        }
        throw new NumberFormatException("Not a radix-prefixed literal: " + text);
    }
}

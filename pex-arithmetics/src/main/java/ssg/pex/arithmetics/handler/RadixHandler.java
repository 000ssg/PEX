package ssg.pex.arithmetics.handler;

import ssg.pex.arithmetics.ast.RadixLiteralNode;
import ssg.pex.ast.node.ExtensionNode;
import ssg.pex.exec.ExecutionContext;
import ssg.pex.exec.NodeHandler;
import ssg.pex.result.Result;

/**
 * Handles parsing of different radix number literals: 0x (hex), 0b (binary), 0o (octal).
 * Processes ExtensionNode with extensionType "radix_literal" wrapping a RadixLiteralNode.
 */
public final class RadixHandler implements NodeHandler {

    public static final String EXTENSION_TYPE = "radix_literal";

    @Override
    public Result<Object> handle(Object node, ExecutionContext ctx) {
        if (node instanceof ExtensionNode ext && EXTENSION_TYPE.equals(ext.extensionType())) {
            if (ext.wrappedNode() instanceof RadixLiteralNode radix) {
                return Result.success(radix.literalValue());
            }
            return Result.failure("HANDLER_ERROR",
                    "Expected RadixLiteralNode inside ExtensionNode, got " +
                    (ext.wrappedNode() == null ? "null" : ext.wrappedNode().getClass().getSimpleName()));
        }
        return Result.failure("HANDLER_ERROR",
                "RadixHandler expects ExtensionNode with type '" + EXTENSION_TYPE + "'");
    }

    /**
     * Parse a radix-prefixed string and return the numeric value.
     *
     * @param text the string to parse (e.g., "0xFF", "0b1010", "0o77")
     * @return the parsed value as int (if fits) or long
     */
    public static Result<Object> parseRadixLiteral(String text) {
        try {
            var node = RadixLiteralNode.parse(text);
            return Result.success(node.literalValue());
        } catch (NumberFormatException e) {
            return Result.failure("PARSE_ERROR", "Invalid radix literal: " + text);
        }
    }
}

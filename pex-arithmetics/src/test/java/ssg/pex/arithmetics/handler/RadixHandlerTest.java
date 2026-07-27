package ssg.pex.arithmetics.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.arithmetics.ast.RadixLiteralNode;
import ssg.pex.ast.SourceLocation;
import ssg.pex.ast.node.ExtensionNode;
import ssg.pex.ast.node.IntLiteral;
import ssg.pex.result.Result;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RadixHandler and RadixLiteralNode")
class RadixHandlerTest {

    private final RadixHandler handler = new RadixHandler();

    // --- RadixLiteralNode ---

    @Nested
    @DisplayName("RadixLiteralNode.parse")
    class ParseTests {

        @Test
        @DisplayName("parses hexadecimal literal 0xFF")
        void testParseHex() {
            var node = RadixLiteralNode.parse("0xFF");
            assertThat(node.radix()).isEqualTo(16);
            assertThat(node.value()).isEqualTo(255L);
            assertThat(node.originalText()).isEqualTo("0xFF");
        }

        @Test
        @DisplayName("parses hexadecimal with uppercase prefix 0X")
        void testParseHexUpperPrefix() {
            var node = RadixLiteralNode.parse("0X1A");
            assertThat(node.radix()).isEqualTo(16);
            assertThat(node.value()).isEqualTo(26L);
        }

        @Test
        @DisplayName("parses binary literal 0b1010")
        void testParseBinary() {
            var node = RadixLiteralNode.parse("0b1010");
            assertThat(node.radix()).isEqualTo(2);
            assertThat(node.value()).isEqualTo(10L);
        }

        @Test
        @DisplayName("parses binary with uppercase prefix 0B")
        void testParseBinaryUpperPrefix() {
            var node = RadixLiteralNode.parse("0B11111111");
            assertThat(node.radix()).isEqualTo(2);
            assertThat(node.value()).isEqualTo(255L);
        }

        @Test
        @DisplayName("parses octal literal 0o17")
        void testParseOctal() {
            var node = RadixLiteralNode.parse("0o17");
            assertThat(node.radix()).isEqualTo(8);
            assertThat(node.value()).isEqualTo(15L);
        }

        @Test
        @DisplayName("parses octal with uppercase prefix 0O")
        void testParseOctalUpperPrefix() {
            var node = RadixLiteralNode.parse("0O77");
            assertThat(node.radix()).isEqualTo(8);
            assertThat(node.value()).isEqualTo(63L);
        }

        @Test
        @DisplayName("throws NumberFormatException for non-radix text")
        void testParseInvalidThrows() {
            org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class,
                    () -> RadixLiteralNode.parse("123"));
        }
    }

    @Nested
    @DisplayName("RadixLiteralNode.literalValue")
    class LiteralValueTests {

        @Test
        @DisplayName("returns int for value within int range")
        void testReturnsIntForSmallValue() {
            var node = RadixLiteralNode.parse("0xFF");
            Object val = node.literalValue();
            assertThat(val).isInstanceOf(Integer.class);
            assertThat(val).isEqualTo(255);
        }

        @Test
        @DisplayName("returns long for value outside int range")
        void testReturnsLongForLargeValue() {
            // 0x100000000 = 4294967296 > Integer.MAX_VALUE
            var node = RadixLiteralNode.parse("0x100000000");
            Object val = node.literalValue();
            assertThat(val).isInstanceOf(Long.class);
            assertThat(val).isEqualTo(4294967296L);
        }
    }

    @Nested
    @DisplayName("RadixLiteralNode.toRadixString")
    class ToRadixStringTests {

        @Test
        @DisplayName("hex toRadixString")
        void testHexToRadixString() {
            var node = new RadixLiteralNode(255L, 16, "0xff");
            assertThat(node.toRadixString()).isEqualTo("0xff");
        }

        @Test
        @DisplayName("binary toRadixString")
        void testBinaryToRadixString() {
            var node = new RadixLiteralNode(10L, 2, "0b1010");
            assertThat(node.toRadixString()).isEqualTo("0b1010");
        }

        @Test
        @DisplayName("octal toRadixString")
        void testOctalToRadixString() {
            var node = new RadixLiteralNode(8L, 8, "0o10");
            assertThat(node.toRadixString()).isEqualTo("0o10");
        }
    }

    // --- RadixHandler ---

    @Nested
    @DisplayName("RadixHandler.parseRadixLiteral static")
    class StaticParseTests {

        @Test
        @DisplayName("successfully parses hex literal")
        void testParseHex() {
            var result = RadixHandler.parseRadixLiteral("0x10");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(16);
        }

        @Test
        @DisplayName("returns failure for invalid literal")
        void testParseInvalid() {
            var result = RadixHandler.parseRadixLiteral("not_a_number");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error().code()).isEqualTo("PARSE_ERROR");
        }
    }

    @Nested
    @DisplayName("RadixHandler.handle")
    class HandleTests {

        @Test
        @DisplayName("handles ExtensionNode with RadixLiteralNode")
        void testHandlesRadixExtensionNode() {
            var radix = new RadixLiteralNode(255L, 16, "0xFF",
                    SourceLocation.UNKNOWN, Map.of());
            var ext = new ExtensionNode(RadixHandler.EXTENSION_TYPE, radix,
                    SourceLocation.UNKNOWN, Map.of());

            var result = handler.handle(ext, null);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(255);
        }

        @Test
        @DisplayName("returns failure for ExtensionNode with wrong wrapped type")
        void testFailsForWrongWrappedType() {
            var ext = new ExtensionNode(RadixHandler.EXTENSION_TYPE,
                    new IntLiteral(42, SourceLocation.UNKNOWN, Map.of()),
                    SourceLocation.UNKNOWN, Map.of());

            var result = handler.handle(ext, null);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns failure for ExtensionNode with wrong type string")
        void testFailsForWrongExtensionType() {
            var radix = new RadixLiteralNode(10L, 2, "0b1010",
                    SourceLocation.UNKNOWN, Map.of());
            var ext = new ExtensionNode("other_type", radix,
                    SourceLocation.UNKNOWN, Map.of());

            var result = handler.handle(ext, null);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns failure for non-ExtensionNode")
        void testFailsForNonExtensionNode() {
            var result = handler.handle(new IntLiteral(1), null);
            assertThat(result.isSuccess()).isFalse();
        }
    }
}

package ssg.pex.tools.converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple tokenizer for ANTLR4 grammar (.g4) files.
 *
 * <p>Produces a list of {@link Token} records from an ANTLR4 grammar source string.
 * Handles string literals, identifiers, operators, comments ({@code //} and {@code /* ... * /}),
 * and action blocks ({@code { ... }}).
 */
public final class Antlr4Lexer {

    /**
     * Token types produced by the lexer.
     */
    public enum TokenType {
        GRAMMAR, PARSER, LEXER, FRAGMENT, RETURNS, OPTIONS, TOKENS, IMPORT, MODE,
        COLON, SEMICOLON, PIPE, STAR, PLUS, QUESTION, LPAREN, RPAREN,
        TILDE, HASH, EQUALS, PLUS_EQUALS, COMMA,
        STRING_LITERAL, IDENTIFIER, REGEX_CLASS, ACTION, ARROW, DOT,
        EOF_TOKEN
    }

    /**
     * A single token with its type, text value, and source position.
     */
    public record Token(TokenType type, String value, int line, int col) {
    }

    private String input;
    private int pos;
    private int length;
    private int line;
    private int col;

    /**
     * Tokenizes the given ANTLR4 grammar source.
     *
     * @param source the ANTLR4 grammar text
     * @return a list of tokens, ending with an {@link TokenType#EOF_TOKEN} token
     */
    public List<Token> tokenize(String source) {
        this.input = source;
        this.pos = 0;
        this.length = source.length();
        this.line = 1;
        this.col = 1;

        var tokens = new ArrayList<Token>();

        while (pos < length) {
            skipWhitespaceAndComments();
            if (pos >= length) break;

            int tokenLine = line;
            int tokenCol = col;
            char c = peek();

            switch (c) {
                case ':' -> {
                    advance();
                    tokens.add(new Token(TokenType.COLON, ":", tokenLine, tokenCol));
                }
                case ';' -> {
                    advance();
                    tokens.add(new Token(TokenType.SEMICOLON, ";", tokenLine, tokenCol));
                }
                case '|' -> {
                    advance();
                    tokens.add(new Token(TokenType.PIPE, "|", tokenLine, tokenCol));
                }
                case '*' -> {
                    advance();
                    tokens.add(new Token(TokenType.STAR, "*", tokenLine, tokenCol));
                }
                case '+' -> {
                    advance();
                    if (pos < length && peek() == '=') {
                        advance();
                        tokens.add(new Token(TokenType.PLUS_EQUALS, "+=", tokenLine, tokenCol));
                    } else {
                        tokens.add(new Token(TokenType.PLUS, "+", tokenLine, tokenCol));
                    }
                }
                case '?' -> {
                    advance();
                    tokens.add(new Token(TokenType.QUESTION, "?", tokenLine, tokenCol));
                }
                case '(' -> {
                    advance();
                    tokens.add(new Token(TokenType.LPAREN, "(", tokenLine, tokenCol));
                }
                case ')' -> {
                    advance();
                    tokens.add(new Token(TokenType.RPAREN, ")", tokenLine, tokenCol));
                }
                case '~' -> {
                    advance();
                    tokens.add(new Token(TokenType.TILDE, "~", tokenLine, tokenCol));
                }
                case '#' -> {
                    advance();
                    tokens.add(new Token(TokenType.HASH, "#", tokenLine, tokenCol));
                }
                case '=' -> {
                    advance();
                    tokens.add(new Token(TokenType.EQUALS, "=", tokenLine, tokenCol));
                }
                case ',' -> {
                    advance();
                    tokens.add(new Token(TokenType.COMMA, ",", tokenLine, tokenCol));
                }
                case '.' -> {
                    advance();
                    tokens.add(new Token(TokenType.DOT, ".", tokenLine, tokenCol));
                }
                case '-' -> {
                    if (pos + 1 < length && input.charAt(pos + 1) == '>') {
                        advance();
                        advance();
                        tokens.add(new Token(TokenType.ARROW, "->", tokenLine, tokenCol));
                    } else {
                        // Unexpected character; skip
                        advance();
                    }
                }
                case '\'' -> tokens.add(readStringLiteral(tokenLine, tokenCol));
                case '[' -> tokens.add(readRegexClass(tokenLine, tokenCol));
                case '{' -> tokens.add(readActionBlock(tokenLine, tokenCol));
                default -> {
                    if (isIdentifierStart(c)) {
                        tokens.add(readIdentifierOrKeyword(tokenLine, tokenCol));
                    } else {
                        // Skip unknown characters
                        advance();
                    }
                }
            }
        }

        tokens.add(new Token(TokenType.EOF_TOKEN, "", line, col));
        return tokens;
    }

    private Token readStringLiteral(int tokenLine, int tokenCol) {
        advance(); // skip opening quote
        var sb = new StringBuilder();
        while (pos < length && peek() != '\'') {
            if (peek() == '\\') {
                sb.append(peek());
                advance();
                if (pos < length) {
                    sb.append(peek());
                    advance();
                }
            } else {
                sb.append(peek());
                advance();
            }
        }
        if (pos < length) advance(); // skip closing quote
        return new Token(TokenType.STRING_LITERAL, sb.toString(), tokenLine, tokenCol);
    }

    private Token readRegexClass(int tokenLine, int tokenCol) {
        advance(); // skip [
        var sb = new StringBuilder();
        sb.append('[');
        while (pos < length && peek() != ']') {
            if (peek() == '\\') {
                sb.append(peek());
                advance();
                if (pos < length) {
                    sb.append(peek());
                    advance();
                }
            } else {
                sb.append(peek());
                advance();
            }
        }
        if (pos < length) {
            sb.append(']');
            advance(); // skip ]
        }
        return new Token(TokenType.REGEX_CLASS, sb.toString(), tokenLine, tokenCol);
    }

    private Token readActionBlock(int tokenLine, int tokenCol) {
        advance(); // skip {
        var sb = new StringBuilder();
        int depth = 1;
        while (pos < length && depth > 0) {
            char c = peek();
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    advance();
                    break;
                }
            }
            sb.append(c);
            advance();
        }
        return new Token(TokenType.ACTION, sb.toString(), tokenLine, tokenCol);
    }

    private Token readIdentifierOrKeyword(int tokenLine, int tokenCol) {
        int start = pos;
        while (pos < length && isIdentifierPart(peek())) {
            advance();
        }
        String text = input.substring(start, pos);
        TokenType type = switch (text) {
            case "grammar" -> TokenType.GRAMMAR;
            case "parser" -> TokenType.PARSER;
            case "lexer" -> TokenType.LEXER;
            case "fragment" -> TokenType.FRAGMENT;
            case "returns" -> TokenType.RETURNS;
            case "options" -> TokenType.OPTIONS;
            case "tokens" -> TokenType.TOKENS;
            case "import" -> TokenType.IMPORT;
            case "mode" -> TokenType.MODE;
            default -> TokenType.IDENTIFIER;
        };
        return new Token(type, text, tokenLine, tokenCol);
    }

    private void skipWhitespaceAndComments() {
        while (pos < length) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '/' && pos + 1 < length && input.charAt(pos + 1) == '/') {
                // Line comment
                while (pos < length && peek() != '\n') advance();
            } else if (c == '/' && pos + 1 < length && input.charAt(pos + 1) == '*') {
                // Block comment
                advance(); // /
                advance(); // *
                while (pos + 1 < length && !(peek() == '*' && input.charAt(pos + 1) == '/')) {
                    advance();
                }
                if (pos + 1 < length) {
                    advance(); // *
                    advance(); // /
                }
            } else {
                break;
            }
        }
    }

    private char peek() {
        return input.charAt(pos);
    }

    private void advance() {
        if (pos < length) {
            if (input.charAt(pos) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}

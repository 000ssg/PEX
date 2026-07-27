package ssg.pex.bnf.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.RuleExpression;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.result.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses BNF/EBNF text notation into a {@link Grammar} model.
 *
 * <p>Supported syntax:
 * <ul>
 *   <li>Grammar declaration: {@code grammar name;}</li>
 *   <li>Rules: {@code ruleName ::= expression ;} or {@code ruleName = expression ;}</li>
 *   <li>Alternation: {@code a | b}</li>
 *   <li>Sequence: {@code a b c}</li>
 *   <li>Repetition: {@code a*}, {@code a+}, {@code a?}</li>
 *   <li>Grouping: {@code ( a b )}</li>
 *   <li>String terminals: {@code 'literal'} or {@code "literal"}</li>
 *   <li>Regex terminals: {@code /regex/}</li>
 *   <li>Non-terminals: bare rule name references</li>
 *   <li>Annotations: {@code @name} or {@code @name(value)} before a rule</li>
 *   <li>Comments: {@code //} to end of line, {@code /* ... * /} block</li>
 * </ul>
 */
public final class BnfParser {

    private static final Logger log = LoggerFactory.getLogger(BnfParser.class);

    private String input;
    private int pos;
    private int length;

    public Result<Grammar> parse(String source) {
        this.input = source;
        this.pos = 0;
        this.length = source.length();

        try {
            skipWhitespaceAndComments();

            // Parse optional grammar declaration
            String grammarName = "unnamed";
            if (lookingAt("grammar")) {
                consume("grammar");
                skipWhitespaceAndComments();
                grammarName = parseIdentifier();
                skipWhitespaceAndComments();
                expect(';');
                skipWhitespaceAndComments();
            }

            // Parse rules
            var rules = new LinkedHashMap<String, Rule>();
            String firstRuleName = null;

            while (pos < length) {
                skipWhitespaceAndComments();
                if (pos >= length) break;

                // Collect annotations
                var annotations = new LinkedHashMap<String, String>();
                while (pos < length && peek() == '@') {
                    parseAnnotation(annotations);
                    skipWhitespaceAndComments();
                }

                if (pos >= length) break;

                // Parse rule name
                var ruleName = parseIdentifier();
                if (firstRuleName == null) {
                    firstRuleName = ruleName;
                }
                skipWhitespaceAndComments();

                // Parse ::= or =
                if (lookingAt("::=")) {
                    consume("::=");
                } else {
                    expect('=');
                }
                skipWhitespaceAndComments();

                // Parse rule body
                var body = parseAlternation();
                skipWhitespaceAndComments();
                expect(';');
                skipWhitespaceAndComments();

                rules.put(ruleName, new Rule(ruleName, body, Map.copyOf(annotations)));
                log.trace("Parsed rule: {}", ruleName);
            }

            if (rules.isEmpty()) {
                return Result.failure("BNF_EMPTY", "No rules found in grammar");
            }

            var grammar = new Grammar(grammarName, rules, firstRuleName);
            log.debug("Parsed grammar '{}' with {} rules, start='{}'",
                    grammarName, rules.size(), firstRuleName);
            return Result.success(grammar);

        } catch (BnfParseException e) {
            return Result.failure("BNF_PARSE_ERROR", e.getMessage());
        } catch (Exception e) {
            return Result.failure("BNF_PARSE_ERROR", "Unexpected error: " + e.getMessage(), e);
        }
    }

    // ---- Expression parsing (precedence: alternation < sequence < postfix < atom) ----

    private RuleExpression parseAlternation() {
        var first = parseSequence();
        if (pos < length && peek() == '|') {
            var alternatives = new ArrayList<RuleExpression>();
            alternatives.add(first);
            while (pos < length && peek() == '|') {
                advance(); // skip '|'
                skipWhitespaceAndComments();
                alternatives.add(parseSequence());
            }
            return new Alternation(alternatives);
        }
        return first;
    }

    private RuleExpression parseSequence() {
        var elements = new ArrayList<RuleExpression>();
        elements.add(parsePostfix());

        while (pos < length && !isSequenceTerminator(peek())) {
            skipWhitespaceAndComments();
            if (pos >= length || isSequenceTerminator(peek())) break;
            elements.add(parsePostfix());
        }

        return elements.size() == 1 ? elements.getFirst() : new Sequence(elements);
    }

    private boolean isSequenceTerminator(char c) {
        return c == '|' || c == ')' || c == ';';
    }

    private RuleExpression parsePostfix() {
        var expr = parseAtom();
        skipWhitespaceAndComments();

        if (pos < length) {
            switch (peek()) {
                case '*' -> { advance(); return new Repetition(expr, RepetitionKind.ZERO_OR_MORE); }
                case '+' -> { advance(); return new Repetition(expr, RepetitionKind.ONE_OR_MORE); }
                case '?' -> { advance(); return new Repetition(expr, RepetitionKind.OPTIONAL); }
            }
        }
        return expr;
    }

    private RuleExpression parseAtom() {
        skipWhitespaceAndComments();
        if (pos >= length) {
            throw new BnfParseException("Unexpected end of input at position " + pos);
        }

        char c = peek();

        // Parenthesized group
        if (c == '(') {
            advance();
            skipWhitespaceAndComments();
            var inner = parseAlternation();
            skipWhitespaceAndComments();
            expect(')');
            return new Group(inner);
        }

        // String terminal with single quotes
        if (c == '\'') {
            return Terminal.literal(parseQuotedString('\''));
        }

        // String terminal with double quotes
        if (c == '"') {
            return Terminal.literal(parseQuotedString('"'));
        }

        // Regex terminal
        if (c == '/') {
            return Terminal.regex(parseRegex());
        }

        // Non-terminal (identifier)
        if (isIdentifierStart(c)) {
            var name = parseIdentifier();
            return new NonTerminal(name);
        }

        throw new BnfParseException(
                "Unexpected character '%c' at position %d".formatted(c, pos));
    }

    // ---- Lexical helpers ----

    private String parseIdentifier() {
        int start = pos;
        if (pos >= length || !isIdentifierStart(peek())) {
            throw new BnfParseException("Expected identifier at position " + pos);
        }
        while (pos < length && isIdentifierPart(peek())) {
            advance();
        }
        return input.substring(start, pos);
    }

    private String parseQuotedString(char quote) {
        expect(quote);
        var sb = new StringBuilder();
        while (pos < length && peek() != quote) {
            if (peek() == '\\') {
                advance(); // skip backslash
                if (pos >= length) {
                    throw new BnfParseException("Unterminated escape sequence at position " + pos);
                }
                sb.append(unescapeChar(peek()));
            } else {
                sb.append(peek());
            }
            advance();
        }
        expect(quote);
        return sb.toString();
    }

    private String parseRegex() {
        expect('/');
        var sb = new StringBuilder();
        while (pos < length && peek() != '/') {
            if (peek() == '\\') {
                sb.append(peek());
                advance();
                if (pos >= length) {
                    throw new BnfParseException("Unterminated regex escape at position " + pos);
                }
            }
            sb.append(peek());
            advance();
        }
        expect('/');
        return sb.toString();
    }

    private void parseAnnotation(Map<String, String> annotations) {
        expect('@');
        var name = parseIdentifier();
        var value = "true"; // default for boolean annotations
        skipWhitespaceAndComments();
        if (pos < length && peek() == '(') {
            advance();
            int start = pos;
            int depth = 1;
            while (pos < length && depth > 0) {
                if (peek() == '(') depth++;
                else if (peek() == ')') depth--;
                if (depth > 0) advance();
            }
            value = input.substring(start, pos);
            expect(')');
        }
        annotations.put(name, value);
    }

    private void skipWhitespaceAndComments() {
        while (pos < length) {
            if (Character.isWhitespace(peek())) {
                advance();
            } else if (pos + 1 < length && peek() == '/' && input.charAt(pos + 1) == '/') {
                // Line comment
                while (pos < length && peek() != '\n') {
                    advance();
                }
            } else if (pos + 1 < length && peek() == '/' && input.charAt(pos + 1) == '*') {
                // Block comment
                advance(); // skip /
                advance(); // skip *
                while (pos + 1 < length && !(peek() == '*' && input.charAt(pos + 1) == '/')) {
                    advance();
                }
                if (pos + 1 < length) {
                    advance(); // skip *
                    advance(); // skip /
                }
            } else {
                break;
            }
        }
    }

    private boolean lookingAt(String keyword) {
        if (pos + keyword.length() > length) return false;
        if (!input.startsWith(keyword, pos)) return false;
        // Ensure the keyword is not a prefix of a longer identifier
        int end = pos + keyword.length();
        return end >= length || !isIdentifierPart(input.charAt(end));
    }

    private void consume(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (pos >= length || peek() != text.charAt(i)) {
                throw new BnfParseException(
                        "Expected '%s' at position %d".formatted(text, pos));
            }
            advance();
        }
    }

    private void expect(char expected) {
        if (pos >= length) {
            throw new BnfParseException(
                    "Expected '%c' but reached end of input".formatted(expected));
        }
        if (peek() != expected) {
            throw new BnfParseException(
                    "Expected '%c' but found '%c' at position %d".formatted(expected, peek(), pos));
        }
        advance();
    }

    private char peek() {
        return input.charAt(pos);
    }

    private void advance() {
        pos++;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static char unescapeChar(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            default -> c;
        };
    }

    private static final class BnfParseException extends RuntimeException {
        BnfParseException(String message) {
            super(message);
        }
    }
}

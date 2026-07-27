package ssg.pex.tools.converter.antlr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.result.Result;
import ssg.pex.tools.converter.Antlr4Lexer;
import ssg.pex.tools.converter.Antlr4Lexer.Token;
import ssg.pex.tools.converter.Antlr4Lexer.TokenType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses ANTLR4 grammar source into a native {@link AntlrGrammar} model,
 * preserving all ANTLR-specific constructs (predicates, actions, negation,
 * labels, lexer commands, modes, fragments).
 *
 * <p>Uses {@link Antlr4Lexer} to tokenize the input, then builds the model
 * from the token stream. Unlike {@code AntlrToBnfConverter}, this parser
 * retains all information rather than converting to BNF.
 */
public final class AntlrGrammarParser {

    private static final Logger log = LoggerFactory.getLogger(AntlrGrammarParser.class);

    private List<Token> tokens;
    private int pos;

    /**
     * Parses ANTLR4 grammar source into an {@link AntlrGrammar}.
     *
     * @param source the ANTLR4 grammar text
     * @return a result containing the parsed grammar
     */
    public Result<AntlrGrammar> parse(String source) {
        try {
            var lexer = new Antlr4Lexer();
            this.tokens = lexer.tokenize(source);
            this.pos = 0;

            // Parse grammar declaration
            AntlrGrammar.GrammarKind grammarKind = AntlrGrammar.GrammarKind.COMBINED;
            if (currentType() == TokenType.LEXER) {
                grammarKind = AntlrGrammar.GrammarKind.LEXER;
                advance();
            } else if (currentType() == TokenType.PARSER) {
                grammarKind = AntlrGrammar.GrammarKind.PARSER;
                advance();
            }

            if (currentType() != TokenType.GRAMMAR) {
                return Result.failure("ANTLR_PARSE",
                        "Expected 'grammar' declaration at line %d".formatted(current().line()));
            }
            advance(); // skip 'grammar'

            if (currentType() != TokenType.IDENTIFIER) {
                return Result.failure("ANTLR_PARSE",
                        "Expected grammar name at line %d".formatted(current().line()));
            }
            String grammarName = current().value();
            advance();

            if (currentType() == TokenType.SEMICOLON) advance();

            // Parse optional sections and rules
            var options = new LinkedHashMap<String, String>();
            var imports = new ArrayList<String>();
            var rules = new ArrayList<AntlrRule>();
            var modes = new LinkedHashMap<String, List<AntlrRule>>();

            while (!isAtEnd()) {
                if (currentType() == TokenType.OPTIONS) {
                    parseOptionsBlock(options);
                    continue;
                }
                if (currentType() == TokenType.TOKENS) {
                    skipTokensBlock();
                    continue;
                }
                if (currentType() == TokenType.IMPORT) {
                    parseImportDeclaration(imports);
                    continue;
                }
                if (currentType() == TokenType.MODE) {
                    parseModeBlock(modes);
                    continue;
                }
                // Skip @ directives (header, members)
                if (currentType() == TokenType.IDENTIFIER && current().value().startsWith("@")) {
                    advance();
                    if (currentType() == TokenType.ACTION) advance();
                    continue;
                }

                // Try to parse a rule
                AntlrRule rule = tryParseRule();
                if (rule != null) {
                    rules.add(rule);
                    log.trace("Parsed ANTLR rule: {} ({})", rule.name(), rule.kind());
                } else {
                    // Skip unknown token
                    advance();
                }
            }

            if (rules.isEmpty() && modes.isEmpty()) {
                return Result.failure("ANTLR_PARSE_EMPTY", "No rules found in ANTLR grammar");
            }

            var grammar = new AntlrGrammar(grammarName, grammarKind, rules, options, imports, modes);
            log.debug("Parsed ANTLR grammar '{}' ({}) with {} rules, {} modes",
                    grammarName, grammarKind, rules.size(), modes.size());
            return Result.success(grammar);

        } catch (ParseException e) {
            return Result.failure("ANTLR_PARSE", e.getMessage());
        } catch (Exception e) {
            return Result.failure("ANTLR_PARSE_ERROR",
                    "Failed to parse ANTLR grammar: " + e.getMessage(), e);
        }
    }

    // ---- Top-level block parsing ----

    private void parseOptionsBlock(Map<String, String> options) {
        advance(); // skip 'options'
        // Options may be in an action block: options { key = value; ... }
        if (currentType() == TokenType.ACTION) {
            String body = current().value();
            advance();
            // Parse key=value pairs from the action block body
            parseOptionEntries(body, options);
        }
        if (currentType() == TokenType.SEMICOLON) advance();
    }

    private void parseOptionEntries(String body, Map<String, String> options) {
        // Simple parsing of "key = value ;" entries within the options block
        String[] parts = body.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                String key = trimmed.substring(0, eqIdx).trim();
                String value = trimmed.substring(eqIdx + 1).trim();
                options.put(key, value);
            }
        }
    }

    private void skipTokensBlock() {
        advance(); // skip 'tokens'
        if (currentType() == TokenType.ACTION) advance();
        if (currentType() == TokenType.SEMICOLON) advance();
    }

    private void parseImportDeclaration(List<String> imports) {
        advance(); // skip 'import'
        while (!isAtEnd() && currentType() != TokenType.SEMICOLON) {
            if (currentType() == TokenType.IDENTIFIER) {
                imports.add(current().value());
            }
            advance();
        }
        if (currentType() == TokenType.SEMICOLON) advance();
    }

    private void parseModeBlock(Map<String, List<AntlrRule>> modes) {
        advance(); // skip 'mode'
        if (currentType() != TokenType.IDENTIFIER) return;
        String modeName = current().value();
        advance();
        if (currentType() == TokenType.SEMICOLON) advance();

        var modeRules = new ArrayList<AntlrRule>();
        // Parse rules until next mode declaration, or end of file
        while (!isAtEnd() && currentType() != TokenType.MODE) {
            // Skip @ directives
            if (currentType() == TokenType.IDENTIFIER && current().value().startsWith("@")) {
                advance();
                if (currentType() == TokenType.ACTION) advance();
                continue;
            }
            AntlrRule rule = tryParseRule();
            if (rule != null) {
                modeRules.add(rule);
            } else {
                break;
            }
        }
        modes.put(modeName, modeRules);
    }

    // ---- Rule parsing ----

    private AntlrRule tryParseRule() {
        boolean isFragment = false;
        if (currentType() == TokenType.FRAGMENT) {
            isFragment = true;
            advance();
        }

        if (currentType() != TokenType.IDENTIFIER) return null;

        String ruleName = current().value();
        int savedPos = pos;
        advance();

        // Skip optional 'returns' clause
        if (currentType() == TokenType.RETURNS) {
            advance();
            // Skip the returns type in brackets like [Type x]
            // The lexer may tokenize the brackets as part of REGEX_CLASS or we skip until COLON
            while (!isAtEnd() && currentType() != TokenType.COLON) {
                advance();
            }
        }

        if (currentType() != TokenType.COLON) {
            // Not a rule, restore position
            pos = savedPos;
            if (isFragment) pos--; // also restore fragment keyword position
            return null;
        }
        advance(); // skip colon

        boolean isLexerRule = !isFragment && Character.isUpperCase(ruleName.charAt(0));
        AntlrRule.RuleKind kind = isFragment ? AntlrRule.RuleKind.FRAGMENT
                : isLexerRule ? AntlrRule.RuleKind.LEXER
                : AntlrRule.RuleKind.PARSER;

        // Parse rule body with alt labels
        var altLabels = new ArrayList<String>();
        AntlrExpression body = parseAlternationWithLabels(altLabels);

        // Parse optional lexer commands: -> command(args), command, ...
        var commands = new ArrayList<String>();
        if (currentType() == TokenType.ARROW) {
            advance(); // skip ->
            parseLexerCommands(commands);
        }

        if (currentType() == TokenType.SEMICOLON) advance();

        return new AntlrRule(ruleName, body, kind, altLabels, commands);
    }

    private void parseLexerCommands(List<String> commands) {
        while (!isAtEnd() && currentType() != TokenType.SEMICOLON) {
            if (currentType() == TokenType.IDENTIFIER) {
                commands.add(current().value());
                advance();
                // Handle command with parenthesized argument: channel(HIDDEN)
                if (currentType() == TokenType.LPAREN) {
                    advance(); // skip (
                    while (!isAtEnd() && currentType() != TokenType.RPAREN) {
                        commands.add(current().value());
                        advance();
                    }
                    if (currentType() == TokenType.RPAREN) advance();
                }
            } else if (currentType() == TokenType.COMMA) {
                advance(); // skip comma between commands
            } else {
                break;
            }
        }
    }

    // ---- Expression parsing: alternation < sequence < postfix < unary < atom ----

    private AntlrExpression parseAlternationWithLabels(List<String> altLabels) {
        var first = parseSequence();
        // Check for alt label after first alternative
        String label = tryParseAltLabel();
        if (label != null) altLabels.add(label);

        if (!isAtEnd() && currentType() == TokenType.PIPE) {
            var alternatives = new ArrayList<AntlrExpression>();
            alternatives.add(first);
            while (!isAtEnd() && currentType() == TokenType.PIPE) {
                advance(); // skip |
                alternatives.add(parseSequence());
                String nextLabel = tryParseAltLabel();
                if (nextLabel != null) altLabels.add(nextLabel);
            }
            return new AntlrExpression.Alt(alternatives);
        }
        return first;
    }

    private String tryParseAltLabel() {
        if (!isAtEnd() && currentType() == TokenType.HASH) {
            advance(); // skip #
            if (currentType() == TokenType.IDENTIFIER) {
                String label = current().value();
                advance();
                return label;
            }
        }
        return null;
    }

    private AntlrExpression parseAlternation() {
        var first = parseSequence();
        if (!isAtEnd() && currentType() == TokenType.PIPE) {
            var alternatives = new ArrayList<AntlrExpression>();
            alternatives.add(first);
            while (!isAtEnd() && currentType() == TokenType.PIPE) {
                advance(); // skip |
                alternatives.add(parseSequence());
            }
            return new AntlrExpression.Alt(alternatives);
        }
        return first;
    }

    private AntlrExpression parseSequence() {
        var elements = new ArrayList<AntlrExpression>();
        elements.add(parsePostfix());

        while (!isAtEnd() && isAtomStart()) {
            elements.add(parsePostfix());
        }

        return elements.size() == 1 ? elements.getFirst() : new AntlrExpression.Seq(elements);
    }

    private boolean isAtomStart() {
        if (isAtEnd()) return false;
        return switch (currentType()) {
            case STRING_LITERAL, IDENTIFIER, LPAREN, REGEX_CLASS, DOT, ACTION, TILDE -> true;
            default -> false;
        };
    }

    private AntlrExpression parsePostfix() {
        var expr = parseUnary();
        while (!isAtEnd()) {
            if (currentType() == TokenType.STAR) {
                advance();
                expr = new AntlrExpression.ZeroOrMore(expr);
            } else if (currentType() == TokenType.PLUS) {
                advance();
                expr = new AntlrExpression.OneOrMore(expr);
            } else if (currentType() == TokenType.QUESTION) {
                advance();
                expr = new AntlrExpression.Optional(expr);
            } else {
                break;
            }
        }
        return expr;
    }

    private AntlrExpression parseUnary() {
        if (currentType() == TokenType.TILDE) {
            advance(); // skip ~
            var inner = parseAtom();
            return new AntlrExpression.Negation(inner);
        }
        return parseLabeledOrAtom();
    }

    private AntlrExpression parseLabeledOrAtom() {
        // Check for label=expr or label+=expr patterns
        // A label is an identifier followed by = or +=
        if (currentType() == TokenType.IDENTIFIER) {
            int savedPos = pos;
            String labelName = current().value();
            advance();

            if (currentType() == TokenType.EQUALS) {
                advance(); // skip =
                var element = parsePostfix();
                return new AntlrExpression.LabeledElement(labelName, false, element);
            } else if (currentType() == TokenType.PLUS_EQUALS) {
                advance(); // skip +=
                var element = parsePostfix();
                return new AntlrExpression.LabeledElement(labelName, true, element);
            } else {
                // Not a label, restore and parse as atom
                pos = savedPos;
            }
        }
        return parseAtom();
    }

    private AntlrExpression parseAtom() {
        if (isAtEnd()) {
            throw new ParseException("Unexpected end of tokens");
        }

        return switch (currentType()) {
            case STRING_LITERAL -> {
                String value = current().value();
                advance();
                yield new AntlrExpression.Literal(value);
            }
            case REGEX_CLASS -> {
                String pattern = current().value();
                advance();
                yield new AntlrExpression.CharClass(pattern);
            }
            case DOT -> {
                advance();
                yield new AntlrExpression.Dot();
            }
            case IDENTIFIER -> {
                String name = current().value();
                advance();
                if (Character.isUpperCase(name.charAt(0))) {
                    yield new AntlrExpression.TokenRef(name);
                }
                yield new AntlrExpression.RuleRef(name);
            }
            case LPAREN -> {
                advance(); // skip (
                var inner = parseAlternation();
                if (currentType() == TokenType.RPAREN) {
                    advance();
                }
                yield new AntlrExpression.Group(inner);
            }
            case ACTION -> {
                String code = current().value();
                advance();
                // Check if this is a predicate ({code}?)
                if (!isAtEnd() && currentType() == TokenType.QUESTION) {
                    advance(); // skip ?
                    yield new AntlrExpression.Predicate(code);
                }
                yield new AntlrExpression.Action(code);
            }
            default -> throw new ParseException(
                    "Unexpected token %s '%s' at line %d col %d"
                            .formatted(currentType(), current().value(), current().line(), current().col()));
        };
    }

    // ---- Token stream helpers ----

    private Token current() {
        return tokens.get(pos);
    }

    private TokenType currentType() {
        return tokens.get(pos).type();
    }

    private void advance() {
        if (pos < tokens.size() - 1) {
            pos++;
        }
    }

    private boolean isAtEnd() {
        return pos >= tokens.size() || currentType() == TokenType.EOF_TOKEN;
    }

    private static final class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }
}

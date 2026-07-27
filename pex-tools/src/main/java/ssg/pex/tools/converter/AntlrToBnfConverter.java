package ssg.pex.tools.converter;

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
import ssg.pex.tools.converter.Antlr4Lexer.Token;
import ssg.pex.tools.converter.Antlr4Lexer.TokenType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts ANTLR4 .g4 grammar text into a PEX {@link Grammar} model.
 *
 * <p>Uses {@link Antlr4Lexer} to tokenize the input, then parses the token stream
 * into Grammar rules. Unsupported ANTLR4 features (predicates, actions, commands,
 * options blocks, header blocks, lexer modes) generate warnings and are skipped.
 */
public final class AntlrToBnfConverter {

    private static final Logger log = LoggerFactory.getLogger(AntlrToBnfConverter.class);

    private List<Token> tokens;
    private int pos;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, String> lexerRules = new LinkedHashMap<>();

    /**
     * Converts an ANTLR4 grammar source string into a PEX Grammar.
     *
     * @param antlrSource the ANTLR4 grammar text
     * @return a result containing the PEX Grammar and any warnings
     */
    public Result<ConversionResult> convert(String antlrSource) {
        warnings.clear();
        lexerRules.clear();

        try {
            var lexer = new Antlr4Lexer();
            this.tokens = lexer.tokenize(antlrSource);
            this.pos = 0;

            // Parse grammar declaration
            String grammarName = parseGrammarDeclaration();

            // First pass: collect all lexer rules (uppercase names)
            int savedPos = pos;
            collectLexerRules();
            pos = savedPos;

            // Second pass: parse all rules
            var rules = new LinkedHashMap<String, Rule>();
            String firstRuleName = null;

            while (!isAtEnd()) {
                skipUnsupportedBlocks();
                if (isAtEnd()) break;

                // Skip fragment keyword
                boolean isFragment = false;
                if (currentType() == TokenType.FRAGMENT) {
                    isFragment = true;
                    advance();
                }

                if (currentType() != TokenType.IDENTIFIER) {
                    advance();
                    continue;
                }

                String ruleName = current().value();
                boolean isLexerRule = Character.isUpperCase(ruleName.charAt(0));
                advance();

                if (currentType() != TokenType.COLON) {
                    continue;
                }
                advance(); // skip colon

                if (isLexerRule || isFragment) {
                    // Skip lexer rule body (already collected)
                    skipUntilSemicolon();
                    continue;
                }

                // Parse parser rule body
                RuleExpression body = parseAlternation();

                // Skip arrow commands if present
                skipArrowCommands();

                expectSemicolon();

                if (firstRuleName == null) {
                    firstRuleName = ruleName;
                }
                rules.put(ruleName, new Rule(ruleName, body));
                log.trace("Parsed ANTLR rule: {}", ruleName);
            }

            if (rules.isEmpty()) {
                return Result.failure("CONV_ANTLR_EMPTY", "No parser rules found in ANTLR grammar");
            }

            var grammar = new Grammar(grammarName, rules, firstRuleName);
            log.debug("Converted ANTLR grammar '{}' to PEX with {} rules, {} warnings",
                    grammarName, rules.size(), warnings.size());
            return Result.success(ConversionResult.ofGrammar(grammar, warnings));

        } catch (AntlrParseException e) {
            return Result.failure("CONV_ANTLR_PARSE", e.getMessage());
        } catch (Exception e) {
            return Result.failure("CONV_ANTLR_ERROR", "Failed to convert ANTLR grammar: " + e.getMessage(), e);
        }
    }

    private String parseGrammarDeclaration() {
        // Optionally skip 'lexer' or 'parser' keyword
        if (currentType() == TokenType.LEXER || currentType() == TokenType.PARSER) {
            advance();
        }

        if (currentType() != TokenType.GRAMMAR) {
            throw new AntlrParseException("Expected 'grammar' declaration at line %d".formatted(current().line()));
        }
        advance(); // skip 'grammar'

        if (currentType() != TokenType.IDENTIFIER) {
            throw new AntlrParseException("Expected grammar name at line %d".formatted(current().line()));
        }
        String name = current().value();
        advance();

        if (currentType() == TokenType.SEMICOLON) {
            advance();
        }

        return name;
    }

    private void collectLexerRules() {
        while (!isAtEnd()) {
            skipUnsupportedBlocks();
            if (isAtEnd()) break;

            boolean isFragment = false;
            if (currentType() == TokenType.FRAGMENT) {
                isFragment = true;
                advance();
            }

            if (currentType() != TokenType.IDENTIFIER) {
                advance();
                continue;
            }

            String name = current().value();
            boolean isLexerRule = Character.isUpperCase(name.charAt(0));
            advance();

            if (currentType() != TokenType.COLON) {
                continue;
            }
            advance(); // skip colon

            if (isLexerRule || isFragment) {
                // Collect the lexer rule body text
                var bodyTokens = new ArrayList<String>();
                while (!isAtEnd() && currentType() != TokenType.SEMICOLON) {
                    if (currentType() == TokenType.ARROW) {
                        // Skip -> commands
                        skipArrowCommands();
                        break;
                    }
                    bodyTokens.add(current().value());
                    advance();
                }
                if (currentType() == TokenType.SEMICOLON) advance();
                lexerRules.put(name, String.join(" ", bodyTokens));
            } else {
                skipUntilSemicolon();
            }
        }
    }

    private void skipUnsupportedBlocks() {
        while (!isAtEnd()) {
            if (currentType() == TokenType.OPTIONS) {
                warnings.add("Skipping 'options' block at line %d".formatted(current().line()));
                advance();
                if (currentType() == TokenType.ACTION) advance(); // skip { ... }
                if (currentType() == TokenType.SEMICOLON) advance();
                continue;
            }
            if (currentType() == TokenType.TOKENS) {
                warnings.add("Skipping 'tokens' block at line %d".formatted(current().line()));
                advance();
                if (currentType() == TokenType.ACTION) advance();
                if (currentType() == TokenType.SEMICOLON) advance();
                continue;
            }
            if (currentType() == TokenType.IDENTIFIER && current().value().startsWith("@")) {
                warnings.add("Skipping '@' directive at line %d".formatted(current().line()));
                advance();
                if (currentType() == TokenType.ACTION) advance();
                continue;
            }
            break;
        }
    }

    // ---- Expression parsing (alternation < sequence < postfix < atom) ----

    private RuleExpression parseAlternation() {
        var first = parseSequence();
        if (!isAtEnd() && currentType() == TokenType.PIPE) {
            var alternatives = new ArrayList<RuleExpression>();
            alternatives.add(first);
            while (!isAtEnd() && currentType() == TokenType.PIPE) {
                advance(); // skip |
                alternatives.add(parseSequence());
            }
            return new Alternation(alternatives);
        }
        return first;
    }

    private RuleExpression parseSequence() {
        var elements = new ArrayList<RuleExpression>();
        elements.add(parsePostfix());

        while (!isAtEnd() && isAtomStart()) {
            elements.add(parsePostfix());
        }

        return elements.size() == 1 ? elements.getFirst() : new Sequence(elements);
    }

    private boolean isAtomStart() {
        if (isAtEnd()) return false;
        return switch (currentType()) {
            case STRING_LITERAL, IDENTIFIER, LPAREN, REGEX_CLASS, DOT, ACTION -> true;
            default -> false;
        };
    }

    private RuleExpression parsePostfix() {
        var expr = parseAtom();
        while (!isAtEnd()) {
            if (currentType() == TokenType.STAR) {
                advance();
                expr = new Repetition(expr, RepetitionKind.ZERO_OR_MORE);
            } else if (currentType() == TokenType.PLUS) {
                advance();
                expr = new Repetition(expr, RepetitionKind.ONE_OR_MORE);
            } else if (currentType() == TokenType.QUESTION) {
                advance();
                expr = new Repetition(expr, RepetitionKind.OPTIONAL);
            } else {
                break;
            }
        }
        return expr;
    }

    private RuleExpression parseAtom() {
        if (isAtEnd()) {
            throw new AntlrParseException("Unexpected end of tokens");
        }

        return switch (currentType()) {
            case STRING_LITERAL -> {
                String value = current().value();
                advance();
                yield Terminal.literal(value);
            }
            case REGEX_CLASS -> {
                String pattern = current().value();
                advance();
                yield Terminal.regex(pattern);
            }
            case DOT -> {
                advance();
                yield Terminal.regex(".");
            }
            case IDENTIFIER -> {
                String name = current().value();
                advance();
                if (Character.isUpperCase(name.charAt(0))) {
                    // Lexer rule reference: convert to regex terminal
                    String body = lexerRules.get(name);
                    if (body != null) {
                        yield Terminal.regex(body);
                    }
                    // Unknown lexer rule, treat as regex with the name
                    yield Terminal.regex(name);
                }
                yield new NonTerminal(name);
            }
            case LPAREN -> {
                advance(); // skip (
                var inner = parseAlternation();
                if (currentType() == TokenType.RPAREN) {
                    advance();
                }
                yield new Group(inner);
            }
            case ACTION -> {
                warnings.add("Skipping action block at line %d".formatted(current().line()));
                advance();
                // Return a placeholder terminal
                yield Terminal.literal("");
            }
            default -> throw new AntlrParseException(
                    "Unexpected token %s '%s' at line %d col %d"
                            .formatted(currentType(), current().value(), current().line(), current().col()));
        };
    }

    private void skipArrowCommands() {
        if (!isAtEnd() && currentType() == TokenType.ARROW) {
            warnings.add("Skipping '->' command at line %d".formatted(current().line()));
            advance(); // skip ->
            // Skip everything until semicolon
            while (!isAtEnd() && currentType() != TokenType.SEMICOLON) {
                advance();
            }
        }
    }

    private void skipUntilSemicolon() {
        while (!isAtEnd() && currentType() != TokenType.SEMICOLON) {
            advance();
        }
        if (!isAtEnd() && currentType() == TokenType.SEMICOLON) {
            advance();
        }
    }

    private void expectSemicolon() {
        if (!isAtEnd() && currentType() == TokenType.SEMICOLON) {
            advance();
        }
    }

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

    private static final class AntlrParseException extends RuntimeException {
        AntlrParseException(String message) {
            super(message);
        }
    }
}

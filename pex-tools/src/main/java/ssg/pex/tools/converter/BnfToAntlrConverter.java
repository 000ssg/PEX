package ssg.pex.tools.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
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
 * Converts a PEX {@link Grammar} model to ANTLR4 .g4 format text.
 *
 * <p>Mapping conventions:
 * <ul>
 *   <li>Grammar name is capitalized for ANTLR convention</li>
 *   <li>Literal terminals become {@code 'literal'}</li>
 *   <li>Regex terminals generate uppercase lexer rules at the bottom</li>
 *   <li>Repetition maps to {@code *}, {@code +}, {@code ?}</li>
 * </ul>
 */
public final class BnfToAntlrConverter {

    private static final Logger log = LoggerFactory.getLogger(BnfToAntlrConverter.class);

    private final Map<String, String> regexToLexerRule = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private int lexerRuleCounter = 0;

    /**
     * Converts the given PEX Grammar to an ANTLR4 grammar string.
     *
     * @param grammar the PEX grammar to convert
     * @return a result containing the ANTLR4 text and any warnings
     */
    public Result<ConversionResult> convert(Grammar grammar) {
        regexToLexerRule.clear();
        warnings.clear();
        lexerRuleCounter = 0;

        try {
            var sb = new StringBuilder();

            // Grammar declaration
            String antlrName = capitalizeFirst(grammar.name());
            sb.append("grammar ").append(antlrName).append(";\n\n");

            // Parser rules
            for (var entry : grammar.rules().entrySet()) {
                Rule rule = entry.getValue();
                sb.append(rule.name()).append("\n    : ");
                sb.append(convertExpression(rule.body()));
                sb.append("\n    ;\n\n");
            }

            // Lexer rules (generated from regex terminals)
            if (!regexToLexerRule.isEmpty()) {
                sb.append("// Lexer rules (generated from regex terminals)\n");
                for (var lexEntry : regexToLexerRule.entrySet()) {
                    sb.append(lexEntry.getValue()).append("\n    : ").append(lexEntry.getKey()).append("\n    ;\n\n");
                }
            }

            String output = sb.toString().stripTrailing() + "\n";
            log.debug("Converted grammar '{}' to ANTLR4 with {} lexer rules, {} warnings",
                    grammar.name(), regexToLexerRule.size(), warnings.size());
            return Result.success(ConversionResult.ofText(output, warnings));

        } catch (Exception e) {
            return Result.failure("CONV_BNF_TO_ANTLR", "Failed to convert grammar: " + e.getMessage(), e);
        }
    }

    private String convertExpression(RuleExpression expr) {
        return switch (expr) {
            case Terminal t -> convertTerminal(t);
            case NonTerminal nt -> nt.ruleName();
            case Sequence seq -> convertSequence(seq);
            case Alternation alt -> convertAlternation(alt);
            case Repetition rep -> convertRepetition(rep);
            case Group grp -> "( " + convertExpression(grp.inner()) + " )";
        };
    }

    private String convertTerminal(Terminal terminal) {
        if (terminal.isRegex()) {
            return convertRegexTerminal(terminal);
        }
        // Escape single quotes in the literal value
        String escaped = terminal.value().replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    private String convertRegexTerminal(Terminal terminal) {
        String pattern = terminal.value();

        // Check if we already have a lexer rule for this pattern
        if (regexToLexerRule.containsKey(pattern)) {
            return regexToLexerRule.get(pattern);
        }

        // Generate a lexer rule name from the pattern
        String lexerRuleName = generateLexerRuleName(pattern);
        regexToLexerRule.put(pattern, lexerRuleName);

        // Warn about complex patterns that may need manual adjustment
        if (pattern.contains("(?") || pattern.contains("\\b") || pattern.contains("\\B")) {
            warnings.add("Complex regex pattern '%s' (lexer rule %s) may need manual adjustment for ANTLR4"
                    .formatted(pattern, lexerRuleName));
        }

        return lexerRuleName;
    }

    private String generateLexerRuleName(String pattern) {
        // Try to derive a meaningful name from the pattern
        String cleaned = pattern.replaceAll("[^a-zA-Z0-9]", "");
        if (!cleaned.isEmpty() && Character.isLetter(cleaned.charAt(0))) {
            String name = cleaned.toUpperCase();
            if (name.length() > 20) name = name.substring(0, 20);
            // Ensure uniqueness
            String candidate = name;
            int suffix = 1;
            while (regexToLexerRule.containsValue(candidate)) {
                candidate = name + "_" + suffix++;
            }
            return candidate;
        }
        return "TOKEN_" + (++lexerRuleCounter);
    }

    private String convertSequence(Sequence seq) {
        var parts = new ArrayList<String>();
        for (var element : seq.elements()) {
            parts.add(convertExpression(element));
        }
        return String.join(" ", parts);
    }

    private String convertAlternation(Alternation alt) {
        var parts = new ArrayList<String>();
        for (var alternative : alt.alternatives()) {
            parts.add(convertExpression(alternative));
        }
        return String.join(" | ", parts);
    }

    private String convertRepetition(Repetition rep) {
        String body = convertExpression(rep.body());
        boolean needsParens = needsParentheses(rep.body());
        if (needsParens) {
            body = "( " + body + " )";
        }
        return switch (rep.kind()) {
            case ZERO_OR_MORE -> body + "*";
            case ONE_OR_MORE -> body + "+";
            case OPTIONAL -> body + "?";
        };
    }

    private boolean needsParentheses(RuleExpression expr) {
        return expr instanceof Alternation || expr instanceof Sequence;
    }

    private static String capitalizeFirst(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

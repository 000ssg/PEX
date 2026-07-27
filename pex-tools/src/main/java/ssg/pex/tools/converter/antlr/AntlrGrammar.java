package ssg.pex.tools.converter.antlr;

import java.util.List;
import java.util.Map;

/**
 * Complete ANTLR4 grammar model preserving all constructs.
 *
 * @param name         grammar name
 * @param grammarKind  combined, parser-only, or lexer-only
 * @param rules        all rules in declaration order (parser + lexer + fragment)
 * @param options      options block entries (e.g., tokenVocab=...)
 * @param imports      import declarations
 * @param modes        lexer modes with their rules
 */
public record AntlrGrammar(
        String name,
        GrammarKind grammarKind,
        List<AntlrRule> rules,
        Map<String, String> options,
        List<String> imports,
        Map<String, List<AntlrRule>> modes
) {
    public AntlrGrammar {
        rules = List.copyOf(rules);
        options = options != null ? Map.copyOf(options) : Map.of();
        imports = imports != null ? List.copyOf(imports) : List.of();
        modes = modes != null ? Map.copyOf(modes) : Map.of();
    }

    public enum GrammarKind { COMBINED, PARSER, LEXER }

    /** Get only parser rules */
    public List<AntlrRule> parserRules() {
        return rules.stream().filter(r -> r.kind() == AntlrRule.RuleKind.PARSER).toList();
    }

    /** Get only lexer rules */
    public List<AntlrRule> lexerRules() {
        return rules.stream().filter(r -> r.kind() == AntlrRule.RuleKind.LEXER).toList();
    }

    /** Get only fragment rules */
    public List<AntlrRule> fragmentRules() {
        return rules.stream().filter(r -> r.kind() == AntlrRule.RuleKind.FRAGMENT).toList();
    }
}

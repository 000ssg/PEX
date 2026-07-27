package ssg.pex.tools.converter.antlr;

import java.util.List;

/**
 * A single rule in an ANTLR4 grammar.
 *
 * @param name       rule name
 * @param body       the rule's expression body
 * @param kind       parser, lexer, or fragment
 * @param altLabels  alternative labels (# labelName), may be empty
 * @param commands   lexer commands after -&gt;, e.g. ["skip"], ["channel", "HIDDEN"]
 */
public record AntlrRule(
        String name,
        AntlrExpression body,
        RuleKind kind,
        List<String> altLabels,
        List<String> commands
) {
    public AntlrRule {
        altLabels = altLabels != null ? List.copyOf(altLabels) : List.of();
        commands = commands != null ? List.copyOf(commands) : List.of();
    }

    /** The kind of ANTLR rule. */
    public enum RuleKind { PARSER, LEXER, FRAGMENT }

    /** Convenience constructor without labels and commands. */
    public AntlrRule(String name, AntlrExpression body, RuleKind kind) {
        this(name, body, kind, List.of(), List.of());
    }
}

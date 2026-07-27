package ssg.pex.tools.converter.antlr;

import java.util.List;

/**
 * Native ANTLR4 expression model representing all grammar constructs.
 * Unlike the BNF RuleExpression hierarchy, this preserves ANTLR-specific
 * features that have no BNF equivalent (predicates, actions, negation,
 * labels, lexer commands).
 */
public sealed interface AntlrExpression {
    /** Literal string: 'hello' */
    record Literal(String value) implements AntlrExpression {}
    /** Character class: [a-zA-Z0-9] */
    record CharClass(String pattern) implements AntlrExpression {}
    /** Parser rule reference: ruleName (lowercase) */
    record RuleRef(String name) implements AntlrExpression {}
    /** Token/lexer rule reference: TOKEN_NAME (uppercase) */
    record TokenRef(String name) implements AntlrExpression {}
    /** Sequence of expressions */
    record Seq(List<AntlrExpression> elements) implements AntlrExpression {
        public Seq { elements = List.copyOf(elements); }
    }
    /** Alternation: a | b | c */
    record Alt(List<AntlrExpression> alternatives) implements AntlrExpression {
        public Alt { alternatives = List.copyOf(alternatives); }
    }
    /** Zero or more: expr* */
    record ZeroOrMore(AntlrExpression body) implements AntlrExpression {}
    /** One or more: expr+ */
    record OneOrMore(AntlrExpression body) implements AntlrExpression {}
    /** Optional: expr? */
    record Optional(AntlrExpression body) implements AntlrExpression {}
    /** Grouped expression: ( expr ) */
    record Group(AntlrExpression inner) implements AntlrExpression {}
    /** Negation: ~expr (ANTLR-specific, no BNF equivalent) */
    record Negation(AntlrExpression inner) implements AntlrExpression {}
    /** Semantic predicate: {code}? (ANTLR-specific, no BNF equivalent) */
    record Predicate(String code) implements AntlrExpression {}
    /** Embedded action: {code} (ANTLR-specific, no BNF equivalent) */
    record Action(String code) implements AntlrExpression {}
    /** Any token: . (dot wildcard) */
    record Dot() implements AntlrExpression {}
    /** Labeled element: label=expr or label+=expr */
    record LabeledElement(String label, boolean listLabel, AntlrExpression element) implements AntlrExpression {}
}

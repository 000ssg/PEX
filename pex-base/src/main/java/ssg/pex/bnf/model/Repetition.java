package ssg.pex.bnf.model;

public record Repetition(RuleExpression body, RepetitionKind kind) implements RuleExpression {
}

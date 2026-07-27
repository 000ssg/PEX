package ssg.pex.bnf.model;

public record Terminal(String value, boolean isRegex) implements RuleExpression {

    public static Terminal literal(String value) {
        return new Terminal(value, false);
    }

    public static Terminal regex(String pattern) {
        return new Terminal(pattern, true);
    }
}

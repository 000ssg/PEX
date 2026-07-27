package ssg.pex.bnf.model;

import java.util.List;

public record Alternation(List<RuleExpression> alternatives) implements RuleExpression {

    public Alternation {
        alternatives = List.copyOf(alternatives);
    }
}

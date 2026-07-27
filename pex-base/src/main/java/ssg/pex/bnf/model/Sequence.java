package ssg.pex.bnf.model;

import java.util.List;

public record Sequence(List<RuleExpression> elements) implements RuleExpression {

    public Sequence {
        elements = List.copyOf(elements);
    }
}

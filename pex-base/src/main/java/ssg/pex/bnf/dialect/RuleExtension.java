package ssg.pex.bnf.dialect;

import ssg.pex.bnf.model.RuleExpression;

import java.util.List;

public record RuleExtension(String targetRuleName,
                            List<RuleExpression> additionalAlternatives) implements RuleModification {

    public RuleExtension {
        additionalAlternatives = List.copyOf(additionalAlternatives);
    }
}

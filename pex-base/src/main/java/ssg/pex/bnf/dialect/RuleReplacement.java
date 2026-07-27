package ssg.pex.bnf.dialect;

import ssg.pex.bnf.model.Rule;

public record RuleReplacement(String targetRuleName, Rule replacement) implements RuleModification {
}

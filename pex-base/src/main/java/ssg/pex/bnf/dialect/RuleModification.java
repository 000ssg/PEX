package ssg.pex.bnf.dialect;

public sealed interface RuleModification
        permits RuleAddition, RuleReplacement, RuleDeletion, RuleExtension {
}

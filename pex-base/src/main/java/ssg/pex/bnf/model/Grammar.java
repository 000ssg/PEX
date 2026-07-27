package ssg.pex.bnf.model;

import ssg.pex.bnf.dialect.DialectExtension;
import ssg.pex.bnf.dialect.RuleAddition;
import ssg.pex.bnf.dialect.RuleDeletion;
import ssg.pex.bnf.dialect.RuleExtension;
import ssg.pex.bnf.dialect.RuleReplacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Grammar {

    private final String name;
    private final Map<String, Rule> rules;
    private final String startRuleName;

    public Grammar(String name, Map<String, Rule> rules, String startRuleName) {
        this.name = name;
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        this.startRuleName = startRuleName;
    }

    public String name() {
        return name;
    }

    public Map<String, Rule> rules() {
        return rules;
    }

    public String startRuleName() {
        return startRuleName;
    }

    public Optional<Rule> rule(String name) {
        return Optional.ofNullable(rules.get(name));
    }

    public Rule startRule() {
        var start = rules.get(startRuleName);
        if (start == null) {
            throw new IllegalStateException(
                    "Start rule '%s' not found in grammar '%s'".formatted(startRuleName, name));
        }
        return start;
    }

    public Grammar withDialect(DialectExtension ext) {
        var modified = new LinkedHashMap<>(rules);

        for (var mod : ext.modifications()) {
            switch (mod) {
                case RuleAddition add -> modified.put(add.rule().name(), add.rule());

                case RuleReplacement rep -> {
                    if (!modified.containsKey(rep.targetRuleName())) {
                        throw new IllegalArgumentException(
                                "Cannot replace non-existent rule '%s' in dialect '%s'"
                                        .formatted(rep.targetRuleName(), ext.dialectName()));
                    }
                    modified.put(rep.targetRuleName(), rep.replacement());
                }

                case RuleDeletion del -> {
                    if (modified.remove(del.targetRuleName()) == null) {
                        throw new IllegalArgumentException(
                                "Cannot delete non-existent rule '%s' in dialect '%s'"
                                        .formatted(del.targetRuleName(), ext.dialectName()));
                    }
                }

                case RuleExtension ruleExt -> {
                    var existing = modified.get(ruleExt.targetRuleName());
                    if (existing == null) {
                        throw new IllegalArgumentException(
                                "Cannot extend non-existent rule '%s' in dialect '%s'"
                                        .formatted(ruleExt.targetRuleName(), ext.dialectName()));
                    }
                    var allAlternatives = new ArrayList<RuleExpression>();
                    if (existing.body() instanceof Alternation alt) {
                        allAlternatives.addAll(alt.alternatives());
                    } else {
                        allAlternatives.add(existing.body());
                    }
                    allAlternatives.addAll(ruleExt.additionalAlternatives());
                    var extendedBody = new Alternation(allAlternatives);
                    modified.put(ruleExt.targetRuleName(),
                            new Rule(ruleExt.targetRuleName(), extendedBody, existing.annotations()));
                }
            }
        }

        return new Grammar(name + "+" + ext.dialectName(), modified, startRuleName);
    }

    public Grammar merge(Grammar other) {
        var merged = new LinkedHashMap<>(rules);
        merged.putAll(other.rules());
        return new Grammar(name, merged, startRuleName);
    }

    @Override
    public String toString() {
        return "Grammar{name='%s', rules=%d, start='%s'}".formatted(name, rules.size(), startRuleName);
    }
}

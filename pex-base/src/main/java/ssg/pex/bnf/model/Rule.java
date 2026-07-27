package ssg.pex.bnf.model;

import java.util.Map;

public record Rule(String name, RuleExpression body, Map<String, String> annotations) {

    public Rule {
        annotations = Map.copyOf(annotations);
    }

    public Rule(String name, RuleExpression body) {
        this(name, body, Map.of());
    }

    public boolean hasAnnotation(String key) {
        return annotations.containsKey(key);
    }

    public String annotation(String key) {
        return annotations.get(key);
    }
}

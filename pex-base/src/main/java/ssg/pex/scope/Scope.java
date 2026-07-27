package ssg.pex.scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Scope {

    private final String name;
    private final Scope parent;
    private final LinkedHashMap<String, Variable> variables = new LinkedHashMap<>();
    private final List<Scope> children = new ArrayList<>();
    private final ScopePath path;

    public Scope(String name, Scope parent) {
        this.name = name;
        this.parent = parent;
        this.path = parent == null
                ? ScopePath.of(name)
                : parent.path.append(name);
    }

    public String name() {
        return name;
    }

    public Scope parent() {
        return parent;
    }

    public ScopePath path() {
        return path;
    }

    public boolean hasLocal(String name) {
        return variables.containsKey(name);
    }

    public Variable getLocal(String name) {
        return variables.get(name);
    }

    public void define(String name, Variable var) {
        variables.put(name, var);
    }

    public void update(String name, Variable var) {
        if (!variables.containsKey(name)) {
            throw new IllegalStateException("Variable '" + name + "' not defined in scope " + this.name);
        }
        variables.put(name, var);
    }

    public void addChild(Scope child) {
        children.add(child);
    }

    public List<Scope> children() {
        return Collections.unmodifiableList(children);
    }

    public Map<String, Variable> variables() {
        return Collections.unmodifiableMap(variables);
    }
}

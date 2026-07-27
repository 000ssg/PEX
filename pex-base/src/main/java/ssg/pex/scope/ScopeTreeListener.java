package ssg.pex.scope;

public interface ScopeTreeListener {

    void onScopeEnter(Scope scope);

    void onScopeExit(Scope scope);

    void onVariableDefine(Scope scope, Variable var);

    void onVariableShadow(ShadowRecord record);
}

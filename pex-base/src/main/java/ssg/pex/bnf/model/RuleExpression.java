package ssg.pex.bnf.model;

public sealed interface RuleExpression
        permits Sequence, Alternation, Repetition, Terminal, NonTerminal, Group {
}

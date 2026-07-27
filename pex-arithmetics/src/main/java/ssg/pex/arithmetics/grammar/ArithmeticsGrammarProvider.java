package ssg.pex.arithmetics.grammar;

import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.RuleExpression;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Builds a Grammar for arithmetic expressions using the BNF model classes programmatically.
 * <p>
 * The grammar supports:
 * <ul>
 *   <li>Integer literals (decimal, hex 0x, binary 0b, octal 0o)</li>
 *   <li>Float literals (with decimal point, optional exponent)</li>
 *   <li>Boolean literals (true, false)</li>
 *   <li>Parenthesized expressions</li>
 *   <li>Operator precedence via grammar structure (lowest to highest):</li>
 *   <ul>
 *     <li>logical OR (||)</li>
 *     <li>logical AND (&amp;&amp;)</li>
 *     <li>bitwise OR (|)</li>
 *     <li>bitwise XOR (^)</li>
 *     <li>bitwise AND (&amp;)</li>
 *     <li>equality (==, !=)</li>
 *     <li>relational (&lt;, &gt;, &lt;=, &gt;=)</li>
 *     <li>shift (&lt;&lt;, &gt;&gt;, &gt;&gt;&gt;)</li>
 *     <li>additive (+, -)</li>
 *     <li>multiplicative (*, /, %)</li>
 *     <li>unary (!, -, ~, +)</li>
 *     <li>primary (literals, identifiers, function calls, parenthesized)</li>
 *   </ul>
 * </ul>
 */
public final class ArithmeticsGrammarProvider {

    private ArithmeticsGrammarProvider() {}

    /**
     * Build and return the arithmetic grammar.
     */
    public static Grammar createGrammar() {
        var rules = new LinkedHashMap<String, Rule>();

        // expression ::= or_expr
        rules.put("expression", new Rule("expression", new NonTerminal("or_expr")));

        // or_expr ::= and_expr ( "||" and_expr )*
        rules.put("or_expr", binaryRule("or_expr", "and_expr", "||"));

        // and_expr ::= bitwise_or_expr ( "&&" bitwise_or_expr )*
        rules.put("and_expr", binaryRule("and_expr", "bitwise_or_expr", "&&"));

        // bitwise_or_expr ::= bitwise_xor_expr ( "|" bitwise_xor_expr )*
        rules.put("bitwise_or_expr", binaryRule("bitwise_or_expr", "bitwise_xor_expr", "|"));

        // bitwise_xor_expr ::= bitwise_and_expr ( "^" bitwise_and_expr )*
        rules.put("bitwise_xor_expr", binaryRule("bitwise_xor_expr", "bitwise_and_expr", "^"));

        // bitwise_and_expr ::= equality_expr ( "&" equality_expr )*
        rules.put("bitwise_and_expr", binaryRule("bitwise_and_expr", "equality_expr", "&"));

        // equality_expr ::= relational_expr ( ( "==" | "!=" ) relational_expr )*
        rules.put("equality_expr", binaryRule("equality_expr", "relational_expr", "==", "!="));

        // relational_expr ::= shift_expr ( ( "<" | ">" | "<=" | ">=" ) shift_expr )*
        rules.put("relational_expr", binaryRule("relational_expr", "shift_expr", "<=", ">=", "<", ">"));

        // shift_expr ::= additive_expr ( ( "<<" | ">>" | ">>>" ) additive_expr )*
        rules.put("shift_expr", binaryRule("shift_expr", "additive_expr", ">>>", "<<", ">>"));

        // additive_expr ::= multiplicative_expr ( ( "+" | "-" ) multiplicative_expr )*
        rules.put("additive_expr", binaryRule("additive_expr", "multiplicative_expr", "+", "-"));

        // multiplicative_expr ::= unary_expr ( ( "*" | "/" | "%" ) unary_expr )*
        rules.put("multiplicative_expr", binaryRule("multiplicative_expr", "unary_expr", "*", "/", "%"));

        // unary_expr ::= ( "!" | "-" | "~" | "+" ) unary_expr | primary
        rules.put("unary_expr", new Rule("unary_expr", new Alternation(List.of(
                new Sequence(List.of(
                        new Alternation(List.of(
                                Terminal.literal("!"),
                                Terminal.literal("-"),
                                Terminal.literal("~"),
                                Terminal.literal("+")
                        )),
                        new NonTerminal("unary_expr")
                )),
                new NonTerminal("primary")
        ))));

        // primary ::= float_literal | hex_literal | bin_literal | oct_literal | int_literal
        //           | bool_literal | function_call | identifier | "(" expression ")"
        rules.put("primary", new Rule("primary", new Alternation(List.of(
                new NonTerminal("float_literal"),
                new NonTerminal("hex_literal"),
                new NonTerminal("bin_literal"),
                new NonTerminal("oct_literal"),
                new NonTerminal("int_literal"),
                new NonTerminal("bool_literal"),
                new NonTerminal("function_call"),
                new NonTerminal("identifier"),
                new Sequence(List.of(
                        Terminal.literal("("),
                        new NonTerminal("expression"),
                        Terminal.literal(")")
                ))
        ))));

        // Literal rules
        // int_literal ::= /[0-9]+/
        rules.put("int_literal", new Rule("int_literal", Terminal.regex("[0-9]+")));

        // float_literal ::= /[0-9]+\\.[0-9]*([eE][+-]?[0-9]+)?/ | /\\.[0-9]+([eE][+-]?[0-9]+)?/ | /[0-9]+[eE][+-]?[0-9]+/
        rules.put("float_literal", new Rule("float_literal", new Alternation(List.of(
                Terminal.regex("[0-9]+\\.[0-9]*([eE][+-]?[0-9]+)?"),
                Terminal.regex("\\.[0-9]+([eE][+-]?[0-9]+)?"),
                Terminal.regex("[0-9]+[eE][+-]?[0-9]+")
        ))));

        // hex_literal ::= /0[xX][0-9a-fA-F]+/
        rules.put("hex_literal", new Rule("hex_literal", Terminal.regex("0[xX][0-9a-fA-F]+")));

        // bin_literal ::= /0[bB][01]+/
        rules.put("bin_literal", new Rule("bin_literal", Terminal.regex("0[bB][01]+")));

        // oct_literal ::= /0[oO][0-7]+/
        rules.put("oct_literal", new Rule("oct_literal", Terminal.regex("0[oO][0-7]+")));

        // bool_literal ::= "true" | "false"
        rules.put("bool_literal", new Rule("bool_literal", new Alternation(List.of(
                Terminal.literal("true"),
                Terminal.literal("false")
        ))));

        // identifier ::= /[a-zA-Z_][a-zA-Z0-9_]*/
        rules.put("identifier", new Rule("identifier", Terminal.regex("[a-zA-Z_][a-zA-Z0-9_]*")));

        // function_call ::= identifier "(" arg_list? ")"
        rules.put("function_call", new Rule("function_call", new Sequence(List.of(
                new NonTerminal("identifier"),
                Terminal.literal("("),
                new Repetition(new NonTerminal("arg_list"), RepetitionKind.OPTIONAL),
                Terminal.literal(")")
        ))));

        // arg_list ::= expression ( "," expression )*
        rules.put("arg_list", new Rule("arg_list", new Sequence(List.of(
                new NonTerminal("expression"),
                new Repetition(
                        new Sequence(List.of(
                                Terminal.literal(","),
                                new NonTerminal("expression")
                        )),
                        RepetitionKind.ZERO_OR_MORE
                )
        ))));

        return new Grammar("arithmetics", rules, "expression");
    }

    /**
     * Create a binary expression rule: name ::= operand ( (op1 | op2 | ...) operand )*
     */
    private static Rule binaryRule(String name, String operandRule, String... operators) {
        RuleExpression opExpr;
        if (operators.length == 1) {
            opExpr = Terminal.literal(operators[0]);
        } else {
            opExpr = new Alternation(
                    java.util.Arrays.stream(operators)
                            .map(Terminal::literal)
                            .map(t -> (RuleExpression) t)
                            .toList()
            );
        }

        return new Rule(name, new Sequence(List.of(
                new NonTerminal(operandRule),
                new Repetition(
                        new Sequence(List.of(opExpr, new NonTerminal(operandRule))),
                        RepetitionKind.ZERO_OR_MORE
                )
        )));
    }
}

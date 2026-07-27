package ssg.pex.tools.converter.antlr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AntlrGrammarParserTest {

    private final AntlrGrammarParser parser = new AntlrGrammarParser();

    // ---- Grammar declaration tests ----

    @Test
    void testParseSimpleCombinedGrammar() {
        var result = parser.parse("""
                grammar Expr;
                expr : 'a' | 'b' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var grammar = result.value();
        assertThat(grammar.name()).isEqualTo("Expr");
        assertThat(grammar.grammarKind()).isEqualTo(AntlrGrammar.GrammarKind.COMBINED);
        assertThat(grammar.rules()).hasSize(1);
    }

    @Test
    void testParseParserOnlyGrammar() {
        var result = parser.parse("""
                parser grammar MyParser;
                expr : 'a' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().grammarKind()).isEqualTo(AntlrGrammar.GrammarKind.PARSER);
        assertThat(result.value().name()).isEqualTo("MyParser");
    }

    @Test
    void testParseLexerOnlyGrammar() {
        var result = parser.parse("""
                lexer grammar MyLexer;
                TOKEN : 'hello' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().grammarKind()).isEqualTo(AntlrGrammar.GrammarKind.LEXER);
        assertThat(result.value().name()).isEqualTo("MyLexer");
    }

    // ---- Rule kind tests ----

    @Test
    void testParserRulesHaveLowercaseNames() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a' ;
                stmt : 'b' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().parserRules()).hasSize(2);
        assertThat(result.value().parserRules()).extracting(AntlrRule::name)
                .containsExactly("expr", "stmt");
        assertThat(result.value().parserRules()).allMatch(r -> r.kind() == AntlrRule.RuleKind.PARSER);
    }

    @Test
    void testLexerRulesHaveUppercaseNames() {
        var result = parser.parse("""
                grammar Test;
                NUMBER : [0-9]+ ;
                STRING : 'hello' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().lexerRules()).hasSize(2);
        assertThat(result.value().lexerRules()).extracting(AntlrRule::name)
                .containsExactly("NUMBER", "STRING");
        assertThat(result.value().lexerRules()).allMatch(r -> r.kind() == AntlrRule.RuleKind.LEXER);
    }

    @Test
    void testFragmentRules() {
        var result = parser.parse("""
                grammar Test;
                fragment DIGIT : [0-9] ;
                NUMBER : DIGIT+ ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().fragmentRules()).hasSize(1);
        assertThat(result.value().fragmentRules().getFirst().name()).isEqualTo("DIGIT");
        assertThat(result.value().fragmentRules().getFirst().kind()).isEqualTo(AntlrRule.RuleKind.FRAGMENT);
    }

    // ---- Expression tests ----

    @Test
    void testAlternation() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a' | 'b' | 'c' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Alt.class);
        var alt = (AntlrExpression.Alt) body;
        assertThat(alt.alternatives()).hasSize(3);
    }

    @Test
    void testSequence() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a' 'b' 'c' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Seq.class);
        var seq = (AntlrExpression.Seq) body;
        assertThat(seq.elements()).hasSize(3);
    }

    @Test
    void testGrouping() {
        var result = parser.parse("""
                grammar Test;
                expr : ('a' | 'b') 'c' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Seq.class);
        var seq = (AntlrExpression.Seq) body;
        assertThat(seq.elements().getFirst()).isInstanceOf(AntlrExpression.Group.class);
        var group = (AntlrExpression.Group) seq.elements().getFirst();
        assertThat(group.inner()).isInstanceOf(AntlrExpression.Alt.class);
    }

    @Test
    void testZeroOrMore() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a'* ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.ZeroOrMore.class);
        var zom = (AntlrExpression.ZeroOrMore) body;
        assertThat(zom.body()).isInstanceOf(AntlrExpression.Literal.class);
    }

    @Test
    void testOneOrMore() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a'+ ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.OneOrMore.class);
    }

    @Test
    void testOptional() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a'? ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Optional.class);
    }

    @Test
    void testNegation() {
        var result = parser.parse("""
                grammar Test;
                NOT_STAR : ~'*' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Negation.class);
        var neg = (AntlrExpression.Negation) body;
        assertThat(neg.inner()).isInstanceOf(AntlrExpression.Literal.class);
    }

    @Test
    void testNegationWithGroup() {
        var result = parser.parse("""
                grammar Test;
                NOT_SPECIAL : ~('*' | '/') ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Negation.class);
        var neg = (AntlrExpression.Negation) body;
        assertThat(neg.inner()).isInstanceOf(AntlrExpression.Group.class);
    }

    @Test
    void testSemanticPredicate() {
        var result = parser.parse("""
                grammar Test;
                expr : {isValid()}? 'a' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Seq.class);
        var seq = (AntlrExpression.Seq) body;
        assertThat(seq.elements().getFirst()).isInstanceOf(AntlrExpression.Predicate.class);
        var pred = (AntlrExpression.Predicate) seq.elements().getFirst();
        assertThat(pred.code()).contains("isValid()");
    }

    @Test
    void testEmbeddedAction() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a' {doSomething();} ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Seq.class);
        var seq = (AntlrExpression.Seq) body;
        assertThat(seq.elements().get(1)).isInstanceOf(AntlrExpression.Action.class);
        var action = (AntlrExpression.Action) seq.elements().get(1);
        assertThat(action.code()).contains("doSomething()");
    }

    @Test
    void testDotWildcard() {
        var result = parser.parse("""
                grammar Test;
                ANY : . ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Dot.class);
    }

    @Test
    void testLabeledElement() {
        var result = parser.parse("""
                grammar Test;
                expr : left=atom '+' right=atom ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Seq.class);
        var seq = (AntlrExpression.Seq) body;
        assertThat(seq.elements().getFirst()).isInstanceOf(AntlrExpression.LabeledElement.class);
        var labeled = (AntlrExpression.LabeledElement) seq.elements().getFirst();
        assertThat(labeled.label()).isEqualTo("left");
        assertThat(labeled.listLabel()).isFalse();
        assertThat(labeled.element()).isInstanceOf(AntlrExpression.RuleRef.class);
    }

    @Test
    void testListLabeledElement() {
        var result = parser.parse("""
                grammar Test;
                expr : items+=atom (',' items+=atom)* ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Seq.class);
        var seq = (AntlrExpression.Seq) body;
        assertThat(seq.elements().getFirst()).isInstanceOf(AntlrExpression.LabeledElement.class);
        var labeled = (AntlrExpression.LabeledElement) seq.elements().getFirst();
        assertThat(labeled.label()).isEqualTo("items");
        assertThat(labeled.listLabel()).isTrue();
    }

    @Test
    void testAltLabels() {
        var result = parser.parse("""
                grammar Test;
                expr : 'a' # LiteralA
                     | 'b' # LiteralB
                     ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var rule = result.value().rules().getFirst();
        assertThat(rule.altLabels()).containsExactly("LiteralA", "LiteralB");
        assertThat(rule.body()).isInstanceOf(AntlrExpression.Alt.class);
    }

    // ---- Lexer commands ----

    @Test
    void testLexerCommandSkip() {
        var result = parser.parse("""
                grammar Test;
                WS : [ \\t\\r\\n]+ -> skip ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var rule = result.value().rules().getFirst();
        assertThat(rule.commands()).containsExactly("skip");
    }

    @Test
    void testLexerCommandChannelHidden() {
        var result = parser.parse("""
                grammar Test;
                WS : [ \\t]+ -> channel(HIDDEN) ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var rule = result.value().rules().getFirst();
        assertThat(rule.commands()).containsExactly("channel", "HIDDEN");
    }

    // ---- Options block ----

    @Test
    void testOptionsBlock() {
        var result = parser.parse("""
                grammar Test;
                options { tokenVocab = MyLexer; }
                expr : 'a' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().options()).containsEntry("tokenVocab", "MyLexer");
    }

    // ---- Import declarations ----

    @Test
    void testImportDeclaration() {
        var result = parser.parse("""
                grammar Test;
                import CommonLexer, CommonParser;
                expr : 'a' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().imports()).containsExactly("CommonLexer", "CommonParser");
    }

    // ---- Modes ----

    @Test
    void testModeBlock() {
        var result = parser.parse("""
                lexer grammar TestLexer;
                OPEN : '<' -> pushMode(INSIDE) ;
                mode INSIDE;
                CLOSE : '>' -> popMode ;
                TEXT : ~[<>]+ ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var grammar = result.value();
        // Default mode has OPEN rule
        assertThat(grammar.rules()).hasSize(1);
        assertThat(grammar.rules().getFirst().name()).isEqualTo("OPEN");
        // INSIDE mode has CLOSE and TEXT rules
        assertThat(grammar.modes()).containsKey("INSIDE");
        assertThat(grammar.modes().get("INSIDE")).hasSize(2);
        assertThat(grammar.modes().get("INSIDE")).extracting(AntlrRule::name)
                .containsExactly("CLOSE", "TEXT");
    }

    // ---- Character classes ----

    @Test
    void testCharacterClass() {
        var result = parser.parse("""
                grammar Test;
                LETTER : [a-zA-Z] ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.CharClass.class);
        var cc = (AntlrExpression.CharClass) body;
        assertThat(cc.pattern()).isEqualTo("[a-zA-Z]");
    }

    // ---- Token refs vs rule refs ----

    @Test
    void testTokenRefVsRuleRef() {
        var result = parser.parse("""
                grammar Test;
                expr : atom TOKEN ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Seq.class);
        var seq = (AntlrExpression.Seq) body;
        assertThat(seq.elements().get(0)).isInstanceOf(AntlrExpression.RuleRef.class);
        assertThat(seq.elements().get(1)).isInstanceOf(AntlrExpression.TokenRef.class);
    }

    // ---- Complex real-world grammar ----

    @Test
    void testComplexRealWorldGrammarSnippet() {
        var result = parser.parse("""
                grammar Calc;

                options { tokenVocab = CalcLexer; }

                prog : stat+ ;

                stat : expr NEWLINE          # printExpr
                     | ID '=' expr NEWLINE   # assign
                     | NEWLINE               # blank
                     ;

                expr : left=expr op=('*'|'/') right=expr  # MulDiv
                     | left=expr op=('+'|'-') right=expr  # AddSub
                     | INT                                # int
                     | ID                                 # id
                     | '(' expr ')'                       # parens
                     ;

                fragment DIGIT : [0-9] ;
                INT : DIGIT+ ;
                ID : [a-zA-Z]+ ;
                NEWLINE : '\\r'? '\\n' ;
                WS : [ \\t]+ -> skip ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var grammar = result.value();
        assertThat(grammar.name()).isEqualTo("Calc");
        assertThat(grammar.options()).containsEntry("tokenVocab", "CalcLexer");
        assertThat(grammar.parserRules()).hasSize(3); // prog, stat, expr
        assertThat(grammar.lexerRules()).hasSize(4); // INT, ID, NEWLINE, WS
        assertThat(grammar.fragmentRules()).hasSize(1); // DIGIT

        // Check alt labels on stat rule
        var statRule = grammar.parserRules().stream()
                .filter(r -> r.name().equals("stat")).findFirst().orElseThrow();
        assertThat(statRule.altLabels()).containsExactly("printExpr", "assign", "blank");

        // Check alt labels on expr rule
        var exprRule = grammar.parserRules().stream()
                .filter(r -> r.name().equals("expr")).findFirst().orElseThrow();
        assertThat(exprRule.altLabels()).containsExactly("MulDiv", "AddSub", "int", "id", "parens");

        // Check WS has skip command
        var wsRule = grammar.lexerRules().stream()
                .filter(r -> r.name().equals("WS")).findFirst().orElseThrow();
        assertThat(wsRule.commands()).contains("skip");
    }

    // ---- Error handling ----

    @Test
    void testMissingGrammarDeclaration() {
        var result = parser.parse("expr : 'a' ;");
        // 'expr' is an IDENTIFIER, not GRAMMAR keyword, so this should fail
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void testEmptyGrammar() {
        var result = parser.parse("grammar Empty;");
        assertThat(result.isFailure()).isTrue();
    }

    // ---- Mixed parser and lexer rules in combined grammar ----

    @Test
    void testMixedParserAndLexerRules() {
        var result = parser.parse("""
                grammar Mixed;
                expr : NUMBER '+' NUMBER ;
                NUMBER : [0-9]+ ;
                PLUS : '+' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var grammar = result.value();
        assertThat(grammar.parserRules()).hasSize(1);
        assertThat(grammar.lexerRules()).hasSize(2);
    }

    @Test
    void testNestedRepetition() {
        var result = parser.parse("""
                grammar Test;
                expr : ('a' 'b')* ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.ZeroOrMore.class);
        var zom = (AntlrExpression.ZeroOrMore) body;
        assertThat(zom.body()).isInstanceOf(AntlrExpression.Group.class);
    }

    @Test
    void testLiteralValue() {
        var result = parser.parse("""
                grammar Test;
                expr : 'hello' ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.Literal.class);
        assertThat(((AntlrExpression.Literal) body).value()).isEqualTo("hello");
    }

    @Test
    void testRuleRefName() {
        var result = parser.parse("""
                grammar Test;
                expr : atom ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.RuleRef.class);
        assertThat(((AntlrExpression.RuleRef) body).name()).isEqualTo("atom");
    }

    @Test
    void testTokenRefName() {
        var result = parser.parse("""
                grammar Test;
                expr : INT ;
                """);

        assertThat(result.isSuccess()).isTrue();
        var body = result.value().rules().getFirst().body();
        assertThat(body).isInstanceOf(AntlrExpression.TokenRef.class);
        assertThat(((AntlrExpression.TokenRef) body).name()).isEqualTo("INT");
    }
}

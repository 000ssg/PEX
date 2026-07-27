package ssg.pex.tools.converter;

import org.junit.jupiter.api.Test;
import ssg.pex.tools.converter.Antlr4Lexer.TokenType;

import static org.assertj.core.api.Assertions.assertThat;

class Antlr4LexerTest {

    private final Antlr4Lexer lexer = new Antlr4Lexer();

    @Test
    void testTokenizeSimpleGrammar() {
        var tokens = lexer.tokenize("grammar Expr; expr : 'a' | 'b' ;");

        assertThat(tokens).extracting(Antlr4Lexer.Token::type).containsExactly(
                TokenType.GRAMMAR,
                TokenType.IDENTIFIER,   // Expr
                TokenType.SEMICOLON,
                TokenType.IDENTIFIER,   // expr
                TokenType.COLON,
                TokenType.STRING_LITERAL, // a
                TokenType.PIPE,
                TokenType.STRING_LITERAL, // b
                TokenType.SEMICOLON,
                TokenType.EOF_TOKEN
        );
    }

    @Test
    void testTokenizeStringLiteralsAndIdentifiers() {
        var tokens = lexer.tokenize("ruleName : 'hello' identifier ;");

        assertThat(tokens).extracting(Antlr4Lexer.Token::value).containsExactly(
                "ruleName", ":", "hello", "identifier", ";", ""
        );
    }

    @Test
    void testTokenizeOperators() {
        var tokens = lexer.tokenize("rule : a* b+ c? (d | e) ;");

        assertThat(tokens).extracting(Antlr4Lexer.Token::type).containsExactly(
                TokenType.IDENTIFIER,  // rule
                TokenType.COLON,
                TokenType.IDENTIFIER,  // a
                TokenType.STAR,
                TokenType.IDENTIFIER,  // b
                TokenType.PLUS,
                TokenType.IDENTIFIER,  // c
                TokenType.QUESTION,
                TokenType.LPAREN,
                TokenType.IDENTIFIER,  // d
                TokenType.PIPE,
                TokenType.IDENTIFIER,  // e
                TokenType.RPAREN,
                TokenType.SEMICOLON,
                TokenType.EOF_TOKEN
        );
    }

    @Test
    void testSkipLineComments() {
        var tokens = lexer.tokenize("""
                grammar Test; // this is a comment
                rule : 'a' ;
                """);

        // Should not contain any comment tokens
        assertThat(tokens).extracting(Antlr4Lexer.Token::type).doesNotContain(TokenType.ACTION);
        assertThat(tokens).extracting(Antlr4Lexer.Token::type).containsSubsequence(
                TokenType.GRAMMAR, TokenType.IDENTIFIER, TokenType.SEMICOLON,
                TokenType.IDENTIFIER, TokenType.COLON, TokenType.STRING_LITERAL, TokenType.SEMICOLON
        );
    }

    @Test
    void testSkipBlockComments() {
        var tokens = lexer.tokenize("""
                grammar Test;
                /* block comment */
                rule : 'a' ;
                """);

        assertThat(tokens).extracting(Antlr4Lexer.Token::type).containsSubsequence(
                TokenType.GRAMMAR, TokenType.IDENTIFIER, TokenType.SEMICOLON,
                TokenType.IDENTIFIER, TokenType.COLON, TokenType.STRING_LITERAL, TokenType.SEMICOLON
        );
    }

    @Test
    void testHandleActionBlocks() {
        var tokens = lexer.tokenize("rule : 'a' { some action code; } ;");

        assertThat(tokens).extracting(Antlr4Lexer.Token::type).contains(TokenType.ACTION);
        var actionToken = tokens.stream()
                .filter(t -> t.type() == TokenType.ACTION)
                .findFirst()
                .orElseThrow();
        assertThat(actionToken.value()).contains("some action code");
    }

    @Test
    void testHandleArrow() {
        var tokens = lexer.tokenize("WS : [ \\t\\r\\n]+ -> skip ;");

        assertThat(tokens).extracting(Antlr4Lexer.Token::type).contains(TokenType.ARROW);
    }

    @Test
    void testHandleRegexClass() {
        var tokens = lexer.tokenize("DIGIT : [0-9] ;");

        assertThat(tokens).extracting(Antlr4Lexer.Token::type).contains(TokenType.REGEX_CLASS);
        var regexToken = tokens.stream()
                .filter(t -> t.type() == TokenType.REGEX_CLASS)
                .findFirst()
                .orElseThrow();
        assertThat(regexToken.value()).isEqualTo("[0-9]");
    }

    @Test
    void testTokenizeKeywords() {
        var tokens = lexer.tokenize("fragment lexer parser options tokens");

        assertThat(tokens).extracting(Antlr4Lexer.Token::type).containsExactly(
                TokenType.FRAGMENT,
                TokenType.LEXER,
                TokenType.PARSER,
                TokenType.OPTIONS,
                TokenType.TOKENS,
                TokenType.EOF_TOKEN
        );
    }
}

package ssg.pex.sql.grammar;

import org.junit.jupiter.api.Test;
import ssg.pex.sql.grammar.SqlTokenizer.Token;
import ssg.pex.sql.grammar.SqlTokenizer.TokenType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlTokenizerTest {

    private List<Token> tokenize(String sql) {
        return new SqlTokenizer(sql).tokenize();
    }

    @Test
    void tokenizeSimpleSelect() {
        var tokens = tokenize("SELECT * FROM users");
        assertThat(tokens).extracting(Token::type)
                .containsExactly(TokenType.KEYWORD, TokenType.OPERATOR, TokenType.KEYWORD, TokenType.IDENTIFIER, TokenType.EOF);
    }

    @Test
    void tokenizeKeywords() {
        var tokens = tokenize("SELECT WHERE FROM INSERT");
        assertThat(tokens).extracting(Token::value)
                .containsExactly("SELECT", "WHERE", "FROM", "INSERT", "");
    }

    @Test
    void tokenizeIdentifiers() {
        var tokens = tokenize("my_table my_column");
        assertThat(tokens).extracting(Token::type)
                .containsExactly(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF);
    }

    @Test
    void tokenizeNumbers() {
        var tokens = tokenize("42 3.14 0.5");
        assertThat(tokens).extracting(Token::type)
                .containsExactly(TokenType.NUMBER, TokenType.NUMBER, TokenType.NUMBER, TokenType.EOF);
        assertThat(tokens.get(0).value()).isEqualTo("42");
        assertThat(tokens.get(1).value()).isEqualTo("3.14");
    }

    @Test
    void tokenizeSingleQuotedString() {
        var tokens = tokenize("'hello world'");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.STRING);
        assertThat(tokens.get(0).value()).isEqualTo("hello world");
    }

    @Test
    void tokenizeEscapedQuoteInString() {
        var tokens = tokenize("'it''s'");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.STRING);
        assertThat(tokens.get(0).value()).isEqualTo("it's");
    }

    @Test
    void tokenizeDoubleQuotedIdentifier() {
        var tokens = tokenize("\"my table\"");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(0).value()).isEqualTo("my table");
    }

    @Test
    void tokenizeBacktickIdentifier() {
        var tokens = tokenize("`my_col`");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(0).value()).isEqualTo("my_col");
    }

    @Test
    void tokenizeOperators() {
        var tokens = tokenize("= < > <= >= <> != + - * /");
        assertThat(tokens).extracting(Token::type)
                .filteredOn(t -> t == TokenType.OPERATOR)
                .hasSize(11);
    }

    @Test
    void tokenizeTwoCharOperators() {
        var tokens = tokenize("<= >= <> !=");
        assertThat(tokens.get(0).value()).isEqualTo("<=");
        assertThat(tokens.get(1).value()).isEqualTo(">=");
        assertThat(tokens.get(2).value()).isEqualTo("<>");
        assertThat(tokens.get(3).value()).isEqualTo("!=");
    }

    @Test
    void tokenizePunctuation() {
        var tokens = tokenize("(a, b);");
        assertThat(tokens).extracting(Token::type)
                .containsExactly(TokenType.PUNCTUATION, TokenType.IDENTIFIER, TokenType.PUNCTUATION,
                        TokenType.IDENTIFIER, TokenType.PUNCTUATION, TokenType.PUNCTUATION, TokenType.EOF);
    }

    @Test
    void tokenizeLineComment() {
        var tokens = tokenize("SELECT -- this is a comment\n* FROM t");
        assertThat(tokens).extracting(Token::value)
                .containsExactly("SELECT", "*", "FROM", "t", "");
    }

    @Test
    void tokenizeBlockComment() {
        var tokens = tokenize("SELECT /* comment */ * FROM t");
        assertThat(tokens).extracting(Token::value)
                .containsExactly("SELECT", "*", "FROM", "t", "");
    }

    @Test
    void tokenizeEmptyInput() {
        var tokens = tokenize("");
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().type()).isEqualTo(TokenType.EOF);
    }

    @Test
    void tokenizeWhitespaceOnly() {
        var tokens = tokenize("   \t\n  ");
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().type()).isEqualTo(TokenType.EOF);
    }

    @Test
    void tokenizeComplexSelect() {
        var tokens = tokenize("SELECT u.name, COUNT(*) FROM users u WHERE u.age > 18 GROUP BY u.name");
        assertThat(tokens).hasSizeGreaterThan(10);
    }

    @Test
    void tokenizeConcatOperator() {
        var tokens = tokenize("a || b");
        assertThat(tokens.get(1).value()).isEqualTo("||");
    }

    @Test
    void tokenizeTypeCast() {
        var tokens = tokenize("a::int");
        assertThat(tokens.get(1).value()).isEqualTo("::");
    }

    @Test
    void tokenizeTokenPositions() {
        var tokens = tokenize("SELECT name");
        assertThat(tokens.get(0).position()).isEqualTo(0);
        assertThat(tokens.get(1).position()).isEqualTo(7);
    }
}

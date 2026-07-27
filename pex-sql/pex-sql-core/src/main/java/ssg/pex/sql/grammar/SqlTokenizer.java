package ssg.pex.sql.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Simple SQL tokenizer that converts SQL text into a list of tokens.
 */
public class SqlTokenizer {

    public enum TokenType {
        KEYWORD, IDENTIFIER, NUMBER, STRING, OPERATOR, PUNCTUATION, EOF
    }

    public record Token(TokenType type, String value, int position) {
        @Override
        public String toString() {
            return type + "(" + value + ")@" + position;
        }
    }

    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE",
            "CREATE", "DROP", "ALTER", "TABLE", "INDEX", "VIEW", "JOIN",
            "ON", "AND", "OR", "NOT", "IN", "BETWEEN", "LIKE", "IS", "NULL",
            "TRUE", "FALSE", "AS", "ORDER", "BY", "GROUP", "HAVING",
            "LIMIT", "OFFSET", "SET", "VALUES", "DISTINCT", "ALL",
            "UNION", "INTERSECT", "EXCEPT", "EXISTS", "CASE", "WHEN",
            "THEN", "ELSE", "END", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT",
            "PROCEDURE", "CALL", "TRIGGER", "BEFORE", "AFTER", "OUT", "INOUT",
            "PRIMARY", "FOREIGN", "KEY", "REFERENCES", "UNIQUE", "CHECK",
            "DEFAULT", "CONSTRAINT", "CASCADE", "ASC", "DESC", "NULLS",
            "FIRST", "LAST", "IF", "FUNCTION", "RETURN", "DECLARE",
            "LEFT", "RIGHT", "FULL", "CROSS", "INNER", "OUTER",
            "ADD", "COLUMN", "RENAME", "TO", "REPLACE",
            "AUTO_INCREMENT", "SERIAL", "INTEGER", "BIGINT", "FLOAT",
            "DOUBLE", "DECIMAL", "VARCHAR", "TEXT", "BOOLEAN", "DATE",
            "TIMESTAMP", "BLOB", "COUNT", "SUM", "AVG", "MIN", "MAX",
            "GROUP_CONCAT", "UPPER", "LOWER", "TRIM", "SUBSTRING",
            "LENGTH", "CONCAT", "COALESCE", "NULLIF", "CAST", "ABS",
            "ROUND", "CEIL", "FLOOR", "NOW", "CURRENT_DATE", "CURRENT_TIMESTAMP",
            "RELEASE", "ILIKE", "RETURNING", "OVER", "PARTITION",
            "WITH", "RECURSIVE", "TRANSACTION", "START", "ISOLATION",
            "LEVEL", "READ", "UNCOMMITTED", "COMMITTED", "REPEATABLE",
            "SERIALIZABLE", "WRITE", "ONLY",
            "NOT_EXISTS", // synthetic token for IF NOT EXISTS
            "TEMP", "TEMPORARY", "GLOBAL" // temporary table keywords
    );

    private final String input;
    private int pos;

    public SqlTokenizer(String input) {
        this.input = input;
        this.pos = 0;
    }

    public List<Token> tokenize() {
        var tokens = new ArrayList<Token>();
        while (pos < input.length()) {
            skipWhitespaceAndComments();
            if (pos >= input.length()) break;

            char c = input.charAt(pos);

            if (c == '\'') {
                tokens.add(readString());
            } else if (c == '"' || c == '`') {
                tokens.add(readQuotedIdentifier(c));
            } else if (Character.isDigit(c) || (c == '.' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
                tokens.add(readNumber());
            } else if (Character.isLetter(c) || c == '_') {
                tokens.add(readWord());
            } else if (isOperatorChar(c)) {
                tokens.add(readOperator());
            } else if (isPunctuation(c)) {
                tokens.add(new Token(TokenType.PUNCTUATION, String.valueOf(c), pos++));
            } else {
                pos++; // skip unknown chars
            }
        }
        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    private void skipWhitespaceAndComments() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == '-' && pos + 1 < input.length() && input.charAt(pos + 1) == '-') {
                // Line comment
                while (pos < input.length() && input.charAt(pos) != '\n') pos++;
            } else if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                // Block comment
                pos += 2;
                while (pos + 1 < input.length() && !(input.charAt(pos) == '*' && input.charAt(pos + 1) == '/')) pos++;
                if (pos + 1 < input.length()) pos += 2;
            } else {
                break;
            }
        }
    }

    private Token readString() {
        int start = pos;
        pos++; // skip opening quote
        var sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\'' && pos + 1 < input.length() && input.charAt(pos + 1) == '\'') {
                sb.append('\'');
                pos += 2;
            } else if (c == '\'') {
                pos++;
                break;
            } else {
                sb.append(c);
                pos++;
            }
        }
        return new Token(TokenType.STRING, sb.toString(), start);
    }

    private Token readQuotedIdentifier(char quoteChar) {
        int start = pos;
        pos++; // skip opening quote
        var sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quoteChar) {
            sb.append(input.charAt(pos));
            pos++;
        }
        if (pos < input.length()) pos++; // skip closing quote
        return new Token(TokenType.IDENTIFIER, sb.toString(), start);
    }

    private Token readNumber() {
        int start = pos;
        var sb = new StringBuilder();
        boolean hasDot = false;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) {
                sb.append(c);
                pos++;
            } else if (c == '.' && !hasDot) {
                hasDot = true;
                sb.append(c);
                pos++;
            } else {
                break;
            }
        }
        return new Token(TokenType.NUMBER, sb.toString(), start);
    }

    private Token readWord() {
        int start = pos;
        var sb = new StringBuilder();
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            sb.append(input.charAt(pos));
            pos++;
        }
        String word = sb.toString();
        String upper = word.toUpperCase();
        if (KEYWORDS.contains(upper)) {
            return new Token(TokenType.KEYWORD, upper, start);
        }
        return new Token(TokenType.IDENTIFIER, word, start);
    }

    private Token readOperator() {
        int start = pos;
        char c = input.charAt(pos);
        // Two-char operators
        if (pos + 1 < input.length()) {
            String twoChar = "" + c + input.charAt(pos + 1);
            if (twoChar.equals("<=") || twoChar.equals(">=") || twoChar.equals("<>") ||
                    twoChar.equals("!=") || twoChar.equals("||") || twoChar.equals("::")) {
                pos += 2;
                return new Token(TokenType.OPERATOR, twoChar, start);
            }
        }
        pos++;
        return new Token(TokenType.OPERATOR, String.valueOf(c), start);
    }

    private boolean isOperatorChar(char c) {
        return c == '=' || c == '<' || c == '>' || c == '!' || c == '+' || c == '-' ||
                c == '*' || c == '/' || c == '%' || c == '|' || c == ':';
    }

    private boolean isPunctuation(char c) {
        return c == '(' || c == ')' || c == ',' || c == ';' || c == '.';
    }
}

package ssg.pex.sql.streaming.engine;

import ssg.pex.ast.SourceLocation;
import ssg.pex.result.Result;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.grammar.SqlTokenizer;
import ssg.pex.sql.grammar.SqlTokenizer.Token;
import ssg.pex.sql.grammar.SqlTokenizer.TokenType;
import ssg.pex.sql.streaming.ast.*;
import ssg.pex.sql.streaming.ast.WindowSpec.WindowType;

import java.util.*;

/**
 * Parser for streaming SQL statements:
 * <ul>
 *   <li>CREATE STREAM name (col TYPE, ...) WITH (property='value', ...)</li>
 *   <li>DROP STREAM name [IF EXISTS]</li>
 *   <li>SELECT ... FROM stream WINDOW TUMBLING (SIZE n SECONDS) [WHERE ...] [GROUP BY ...] EMIT CHANGES|FINAL</li>
 *   <li>INSERT INTO stream SELECT ... FROM ...</li>
 * </ul>
 */
public class StreamSqlParser {

    private List<Token> tokens;
    private int pos;

    public Result<StreamNode> parse(String sql) {
        try {
            var tokenizer = new SqlTokenizer(sql);
            this.tokens = tokenizer.tokenize();
            this.pos = 0;
            StreamNode node = parseStatement();
            return Result.success(node);
        } catch (StreamParseException e) {
            return Result.failure("STREAM_PARSE_ERROR", e.getMessage());
        }
    }

    private StreamNode parseStatement() {
        Token t = peek();
        String upper = t.value().toUpperCase();
        return switch (upper) {
            case "CREATE" -> parseCreate();
            case "DROP" -> parseDrop();
            case "SELECT" -> parseStreamSelect();
            case "INSERT" -> parseInsertInto();
            default -> throw error("Expected CREATE, DROP, SELECT, or INSERT, got: " + t.value());
        };
    }

    // ---- CREATE STREAM ----

    private CreateStreamNode parseCreate() {
        SourceLocation loc = location();
        expect("CREATE");
        expectIdentifier("STREAM");

        String streamName = consumeIdentifier();

        expect("(");
        var columns = new ArrayList<ColumnDef>();
        columns.add(parseColumnDef());
        while (matchPunctuation(",")) {
            columns.add(parseColumnDef());
        }
        expect(")");

        Map<String, String> properties = new LinkedHashMap<>();
        if (matchKeyword("WITH")) {
            expect("(");
            parseProperties(properties);
            expect(")");
        }

        matchPunctuation(";");
        return new CreateStreamNode(streamName, columns, properties, loc);
    }

    private ColumnDef parseColumnDef() {
        String name = consumeIdentifier();
        SqlDataType type = parseDataType();
        return new ColumnDef(name, type, true, null, false, false);
    }

    private SqlDataType parseDataType() {
        Token t = peek();
        String typeName = t.value().toUpperCase();
        advance();

        // Skip optional size parameters
        if (matchPunctuation("(")) {
            while (!peek().value().equals(")") && peek().type() != TokenType.EOF) {
                advance();
            }
            expect(")");
        }

        return switch (typeName) {
            case "INT", "INTEGER" -> SqlDataType.INTEGER;
            case "BIGINT" -> SqlDataType.BIGINT;
            case "FLOAT", "REAL" -> SqlDataType.FLOAT;
            case "DOUBLE" -> SqlDataType.DOUBLE;
            case "DECIMAL", "NUMERIC" -> SqlDataType.DECIMAL;
            case "VARCHAR", "CHAR" -> SqlDataType.VARCHAR;
            case "TEXT" -> SqlDataType.TEXT;
            case "BOOLEAN", "BOOL" -> SqlDataType.BOOLEAN;
            case "DATE" -> SqlDataType.DATE;
            case "TIMESTAMP", "DATETIME" -> SqlDataType.TIMESTAMP;
            default -> SqlDataType.VARCHAR;
        };
    }

    private void parseProperties(Map<String, String> properties) {
        parseProperty(properties);
        while (matchPunctuation(",")) {
            parseProperty(properties);
        }
    }

    private void parseProperty(Map<String, String> properties) {
        String key = consumeIdentifier();
        expectOperator("=");
        Token val = peek();
        String value;
        if (val.type() == TokenType.STRING) {
            value = val.value();
            advance();
        } else if (val.type() == TokenType.NUMBER) {
            value = val.value();
            advance();
        } else {
            value = consumeIdentifier();
        }
        properties.put(key, value);
    }

    // ---- DROP STREAM ----

    private DropStreamNode parseDrop() {
        SourceLocation loc = location();
        expect("DROP");
        expectIdentifier("STREAM");

        boolean ifExists = false;
        if (matchKeyword("IF")) {
            expect("EXISTS");
            ifExists = true;
        }

        String streamName = consumeIdentifier();
        matchPunctuation(";");
        return new DropStreamNode(streamName, ifExists, loc);
    }

    // ---- SELECT ... FROM stream WINDOW ... EMIT ... ----

    private StreamSelectNode parseStreamSelect() {
        SourceLocation loc = location();
        expect("SELECT");

        List<SelectItem> selectItems = parseSelectItems();

        expect("FROM");
        String fromStream = consumeIdentifier();

        // WINDOW clause
        WindowSpec window = null;
        if (matchIdentifier("WINDOW")) {
            window = parseWindowSpec();
        }

        // WHERE clause
        WhereClause where = null;
        if (matchKeyword("WHERE")) {
            where = new WhereClause(parseSimpleCondition());
        }

        // GROUP BY clause
        GroupByClause groupBy = null;
        if (matchKeyword("GROUP")) {
            expect("BY");
            var cols = new ArrayList<String>();
            cols.add(consumeIdentifier());
            while (matchPunctuation(",")) {
                cols.add(consumeIdentifier());
            }
            groupBy = new GroupByClause(cols);
        }

        // EMIT strategy
        EmitStrategy emit = EmitStrategy.CHANGES; // default
        if (matchIdentifier("EMIT")) {
            Token emitToken = peek();
            String emitVal = emitToken.value().toUpperCase();
            if (emitVal.equals("CHANGES")) {
                advance();
                emit = EmitStrategy.CHANGES;
            } else if (emitVal.equals("FINAL")) {
                advance();
                emit = EmitStrategy.FINAL;
            } else {
                throw error("Expected CHANGES or FINAL after EMIT, got: " + emitToken.value());
            }
        }

        matchPunctuation(";");
        return new StreamSelectNode(selectItems, fromStream, window, where, groupBy, emit, loc);
    }

    private WindowSpec parseWindowSpec() {
        Token typeToken = peek();
        String typeStr = typeToken.value().toUpperCase();
        advance();

        WindowType windowType = switch (typeStr) {
            case "TUMBLING" -> WindowType.TUMBLING;
            case "HOPPING" -> WindowType.HOPPING;
            case "SLIDING" -> WindowType.SLIDING;
            case "SESSION" -> WindowType.SESSION;
            default -> throw error("Invalid window type: " + typeStr + ". Expected TUMBLING, HOPPING, SLIDING, or SESSION");
        };

        expect("(");

        long durationMs = 0;
        long advanceMs = 0;
        long gapMs = 0;

        switch (windowType) {
            case TUMBLING -> {
                expectIdentifier("SIZE");
                durationMs = parseDurationMs();
            }
            case HOPPING -> {
                expectIdentifier("SIZE");
                durationMs = parseDurationMs();
                matchPunctuation(",");
                expectIdentifier("ADVANCE");
                advanceMs = parseDurationMs();
            }
            case SLIDING -> {
                expectIdentifier("SIZE");
                durationMs = parseDurationMs();
            }
            case SESSION -> {
                expectIdentifier("GAP");
                gapMs = parseDurationMs();
            }
        }

        expect(")");
        return new WindowSpec(windowType, durationMs, advanceMs, gapMs);
    }

    private long parseDurationMs() {
        Token numToken = peek();
        if (numToken.type() != TokenType.NUMBER) {
            throw error("Expected number for duration, got: " + numToken.value());
        }
        advance();
        long value = Long.parseLong(numToken.value());

        Token unitToken = peek();
        String unit = unitToken.value().toUpperCase();
        advance();

        return switch (unit) {
            case "MILLISECONDS", "MILLIS", "MS" -> value;
            case "SECONDS", "SECOND", "SEC" -> value * 1000;
            case "MINUTES", "MINUTE", "MIN" -> value * 60_000;
            case "HOURS", "HOUR" -> value * 3_600_000;
            case "DAYS", "DAY" -> value * 86_400_000;
            default -> throw error("Unknown time unit: " + unit + ". Expected SECONDS, MINUTES, HOURS, etc.");
        };
    }

    // ---- INSERT INTO stream SELECT ... ----

    private InsertIntoStreamNode parseInsertInto() {
        SourceLocation loc = location();
        expect("INSERT");
        expect("INTO");
        String targetStream = consumeIdentifier();

        StreamSelectNode query = parseStreamSelect();
        return new InsertIntoStreamNode(targetStream, query, loc);
    }

    // ---- SELECT items ----

    private List<SelectItem> parseSelectItems() {
        var items = new ArrayList<SelectItem>();
        items.add(parseSelectItem());
        while (matchPunctuation(",")) {
            items.add(parseSelectItem());
        }
        return items;
    }

    private SelectItem parseSelectItem() {
        Token t = peek();

        // Star
        if (t.type() == TokenType.OPERATOR && t.value().equals("*")) {
            advance();
            return new SelectItem("*", null, true);
        }

        // Aggregate function or column reference
        String expr = parseExpressionString();
        String alias = null;
        if (matchKeyword("AS")) {
            alias = consumeIdentifier();
        }
        return new SelectItem(expr, alias, false);
    }

    private String parseExpressionString() {
        Token t = peek();
        String name = t.value();

        // Check for function call: NAME(...)
        if ((t.type() == TokenType.KEYWORD || t.type() == TokenType.IDENTIFIER)
                && pos + 1 < tokens.size()
                && tokens.get(pos + 1).type() == TokenType.PUNCTUATION
                && tokens.get(pos + 1).value().equals("(")) {
            advance(); // name
            advance(); // (
            var sb = new StringBuilder(name.toUpperCase()).append("(");
            int depth = 1;
            while (depth > 0 && peek().type() != TokenType.EOF) {
                Token inner = peek();
                if (inner.value().equals("(")) depth++;
                else if (inner.value().equals(")")) depth--;
                if (depth > 0) {
                    sb.append(inner.value());
                }
                advance();
            }
            sb.append(")");
            return sb.toString();
        }

        advance();
        return name;
    }

    // ---- Simple WHERE condition parsing ----

    private ssg.pex.sql.ast.SqlExpression parseSimpleCondition() {
        // Parse: column operator value [AND/OR column operator value ...]
        var left = parseSimpleComparison();
        while (peekKeyword("AND") || peekKeyword("OR")) {
            String op = peek().value().toUpperCase();
            advance();
            var right = parseSimpleComparison();
            left = new ssg.pex.sql.ast.SqlExpression.BinaryExpr(left, op, right);
        }
        return left;
    }

    private ssg.pex.sql.ast.SqlExpression parseSimpleComparison() {
        Token colToken = peek();
        advance();
        var colRef = new ssg.pex.sql.ast.SqlExpression.ColumnRef(null, colToken.value());

        Token opToken = peek();
        String operator = opToken.value();
        advance();

        Token valToken = peek();
        advance();
        Object value;
        if (valToken.type() == TokenType.STRING) {
            value = valToken.value();
        } else if (valToken.type() == TokenType.NUMBER) {
            String v = valToken.value();
            value = v.contains(".") ? Double.parseDouble(v) : Long.parseLong(v);
        } else if (valToken.type() == TokenType.KEYWORD && valToken.value().equals("NULL")) {
            value = null;
        } else if (valToken.type() == TokenType.KEYWORD && valToken.value().equals("TRUE")) {
            value = true;
        } else if (valToken.type() == TokenType.KEYWORD && valToken.value().equals("FALSE")) {
            value = false;
        } else {
            value = valToken.value();
        }

        return new ssg.pex.sql.ast.SqlExpression.BinaryExpr(
                colRef, operator, new ssg.pex.sql.ast.SqlExpression.LiteralExpr(value));
    }

    // ---- Token helpers ----

    private Token peek() {
        if (pos >= tokens.size()) {
            return new Token(TokenType.EOF, "", -1);
        }
        return tokens.get(pos);
    }

    private Token advance() {
        Token t = peek();
        pos++;
        return t;
    }

    private boolean matchKeyword(String keyword) {
        Token t = peek();
        if (t.type() == TokenType.KEYWORD && t.value().equalsIgnoreCase(keyword)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean peekKeyword(String keyword) {
        Token t = peek();
        return t.type() == TokenType.KEYWORD && t.value().equalsIgnoreCase(keyword);
    }

    private boolean matchIdentifier(String name) {
        Token t = peek();
        if ((t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD)
                && t.value().equalsIgnoreCase(name)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchPunctuation(String punct) {
        Token t = peek();
        if ((t.type() == TokenType.PUNCTUATION || t.type() == TokenType.OPERATOR) && t.value().equals(punct)) {
            advance();
            return true;
        }
        return false;
    }

    private void expect(String value) {
        Token t = peek();
        if (t.value().equalsIgnoreCase(value) || t.value().equals(value)) {
            advance();
        } else {
            throw error("Expected '" + value + "', got '" + t.value() + "' at position " + t.position());
        }
    }

    private void expectIdentifier(String name) {
        Token t = peek();
        if ((t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD)
                && t.value().equalsIgnoreCase(name)) {
            advance();
        } else {
            throw error("Expected '" + name + "', got '" + t.value() + "'");
        }
    }

    private void expectOperator(String op) {
        Token t = peek();
        if (t.type() == TokenType.OPERATOR && t.value().equals(op)) {
            advance();
        } else {
            throw error("Expected operator '" + op + "', got '" + t.value() + "'");
        }
    }

    private String consumeIdentifier() {
        Token t = peek();
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD) {
            advance();
            return t.value();
        }
        throw error("Expected identifier, got: " + t);
    }

    private SourceLocation location() {
        Token t = peek();
        return SourceLocation.at(t.position(), 0);
    }

    private StreamParseException error(String message) {
        return new StreamParseException(message);
    }

    public static class StreamParseException extends RuntimeException {
        public StreamParseException(String message) {
            super(message);
        }
    }
}

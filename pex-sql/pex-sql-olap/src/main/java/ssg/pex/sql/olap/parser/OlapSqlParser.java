package ssg.pex.sql.olap.parser;

import ssg.pex.ast.SourceLocation;
import ssg.pex.result.Result;
import ssg.pex.sql.ast.SelectNode;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlNode;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.grammar.SqlParser;
import ssg.pex.sql.grammar.SqlTokenizer;
import ssg.pex.sql.grammar.SqlTokenizer.Token;
import ssg.pex.sql.grammar.SqlTokenizer.TokenType;
import ssg.pex.sql.olap.ast.*;
import ssg.pex.sql.olap.ast.FrameSpec.FrameType;
import ssg.pex.sql.olap.ast.GroupingSetSpec.GroupingType;

import java.util.*;

/**
 * Extended SQL parser with OLAP capabilities: window functions, CTEs, MERGE, CUBE/ROLLUP, PIVOT/UNPIVOT.
 * Wraps the core SqlParser and adds OLAP-specific parsing.
 */
public class OlapSqlParser {

    private final SqlParser coreParser;
    private List<Token> tokens;
    private int pos;

    public OlapSqlParser() {
        this.coreParser = new SqlParser();
    }

    /**
     * Parse a SQL string. Returns either a standard SqlNode or an OLAP-specific node.
     */
    public Result<Object> parse(String sql) {
        try {
            var tokenizer = new SqlTokenizer(sql);
            this.tokens = tokenizer.tokenize();
            this.pos = 0;

            String firstKeyword = peekKeywordValue();

            // WITH clause -> CTE
            if (firstKeyword.equals("WITH")) {
                return Result.success(parseWithClause());
            }

            // MERGE -> MergeNode
            if (firstKeyword.equals("MERGE")) {
                return Result.success(parseMerge());
            }

            // For SELECT, try core parser first, then detect OLAP extensions
            if (firstKeyword.equals("SELECT")) {
                return parseSelectWithOlapExtensions(sql);
            }

            // Delegate everything else to core parser
            return coreParser.parse(sql).map(n -> n);
        } catch (Exception e) {
            return Result.failure("OLAP_PARSE_ERROR", e.getMessage(), e);
        }
    }

    // ---- WITH (CTE) parsing ----

    private WithClause parseWithClause() {
        SourceLocation loc = location();
        expect("WITH");
        boolean recursive = matchKeyword("RECURSIVE");

        var ctes = new ArrayList<CteDefinition>();
        ctes.add(parseCteDefinition());
        while (matchPunctuation(",")) {
            ctes.add(parseCteDefinition());
        }

        return new WithClause(ctes, recursive, loc);
    }

    private CteDefinition parseCteDefinition() {
        String name = consumeIdentifier();

        List<String> columnAliases = List.of();
        if (matchPunctuation("(")) {
            columnAliases = parseIdentifierListUntilClose();
        }

        expect("AS");
        expect("(");

        // Parse the inner SELECT by finding the matching closing parenthesis
        SelectNode query = parseInnerSelect();

        expect(")");

        return new CteDefinition(name, columnAliases, query);
    }

    private SelectNode parseInnerSelect() {
        // Collect tokens for the inner SELECT until matching ')'
        int depth = 0;
        int startPos = pos;
        var innerTokens = new ArrayList<Token>();

        while (pos < tokens.size()) {
            Token t = tokens.get(pos);
            if (t.type() == TokenType.PUNCTUATION && t.value().equals("(")) {
                depth++;
                innerTokens.add(t);
                pos++;
            } else if (t.type() == TokenType.PUNCTUATION && t.value().equals(")")) {
                if (depth == 0) {
                    break; // This is the closing paren of the CTE
                }
                depth--;
                innerTokens.add(t);
                pos++;
            } else {
                innerTokens.add(t);
                pos++;
            }
        }

        // Build the SQL string from tokens for the inner select
        var sb = new StringBuilder();
        for (Token t : innerTokens) {
            if (!sb.isEmpty()) sb.append(" ");
            if (t.type() == TokenType.STRING) {
                sb.append("'").append(t.value()).append("'");
            } else {
                sb.append(t.value());
            }
        }

        var result = coreParser.parse(sb.toString());
        if (result.isFailure()) {
            throw new RuntimeException("Failed to parse CTE body: " + result.error().message());
        }
        return (SelectNode) result.value();
    }

    // ---- MERGE parsing ----

    private MergeNode parseMerge() {
        SourceLocation loc = location();
        expect("MERGE");
        expect("INTO");

        String targetTable = consumeIdentifier();
        String targetAlias = null;
        if (peekIsIdentifier() && !peekKeywordEquals("USING")) {
            if (matchKeyword("AS")) {
                targetAlias = consumeIdentifier();
            } else {
                targetAlias = consumeIdentifier();
            }
        }

        expect("USING");
        String sourceTable = consumeIdentifier();
        String sourceAlias = null;
        if (peekIsIdentifier() && !peekKeywordEquals("ON")) {
            if (matchKeyword("AS")) {
                sourceAlias = consumeIdentifier();
            } else {
                sourceAlias = consumeIdentifier();
            }
        }

        expect("ON");
        SqlExpression onCondition = parseSimpleCondition();

        var actions = new ArrayList<MergeAction>();
        while (matchKeyword("WHEN")) {
            actions.add(parseMergeAction());
        }

        matchPunctuation(";");
        return new MergeNode(targetTable, sourceTable, targetAlias, sourceAlias, onCondition, actions, loc);
    }

    private MergeAction parseMergeAction() {
        if (matchKeyword("MATCHED")) {
            expect("THEN");
            if (matchKeyword("UPDATE")) {
                expect("SET");
                var setCols = new LinkedHashMap<String, SqlExpression>();
                parseSetColumn(setCols);
                while (matchPunctuation(",")) {
                    parseSetColumn(setCols);
                }
                return new MergeAction.WhenMatchedUpdate(setCols);
            } else if (matchKeyword("DELETE")) {
                return new MergeAction.WhenMatchedDelete();
            }
            throw new RuntimeException("Expected UPDATE or DELETE after WHEN MATCHED THEN");
        } else if (matchKeyword("NOT")) {
            expect("MATCHED");
            expect("THEN");
            expect("INSERT");

            List<String> columns = List.of();
            if (matchPunctuation("(")) {
                columns = parseIdentifierListUntilClose();
            }

            expect("VALUES");
            expect("(");
            var values = new ArrayList<SqlExpression>();
            values.add(parseSimpleExpression());
            while (matchPunctuation(",")) {
                values.add(parseSimpleExpression());
            }
            expect(")");

            return new MergeAction.WhenNotMatchedInsert(columns, values);
        }
        throw new RuntimeException("Expected MATCHED or NOT MATCHED after WHEN");
    }

    private void parseSetColumn(Map<String, SqlExpression> setCols) {
        // Handle target.col = source.col format
        String part1 = consumeIdentifier();
        String colName;
        if (matchPunctuation(".")) {
            colName = consumeIdentifier();
        } else {
            colName = part1;
        }
        expectOperator("=");
        SqlExpression value = parseSimpleExpression();
        setCols.put(colName, value);
    }

    // ---- SELECT with OLAP extensions ----

    private Result<Object> parseSelectWithOlapExtensions(String sql) {
        // First, use core parser for the base SELECT
        var coreResult = coreParser.parse(sql);
        if (coreResult.isFailure()) {
            return Result.failure(coreResult.error());
        }
        return Result.success(coreResult.value());
    }

    // ---- Window function parsing (used by OlapDatabase) ----

    /**
     * Parses window function calls from a SELECT item string, e.g. "ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary)".
     */
    public WindowFunctionCall parseWindowFunction(String expr) {
        var tokenizer = new SqlTokenizer(expr);
        this.tokens = tokenizer.tokenize();
        this.pos = 0;

        String funcName = consumeIdentifier().toUpperCase();
        expect("(");

        var args = new ArrayList<SqlExpression>();
        if (!peek().value().equals(")")) {
            args.add(parseSimpleExpression());
            while (matchPunctuation(",")) {
                args.add(parseSimpleExpression());
            }
        }
        expect(")");

        expect("OVER");
        expect("(");

        List<String> partitionBy = List.of();
        if (matchKeyword("PARTITION")) {
            expect("BY");
            partitionBy = parseIdentifierList();
        }

        OrderByClause orderBy = null;
        if (matchKeyword("ORDER")) {
            expect("BY");
            orderBy = parseOrderByClause();
        }

        FrameSpec frame = null;
        if (matchKeyword("ROWS") || matchKeyword("RANGE")) {
            String frameTypeStr = tokens.get(pos - 1).value().toUpperCase();
            FrameType frameType = frameTypeStr.equals("ROWS") ? FrameType.ROWS : FrameType.RANGE;
            frame = parseFrameSpec(frameType);
        }

        expect(")");

        return new WindowFunctionCall(funcName, args,
                new OverClause(partitionBy, orderBy, frame),
                SourceLocation.UNKNOWN);
    }

    private FrameSpec parseFrameSpec(FrameType frameType) {
        if (matchKeyword("BETWEEN")) {
            FrameBound start = parseFrameBound();
            expect("AND");
            FrameBound end = parseFrameBound();
            return new FrameSpec(frameType, start, end);
        }
        // Single bound: shorthand for BETWEEN <bound> AND CURRENT ROW
        FrameBound start = parseFrameBound();
        return new FrameSpec(frameType, start, FrameBound.currentRow());
    }

    private FrameBound parseFrameBound() {
        if (matchKeyword("UNBOUNDED")) {
            if (matchKeyword("PRECEDING")) {
                return FrameBound.unboundedPreceding();
            } else {
                expect("FOLLOWING");
                return FrameBound.unboundedFollowing();
            }
        }
        if (matchKeyword("CURRENT")) {
            expect("ROW");
            return FrameBound.currentRow();
        }
        // N PRECEDING or N FOLLOWING
        int n = Integer.parseInt(consumeNumber());
        if (matchKeyword("PRECEDING")) {
            return FrameBound.preceding(n);
        }
        expect("FOLLOWING");
        return FrameBound.following(n);
    }

    /**
     * Parse a PIVOT clause string.
     */
    public PivotClause parsePivot(String expr) {
        var tokenizer = new SqlTokenizer(expr);
        this.tokens = tokenizer.tokenize();
        this.pos = 0;

        expect("PIVOT");
        expect("(");

        String aggFunc = consumeIdentifier().toUpperCase();
        expect("(");
        String aggCol = consumeIdentifier();
        expect(")");

        expect("FOR");
        String forCol = consumeIdentifier();

        expect("IN");
        expect("(");
        var inValues = new ArrayList<Object>();
        inValues.add(parseLiteralValue());
        while (matchPunctuation(",")) {
            inValues.add(parseLiteralValue());
        }
        expect(")");
        expect(")");

        return new PivotClause(aggFunc, aggCol, forCol, inValues, SourceLocation.UNKNOWN);
    }

    /**
     * Parse an UNPIVOT clause string.
     */
    public UnpivotClause parseUnpivot(String expr) {
        var tokenizer = new SqlTokenizer(expr);
        this.tokens = tokenizer.tokenize();
        this.pos = 0;

        expect("UNPIVOT");
        expect("(");

        String valueCol = consumeIdentifier();

        expect("FOR");
        String nameCol = consumeIdentifier();

        expect("IN");
        expect("(");
        var sourceCols = new ArrayList<String>();
        sourceCols.add(consumeIdentifier());
        while (matchPunctuation(",")) {
            sourceCols.add(consumeIdentifier());
        }
        expect(")");
        expect(")");

        return new UnpivotClause(valueCol, nameCol, sourceCols, SourceLocation.UNKNOWN);
    }

    /**
     * Parse a GROUPING SET specification.
     */
    public GroupingSetSpec parseGroupingSet(String expr) {
        var tokenizer = new SqlTokenizer(expr);
        this.tokens = tokenizer.tokenize();
        this.pos = 0;

        GroupingType type;
        String keyword = consumeIdentifier().toUpperCase();
        type = switch (keyword) {
            case "CUBE" -> GroupingType.CUBE;
            case "ROLLUP" -> GroupingType.ROLLUP;
            default -> {
                // GROUPING SETS
                expect("SETS");
                yield GroupingType.GROUPING_SETS;
            }
        };

        expect("(");
        var sets = new ArrayList<List<String>>();

        if (type == GroupingType.GROUPING_SETS) {
            // Each item is either a parenthesized list or a single column or ()
            sets.add(parseGroupingSetElement());
            while (matchPunctuation(",")) {
                sets.add(parseGroupingSetElement());
            }
        } else {
            // CUBE/ROLLUP: simple list of columns, each becomes a single-element set
            var columns = new ArrayList<String>();
            columns.add(consumeIdentifier());
            while (matchPunctuation(",")) {
                columns.add(consumeIdentifier());
            }
            for (String col : columns) {
                sets.add(List.of(col));
            }
        }

        expect(")");

        return new GroupingSetSpec(type, sets, SourceLocation.UNKNOWN);
    }

    private List<String> parseGroupingSetElement() {
        if (matchPunctuation("(")) {
            if (peek().value().equals(")")) {
                // Empty grouping set ()
                expect(")");
                return List.of();
            }
            var cols = new ArrayList<String>();
            cols.add(consumeIdentifier());
            while (matchPunctuation(",")) {
                cols.add(consumeIdentifier());
            }
            expect(")");
            return cols;
        }
        return List.of(consumeIdentifier());
    }

    // ---- Simple expression parsing for MERGE values ----

    private SqlExpression parseSimpleExpression() {
        return parseSimpleAddition();
    }

    private SqlExpression parseSimpleAddition() {
        SqlExpression left = parseSimplePrimary();
        while (true) {
            Token op = peek();
            if (op.type() == TokenType.OPERATOR && (op.value().equals("+") || op.value().equals("-"))) {
                advance();
                SqlExpression right = parseSimplePrimary();
                left = new BinaryExpr(left, op.value(), right);
            } else {
                break;
            }
        }
        return left;
    }

    private SqlExpression parseSimpleCondition() {
        SqlExpression left = parseSimpleExpression();

        Token op = peek();
        if (op.type() == TokenType.OPERATOR) {
            String opStr = op.value();
            if (opStr.equals("=") || opStr.equals("<") || opStr.equals(">") ||
                    opStr.equals("<=") || opStr.equals(">=") || opStr.equals("<>") || opStr.equals("!=")) {
                advance();
                SqlExpression right = parseSimpleExpression();
                left = new BinaryExpr(left, opStr, right);
            }
        }

        while (matchKeyword("AND")) {
            SqlExpression right = parseSimpleConditionUnit();
            left = new BinaryExpr(left, "AND", right);
        }

        return left;
    }

    private SqlExpression parseSimpleConditionUnit() {
        SqlExpression left = parseSimpleExpression();
        Token op = peek();
        if (op.type() == TokenType.OPERATOR) {
            String opStr = op.value();
            if (opStr.equals("=") || opStr.equals("<") || opStr.equals(">") ||
                    opStr.equals("<=") || opStr.equals(">=") || opStr.equals("<>") || opStr.equals("!=")) {
                advance();
                SqlExpression right = parseSimpleExpression();
                left = new BinaryExpr(left, opStr, right);
            }
        }
        return left;
    }

    private SqlExpression parseSimplePrimary() {
        Token t = peek();

        // Handle unary minus for negative literals
        if (t.type() == TokenType.OPERATOR && t.value().equals("-")) {
            advance();
            Token next = peek();
            if (next.type() == TokenType.NUMBER) {
                advance();
                String val = next.value();
                if (val.contains(".")) {
                    return new LiteralExpr(-Double.parseDouble(val));
                }
                return new LiteralExpr(-Long.parseLong(val));
            }
            // General unary minus on an expression
            SqlExpression operand = parseSimplePrimary();
            return new BinaryExpr(new LiteralExpr(0L), "-", operand);
        }

        if (t.type() == TokenType.NUMBER) {
            advance();
            String val = t.value();
            if (val.contains(".")) {
                return new LiteralExpr(Double.parseDouble(val));
            }
            return new LiteralExpr(Long.parseLong(val));
        }

        if (t.type() == TokenType.STRING) {
            advance();
            return new LiteralExpr(t.value());
        }

        if (t.type() == TokenType.KEYWORD && t.value().equals("NULL")) {
            advance();
            return new LiteralExpr(null);
        }

        if (t.type() == TokenType.KEYWORD && t.value().equals("TRUE")) {
            advance();
            return new LiteralExpr(true);
        }

        if (t.type() == TokenType.KEYWORD && t.value().equals("FALSE")) {
            advance();
            return new LiteralExpr(false);
        }

        if (t.type() == TokenType.OPERATOR && t.value().equals("*")) {
            advance();
            return new StarExpr();
        }

        // Identifier or qualified name
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD) {
            String name = t.value();
            advance();

            // Check for table.column
            if (peek().type() == TokenType.PUNCTUATION && peek().value().equals(".")) {
                advance(); // skip dot
                String col = consumeIdentifier();
                return new ColumnRef(name, col);
            }

            return new ColumnRef(null, name);
        }

        if (t.type() == TokenType.PUNCTUATION && t.value().equals("(")) {
            advance();
            SqlExpression inner = parseSimpleExpression();
            expect(")");
            return inner;
        }

        throw new RuntimeException("Unexpected token in expression: " + t);
    }

    // ---- Helpers ----

    private Object parseLiteralValue() {
        Token t = peek();
        if (t.type() == TokenType.NUMBER) {
            advance();
            String val = t.value();
            if (val.contains(".")) return Double.parseDouble(val);
            return Long.parseLong(val);
        }
        if (t.type() == TokenType.STRING) {
            advance();
            return t.value();
        }
        if (t.type() == TokenType.KEYWORD && t.value().equals("NULL")) {
            advance();
            return null;
        }
        throw new RuntimeException("Expected literal value, got: " + t);
    }

    private OrderByClause parseOrderByClause() {
        var items = new ArrayList<OrderByItem>();
        items.add(parseOrderByItem());
        while (matchPunctuation(",")) {
            items.add(parseOrderByItem());
        }
        return new OrderByClause(items);
    }

    private OrderByItem parseOrderByItem() {
        String column = consumeIdentifier();
        boolean ascending = true;
        if (matchKeyword("ASC")) {
            ascending = true;
        } else if (matchKeyword("DESC")) {
            ascending = false;
        }
        boolean nullsFirst = ascending;
        if (matchKeyword("NULLS")) {
            if (matchKeyword("FIRST")) {
                nullsFirst = true;
            } else if (matchKeyword("LAST")) {
                nullsFirst = false;
            }
        }
        return new OrderByItem(column, ascending, nullsFirst);
    }

    private List<String> parseIdentifierList() {
        var ids = new ArrayList<String>();
        ids.add(consumeIdentifier());
        while (matchPunctuation(",")) {
            // Check for end conditions (ORDER, ROWS, RANGE, close paren)
            Token next = peek();
            if (next.type() == TokenType.KEYWORD &&
                    (next.value().equals("ORDER") || next.value().equals("ROWS") || next.value().equals("RANGE"))) {
                break;
            }
            if (next.type() == TokenType.PUNCTUATION && next.value().equals(")")) {
                break;
            }
            ids.add(consumeIdentifier());
        }
        return ids;
    }

    private List<String> parseIdentifierListUntilClose() {
        var ids = new ArrayList<String>();
        if (!peek().value().equals(")")) {
            ids.add(consumeIdentifier());
            while (matchPunctuation(",")) {
                ids.add(consumeIdentifier());
            }
        }
        expect(")");
        return ids;
    }

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
        if ((t.type() == TokenType.KEYWORD || t.type() == TokenType.IDENTIFIER) && t.value().equalsIgnoreCase(keyword)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean peekKeywordEquals(String keyword) {
        Token t = peek();
        return (t.type() == TokenType.KEYWORD || t.type() == TokenType.IDENTIFIER)
                && t.value().equalsIgnoreCase(keyword);
    }

    private boolean peekIsIdentifier() {
        Token t = peek();
        return t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD;
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
            throw new RuntimeException("Expected '" + value + "', got '" + t.value() + "' at position " + t.position());
        }
    }

    private void expectOperator(String op) {
        Token t = peek();
        if (t.type() == TokenType.OPERATOR && t.value().equals(op)) {
            advance();
        } else {
            throw new RuntimeException("Expected operator '" + op + "', got '" + t.value() + "'");
        }
    }

    private String consumeIdentifier() {
        Token t = peek();
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD) {
            advance();
            return t.value();
        }
        throw new RuntimeException("Expected identifier, got: " + t);
    }

    private String consumeNumber() {
        Token t = peek();
        if (t.type() == TokenType.NUMBER) {
            advance();
            return t.value();
        }
        throw new RuntimeException("Expected number, got: " + t);
    }

    private String peekKeywordValue() {
        Token t = peek();
        return (t.type() == TokenType.KEYWORD || t.type() == TokenType.IDENTIFIER)
                ? t.value().toUpperCase() : "";
    }

    private SourceLocation location() {
        Token t = peek();
        return SourceLocation.at(t.position(), 0);
    }
}

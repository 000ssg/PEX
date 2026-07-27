package ssg.pex.sql.grammar;

import ssg.pex.ast.SourceLocation;
import ssg.pex.result.Result;
import ssg.pex.sql.ast.*;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.ast.TransactionNode.TransactionAction;
import ssg.pex.sql.grammar.SqlTokenizer.Token;
import ssg.pex.sql.grammar.SqlTokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written recursive descent SQL parser.
 * Produces SQL AST nodes directly.
 */
public class SqlParser {

    private List<Token> tokens;
    private int pos;

    public Result<SqlNode> parse(String sql) {
        try {
            var tokenizer = new SqlTokenizer(sql);
            this.tokens = tokenizer.tokenize();
            this.pos = 0;
            SqlNode node = parseStatement();
            return Result.success(node);
        } catch (SqlParseException e) {
            return Result.failure("SQL_PARSE_ERROR", e.getMessage());
        }
    }

    private SqlNode parseStatement() {
        Token t = peek();
        return switch (t.value().toUpperCase()) {
            case "SELECT" -> parseSelect();
            case "INSERT" -> parseInsert();
            case "UPDATE" -> parseUpdate();
            case "DELETE" -> parseDelete();
            case "CREATE" -> parseCreateStatement();
            case "DROP" -> parseDrop();
            case "ALTER" -> parseAlter();
            case "BEGIN", "START" -> parseTransaction();
            case "COMMIT" -> parseTransaction();
            case "ROLLBACK" -> parseTransaction();
            case "SAVEPOINT" -> parseTransaction();
            case "RELEASE" -> parseTransaction();
            case "CALL" -> parseCall();
            default -> throw error("Unexpected token: " + t.value());
        };
    }

    // ---- SELECT ----

    public SelectNode parseSelect() {
        SourceLocation loc = location();
        expect("SELECT");

        boolean distinct = false;
        if (matchKeyword("DISTINCT")) {
            distinct = true;
        }

        List<SelectItem> selectItems = parseSelectItems();

        FromClause from = null;
        if (matchKeyword("FROM")) {
            from = parseFromClause();
        }

        WhereClause where = null;
        if (matchKeyword("WHERE")) {
            where = new WhereClause(parseExpression());
        }

        GroupByClause groupBy = null;
        if (matchKeyword("GROUP")) {
            expect("BY");
            groupBy = new GroupByClause(parseIdentifierList());
        }

        HavingClause having = null;
        if (matchKeyword("HAVING")) {
            having = new HavingClause(parseExpression());
        }

        OrderByClause orderBy = null;
        if (matchKeyword("ORDER")) {
            expect("BY");
            orderBy = parseOrderByClause();
        }

        LimitClause limit = null;
        if (matchKeyword("LIMIT")) {
            limit = parseLimitClause();
        }

        // Skip optional semicolon
        matchPunctuation(";");

        return new SelectNode(selectItems, from, where, groupBy, having, orderBy, limit, distinct, loc);
    }

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
        if (t.type() == TokenType.OPERATOR && t.value().equals("*")) {
            advance();
            return new SelectItem("*", null, true);
        }

        // Check for table.* pattern
        if (t.type() == TokenType.IDENTIFIER && pos + 1 < tokens.size()) {
            Token next = tokens.get(pos + 1);
            if (next.type() == TokenType.PUNCTUATION && next.value().equals(".")) {
                if (pos + 2 < tokens.size()) {
                    Token afterDot = tokens.get(pos + 2);
                    if (afterDot.type() == TokenType.OPERATOR && afterDot.value().equals("*")) {
                        String tableName = t.value();
                        advance(); // identifier
                        advance(); // dot
                        advance(); // star
                        return new SelectItem(tableName + ".*", null, true);
                    }
                }
            }
        }

        // Parse expression as string representation
        String expr = parseExpressionAsString();
        String alias = null;
        if (matchKeyword("AS")) {
            alias = consumeIdentifier();
        } else if (peek().type() == TokenType.IDENTIFIER && !isClauseKeyword(peek().value())) {
            alias = consumeIdentifier();
        }
        return new SelectItem(expr, alias, false);
    }

    private String parseExpressionAsString() {
        SqlExpression expr = parseExpression();
        return expressionToString(expr);
    }

    private String expressionToString(SqlExpression expr) {
        return switch (expr) {
            case ColumnRef cr -> cr.table() != null ? cr.table() + "." + cr.column() : cr.column();
            case LiteralExpr le -> le.value() == null ? "NULL" : le.value().toString();
            case BinaryExpr be -> expressionToString(be.left()) + " " + be.operator() + " " + expressionToString(be.right());
            case UnaryExpr ue -> ue.operator() + " " + expressionToString(ue.operand());
            case FunctionExpr fe -> fe.name() + "(" + fe.args().stream().map(this::expressionToString).reduce((a, b) -> a + ", " + b).orElse("") + ")";
            case AggregateExpr ae -> ae.func().name() + "(" + (ae.distinct() ? "DISTINCT " : "") + (ae.arg() != null ? expressionToString(ae.arg()) : "*") + ")";
            case StarExpr ignored -> "*";
            case InExpr ie -> expressionToString(ie.value()) + (ie.negated() ? " NOT IN " : " IN ") + "(" + ie.list().stream().map(this::expressionToString).reduce((a, b) -> a + ", " + b).orElse("") + ")";
            case BetweenExpr be -> expressionToString(be.value()) + (be.negated() ? " NOT BETWEEN " : " BETWEEN ") + expressionToString(be.low()) + " AND " + expressionToString(be.high());
            case LikeExpr le -> expressionToString(le.value()) + (le.negated() ? " NOT LIKE " : " LIKE ") + "'" + le.pattern() + "'";
            case IsNullExpr ine -> expressionToString(ine.value()) + (ine.negated() ? " IS NOT NULL" : " IS NULL");
            case ExistsExpr ignored -> "EXISTS(...)";
            case SubqueryExpr ignored -> "(SELECT ...)";
            case CaseExpr ignored -> "CASE ...";
        };
    }

    private boolean isClauseKeyword(String value) {
        String upper = value.toUpperCase();
        return upper.equals("FROM") || upper.equals("WHERE") || upper.equals("GROUP") ||
                upper.equals("HAVING") || upper.equals("ORDER") || upper.equals("LIMIT") ||
                upper.equals("UNION") || upper.equals("INTERSECT") || upper.equals("EXCEPT") ||
                upper.equals("ON") || upper.equals("JOIN") || upper.equals("LEFT") ||
                upper.equals("RIGHT") || upper.equals("INNER") || upper.equals("FULL") ||
                upper.equals("CROSS") || upper.equals("SET") || upper.equals("VALUES") ||
                upper.equals("INTO") || upper.equals("AND") || upper.equals("OR") ||
                upper.equals("THEN") || upper.equals("WHEN") || upper.equals("ELSE") ||
                upper.equals("END");
    }

    private FromClause parseFromClause() {
        var tables = new ArrayList<TableRef>();
        tables.add(parseTableRef());
        while (matchPunctuation(",")) {
            tables.add(parseTableRef());
        }
        return new FromClause(tables);
    }

    private TableRef parseTableRef() {
        // Handle subquery in FROM
        if (peek().type() == TokenType.PUNCTUATION && peek().value().equals("(")) {
            advance(); // skip (
            // For now, treat as a subquery - but skip it in TableRef
            // Simple approach: just parse inner select and wrap
            advance(); // simplify - skip past
            // TODO: full subquery-in-FROM support
        }

        String tableName = consumeIdentifier();
        String alias = null;
        if (matchKeyword("AS")) {
            alias = consumeIdentifier();
        } else if (peek().type() == TokenType.IDENTIFIER && !isClauseKeyword(peek().value()) && !isJoinKeyword(peek().value())) {
            alias = consumeIdentifier();
        }

        JoinClause join = null;
        if (isJoinKeyword(peek().value())) {
            join = parseJoinClause();
        }

        return new TableRef(tableName, alias, join);
    }

    private boolean isJoinKeyword(String value) {
        String upper = value.toUpperCase();
        return upper.equals("JOIN") || upper.equals("LEFT") || upper.equals("RIGHT") ||
                upper.equals("INNER") || upper.equals("FULL") || upper.equals("CROSS");
    }

    private JoinClause parseJoinClause() {
        JoinType joinType = JoinType.INNER;
        Token t = peek();
        String upper = t.value().toUpperCase();

        if (upper.equals("LEFT")) {
            joinType = JoinType.LEFT;
            advance();
            matchKeyword("OUTER");
        } else if (upper.equals("RIGHT")) {
            joinType = JoinType.RIGHT;
            advance();
            matchKeyword("OUTER");
        } else if (upper.equals("FULL")) {
            joinType = JoinType.FULL;
            advance();
            matchKeyword("OUTER");
        } else if (upper.equals("CROSS")) {
            joinType = JoinType.CROSS;
            advance();
        } else if (upper.equals("INNER")) {
            joinType = JoinType.INNER;
            advance();
        }

        expect("JOIN");

        String tableName = consumeIdentifier();
        String alias = null;
        if (matchKeyword("AS")) {
            alias = consumeIdentifier();
        } else if (peek().type() == TokenType.IDENTIFIER && !isClauseKeyword(peek().value()) && !peek().value().equalsIgnoreCase("ON")) {
            alias = consumeIdentifier();
        }

        SqlExpression onExpr = null;
        if (joinType != JoinType.CROSS && matchKeyword("ON")) {
            onExpr = parseExpression();
        }

        return new JoinClause(joinType, tableName, alias, onExpr);
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
        boolean nullsFirst = ascending; // default
        if (matchKeyword("NULLS")) {
            if (matchKeyword("FIRST")) {
                nullsFirst = true;
            } else if (matchKeyword("LAST")) {
                nullsFirst = false;
            }
        }
        return new OrderByItem(column, ascending, nullsFirst);
    }

    private LimitClause parseLimitClause() {
        int limitVal = Integer.parseInt(consumeNumber());
        int offset = 0;
        if (matchKeyword("OFFSET")) {
            offset = Integer.parseInt(consumeNumber());
        } else if (matchPunctuation(",")) {
            // MySQL style: LIMIT offset, count
            int second = Integer.parseInt(consumeNumber());
            offset = limitVal;
            limitVal = second;
        }
        return new LimitClause(limitVal, offset);
    }

    // ---- INSERT ----

    public InsertNode parseInsert() {
        SourceLocation loc = location();
        expect("INSERT");
        expect("INTO");
        String tableName = consumeIdentifier();

        List<String> columns = List.of();
        if (matchPunctuation("(")) {
            columns = parseIdentifierListUntilClose();
        }

        SelectNode subquery = null;
        List<List<Object>> valueRows = null;

        if (peekKeyword("SELECT")) {
            subquery = parseSelect();
        } else {
            expect("VALUES");
            valueRows = new ArrayList<>();
            valueRows.add(parseValueRow());
            while (matchPunctuation(",")) {
                valueRows.add(parseValueRow());
            }
        }

        matchPunctuation(";");
        return new InsertNode(tableName, columns, valueRows, subquery, loc);
    }

    private List<Object> parseValueRow() {
        expect("(");
        var values = new ArrayList<Object>();
        values.add(parseLiteralValue());
        while (matchPunctuation(",")) {
            values.add(parseLiteralValue());
        }
        expect(")");
        return values;
    }

    private Object parseLiteralValue() {
        Token t = peek();
        if (t.type() == TokenType.NUMBER) {
            advance();
            String val = t.value();
            if (val.contains(".")) {
                return Double.parseDouble(val);
            }
            return Long.parseLong(val);
        } else if (t.type() == TokenType.STRING) {
            advance();
            return t.value();
        } else if (t.type() == TokenType.KEYWORD && t.value().equals("NULL")) {
            advance();
            return null;
        } else if (t.type() == TokenType.KEYWORD && t.value().equals("TRUE")) {
            advance();
            return true;
        } else if (t.type() == TokenType.KEYWORD && t.value().equals("FALSE")) {
            advance();
            return false;
        } else if (t.type() == TokenType.OPERATOR && t.value().equals("-")) {
            advance();
            Token num = peek();
            if (num.type() == TokenType.NUMBER) {
                advance();
                String val = num.value();
                if (val.contains(".")) {
                    return -Double.parseDouble(val);
                }
                return -Long.parseLong(val);
            }
            throw error("Expected number after '-'");
        }
        throw error("Expected literal value, got: " + t);
    }

    // ---- UPDATE ----

    public UpdateNode parseUpdate() {
        SourceLocation loc = location();
        expect("UPDATE");
        String tableName = consumeIdentifier();
        expect("SET");

        var setClauses = new ArrayList<SetClause>();
        setClauses.add(parseSetClause());
        while (matchPunctuation(",")) {
            setClauses.add(parseSetClause());
        }

        WhereClause where = null;
        if (matchKeyword("WHERE")) {
            where = new WhereClause(parseExpression());
        }

        matchPunctuation(";");
        return new UpdateNode(tableName, setClauses, where, loc);
    }

    private SetClause parseSetClause() {
        String column = consumeIdentifier();
        expectOperator("=");
        // Support full expressions (e.g., salary + 5000, column references, function calls)
        SqlExpression expr = parseExpression();
        return new SetClause(column, expr);
    }

    // ---- DELETE ----

    public DeleteNode parseDelete() {
        SourceLocation loc = location();
        expect("DELETE");
        expect("FROM");
        String tableName = consumeIdentifier();

        WhereClause where = null;
        if (matchKeyword("WHERE")) {
            where = new WhereClause(parseExpression());
        }

        matchPunctuation(";");
        return new DeleteNode(tableName, where, loc);
    }

    // ---- CREATE ----

    private SqlNode parseCreateStatement() {
        expect("CREATE");

        boolean orReplace = false;
        boolean unique = false;
        boolean temporary = false;

        if (matchKeyword("OR")) {
            expect("REPLACE");
            orReplace = true;
        }

        if (matchKeyword("UNIQUE")) {
            unique = true;
        }

        // Support CREATE TEMP TABLE and CREATE TEMPORARY TABLE (PostgreSQL / MySQL)
        // Also CREATE GLOBAL TEMPORARY TABLE (Oracle GTT, treated the same way)
        if (matchKeyword("GLOBAL")) {
            // Oracle: CREATE GLOBAL TEMPORARY TABLE ...
            temporary = true;
        }
        if (matchKeyword("TEMP") || matchKeyword("TEMPORARY")) {
            temporary = true;
        }

        Token t = peek();
        return switch (t.value().toUpperCase()) {
            case "TABLE" -> parseCreateTable(temporary);
            case "INDEX" -> parseCreateIndex(unique);
            case "VIEW" -> parseCreateView(orReplace);
            case "TRIGGER" -> parseCreateTrigger();
            case "PROCEDURE" -> parseCreateProcedure();
            default -> throw error("Expected TABLE, INDEX, VIEW, TRIGGER, or PROCEDURE after CREATE, got: " + t.value());
        };
    }

    public CreateTableNode parseCreateTable() {
        return parseCreateTable(false);
    }

    public CreateTableNode parseCreateTable(boolean isTemporary) {
        SourceLocation loc = location();
        expect("TABLE");

        boolean ifNotExists = false;
        if (matchKeyword("IF")) {
            expect("NOT");
            // Handle the case where NOT might be treated as keyword and EXISTS might follow
            if (peekKeyword("EXISTS")) {
                advance();
                ifNotExists = true;
            } else {
                throw error("Expected EXISTS after IF NOT");
            }
        }

        String tableName = consumeIdentifier();
        expect("(");

        var columns = new ArrayList<ColumnDef>();
        var constraints = new ArrayList<TableConstraint>();

        parseTableElements(columns, constraints);

        expect(")");
        // Oracle GTT: skip optional "ON COMMIT DELETE ROWS" / "ON COMMIT PRESERVE ROWS"
        if (matchKeyword("ON")) {
            expect("COMMIT");
            Token action = peek();
            String actionUpper = action.value().toUpperCase();
            if (actionUpper.equals("DELETE") || actionUpper.equals("PRESERVE")) {
                advance(); // DELETE / PRESERVE
                expect("ROWS");
            }
        }
        matchPunctuation(";");

        return new CreateTableNode(tableName, columns, constraints, ifNotExists, isTemporary, loc);
    }

    private void parseTableElements(List<ColumnDef> columns, List<TableConstraint> constraints) {
        parseTableElement(columns, constraints);
        while (matchPunctuation(",")) {
            parseTableElement(columns, constraints);
        }
    }

    private void parseTableElement(List<ColumnDef> columns, List<TableConstraint> constraints) {
        Token t = peek();
        if (t.type() == TokenType.KEYWORD && (t.value().equals("PRIMARY") || t.value().equals("FOREIGN") ||
                t.value().equals("UNIQUE") || t.value().equals("CHECK") || t.value().equals("CONSTRAINT"))) {
            constraints.add(parseTableConstraint());
        } else {
            columns.add(parseColumnDef());
        }
    }

    private ColumnDef parseColumnDef() {
        String name = consumeIdentifier();
        SqlDataType dataType = parseDataType();

        boolean nullable = true;
        Object defaultValue = null;
        boolean autoIncrement = false;
        boolean primaryKey = false;

        // Parse column constraints
        while (true) {
            Token t = peek();
            if (t.type() == TokenType.KEYWORD && t.value().equals("NOT")) {
                advance();
                expect("NULL");
                nullable = false;
            } else if (t.type() == TokenType.KEYWORD && t.value().equals("NULL")) {
                advance();
                nullable = true;
            } else if (t.type() == TokenType.KEYWORD && t.value().equals("DEFAULT")) {
                advance();
                defaultValue = parseLiteralValue();
            } else if (t.type() == TokenType.KEYWORD && t.value().equals("AUTO_INCREMENT")) {
                advance();
                autoIncrement = true;
            } else if (t.type() == TokenType.KEYWORD && t.value().equals("SERIAL")) {
                advance();
                autoIncrement = true;
            } else if (t.type() == TokenType.KEYWORD && t.value().equals("PRIMARY")) {
                advance();
                expect("KEY");
                primaryKey = true;
            } else if (t.type() == TokenType.KEYWORD && t.value().equals("UNIQUE")) {
                advance();
                // column-level unique, no separate constraint needed here
            } else if (t.type() == TokenType.KEYWORD && t.value().equals("REFERENCES")) {
                // Column-level FK: skip for now
                advance();
                consumeIdentifier();
                if (matchPunctuation("(")) {
                    parseIdentifierListUntilClose();
                }
            } else {
                break;
            }
        }

        return new ColumnDef(name, dataType, nullable, defaultValue, autoIncrement, primaryKey);
    }

    private SqlDataType parseDataType() {
        Token t = peek();
        String typeName = t.value().toUpperCase();
        advance();

        // Skip optional size parameters like VARCHAR(255)
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
            case "VARCHAR", "CHAR", "CHARACTER" -> SqlDataType.VARCHAR;
            case "TEXT" -> SqlDataType.TEXT;
            case "BOOLEAN", "BOOL" -> SqlDataType.BOOLEAN;
            case "DATE" -> SqlDataType.DATE;
            case "TIMESTAMP", "DATETIME" -> SqlDataType.TIMESTAMP;
            case "BLOB", "BYTEA" -> SqlDataType.BLOB;
            case "SERIAL" -> SqlDataType.INTEGER; // PostgreSQL serial mapped to INTEGER
            default -> SqlDataType.VARCHAR; // fallback
        };
    }

    private TableConstraint parseTableConstraint() {
        String constraintName = null;
        if (matchKeyword("CONSTRAINT")) {
            constraintName = consumeIdentifier();
        }

        Token t = peek();
        return switch (t.value().toUpperCase()) {
            case "PRIMARY" -> {
                advance();
                expect("KEY");
                expect("(");
                List<String> cols = parseIdentifierListUntilClose();
                yield new PrimaryKeyConstraint(cols);
            }
            case "FOREIGN" -> {
                advance();
                expect("KEY");
                expect("(");
                List<String> cols = parseIdentifierListUntilClose();
                expect("REFERENCES");
                String refTable = consumeIdentifier();
                expect("(");
                List<String> refCols = parseIdentifierListUntilClose();
                // Optional CASCADE etc.
                while (matchKeyword("ON")) {
                    advance(); // DELETE/UPDATE
                    advance(); // CASCADE/SET/RESTRICT etc.
                    matchKeyword("NULL"); // for SET NULL
                }
                yield new ForeignKeyConstraint(cols, refTable, refCols);
            }
            case "UNIQUE" -> {
                advance();
                expect("(");
                List<String> cols = parseIdentifierListUntilClose();
                yield new UniqueConstraint(constraintName, cols);
            }
            case "CHECK" -> {
                advance();
                expect("(");
                SqlExpression expr = parseExpression();
                expect(")");
                yield new CheckConstraint(constraintName, expr);
            }
            default -> throw error("Expected constraint type, got: " + t.value());
        };
    }

    // ---- DROP ----

    private SqlNode parseDrop() {
        SourceLocation loc = location();
        expect("DROP");
        expect("TABLE");

        boolean ifExists = false;
        if (matchKeyword("IF")) {
            expect("EXISTS");
            ifExists = true;
        }

        String tableName = consumeIdentifier();
        matchPunctuation(";");
        return new DropTableNode(tableName, ifExists, loc);
    }

    public DropTableNode parseDropTable() {
        return (DropTableNode) parseDrop();
    }

    // ---- ALTER ----

    private SqlNode parseAlter() {
        SourceLocation loc = location();
        expect("ALTER");
        expect("TABLE");
        String tableName = consumeIdentifier();

        var actions = new ArrayList<AlterAction>();
        actions.add(parseAlterAction());
        while (matchPunctuation(",")) {
            actions.add(parseAlterAction());
        }

        matchPunctuation(";");
        return new AlterTableNode(tableName, actions, loc);
    }

    private AlterAction parseAlterAction() {
        Token t = peek();
        return switch (t.value().toUpperCase()) {
            case "ADD" -> {
                advance();
                if (matchKeyword("COLUMN")) {
                    yield new AlterAction.AddColumn(parseColumnDef());
                } else if (peekKeyword("CONSTRAINT") || peekKeyword("PRIMARY") || peekKeyword("FOREIGN") ||
                        peekKeyword("UNIQUE") || peekKeyword("CHECK")) {
                    yield new AlterAction.AddConstraint(parseTableConstraint());
                } else {
                    yield new AlterAction.AddColumn(parseColumnDef());
                }
            }
            case "DROP" -> {
                advance();
                matchKeyword("COLUMN");
                String colName = consumeIdentifier();
                yield new AlterAction.DropColumn(colName);
            }
            case "RENAME" -> {
                advance();
                matchKeyword("COLUMN");
                String oldName = consumeIdentifier();
                expect("TO");
                String newName = consumeIdentifier();
                yield new AlterAction.RenameColumn(oldName, newName);
            }
            default -> throw error("Expected ADD, DROP, or RENAME, got: " + t.value());
        };
    }

    // ---- CREATE INDEX ----

    public CreateIndexNode parseCreateIndex(boolean unique) {
        SourceLocation loc = location();
        expect("INDEX");
        String indexName = consumeIdentifier();
        expect("ON");
        String tableName = consumeIdentifier();
        expect("(");
        List<String> columns = parseIdentifierListUntilClose();
        matchPunctuation(";");
        return new CreateIndexNode(indexName, tableName, columns, unique, loc);
    }

    // ---- CREATE VIEW ----

    public CreateViewNode parseCreateView(boolean orReplace) {
        SourceLocation loc = location();
        expect("VIEW");
        String viewName = consumeIdentifier();
        expect("AS");
        SelectNode query = parseSelect();
        return new CreateViewNode(viewName, query, orReplace, loc);
    }

    // ---- CREATE TRIGGER ----

    public CreateTriggerNode parseCreateTrigger() {
        SourceLocation loc = location();
        expect("TRIGGER");
        String triggerName = consumeIdentifier();

        TriggerTiming timing;
        if (matchKeyword("BEFORE")) {
            timing = TriggerTiming.BEFORE;
        } else {
            expect("AFTER");
            timing = TriggerTiming.AFTER;
        }

        TriggerEvent event;
        Token eventToken = peek();
        advance();
        event = switch (eventToken.value().toUpperCase()) {
            case "INSERT" -> TriggerEvent.INSERT;
            case "UPDATE" -> TriggerEvent.UPDATE;
            case "DELETE" -> TriggerEvent.DELETE;
            default -> throw error("Expected INSERT, UPDATE, or DELETE for trigger event");
        };

        expect("ON");
        String tableName = consumeIdentifier();

        // Body: simplified, expect BEGIN ... END
        List<SqlNode> body = List.of();
        if (matchKeyword("BEGIN")) {
            body = new ArrayList<>();
            while (!peekKeyword("END")) {
                body.add(parseStatement());
                matchPunctuation(";");
            }
            expect("END");
        }

        matchPunctuation(";");
        return new CreateTriggerNode(triggerName, timing, event, tableName, body, loc);
    }

    // ---- CREATE PROCEDURE ----

    public CreateProcedureNode parseCreateProcedure() {
        SourceLocation loc = location();
        expect("PROCEDURE");
        String procName = consumeIdentifier();

        expect("(");
        var params = new ArrayList<ProcedureParam>();
        if (!peek().value().equals(")")) {
            params.add(parseProcedureParam());
            while (matchPunctuation(",")) {
                params.add(parseProcedureParam());
            }
        }
        expect(")");

        List<SqlNode> body = List.of();
        if (matchKeyword("BEGIN")) {
            body = new ArrayList<>();
            while (!peekKeyword("END")) {
                body.add(parseStatement());
                matchPunctuation(";");
            }
            expect("END");
        }

        matchPunctuation(";");
        return new CreateProcedureNode(procName, params, body, loc);
    }

    private ProcedureParam parseProcedureParam() {
        ParamMode mode = ParamMode.IN;
        if (matchKeyword("IN")) {
            if (matchKeyword("OUT")) {
                mode = ParamMode.INOUT;
            }
        } else if (matchKeyword("OUT")) {
            mode = ParamMode.OUT;
        } else if (matchKeyword("INOUT")) {
            mode = ParamMode.INOUT;
        }

        String name = consumeIdentifier();
        SqlDataType type = parseDataType();
        return new ProcedureParam(name, type, mode);
    }

    // ---- TRANSACTION ----

    public TransactionNode parseTransaction() {
        SourceLocation loc = location();
        Token t = peek();
        advance();

        TransactionAction action;
        String savepointName = null;

        switch (t.value().toUpperCase()) {
            case "BEGIN", "START" -> {
                if (t.value().equalsIgnoreCase("START")) {
                    matchKeyword("TRANSACTION");
                }
                action = TransactionAction.BEGIN;
            }
            case "COMMIT" -> action = TransactionAction.COMMIT;
            case "ROLLBACK" -> {
                if (matchKeyword("TO")) {
                    matchKeyword("SAVEPOINT");
                    savepointName = consumeIdentifier();
                    action = TransactionAction.ROLLBACK;
                } else {
                    action = TransactionAction.ROLLBACK;
                }
            }
            case "SAVEPOINT" -> {
                savepointName = consumeIdentifier();
                action = TransactionAction.SAVEPOINT;
            }
            case "RELEASE" -> {
                expect("SAVEPOINT");
                savepointName = consumeIdentifier();
                action = TransactionAction.RELEASE_SAVEPOINT;
            }
            default -> throw error("Unexpected transaction keyword: " + t.value());
        }

        matchPunctuation(";");
        return new TransactionNode(action, savepointName, loc);
    }

    // ---- CALL ----

    private CallNode parseCall() {
        SourceLocation loc = location();
        expect("CALL");
        String procName = consumeIdentifier();
        expect("(");
        var args = new ArrayList<Object>();
        if (!peek().value().equals(")")) {
            args.add(parseLiteralValue());
            while (matchPunctuation(",")) {
                args.add(parseLiteralValue());
            }
        }
        expect(")");
        matchPunctuation(";");
        return new CallNode(procName, args, loc);
    }

    // ---- EXPRESSION PARSING with operator precedence ----

    public SqlExpression parseExpression() {
        return parseOrExpression();
    }

    private SqlExpression parseOrExpression() {
        SqlExpression left = parseAndExpression();
        while (matchKeyword("OR")) {
            SqlExpression right = parseAndExpression();
            left = new BinaryExpr(left, "OR", right);
        }
        return left;
    }

    private SqlExpression parseAndExpression() {
        SqlExpression left = parseNotExpression();
        while (matchKeyword("AND")) {
            SqlExpression right = parseNotExpression();
            left = new BinaryExpr(left, "AND", right);
        }
        return left;
    }

    private SqlExpression parseNotExpression() {
        if (matchKeyword("NOT")) {
            SqlExpression operand = parseNotExpression();
            return new UnaryExpr("NOT", operand);
        }
        return parseComparisonExpression();
    }

    private SqlExpression parseComparisonExpression() {
        SqlExpression left = parseAdditionExpression();

        // IS [NOT] NULL
        if (peekKeyword("IS")) {
            advance();
            boolean negated = matchKeyword("NOT");
            expect("NULL");
            return new IsNullExpr(left, negated);
        }

        // [NOT] IN
        boolean negated = false;
        if (peekKeyword("NOT")) {
            // lookahead: NOT IN, NOT BETWEEN, NOT LIKE
            int savedPos = pos;
            advance();
            if (peekKeyword("IN")) {
                negated = true;
            } else if (peekKeyword("BETWEEN")) {
                negated = true;
            } else if (peekKeyword("LIKE") || peekKeyword("ILIKE")) {
                negated = true;
            } else {
                pos = savedPos;
            }
        }

        if (matchKeyword("IN")) {
            expect("(");
            if (peekKeyword("SELECT")) {
                SelectNode subquery = parseSelect();
                expect(")");
                return new InExpr(left, List.of(new SubqueryExpr(subquery)), negated);
            }
            var list = new ArrayList<SqlExpression>();
            list.add(parseExpression());
            while (matchPunctuation(",")) {
                list.add(parseExpression());
            }
            expect(")");
            return new InExpr(left, list, negated);
        }

        if (matchKeyword("BETWEEN")) {
            SqlExpression low = parseAdditionExpression();
            expect("AND");
            SqlExpression high = parseAdditionExpression();
            return new BetweenExpr(left, low, high, negated);
        }

        if (matchKeyword("LIKE") || matchKeyword("ILIKE")) {
            Token patternToken = peek();
            advance();
            return new LikeExpr(left, patternToken.value(), negated);
        }

        // EXISTS
        if (peekKeyword("EXISTS")) {
            advance();
            expect("(");
            SelectNode subquery = parseSelect();
            expect(")");
            return new ExistsExpr(subquery);
        }

        // Comparison operators
        Token op = peek();
        if (op.type() == TokenType.OPERATOR) {
            String opStr = op.value();
            if (opStr.equals("=") || opStr.equals("<") || opStr.equals(">") ||
                    opStr.equals("<=") || opStr.equals(">=") || opStr.equals("<>") || opStr.equals("!=")) {
                advance();
                SqlExpression right = parseAdditionExpression();
                return new BinaryExpr(left, opStr, right);
            }
        }

        return left;
    }

    private SqlExpression parseAdditionExpression() {
        SqlExpression left = parseMultiplicationExpression();
        while (true) {
            Token op = peek();
            if (op.type() == TokenType.OPERATOR && (op.value().equals("+") || op.value().equals("-") || op.value().equals("||"))) {
                advance();
                SqlExpression right = parseMultiplicationExpression();
                left = new BinaryExpr(left, op.value(), right);
            } else {
                break;
            }
        }
        return left;
    }

    private SqlExpression parseMultiplicationExpression() {
        SqlExpression left = parseUnaryExpression();
        while (true) {
            Token op = peek();
            if (op.type() == TokenType.OPERATOR && (op.value().equals("*") || op.value().equals("/") || op.value().equals("%"))) {
                advance();
                SqlExpression right = parseUnaryExpression();
                left = new BinaryExpr(left, op.value(), right);
            } else {
                break;
            }
        }
        return left;
    }

    private SqlExpression parseUnaryExpression() {
        Token t = peek();
        if (t.type() == TokenType.OPERATOR && (t.value().equals("-") || t.value().equals("+"))) {
            advance();
            SqlExpression operand = parsePrimaryExpression();
            return new UnaryExpr(t.value(), operand);
        }
        return parsePrimaryExpression();
    }

    private SqlExpression parsePrimaryExpression() {
        Token t = peek();

        // Star
        if (t.type() == TokenType.OPERATOR && t.value().equals("*")) {
            advance();
            return new StarExpr();
        }

        // Parenthesized expression or subquery
        if (t.type() == TokenType.PUNCTUATION && t.value().equals("(")) {
            advance();
            if (peekKeyword("SELECT")) {
                SelectNode subquery = parseSelect();
                expect(")");
                return new SubqueryExpr(subquery);
            }
            SqlExpression expr = parseExpression();
            expect(")");
            return expr;
        }

        // EXISTS
        if (t.type() == TokenType.KEYWORD && t.value().equals("EXISTS")) {
            advance();
            expect("(");
            SelectNode subquery = parseSelect();
            expect(")");
            return new ExistsExpr(subquery);
        }

        // CASE expression
        if (t.type() == TokenType.KEYWORD && t.value().equals("CASE")) {
            return parseCaseExpression();
        }

        // NULL
        if (t.type() == TokenType.KEYWORD && t.value().equals("NULL")) {
            advance();
            return new LiteralExpr(null);
        }

        // TRUE / FALSE
        if (t.type() == TokenType.KEYWORD && t.value().equals("TRUE")) {
            advance();
            return new LiteralExpr(true);
        }
        if (t.type() == TokenType.KEYWORD && t.value().equals("FALSE")) {
            advance();
            return new LiteralExpr(false);
        }

        // Number literal
        if (t.type() == TokenType.NUMBER) {
            advance();
            String val = t.value();
            if (val.contains(".")) {
                return new LiteralExpr(Double.parseDouble(val));
            }
            return new LiteralExpr(Long.parseLong(val));
        }

        // String literal
        if (t.type() == TokenType.STRING) {
            advance();
            return new LiteralExpr(t.value());
        }

        // Aggregate functions
        if (t.type() == TokenType.KEYWORD && isAggregateFunction(t.value())) {
            return parseAggregateExpression();
        }

        // Function call or identifier
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD) {
            String name = t.value();
            advance();

            // Check for function call
            if (peek().type() == TokenType.PUNCTUATION && peek().value().equals("(")) {
                // It's a function call
                if (isAggregateFunction(name.toUpperCase())) {
                    pos--; // back up
                    return parseAggregateExpression();
                }
                advance(); // skip (
                var args = new ArrayList<SqlExpression>();
                if (!peek().value().equals(")")) {
                    args.add(parseExpression());
                    while (matchPunctuation(",")) {
                        args.add(parseExpression());
                    }
                }
                expect(")");
                return new FunctionExpr(name.toUpperCase(), args);
            }

            // Check for table.column reference
            if (peek().type() == TokenType.PUNCTUATION && peek().value().equals(".")) {
                advance(); // skip dot
                String column = consumeIdentifierOrKeyword();
                return new ColumnRef(name, column);
            }

            return new ColumnRef(null, name);
        }

        throw error("Unexpected token in expression: " + t);
    }

    private boolean isAggregateFunction(String name) {
        return name.equals("COUNT") || name.equals("SUM") || name.equals("AVG") ||
                name.equals("MIN") || name.equals("MAX") || name.equals("GROUP_CONCAT");
    }

    private SqlExpression parseAggregateExpression() {
        Token funcToken = peek();
        advance();
        AggregateFunction func = AggregateFunction.valueOf(funcToken.value().toUpperCase());
        expect("(");

        boolean distinct = matchKeyword("DISTINCT");

        SqlExpression arg = null;
        if (peek().type() == TokenType.OPERATOR && peek().value().equals("*")) {
            advance(); // COUNT(*)
        } else if (!peek().value().equals(")")) {
            arg = parseExpression();
        }

        expect(")");
        return new AggregateExpr(func, arg, distinct);
    }

    private SqlExpression parseCaseExpression() {
        expect("CASE");

        SqlExpression operand = null;
        if (!peekKeyword("WHEN")) {
            operand = parseExpression();
        }

        var whenClauses = new ArrayList<WhenClause>();
        while (matchKeyword("WHEN")) {
            SqlExpression condition = parseExpression();
            expect("THEN");
            SqlExpression result = parseExpression();
            whenClauses.add(new WhenClause(condition, result));
        }

        SqlExpression elseExpr = null;
        if (matchKeyword("ELSE")) {
            elseExpr = parseExpression();
        }

        expect("END");
        return new CaseExpr(operand, whenClauses, elseExpr);
    }

    // ---- Helper methods ----

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

    private String consumeIdentifierOrKeyword() {
        Token t = peek();
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD) {
            advance();
            return t.value();
        }
        throw error("Expected identifier or keyword, got: " + t);
    }

    private String consumeNumber() {
        Token t = peek();
        if (t.type() == TokenType.NUMBER) {
            advance();
            return t.value();
        }
        throw error("Expected number, got: " + t);
    }

    private List<String> parseIdentifierList() {
        var identifiers = new ArrayList<String>();
        identifiers.add(consumeIdentifier());
        while (matchPunctuation(",")) {
            identifiers.add(consumeIdentifier());
        }
        return identifiers;
    }

    private List<String> parseIdentifierListUntilClose() {
        var identifiers = new ArrayList<String>();
        if (!peek().value().equals(")")) {
            identifiers.add(consumeIdentifier());
            while (matchPunctuation(",")) {
                identifiers.add(consumeIdentifier());
            }
        }
        expect(")");
        return identifiers;
    }

    private void expect(TokenType type) {
        Token t = peek();
        if (t.type() != type) {
            throw error("Expected " + type + ", got " + t.type() + "(" + t.value() + ")");
        }
        advance();
    }

    private SourceLocation location() {
        Token t = peek();
        return SourceLocation.at(t.position(), 0);
    }

    private SqlParseException error(String message) {
        return new SqlParseException(message);
    }

    public static class SqlParseException extends RuntimeException {
        public SqlParseException(String message) {
            super(message);
        }
    }
}

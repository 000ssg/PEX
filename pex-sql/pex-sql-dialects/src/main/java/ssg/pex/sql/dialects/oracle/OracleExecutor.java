package ssg.pex.sql.dialects.oracle;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.*;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.executor.ExpressionEvaluator;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectFunctionRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles Oracle-specific SQL execution:
 * hierarchical queries, ROWNUM, DUAL, sequences.
 */
public class OracleExecutor {

    private final InMemoryDatabase db;
    private final OracleParser parser;
    private final ExpressionEvaluator evaluator;
    private final Map<String, OracleSequence> sequences;

    private static final Pattern ROWNUM_PATTERN = Pattern.compile(
            "(?i)WHERE\\s+ROWNUM\\s*<=\\s*(\\d+)");
    private static final Pattern CONNECT_BY_PATTERN = Pattern.compile(
            "(?i)CONNECT\\s+BY\\s+PRIOR\\s+(\\w+)\\s*=\\s*(\\w+)");
    private static final Pattern START_WITH_PATTERN = Pattern.compile(
            "(?i)START\\s+WITH\\s+(\\w+)\\s*(?:=\\s*(.+?))?(?:\\s+CONNECT|$)");
    private static final Pattern NEXTVAL_PATTERN = Pattern.compile(
            "(?i)(\\w+)\\.NEXTVAL");
    private static final Pattern CURRVAL_PATTERN = Pattern.compile(
            "(?i)(\\w+)\\.CURRVAL");

    public OracleExecutor(InMemoryDatabase db) {
        this.db = db;
        this.parser = new OracleParser();
        this.evaluator = new ExpressionEvaluator();
        this.sequences = new ConcurrentHashMap<>();
    }

    public Result<Object> execute(String sql, DialectFunctionRegistry functions) {
        try {
            String trimmed = sql.trim();

            // CREATE SEQUENCE
            if (parser.isCreateSequence(trimmed)) {
                return executeCreateSequence(trimmed);
            }

            // Sequence operations in SELECT
            if (parser.hasSequenceOp(trimmed)) {
                return executeSequenceSelect(trimmed, functions);
            }

            // CONNECT BY hierarchical query
            if (parser.hasConnectBy(trimmed)) {
                return executeHierarchicalQuery(trimmed, functions);
            }

            // ROWNUM filtering
            if (parser.hasRowNum(trimmed)) {
                return executeRowNumQuery(trimmed, functions);
            }

            // DUAL queries
            if (parser.isDualQuery(trimmed)) {
                return executeDualQuery(trimmed, functions);
            }

            // Pre-process (e.g., MINUS -> EXCEPT) and delegate to core
            String processed = parser.preprocess(trimmed);
            Result<Object> result = db.execute(processed);

            // Post-process: evaluate Oracle function calls in SELECT items
            if (result.isSuccess() && result.value() instanceof QueryResult qr
                    && trimmed.toUpperCase().startsWith("SELECT")
                    && hasDialectFunctions(trimmed, functions)) {
                return Result.success(applyOracleFunctions(trimmed, qr, functions));
            }
            return result;

        } catch (Exception e) {
            return Result.failure("ORACLE_ERROR", e.getMessage(), e);
        }
    }

    private Result<Object> executeCreateSequence(String sql) {
        OracleNodes.CreateSequenceNode node = parser.parseCreateSequence(sql);
        sequences.put(node.name().toUpperCase(), new OracleSequence(node.startWith(), node.incrementBy()));
        return Result.success(null);
    }

    private Result<Object> executeSequenceSelect(String sql, DialectFunctionRegistry functions) {
        String upper = sql.toUpperCase();

        // Handle SELECT seq.NEXTVAL FROM DUAL
        Matcher nextvalMatcher = NEXTVAL_PATTERN.matcher(sql);
        if (nextvalMatcher.find()) {
            String seqName = nextvalMatcher.group(1).toUpperCase();
            OracleSequence seq = sequences.get(seqName);
            if (seq == null) {
                return Result.failure("ORACLE_ERROR", "Sequence not found: " + seqName);
            }
            long val = seq.nextVal();
            List<String> colNames = List.of("NEXTVAL");
            List<Row> rows = List.of(new Row(new Object[]{val}));
            return Result.success(new QueryResult(colNames, rows, 0));
        }

        Matcher currvalMatcher = CURRVAL_PATTERN.matcher(sql);
        if (currvalMatcher.find()) {
            String seqName = currvalMatcher.group(1).toUpperCase();
            OracleSequence seq = sequences.get(seqName);
            if (seq == null) {
                return Result.failure("ORACLE_ERROR", "Sequence not found: " + seqName);
            }
            long val = seq.currVal();
            List<String> colNames = List.of("CURRVAL");
            List<Row> rows = List.of(new Row(new Object[]{val}));
            return Result.success(new QueryResult(colNames, rows, 0));
        }

        return Result.failure("ORACLE_ERROR", "Unable to parse sequence expression in: " + sql);
    }

    private Result<Object> executeHierarchicalQuery(String sql, DialectFunctionRegistry functions) {
        // Parse the FROM table
        String upper = sql.toUpperCase();
        int fromIdx = upper.indexOf("FROM ");
        int startWithIdx = upper.indexOf("START WITH");
        int connectByIdx = upper.indexOf("CONNECT BY");
        int selectIdx = upper.indexOf("SELECT ");

        if (fromIdx < 0) {
            return Result.failure("ORACLE_ERROR", "Missing FROM clause in hierarchical query");
        }

        // Extract table name
        String afterFrom = sql.substring(fromIdx + 5).trim();
        String tableName = afterFrom.split("\\s+")[0].replaceAll(";", "");

        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) {
            return Result.failure("ORACLE_ERROR", "Table not found: " + tableName);
        }

        // Parse SELECT columns
        String selectPart = sql.substring(selectIdx + 7, fromIdx).trim();
        String[] selectCols = selectPart.split("\\s*,\\s*");

        // Parse START WITH condition
        String startWithCol = null;
        Object startWithVal = null;
        if (startWithIdx >= 0) {
            Matcher swm = START_WITH_PATTERN.matcher(sql);
            if (swm.find()) {
                startWithCol = swm.group(1);
                String valStr = swm.group(2);
                if (valStr != null) {
                    valStr = valStr.trim().replaceAll(";$", "");
                    if (valStr.equalsIgnoreCase("NULL")) {
                        startWithVal = null;
                    } else {
                        try {
                            startWithVal = Long.parseLong(valStr);
                        } catch (NumberFormatException e) {
                            startWithVal = valStr.replaceAll("^'|'$", "");
                        }
                    }
                }
            }
        }

        // Parse CONNECT BY PRIOR
        String priorCol = null;
        String parentCol = null;
        Matcher cbm = CONNECT_BY_PATTERN.matcher(sql);
        if (cbm.find()) {
            priorCol = cbm.group(1);
            parentCol = cbm.group(2);
        }

        if (priorCol == null || parentCol == null) {
            return Result.failure("ORACLE_ERROR", "Invalid CONNECT BY clause");
        }

        // Perform hierarchical traversal
        List<Row> allRows = table.scan();
        List<Row> resultRows = new ArrayList<>();

        // Find root rows (where START WITH condition matches)
        List<Row> rootRows = new ArrayList<>();
        int startWithColIdx = table.getColumnIndex(startWithCol);
        for (Row row : allRows) {
            Object val = row.getValue(startWithColIdx);
            if (startWithVal == null) {
                if (val == null) rootRows.add(row);
            } else {
                if (ExpressionEvaluator.compareValues(val, startWithVal) == 0) {
                    rootRows.add(row);
                }
            }
        }

        // BFS traversal
        int priorColIdx = table.getColumnIndex(priorCol);
        int parentColIdx = table.getColumnIndex(parentCol);

        Queue<Row> queue = new LinkedList<>(rootRows);
        Set<Object> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Row current = queue.poll();
            resultRows.add(current);

            Object currentKey = current.getValue(priorColIdx);
            if (currentKey != null && visited.add(currentKey)) {
                // Find children where parentCol = currentKey
                for (Row candidate : allRows) {
                    Object candidateParent = candidate.getValue(parentColIdx);
                    if (candidateParent != null && ExpressionEvaluator.compareValues(candidateParent, currentKey) == 0) {
                        queue.add(candidate);
                    }
                }
            }
        }

        // Project selected columns
        List<String> colNames = new ArrayList<>();
        List<Integer> colIndices = new ArrayList<>();
        for (String col : selectCols) {
            String colName = col.trim();
            if (colName.equals("*")) {
                for (Column c : table.columns()) {
                    colNames.add(c.name());
                    colIndices.add(c.ordinal());
                }
            } else {
                colNames.add(colName);
                colIndices.add(table.getColumnIndex(colName));
            }
        }

        List<Row> projected = new ArrayList<>();
        for (Row row : resultRows) {
            Object[] vals = new Object[colIndices.size()];
            for (int i = 0; i < colIndices.size(); i++) {
                int idx = colIndices.get(i);
                vals[i] = idx >= 0 ? row.getValue(idx) : null;
            }
            projected.add(new Row(vals));
        }

        return Result.success(new QueryResult(colNames, projected, 0));
    }

    private Result<Object> executeRowNumQuery(String sql, DialectFunctionRegistry functions) {
        // Extract ROWNUM limit
        Matcher m = ROWNUM_PATTERN.matcher(sql);
        int limit = Integer.MAX_VALUE;
        if (m.find()) {
            limit = Integer.parseInt(m.group(1));
        }

        // Remove ROWNUM clause and execute as normal query
        String cleaned = sql.replaceAll("(?i)\\s*WHERE\\s+ROWNUM\\s*<=\\s*\\d+", "")
                           .replaceAll("(?i)\\s*AND\\s+ROWNUM\\s*<=\\s*\\d+", "");

        Result<Object> result = db.execute(cleaned);
        if (result.isFailure()) return result;

        if (result.value() instanceof QueryResult qr) {
            List<Row> limitedRows = qr.rows().size() > limit
                    ? qr.rows().subList(0, limit) : qr.rows();
            return Result.success(new QueryResult(qr.columnNames(), new ArrayList<>(limitedRows), qr.executionTimeNanos()));
        }

        return result;
    }

    private Result<Object> executeDualQuery(String sql, DialectFunctionRegistry functions) {
        // Handle SELECT expr FROM DUAL
        String upper = sql.toUpperCase().trim();
        int selectIdx = upper.indexOf("SELECT ");
        int fromIdx = upper.indexOf(" FROM DUAL");
        if (selectIdx < 0 || fromIdx < 0) {
            return Result.failure("ORACLE_ERROR", "Invalid DUAL query");
        }

        String exprPart = sql.substring(selectIdx + 7, fromIdx).trim();

        // Check for function calls
        Object value = evaluateOracleExpression(exprPart, functions);

        String alias = exprPart;
        // Check for AS alias
        String upperExpr = exprPart.toUpperCase();
        int asIdx = -1;
        // Find the AS keyword that is not inside parentheses
        int parenDepth = 0;
        for (int i = 0; i < exprPart.length() - 2; i++) {
            char c = exprPart.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (parenDepth == 0 && (c == ' ' || c == '\t')) {
                String rest = exprPart.substring(i).trim();
                if (rest.toUpperCase().startsWith("AS ")) {
                    alias = rest.substring(3).trim();
                    exprPart = exprPart.substring(0, i).trim();
                    value = evaluateOracleExpression(exprPart, functions);
                    break;
                }
            }
        }

        List<String> colNames = List.of(alias);
        List<Row> rows = List.of(new Row(new Object[]{value}));
        return Result.success(new QueryResult(colNames, rows, 0));
    }

    private Object evaluateOracleExpression(String expr, DialectFunctionRegistry functions) {
        expr = expr.trim();

        // Check for SYSDATE
        if (expr.equalsIgnoreCase("SYSDATE")) {
            if (functions.has("SYSDATE")) {
                return functions.get("SYSDATE").apply(List.of());
            }
            return java.time.LocalDate.now().toString();
        }

        // Check for function call pattern: FUNC(args)
        int parenIdx = expr.indexOf('(');
        if (parenIdx > 0 && expr.endsWith(")")) {
            String funcName = expr.substring(0, parenIdx).trim().toUpperCase();
            String argsStr = expr.substring(parenIdx + 1, expr.length() - 1).trim();

            List<Object> args = parseArgsList(argsStr, functions);

            if (functions.has(funcName)) {
                return functions.get(funcName).apply(args);
            }
        }

        // Number literal
        try { return Long.parseLong(expr); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(expr); } catch (NumberFormatException ignored) {}

        // String literal
        if (expr.startsWith("'") && expr.endsWith("'")) {
            return expr.substring(1, expr.length() - 1);
        }

        // NULL
        if (expr.equalsIgnoreCase("NULL")) return null;

        // Arithmetic expression evaluation with operator precedence
        if (expr.contains("+") || expr.contains("-") || expr.contains("*") || expr.contains("/")) {
            try {
                double result = evaluateArithmeticExpr(expr);
                if (result == Math.floor(result) && !Double.isInfinite(result)
                        && result >= Long.MIN_VALUE && result <= Long.MAX_VALUE) {
                    return (long) result;
                }
                return result;
            } catch (Exception ignored) {}
        }

        return expr;
    }

    /**
     * Evaluates a simple arithmetic expression with correct operator precedence.
     * Supports +, -, *, / on numeric literals.
     */
    private double evaluateArithmeticExpr(String expr) {
        expr = expr.trim();
        // Split on +/- (additive level) while respecting precedence
        // First handle addition/subtraction (lowest precedence)
        int depth = 0;
        int lastAddSub = -1;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '+' || c == '-') && i > 0) {
                // Make sure it's an operator not a sign (preceded by a digit or space)
                char prev = expr.charAt(i - 1);
                if (Character.isDigit(prev) || prev == ')' || prev == ' ') {
                    lastAddSub = i;
                    break;
                }
            }
        }
        if (lastAddSub > 0) {
            double left = evaluateArithmeticExpr(expr.substring(0, lastAddSub));
            char op = expr.charAt(lastAddSub);
            double right = evaluateArithmeticExpr(expr.substring(lastAddSub + 1));
            return op == '+' ? left + right : left - right;
        }

        // Then handle multiplication/division (higher precedence)
        depth = 0;
        int lastMulDiv = -1;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '*' || c == '/')) {
                lastMulDiv = i;
                break;
            }
        }
        if (lastMulDiv > 0) {
            double left = evaluateArithmeticExpr(expr.substring(0, lastMulDiv));
            char op = expr.charAt(lastMulDiv);
            double right = evaluateArithmeticExpr(expr.substring(lastMulDiv + 1));
            return op == '*' ? left * right : left / right;
        }

        // Handle parentheses
        expr = expr.trim();
        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evaluateArithmeticExpr(expr.substring(1, expr.length() - 1));
        }

        return Double.parseDouble(expr.trim());
    }

    private List<Object> parseArgsList(String argsStr, DialectFunctionRegistry functions) {
        if (argsStr.isEmpty()) return List.of();

        List<Object> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                args.add(parseArgValue(argsStr.substring(start, i).trim(), functions));
                start = i + 1;
            }
        }
        args.add(parseArgValue(argsStr.substring(start).trim(), functions));
        return args;
    }

    private Object parseArgValue(String val, DialectFunctionRegistry functions) {
        val = val.trim();
        if (val.equalsIgnoreCase("NULL")) return null;
        if (val.startsWith("'") && val.endsWith("'")) return val.substring(1, val.length() - 1);
        try { return Long.parseLong(val); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}

        // Could be a nested function call
        int parenIdx = val.indexOf('(');
        if (parenIdx > 0 && val.endsWith(")")) {
            String funcName = val.substring(0, parenIdx).trim().toUpperCase();
            String innerArgs = val.substring(parenIdx + 1, val.length() - 1);
            List<Object> args = parseArgsList(innerArgs, functions);
            if (functions.has(funcName)) {
                return functions.get(funcName).apply(args);
            }
        }

        return val;
    }

    public Map<String, OracleSequence> sequences() {
        return Collections.unmodifiableMap(sequences);
    }

    private boolean hasDialectFunctions(String sql, DialectFunctionRegistry functions) {
        String upper = sql.toUpperCase();
        for (String name : functions.functionNames()) {
            if (upper.contains(name + "(")) return true;
        }
        return false;
    }

    private QueryResult applyOracleFunctions(String sql, QueryResult projected,
                                             DialectFunctionRegistry functions) {
        String upper = sql.toUpperCase().trim();
        int fromIdx = findFromIdx(upper, sql);
        if (fromIdx < 0) return projected;

        String fromAndBeyond = sql.substring(fromIdx);
        Result<Object> fullResult = db.execute("SELECT * " + fromAndBeyond);
        if (fullResult.isFailure() || !(fullResult.value() instanceof QueryResult fullQr)) {
            return projected;
        }

        String selectPart = sql.substring("SELECT ".length(), fromIdx).trim();
        List<String[]> items = splitSelectItemsWithAlias(selectPart);

        List<String> colNames = new ArrayList<>();
        for (String[] item : items) {
            colNames.add(item[1] != null ? item[1] : item[0]);
        }

        List<Row> resultRows = new ArrayList<>();
        for (Row row : fullQr.rows()) {
            Object[] values = new Object[items.size()];
            for (int i = 0; i < items.size(); i++) {
                values[i] = evaluateExprOnRow(items.get(i)[0], row, fullQr.columnNames(), functions);
            }
            resultRows.add(new Row(values));
        }
        return new QueryResult(colNames, resultRows, projected.executionTimeNanos());
    }

    private int findFromIdx(String upper, String original) {
        int depth = 0;
        for (int i = 0; i <= original.length() - 4; i++) {
            char c = original.charAt(i);
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (depth == 0 && upper.startsWith("FROM", i)) {
                boolean prevOk = i == 0 || !Character.isLetterOrDigit(original.charAt(i - 1));
                boolean nextOk = i + 4 >= original.length() || !Character.isLetterOrDigit(original.charAt(i + 4));
                if (prevOk && nextOk) return i;
            }
        }
        return -1;
    }

    private List<String[]> splitSelectItemsWithAlias(String clause) {
        List<String[]> items = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < clause.length(); i++) {
            char c = clause.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                items.add(splitExprAlias(clause.substring(start, i).trim()));
                start = i + 1;
            }
        }
        if (start < clause.length()) items.add(splitExprAlias(clause.substring(start).trim()));
        return items;
    }

    private String[] splitExprAlias(String item) {
        int depth = 0;
        String upper = item.toUpperCase();
        for (int i = 0; i < item.length(); i++) {
            char c = item.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && upper.startsWith(" AS ", i)) {
                return new String[]{item.substring(0, i).trim(), item.substring(i + 4).trim()};
            }
        }
        return new String[]{item.trim(), null};
    }

    private Object evaluateExprOnRow(String expr, Row row, List<String> colNames,
                                     DialectFunctionRegistry functions) {
        expr = expr.trim();
        if (expr.equalsIgnoreCase("NULL")) return null;
        if (expr.startsWith("'") && expr.endsWith("'")) return expr.substring(1, expr.length() - 1);
        try { return Long.parseLong(expr); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(expr); } catch (NumberFormatException ignored) {}

        // Function call
        int parenIdx = expr.indexOf('(');
        if (parenIdx > 0 && expr.endsWith(")")) {
            String funcName = expr.substring(0, parenIdx).trim().toUpperCase();
            String argsStr = expr.substring(parenIdx + 1, expr.length() - 1).trim();
            if (functions.has(funcName)) {
                List<Object> args = splitAndEvaluateArgs(argsStr, row, colNames, functions);
                return functions.get(funcName).apply(args);
            }
        }

        // Column reference (case-insensitive)
        for (int i = 0; i < colNames.size(); i++) {
            if (colNames.get(i).equalsIgnoreCase(expr)) return row.getValue(i);
        }
        // Strip table prefix
        int dot = expr.indexOf('.');
        if (dot >= 0) {
            String simple = expr.substring(dot + 1);
            for (int i = 0; i < colNames.size(); i++) {
                if (colNames.get(i).equalsIgnoreCase(simple)) return row.getValue(i);
            }
        }
        return null;
    }

    private List<Object> splitAndEvaluateArgs(String argsStr, Row row, List<String> colNames,
                                              DialectFunctionRegistry functions) {
        List<Object> args = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                args.add(evaluateExprOnRow(argsStr.substring(start, i).trim(), row, colNames, functions));
                start = i + 1;
            }
        }
        if (start < argsStr.length()) {
            args.add(evaluateExprOnRow(argsStr.substring(start).trim(), row, colNames, functions));
        }
        return args;
    }

    /**
     * Oracle sequence with atomic counter.
     */
    public static class OracleSequence {
        private final AtomicLong counter;
        private final long incrementBy;
        private volatile boolean initialized = false;

        public OracleSequence(long startWith, long incrementBy) {
            this.counter = new AtomicLong(startWith - incrementBy); // so first NEXTVAL returns startWith
            this.incrementBy = incrementBy;
        }

        public long nextVal() {
            initialized = true;
            return counter.addAndGet(incrementBy);
        }

        public long currVal() {
            if (!initialized) {
                throw new IllegalStateException("CURRVAL is not yet defined for this sequence");
            }
            return counter.get();
        }
    }
}

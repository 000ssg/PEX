package ssg.pex.sql.dialects.mssql;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectFunctionRegistry;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles MSSQL-specific SQL execution:
 * TOP N, @variables, TRY/CATCH, OUTPUT clause.
 */
public class MssqlExecutor {

    private final InMemoryDatabase db;
    private final MssqlParser parser;
    private final Map<String, Object> variables;
    private Object lastPrintValue;
    private int lastRowCount;

    private static final Pattern SELECT_VAR_PATTERN = Pattern.compile(
            "(?i)SELECT\\s+(@\\w+)\\s*$");
    private static final Pattern OUTPUT_INSERTED_PATTERN = Pattern.compile(
            "(?i)OUTPUT\\s+INSERTED\\.(\\w+(?:\\s*,\\s*INSERTED\\.\\w+)*)");

    public MssqlExecutor(InMemoryDatabase db) {
        this.db = db;
        this.parser = new MssqlParser();
        this.variables = new LinkedHashMap<>();
        this.lastRowCount = 0;
    }

    public Result<Object> execute(String sql, DialectFunctionRegistry functions) {
        try {
            String trimmed = sql.trim();

            // DECLARE @var TYPE
            if (parser.isDeclare(trimmed)) {
                return executeDeclare(trimmed);
            }

            // SET @var = value
            if (parser.isSetVariable(trimmed)) {
                return executeSetVariable(trimmed, functions);
            }

            // PRINT @var
            if (parser.isPrint(trimmed)) {
                return executePrint(trimmed);
            }

            // BEGIN TRY ... END TRY BEGIN CATCH ... END CATCH
            if (parser.isTryCatch(trimmed)) {
                return executeTryCatch(trimmed, functions);
            }

            // SELECT @var
            Matcher selectVarMatcher = SELECT_VAR_PATTERN.matcher(trimmed.replaceAll(";$", ""));
            if (selectVarMatcher.matches()) {
                return executeSelectVariable(selectVarMatcher.group(1));
            }

            // Remove NOLOCK hints
            if (parser.hasNolock(trimmed)) {
                trimmed = parser.removeNolock(trimmed);
            }

            // MSSQL #temp table syntax: rewrite #tableName -> tableName with TEMP keyword
            if (parser.hasHashTable(trimmed)) {
                trimmed = parser.rewriteHashTableToTemp(trimmed);
            }

            // INSERT with OUTPUT clause
            if (parser.hasOutputClause(trimmed) && trimmed.toUpperCase().startsWith("INSERT")) {
                return executeInsertWithOutput(trimmed, functions);
            }

            // SELECT TOP N
            if (parser.hasTop(trimmed)) {
                return executeTopQuery(trimmed, functions);
            }

            // Delegate to core
            Result<Object> result = db.execute(trimmed);
            if (result.isSuccess() && result.value() instanceof QueryResult qr) {
                lastRowCount = qr.rowCount();
            }
            return result;
        } catch (Exception e) {
            return Result.failure("MSSQL_ERROR", e.getMessage(), e);
        }
    }

    private Result<Object> executeDeclare(String sql) {
        MssqlNodes.VariableDecl decl = parser.parseDeclare(sql);
        if (decl == null) {
            return Result.failure("MSSQL_ERROR", "Invalid DECLARE syntax");
        }
        variables.put(decl.name(), null);
        return Result.success(null);
    }

    private Result<Object> executeSetVariable(String sql, DialectFunctionRegistry functions) {
        MssqlNodes.SetVariable sv = parser.parseSetVariable(sql);
        if (sv == null) {
            return Result.failure("MSSQL_ERROR", "Invalid SET syntax");
        }
        Object value = sv.value();

        // If value is a string that starts with @, it's a variable reference
        if (value instanceof String s && s.startsWith("@")) {
            value = variables.get(s);
        }

        // Check if value is a function call
        if (value instanceof String s) {
            // Try to evaluate as function
            String upper = s.toUpperCase().trim();
            int parenIdx = s.indexOf('(');
            if (parenIdx > 0 && s.endsWith(")")) {
                String funcName = s.substring(0, parenIdx).trim().toUpperCase();
                if (functions.has(funcName)) {
                    String argsStr = s.substring(parenIdx + 1, s.length() - 1).trim();
                    List<Object> args = parseArgs(argsStr, functions);
                    value = functions.get(funcName).apply(args);
                }
            } else if (functions.has(upper)) {
                value = functions.get(upper).apply(List.of());
            }
        }

        variables.put(sv.name(), value);
        return Result.success(null);
    }

    private Result<Object> executePrint(String sql) {
        MssqlNodes.PrintStatement stmt = parser.parsePrint(sql);
        if (stmt == null) {
            return Result.failure("MSSQL_ERROR", "Invalid PRINT syntax");
        }
        Object value = stmt.value();
        if (value instanceof String s && s.startsWith("@")) {
            value = variables.get(s);
        }
        lastPrintValue = value;

        List<String> colNames = List.of("print");
        List<Row> rows = List.of(new Row(new Object[]{value}));
        return Result.success(new QueryResult(colNames, rows, 0));
    }

    private Result<Object> executeTryCatch(String sql, DialectFunctionRegistry functions) {
        MssqlNodes.TryCatchNode node = parser.parseTryCatch(sql);
        if (node == null) {
            return Result.failure("MSSQL_ERROR", "Invalid TRY/CATCH syntax");
        }

        // Execute try body
        Result<Object> lastResult = Result.success(null);
        try {
            for (String stmt : node.tryBody()) {
                lastResult = execute(stmt, functions);
                if (lastResult.isFailure()) {
                    // Error in try - fall through to catch
                    throw new RuntimeException(lastResult.error().message());
                }
            }
            return lastResult;
        } catch (Exception e) {
            // Execute catch body
            for (String stmt : node.catchBody()) {
                lastResult = execute(stmt, functions);
                if (lastResult.isFailure()) return lastResult;
            }
            return lastResult;
        }
    }

    private Result<Object> executeSelectVariable(String varName) {
        Object value = variables.get(varName);
        List<String> colNames = List.of(varName);
        List<Row> rows = List.of(new Row(new Object[]{value}));
        return Result.success(new QueryResult(colNames, rows, 0));
    }

    private Result<Object> executeTopQuery(String sql, DialectFunctionRegistry functions) {
        MssqlNodes.TopClause top = parser.parseTop(sql);
        if (top == null) {
            return db.execute(sql);
        }

        // Remove TOP clause and execute normally
        String cleaned = parser.removeTopClause(sql);
        Result<Object> result = db.execute(cleaned);

        if (result.isSuccess() && result.value() instanceof QueryResult qr) {
            int limit;
            if (top.percent()) {
                limit = (int) Math.ceil(qr.rowCount() * top.count() / 100.0);
            } else {
                limit = top.count();
            }

            List<Row> limited = qr.rowCount() > limit
                    ? new ArrayList<>(qr.rows().subList(0, limit))
                    : new ArrayList<>(qr.rows());
            lastRowCount = limited.size();
            return Result.success(new QueryResult(qr.columnNames(), limited, qr.executionTimeNanos()));
        }

        return result;
    }

    private Result<Object> executeInsertWithOutput(String sql, DialectFunctionRegistry functions) {
        // Extract OUTPUT clause columns
        Matcher outputMatcher = OUTPUT_INSERTED_PATTERN.matcher(sql);
        List<String> outputCols = new ArrayList<>();
        if (outputMatcher.find()) {
            String colsStr = outputMatcher.group(1);
            for (String col : colsStr.split(",")) {
                col = col.trim();
                if (col.toUpperCase().startsWith("INSERTED.")) {
                    col = col.substring(9);
                }
                outputCols.add(col.trim());
            }
        }

        // Remove OUTPUT clause and execute the INSERT
        String cleaned = sql.replaceAll("(?i)OUTPUT\\s+INSERTED\\.\\w+(?:\\s*,\\s*INSERTED\\.\\w+)*\\s*", "");
        Result<Object> result = db.execute(cleaned);

        if (result.isSuccess() && !outputCols.isEmpty()) {
            // Extract the table name from INSERT INTO
            String upper = cleaned.toUpperCase();
            int intoIdx = upper.indexOf("INTO ");
            if (intoIdx >= 0) {
                String afterInto = cleaned.substring(intoIdx + 5).trim();
                String tableName = afterInto.split("[\\s(]")[0];
                var table = db.defaultSchema().getTable(tableName);
                if (table != null) {
                    // Return the last inserted rows
                    List<Row> outputRows = new ArrayList<>();
                    int rowCount = table.rowCount();
                    if (rowCount > 0) {
                        Row lastRow = table.rows().get(rowCount - 1);
                        Object[] vals = new Object[outputCols.size()];
                        for (int i = 0; i < outputCols.size(); i++) {
                            int colIdx = table.getColumnIndex(outputCols.get(i));
                            vals[i] = colIdx >= 0 ? lastRow.getValue(colIdx) : null;
                        }
                        outputRows.add(new Row(vals));
                    }
                    lastRowCount = outputRows.size();
                    return Result.success(new QueryResult(outputCols, outputRows, 0));
                }
            }
        }

        return result;
    }

    private List<Object> parseArgs(String argsStr, DialectFunctionRegistry functions) {
        if (argsStr.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                args.add(parseArgValue(argsStr.substring(start, i).trim()));
                start = i + 1;
            }
        }
        args.add(parseArgValue(argsStr.substring(start).trim()));
        return args;
    }

    private Object parseArgValue(String val) {
        val = val.trim();
        if (val.startsWith("@")) return variables.getOrDefault(val, val);
        if (val.startsWith("'") && val.endsWith("'")) return val.substring(1, val.length() - 1);
        try { return Long.parseLong(val); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}
        if (val.equalsIgnoreCase("NULL")) return null;
        return val;
    }

    public Map<String, Object> variables() {
        return Collections.unmodifiableMap(variables);
    }

    public Object lastPrintValue() {
        return lastPrintValue;
    }

    public int lastRowCount() {
        return lastRowCount;
    }
}

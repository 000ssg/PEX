package ssg.pex.sql.dialects.engines;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Column-oriented storage simulation.
 * Stores data as Map&lt;String, List&lt;Object&gt;&gt; (column name -> values list).
 */
public class ColumnarEngine {

    /**
     * Columnar table: stores data column-wise instead of row-wise.
     */
    public static class ColumnarTable {
        private final String name;
        private final List<String> columnNames;
        private final Map<String, List<Object>> columns;
        private int rowCount;

        public ColumnarTable(String name, List<String> columnNames) {
            this.name = name;
            this.columnNames = new ArrayList<>(columnNames);
            this.columns = new LinkedHashMap<>();
            for (String col : columnNames) {
                columns.put(col.toLowerCase(), new ArrayList<>());
            }
            this.rowCount = 0;
        }

        public void addRow(Map<String, Object> values) {
            for (String col : columnNames) {
                columns.get(col.toLowerCase()).add(values.getOrDefault(col.toLowerCase(),
                        values.getOrDefault(col, null)));
            }
            rowCount++;
        }

        public List<Object> getColumn(String name) {
            return columns.getOrDefault(name.toLowerCase(), List.of());
        }

        public int rowCount() {
            return rowCount;
        }

        public String name() {
            return name;
        }

        public List<String> columnNames() {
            return Collections.unmodifiableList(columnNames);
        }

        /**
         * Scan only the specified columns (column projection).
         */
        public Map<String, List<Object>> columnScan(List<String> requestedCols) {
            Map<String, List<Object>> result = new LinkedHashMap<>();
            for (String col : requestedCols) {
                List<Object> data = columns.get(col.toLowerCase());
                if (data != null) {
                    result.put(col, new ArrayList<>(data));
                }
            }
            return result;
        }

        /**
         * Compute aggregate directly on column array (optimized for columnar storage).
         */
        public Object computeAggregate(String column, String aggFunc) {
            List<Object> data = columns.get(column.toLowerCase());
            if (data == null || data.isEmpty()) return null;

            return switch (aggFunc.toUpperCase()) {
                case "COUNT" -> (long) data.stream().filter(Objects::nonNull).count();
                case "SUM" -> data.stream()
                        .filter(v -> v instanceof Number)
                        .mapToDouble(v -> ((Number) v).doubleValue())
                        .sum();
                case "AVG" -> data.stream()
                        .filter(v -> v instanceof Number)
                        .mapToDouble(v -> ((Number) v).doubleValue())
                        .average().orElse(0.0);
                case "MIN" -> data.stream()
                        .filter(v -> v instanceof Number)
                        .mapToDouble(v -> ((Number) v).doubleValue())
                        .min().orElse(0.0);
                case "MAX" -> data.stream()
                        .filter(v -> v instanceof Number)
                        .mapToDouble(v -> ((Number) v).doubleValue())
                        .max().orElse(0.0);
                default -> null;
            };
        }

        /**
         * Run-length encoding compression simulation for a column.
         */
        public List<RleEntry> compressColumn(String column) {
            List<Object> data = columns.get(column.toLowerCase());
            if (data == null || data.isEmpty()) return List.of();

            List<RleEntry> compressed = new ArrayList<>();
            Object current = data.getFirst();
            int count = 1;
            for (int i = 1; i < data.size(); i++) {
                if (Objects.equals(data.get(i), current)) {
                    count++;
                } else {
                    compressed.add(new RleEntry(current, count));
                    current = data.get(i);
                    count = 1;
                }
            }
            compressed.add(new RleEntry(current, count));
            return compressed;
        }
    }

    public record RleEntry(Object value, int count) {}

    private final Map<String, ColumnarTable> tables = new ConcurrentHashMap<>();

    public Result<Object> execute(String sql, InMemoryDatabase db) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        // CREATE TABLE ... ENGINE=COLUMNAR
        if (upper.startsWith("CREATE TABLE") && upper.contains("ENGINE=COLUMNAR")) {
            return createColumnarTable(trimmed);
        }

        // INSERT INTO columnar table
        if (upper.startsWith("INSERT INTO")) {
            String tableName = extractTableName(trimmed, "INSERT INTO");
            if (tables.containsKey(tableName.toLowerCase())) {
                return insertColumnar(trimmed, tableName);
            }
        }

        // SELECT from columnar table
        if (upper.startsWith("SELECT")) {
            String fromTable = extractFromTable(trimmed);
            if (fromTable != null && tables.containsKey(fromTable.toLowerCase())) {
                return selectColumnar(trimmed, fromTable);
            }
        }

        // Delegate to standard database
        return db.execute(trimmed);
    }

    private Result<Object> createColumnarTable(String sql) {
        // Simplified parsing: CREATE TABLE name (col1 type, col2 type, ...) ENGINE=COLUMNAR
        String upper = sql.toUpperCase();
        int tableIdx = upper.indexOf("TABLE ") + 6;
        int parenIdx = sql.indexOf('(');
        if (parenIdx < 0) return Result.failure("COLUMNAR_ERROR", "Invalid CREATE TABLE syntax");

        String tableName = sql.substring(tableIdx, parenIdx).trim();

        // Extract column names — find matching close paren (handle nested parens like VARCHAR(50))
        int depth = 1;
        int closeParenIdx = parenIdx + 1;
        while (closeParenIdx < sql.length() && depth > 0) {
            char c = sql.charAt(closeParenIdx);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth > 0) closeParenIdx++;
        }
        String colsDef = sql.substring(parenIdx + 1, closeParenIdx);
        List<String> colNames = new ArrayList<>();
        for (String col : colsDef.split(",")) {
            String colName = col.trim().split("\\s+")[0];
            colNames.add(colName);
        }

        tables.put(tableName.toLowerCase(), new ColumnarTable(tableName, colNames));
        return Result.success(null);
    }

    private Result<Object> insertColumnar(String sql, String tableName) {
        ColumnarTable table = tables.get(tableName.toLowerCase());
        if (table == null) return Result.failure("COLUMNAR_ERROR", "Table not found: " + tableName);

        // Parse INSERT INTO table (cols) VALUES (vals)
        int valuesIdx = sql.toUpperCase().indexOf("VALUES");
        if (valuesIdx < 0) return Result.failure("COLUMNAR_ERROR", "Missing VALUES clause");

        // Extract column names from the insert
        int openParen = sql.indexOf('(');
        int closeParen = sql.indexOf(')');
        List<String> insertCols;
        if (openParen >= 0 && openParen < valuesIdx) {
            String colStr = sql.substring(openParen + 1, closeParen);
            insertCols = new ArrayList<>();
            for (String c : colStr.split(",")) insertCols.add(c.trim());
        } else {
            insertCols = table.columnNames();
        }

        // Extract values
        String valuesPart = sql.substring(valuesIdx + 6).trim();
        int vOpenParen = valuesPart.indexOf('(');
        int vCloseParen = valuesPart.lastIndexOf(')');
        if (vOpenParen < 0) return Result.failure("COLUMNAR_ERROR", "Invalid VALUES syntax");

        String valsStr = valuesPart.substring(vOpenParen + 1, vCloseParen);
        String[] vals = valsStr.split(",");

        Map<String, Object> rowData = new HashMap<>();
        for (int i = 0; i < insertCols.size() && i < vals.length; i++) {
            rowData.put(insertCols.get(i).toLowerCase(), parseLiteral(vals[i].trim()));
        }

        table.addRow(rowData);
        return Result.success(null);
    }

    private Result<Object> selectColumnar(String sql, String tableName) {
        ColumnarTable table = tables.get(tableName.toLowerCase());
        if (table == null) return Result.failure("COLUMNAR_ERROR", "Table not found: " + tableName);

        String upper = sql.toUpperCase();
        int selectIdx = upper.indexOf("SELECT ") + 7;
        int fromIdx = upper.indexOf(" FROM ");

        String selectPart = sql.substring(selectIdx, fromIdx).trim();

        // Check for aggregates
        if (selectPart.toUpperCase().contains("COUNT(") || selectPart.toUpperCase().contains("SUM(") ||
                selectPart.toUpperCase().contains("AVG(") || selectPart.toUpperCase().contains("MIN(") ||
                selectPart.toUpperCase().contains("MAX(")) {
            return executeColumnarAggregate(selectPart, table);
        }

        // Column scan projection
        List<String> requestedCols;
        if (selectPart.equals("*")) {
            requestedCols = table.columnNames();
        } else {
            requestedCols = new ArrayList<>();
            for (String c : selectPart.split(",")) {
                requestedCols.add(c.trim());
            }
        }

        Map<String, List<Object>> scan = table.columnScan(requestedCols);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < table.rowCount(); i++) {
            Object[] vals = new Object[requestedCols.size()];
            for (int j = 0; j < requestedCols.size(); j++) {
                List<Object> colData = scan.get(requestedCols.get(j));
                vals[j] = colData != null && i < colData.size() ? colData.get(i) : null;
            }
            rows.add(new Row(vals));
        }

        return Result.success(new QueryResult(requestedCols, rows, 0));
    }

    private Result<Object> executeColumnarAggregate(String selectPart, ColumnarTable table) {
        // Parse aggregate expression: e.g., SUM(amount), COUNT(*)
        String upper = selectPart.toUpperCase().trim();
        int parenIdx = upper.indexOf('(');
        int closeParenIdx = upper.lastIndexOf(')');
        if (parenIdx < 0 || closeParenIdx < 0) {
            return Result.failure("COLUMNAR_ERROR", "Invalid aggregate expression");
        }

        String func = upper.substring(0, parenIdx).trim();
        String col = selectPart.substring(parenIdx + 1, closeParenIdx).trim();

        Object result;
        if (col.equals("*")) {
            result = (long) table.rowCount();
        } else {
            result = table.computeAggregate(col, func);
        }

        String alias = selectPart.trim();
        List<Row> rows = List.of(new Row(new Object[]{result}));
        return Result.success(new QueryResult(List.of(alias), rows, 0));
    }

    private String extractTableName(String sql, String prefix) {
        String after = sql.substring(sql.toUpperCase().indexOf(prefix.toUpperCase()) + prefix.length()).trim();
        return after.split("[\\s(]")[0];
    }

    private String extractFromTable(String sql) {
        String upper = sql.toUpperCase();
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) return null;
        String afterFrom = sql.substring(fromIdx + 6).trim();
        return afterFrom.split("[\\s;]")[0];
    }

    private Object parseLiteral(String val) {
        val = val.trim();
        if (val.startsWith("'") && val.endsWith("'")) return val.substring(1, val.length() - 1);
        if (val.equalsIgnoreCase("NULL")) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}
        return val;
    }

    public ColumnarTable getTable(String name) {
        return tables.get(name.toLowerCase());
    }

    public Map<String, ColumnarTable> tables() {
        return Collections.unmodifiableMap(tables);
    }
}

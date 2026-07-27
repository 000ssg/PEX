package ssg.pex.sql.dbms.executor;

import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates SqlExpression against a row context.
 */
public class ExpressionEvaluator {

    private final Map<String, Object> functionRegistry;

    public ExpressionEvaluator() {
        this.functionRegistry = Map.of();
    }

    public Object evaluate(SqlExpression expr, Row row, List<Column> columns) {
        return evaluate(expr, row, columns, null);
    }

    public Object evaluate(SqlExpression expr, Row row, List<Column> columns, Map<String, String> tableAliases) {
        return switch (expr) {
            case ColumnRef cr -> evaluateColumnRef(cr, row, columns, tableAliases);
            case LiteralExpr le -> le.value();
            case BinaryExpr be -> evaluateBinary(be, row, columns, tableAliases);
            case UnaryExpr ue -> evaluateUnary(ue, row, columns, tableAliases);
            case FunctionExpr fe -> evaluateFunction(fe, row, columns, tableAliases);
            case InExpr ie -> evaluateIn(ie, row, columns, tableAliases);
            case BetweenExpr be -> evaluateBetween(be, row, columns, tableAliases);
            case LikeExpr le -> evaluateLike(le, row, columns, tableAliases);
            case IsNullExpr ine -> evaluateIsNull(ine, row, columns, tableAliases);
            case ExistsExpr ignored -> false; // simplified: would need subquery execution
            case SubqueryExpr ignored -> null; // simplified
            case CaseExpr ce -> evaluateCase(ce, row, columns, tableAliases);
            case AggregateExpr ignored -> null; // aggregates are handled by AggregateEngine
            case StarExpr ignored -> null;
        };
    }

    private Object evaluateColumnRef(ColumnRef cr, Row row, List<Column> columns, Map<String, String> tableAliases) {
        String colName = cr.column();
        String tablePrefix = cr.table();

        // If there's a table prefix, try to match with alias resolution
        if (tablePrefix != null && tableAliases != null) {
            // Resolve alias to table name if needed
            String resolvedTable = tableAliases.getOrDefault(tablePrefix.toLowerCase(), tablePrefix);
            // Look for column with table prefix
            for (int i = 0; i < columns.size(); i++) {
                String qualifiedName = columns.get(i).name();
                if (qualifiedName.equalsIgnoreCase(colName) ||
                        qualifiedName.equalsIgnoreCase(tablePrefix + "." + colName) ||
                        qualifiedName.equalsIgnoreCase(resolvedTable + "." + colName)) {
                    return row.getValue(i);
                }
            }
        }

        // Simple column name match
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i).name();
            if (name.equalsIgnoreCase(colName)) {
                return row.getValue(i);
            }
            // Handle qualified names like "table.column"
            int dotIdx = name.indexOf('.');
            if (dotIdx >= 0 && name.substring(dotIdx + 1).equalsIgnoreCase(colName)) {
                if (tablePrefix == null || name.substring(0, dotIdx).equalsIgnoreCase(tablePrefix)) {
                    return row.getValue(i);
                }
            }
        }

        return null;
    }

    private Object evaluateBinary(BinaryExpr be, Row row, List<Column> columns, Map<String, String> aliases) {
        String op = be.operator().toUpperCase();

        // Short-circuit for AND/OR
        if (op.equals("AND")) {
            Object leftVal = evaluate(be.left(), row, columns, aliases);
            if (!isTruthy(leftVal)) return false;
            Object rightVal = evaluate(be.right(), row, columns, aliases);
            return isTruthy(rightVal);
        }
        if (op.equals("OR")) {
            Object leftVal = evaluate(be.left(), row, columns, aliases);
            if (isTruthy(leftVal)) return true;
            Object rightVal = evaluate(be.right(), row, columns, aliases);
            return isTruthy(rightVal);
        }

        Object left = evaluate(be.left(), row, columns, aliases);
        Object right = evaluate(be.right(), row, columns, aliases);

        // NULL propagation
        if (left == null || right == null) {
            return switch (op) {
                case "=", "==", "<>", "!=" -> left == null && right == null ? op.equals("=") || op.equals("==") : null;
                default -> null;
            };
        }

        return switch (op) {
            case "=" , "==" -> compareValues(left, right) == 0;
            case "<>", "!=" -> compareValues(left, right) != 0;
            case "<" -> compareValues(left, right) < 0;
            case ">" -> compareValues(left, right) > 0;
            case "<=" -> compareValues(left, right) <= 0;
            case ">=" -> compareValues(left, right) >= 0;
            case "+" -> numericOp(left, right, '+');
            case "-" -> numericOp(left, right, '-');
            case "*" -> numericOp(left, right, '*');
            case "/" -> numericOp(left, right, '/');
            case "%" -> numericOp(left, right, '%');
            case "||" -> String.valueOf(left) + String.valueOf(right);
            default -> null;
        };
    }

    private Object evaluateUnary(UnaryExpr ue, Row row, List<Column> columns, Map<String, String> aliases) {
        Object val = evaluate(ue.operand(), row, columns, aliases);
        return switch (ue.operator().toUpperCase()) {
            case "NOT" -> val == null ? null : !isTruthy(val);
            case "-" -> {
                if (val instanceof Number n) yield negateNumber(n);
                yield null;
            }
            case "+" -> val;
            default -> null;
        };
    }

    private Object evaluateFunction(FunctionExpr fe, Row row, List<Column> columns, Map<String, String> aliases) {
        String name = fe.name().toUpperCase();
        List<Object> args = fe.args().stream()
                .map(a -> evaluate(a, row, columns, aliases))
                .toList();

        return switch (name) {
            case "UPPER" -> args.isEmpty() || args.getFirst() == null ? null : args.getFirst().toString().toUpperCase();
            case "LOWER" -> args.isEmpty() || args.getFirst() == null ? null : args.getFirst().toString().toLowerCase();
            case "TRIM" -> args.isEmpty() || args.getFirst() == null ? null : args.getFirst().toString().trim();
            case "LTRIM" -> args.isEmpty() || args.getFirst() == null ? null : ltrim(args.getFirst().toString());
            case "RTRIM" -> args.isEmpty() || args.getFirst() == null ? null : rtrim(args.getFirst().toString());
            case "LENGTH" -> args.isEmpty() || args.getFirst() == null ? null : (long) args.getFirst().toString().length();
            case "CONCAT" -> args.stream().filter(Objects::nonNull).map(Object::toString).reduce("", String::concat);
            case "REPLACE" -> {
                if (args.size() < 3 || args.get(0) == null) yield null;
                yield args.get(0).toString().replace(args.get(1).toString(), args.get(2).toString());
            }
            case "SUBSTRING", "SUBSTR" -> {
                if (args.size() < 2 || args.getFirst() == null) yield null;
                String str = args.getFirst().toString();
                int start = toInt(args.get(1)) - 1; // SQL is 1-based
                if (start < 0) start = 0;
                if (args.size() >= 3) {
                    int len = toInt(args.get(2));
                    yield str.substring(start, Math.min(start + len, str.length()));
                }
                yield str.substring(start);
            }
            case "COALESCE" -> args.stream().filter(Objects::nonNull).findFirst().orElse(null);
            case "NULLIF" -> {
                if (args.size() < 2) yield null;
                yield Objects.equals(args.get(0), args.get(1)) ? null : args.get(0);
            }
            case "ABS" -> {
                if (args.isEmpty() || args.getFirst() == null) yield null;
                yield absNumber(args.getFirst());
            }
            case "ROUND" -> {
                if (args.isEmpty() || args.getFirst() == null) yield null;
                double val = toDouble(args.getFirst());
                int scale = args.size() >= 2 ? toInt(args.get(1)) : 0;
                double factor = Math.pow(10, scale);
                yield Math.round(val * factor) / factor;
            }
            case "CEIL", "CEILING" -> {
                if (args.isEmpty() || args.getFirst() == null) yield null;
                yield Math.ceil(toDouble(args.getFirst()));
            }
            case "FLOOR" -> {
                if (args.isEmpty() || args.getFirst() == null) yield null;
                yield Math.floor(toDouble(args.getFirst()));
            }
            case "CAST" -> args.isEmpty() ? null : args.getFirst(); // simplified
            case "NOW", "CURRENT_TIMESTAMP" -> java.time.Instant.now().toString();
            case "CURRENT_DATE" -> java.time.LocalDate.now().toString();
            default -> null;
        };
    }

    private Object evaluateIn(InExpr ie, Row row, List<Column> columns, Map<String, String> aliases) {
        Object val = evaluate(ie.value(), row, columns, aliases);
        if (val == null) return null;
        boolean found = false;
        for (SqlExpression item : ie.list()) {
            Object itemVal = evaluate(item, row, columns, aliases);
            if (compareValues(val, itemVal) == 0) {
                found = true;
                break;
            }
        }
        return ie.negated() ? !found : found;
    }

    @SuppressWarnings("unchecked")
    private Object evaluateBetween(BetweenExpr be, Row row, List<Column> columns, Map<String, String> aliases) {
        Object val = evaluate(be.value(), row, columns, aliases);
        Object low = evaluate(be.low(), row, columns, aliases);
        Object high = evaluate(be.high(), row, columns, aliases);
        if (val == null || low == null || high == null) return null;
        boolean inRange = compareValues(val, low) >= 0 && compareValues(val, high) <= 0;
        return be.negated() ? !inRange : inRange;
    }

    private Object evaluateLike(LikeExpr le, Row row, List<Column> columns, Map<String, String> aliases) {
        Object val = evaluate(le.value(), row, columns, aliases);
        if (val == null) return null;
        String str = val.toString();
        String pattern = le.pattern();
        // Convert SQL LIKE pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("%", ".*")
                .replace("_", ".");
        boolean matches = str.matches("(?i)" + regex);
        return le.negated() ? !matches : matches;
    }

    private Object evaluateIsNull(IsNullExpr ine, Row row, List<Column> columns, Map<String, String> aliases) {
        Object val = evaluate(ine.value(), row, columns, aliases);
        boolean isNull = val == null;
        return ine.negated() ? !isNull : isNull;
    }

    private Object evaluateCase(CaseExpr ce, Row row, List<Column> columns, Map<String, String> aliases) {
        if (ce.operand() != null) {
            // Simple CASE
            Object operand = evaluate(ce.operand(), row, columns, aliases);
            for (var when : ce.whenClauses()) {
                Object condition = evaluate(when.condition(), row, columns, aliases);
                if (compareValues(operand, condition) == 0) {
                    return evaluate(when.result(), row, columns, aliases);
                }
            }
        } else {
            // Searched CASE
            for (var when : ce.whenClauses()) {
                Object condition = evaluate(when.condition(), row, columns, aliases);
                if (isTruthy(condition)) {
                    return evaluate(when.result(), row, columns, aliases);
                }
            }
        }
        return ce.elseExpr() != null ? evaluate(ce.elseExpr(), row, columns, aliases) : null;
    }

    // ---- Utilities ----

    public static boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.doubleValue() != 0;
        if (val instanceof String s) return !s.isEmpty();
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compareValues(Object left, Object right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;

        // Promote to compatible types for comparison
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }

        if (left instanceof Comparable && right instanceof Comparable) {
            try {
                // Try comparing as strings if types don't match
                if (left.getClass() != right.getClass()) {
                    return left.toString().compareToIgnoreCase(right.toString());
                }
                return ((Comparable) left).compareTo(right);
            } catch (ClassCastException e) {
                return left.toString().compareToIgnoreCase(right.toString());
            }
        }

        return left.toString().compareToIgnoreCase(right.toString());
    }

    private Object numericOp(Object left, Object right, char op) {
        if (!(left instanceof Number nl) || !(right instanceof Number nr)) {
            return null;
        }
        // If either is floating point, use double
        if (left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float) {
            double l = nl.doubleValue();
            double r = nr.doubleValue();
            return switch (op) {
                case '+' -> l + r;
                case '-' -> l - r;
                case '*' -> l * r;
                case '/' -> r == 0 ? null : l / r;
                case '%' -> r == 0 ? null : l % r;
                default -> null;
            };
        }
        long l = nl.longValue();
        long r = nr.longValue();
        return switch (op) {
            case '+' -> l + r;
            case '-' -> l - r;
            case '*' -> l * r;
            case '/' -> r == 0 ? null : l / r;
            case '%' -> r == 0 ? null : l % r;
            default -> null;
        };
    }

    private Number negateNumber(Number n) {
        if (n instanceof Long l) return -l;
        if (n instanceof Integer i) return -i;
        if (n instanceof Double d) return -d;
        if (n instanceof Float f) return -f;
        return -n.doubleValue();
    }

    private Object absNumber(Object val) {
        if (val instanceof Long l) return Math.abs(l);
        if (val instanceof Integer i) return Math.abs(i);
        if (val instanceof Double d) return Math.abs(d);
        if (val instanceof Float f) return Math.abs(f);
        if (val instanceof Number n) return Math.abs(n.doubleValue());
        return null;
    }

    private static int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static String ltrim(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return s.substring(i);
    }

    private static String rtrim(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        return s.substring(0, i + 1);
    }
}

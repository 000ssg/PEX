package ssg.pex.sql.dbms.executor;

import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.BinaryExpr;
import ssg.pex.sql.ast.SqlExpression.ColumnRef;
import ssg.pex.sql.ast.SqlSupport.JoinClause;
import ssg.pex.sql.ast.SqlSupport.JoinType;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes JOINs: INNER, LEFT, RIGHT, FULL, CROSS.
 */
public class JoinEngine {

    private final ExpressionEvaluator evaluator;

    public JoinEngine() {
        this.evaluator = new ExpressionEvaluator();
    }

    public JoinEngine(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public List<Row> join(List<Row> left, List<Column> leftCols,
                          List<Row> right, List<Column> rightCols,
                          JoinClause clause) {
        return switch (clause.type()) {
            case INNER -> innerJoin(left, leftCols, right, rightCols, clause.on());
            case LEFT -> leftJoin(left, leftCols, right, rightCols, clause.on());
            case RIGHT -> rightJoin(left, leftCols, right, rightCols, clause.on());
            case FULL -> fullJoin(left, leftCols, right, rightCols, clause.on());
            case CROSS -> crossJoin(left, right);
        };
    }

    private List<Row> innerJoin(List<Row> left, List<Column> leftCols,
                                List<Row> right, List<Column> rightCols,
                                SqlExpression onExpr) {
        // Detect equi-join: ON leftTable.col = rightTable.col
        EquiJoinKeys equiKeys = detectEquiJoin(onExpr, leftCols, rightCols);
        if (equiKeys != null) {
            return hashJoinInner(left, leftCols, right, rightCols, equiKeys);
        }

        var result = new ArrayList<Row>();
        var mergedCols = mergeCols(leftCols, rightCols);
        for (Row l : left) {
            for (Row r : right) {
                Row merged = Row.merge(l, r);
                if (onExpr == null || ExpressionEvaluator.isTruthy(evaluator.evaluate(onExpr, merged, mergedCols))) {
                    result.add(merged);
                }
            }
        }
        return result;
    }

    /**
     * Hash-join for equi-joins. Builds a hash table from the right (probe) side,
     * then probes for each left row. O(n + m) instead of O(n * m).
     * Rows with NULL join keys are excluded (standard SQL equi-join semantics).
     */
    private List<Row> hashJoinInner(List<Row> left, List<Column> leftCols,
                                    List<Row> right, List<Column> rightCols,
                                    EquiJoinKeys keys) {
        // Build phase: index right rows by their join-key value
        Map<Object, List<Row>> hashTable = new HashMap<>();
        for (Row r : right) {
            Object keyVal = r.getValue(keys.rightColIdx());
            if (keyVal == null) continue; // NULL keys excluded
            hashTable.computeIfAbsent(keyVal, k -> new ArrayList<>()).add(r);
        }

        // Probe phase
        var result = new ArrayList<Row>();
        for (Row l : left) {
            Object keyVal = l.getValue(keys.leftColIdx());
            if (keyVal == null) continue; // NULL keys excluded
            List<Row> matches = hashTable.get(keyVal);
            if (matches != null) {
                for (Row r : matches) {
                    result.add(Row.merge(l, r));
                }
            }
        }
        return result;
    }

    /**
     * Detects a simple equi-join predicate of the form: leftCol = rightCol.
     * Returns null when the ON expression is not a simple column-equality.
     */
    private EquiJoinKeys detectEquiJoin(SqlExpression onExpr,
                                        List<Column> leftCols, List<Column> rightCols) {
        if (!(onExpr instanceof BinaryExpr be)) return null;
        if (!be.operator().equals("=")) return null;
        if (!(be.left() instanceof ColumnRef lRef)) return null;
        if (!(be.right() instanceof ColumnRef rRef)) return null;

        int leftIdx = resolveColIdx(lRef, leftCols, 0);
        int rightIdx = resolveColIdx(rRef, rightCols, 0);

        if (leftIdx >= 0 && rightIdx >= 0) {
            return new EquiJoinKeys(leftIdx, rightIdx);
        }

        // Also try swapped: right = left
        int leftIdxSwap = resolveColIdx(rRef, leftCols, 0);
        int rightIdxSwap = resolveColIdx(lRef, rightCols, 0);
        if (leftIdxSwap >= 0 && rightIdxSwap >= 0) {
            return new EquiJoinKeys(leftIdxSwap, rightIdxSwap);
        }

        return null;
    }

    /**
     * Resolve a ColumnRef against a column list. Returns the position within the list
     * (ignoring any base offset, since Row positions within left/right are 0-based).
     */
    private int resolveColIdx(ColumnRef ref, List<Column> cols, int baseOffset) {
        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            String name = c.name();
            int dot = name.indexOf('.');
            String simpleName = dot >= 0 ? name.substring(dot + 1) : name;
            String tablePrefix = dot >= 0 ? name.substring(0, dot) : null;

            boolean nameMatch = simpleName.equalsIgnoreCase(ref.column())
                    || name.equalsIgnoreCase(ref.column());
            boolean tableMatch = ref.table() == null || tablePrefix == null
                    || tablePrefix.equalsIgnoreCase(ref.table());
            if (nameMatch && tableMatch) {
                // Return the ordinal stored in the column (which is the row-array index)
                return c.ordinal() - baseOffset >= 0 ? i : i;
            }
        }
        return -1;
    }

    /** Carries the left- and right-side column indices for an equi-join. */
    private record EquiJoinKeys(int leftColIdx, int rightColIdx) {}

    private List<Row> leftJoin(List<Row> left, List<Column> leftCols,
                               List<Row> right, List<Column> rightCols,
                               SqlExpression onExpr) {
        var result = new ArrayList<Row>();
        var mergedCols = mergeCols(leftCols, rightCols);
        for (Row l : left) {
            boolean matched = false;
            for (Row r : right) {
                Row merged = Row.merge(l, r);
                if (onExpr == null || ExpressionEvaluator.isTruthy(evaluator.evaluate(onExpr, merged, mergedCols))) {
                    result.add(merged);
                    matched = true;
                }
            }
            if (!matched) {
                result.add(Row.merge(l, Row.nullRow(rightCols.size())));
            }
        }
        return result;
    }

    private List<Row> rightJoin(List<Row> left, List<Column> leftCols,
                                List<Row> right, List<Column> rightCols,
                                SqlExpression onExpr) {
        var result = new ArrayList<Row>();
        var mergedCols = mergeCols(leftCols, rightCols);
        for (Row r : right) {
            boolean matched = false;
            for (Row l : left) {
                Row merged = Row.merge(l, r);
                if (onExpr == null || ExpressionEvaluator.isTruthy(evaluator.evaluate(onExpr, merged, mergedCols))) {
                    result.add(merged);
                    matched = true;
                }
            }
            if (!matched) {
                result.add(Row.merge(Row.nullRow(leftCols.size()), r));
            }
        }
        return result;
    }

    private List<Row> fullJoin(List<Row> left, List<Column> leftCols,
                               List<Row> right, List<Column> rightCols,
                               SqlExpression onExpr) {
        var result = new ArrayList<Row>();
        var mergedCols = mergeCols(leftCols, rightCols);
        var matchedRight = new boolean[right.size()];

        for (Row l : left) {
            boolean matched = false;
            for (int j = 0; j < right.size(); j++) {
                Row merged = Row.merge(l, right.get(j));
                if (onExpr == null || ExpressionEvaluator.isTruthy(evaluator.evaluate(onExpr, merged, mergedCols))) {
                    result.add(merged);
                    matched = true;
                    matchedRight[j] = true;
                }
            }
            if (!matched) {
                result.add(Row.merge(l, Row.nullRow(rightCols.size())));
            }
        }

        // Add unmatched right rows
        for (int j = 0; j < right.size(); j++) {
            if (!matchedRight[j]) {
                result.add(Row.merge(Row.nullRow(leftCols.size()), right.get(j)));
            }
        }

        return result;
    }

    private List<Row> crossJoin(List<Row> left, List<Row> right) {
        var result = new ArrayList<Row>();
        for (Row l : left) {
            for (Row r : right) {
                result.add(Row.merge(l, r));
            }
        }
        return result;
    }

    private List<Column> mergeCols(List<Column> leftCols, List<Column> rightCols) {
        var merged = new ArrayList<Column>(leftCols.size() + rightCols.size());
        merged.addAll(leftCols);
        for (int i = 0; i < rightCols.size(); i++) {
            Column c = rightCols.get(i);
            merged.add(new Column(c.name(), c.dataType(), c.nullable(), c.defaultValue(), c.autoIncrement(), leftCols.size() + i));
        }
        return merged;
    }
}

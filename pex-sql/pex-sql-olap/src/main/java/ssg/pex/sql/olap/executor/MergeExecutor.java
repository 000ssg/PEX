package ssg.pex.sql.olap.executor;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.executor.ExpressionEvaluator;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.olap.ast.MergeAction;
import ssg.pex.sql.olap.ast.MergeNode;

import java.util.*;

/**
 * Executes MERGE INTO statements.
 * Matches source rows against target using the ON condition,
 * then applies WHEN MATCHED / WHEN NOT MATCHED actions.
 */
public class MergeExecutor {

    private final InMemoryDatabase database;
    private final ExpressionEvaluator evaluator;

    public MergeExecutor(InMemoryDatabase database) {
        this.database = database;
        this.evaluator = new ExpressionEvaluator();
    }

    public Result<DmlResult> execute(MergeNode merge) {
        try {
            Table targetTable = database.defaultSchema().getTable(merge.targetTable());
            Table sourceTable = database.defaultSchema().getTable(merge.sourceTable());

            if (targetTable == null) {
                return Result.failure("MERGE_ERROR", "Target table not found: " + merge.targetTable());
            }
            if (sourceTable == null) {
                return Result.failure("MERGE_ERROR", "Source table not found: " + merge.sourceTable());
            }

            String targetPrefix = merge.targetAlias() != null ? merge.targetAlias() : merge.targetTable();
            String sourcePrefix = merge.sourceAlias() != null ? merge.sourceAlias() : merge.sourceTable();

            // Build combined column list for expression evaluation
            var allColumns = new ArrayList<Column>();
            for (Column c : targetTable.columns()) {
                allColumns.add(new Column(targetPrefix + "." + c.name(), c.dataType(), c.nullable(),
                        c.defaultValue(), c.autoIncrement(), allColumns.size()));
            }
            int sourceOffset = allColumns.size();
            for (Column c : sourceTable.columns()) {
                allColumns.add(new Column(sourcePrefix + "." + c.name(), c.dataType(), c.nullable(),
                        c.defaultValue(), c.autoIncrement(), allColumns.size()));
            }

            int affected = 0;
            List<Row> sourceRows = sourceTable.scan();
            List<Row> targetRows = targetTable.scan();

            // Track which target rows were matched
            var matchedTargetIndices = new HashSet<Integer>();
            // Track rows to add/remove/update
            var rowsToAdd = new ArrayList<Row>();
            var rowsToRemove = new ArrayList<Integer>(); // indices in descending order
            var rowUpdates = new LinkedHashMap<Integer, Row>(); // target index -> new row

            for (Row sourceRow : sourceRows) {
                boolean matched = false;
                for (int ti = 0; ti < targetRows.size(); ti++) {
                    Row targetRow = targetRows.get(ti);
                    Row combined = Row.merge(targetRow, sourceRow);

                    Object result = evaluator.evaluate(merge.onCondition(), combined, allColumns);
                    if (ExpressionEvaluator.isTruthy(result)) {
                        matched = true;
                        matchedTargetIndices.add(ti);

                        // Apply WHEN MATCHED actions
                        for (MergeAction action : merge.actions()) {
                            if (action instanceof MergeAction.WhenMatchedUpdate update) {
                                Row updatedRow = applyUpdate(targetRow, targetTable, update, combined, allColumns);
                                rowUpdates.put(ti, updatedRow);
                                affected++;
                            } else if (action instanceof MergeAction.WhenMatchedDelete) {
                                rowsToRemove.add(ti);
                                affected++;
                            }
                        }
                        break; // Each source row matches at most one target row
                    }
                }

                if (!matched) {
                    // Apply WHEN NOT MATCHED actions
                    for (MergeAction action : merge.actions()) {
                        if (action instanceof MergeAction.WhenNotMatchedInsert insert) {
                            Row newRow = buildInsertRow(targetTable, insert, sourceRow, sourceTable,
                                    sourcePrefix, allColumns, Row.merge(Row.nullRow(targetTable.columns().size()), sourceRow));
                            rowsToAdd.add(newRow);
                            affected++;
                        }
                    }
                }
            }

            // Apply updates
            for (var entry : rowUpdates.entrySet()) {
                targetTable.updateRow(entry.getKey(), entry.getValue());
            }

            // Apply deletes (in reverse order to preserve indices)
            rowsToRemove.sort(Comparator.reverseOrder());
            for (int idx : rowsToRemove) {
                targetTable.removeRow(idx);
            }

            // Apply inserts
            for (Row row : rowsToAdd) {
                targetTable.addRow(row);
            }

            return Result.success(DmlResult.of(affected));
        } catch (Exception e) {
            return Result.failure("MERGE_ERROR", e.getMessage(), e);
        }
    }

    private Row applyUpdate(Row targetRow, Table targetTable, MergeAction.WhenMatchedUpdate update,
                            Row combined, List<Column> allColumns) {
        Row newRow = targetRow.copy();
        for (var entry : update.setColumns().entrySet()) {
            String colName = entry.getKey();
            SqlExpression valueExpr = entry.getValue();

            int colIdx = targetTable.getColumnIndex(colName);
            if (colIdx >= 0) {
                Object value = evaluator.evaluate(valueExpr, combined, allColumns);
                newRow.setValue(colIdx, value);
            }
        }
        return newRow;
    }

    private Row buildInsertRow(Table targetTable, MergeAction.WhenNotMatchedInsert insert,
                               Row sourceRow, Table sourceTable, String sourcePrefix,
                               List<Column> allColumns, Row combined) {
        Object[] values = new Object[targetTable.columns().size()];

        if (!insert.columns().isEmpty()) {
            for (int i = 0; i < insert.columns().size() && i < insert.values().size(); i++) {
                String colName = insert.columns().get(i);
                int colIdx = targetTable.getColumnIndex(colName);
                if (colIdx >= 0) {
                    Object value = evaluator.evaluate(insert.values().get(i), combined, allColumns);
                    values[colIdx] = value;
                }
            }
        } else {
            // No column list specified: evaluate values in order
            for (int i = 0; i < insert.values().size() && i < targetTable.columns().size(); i++) {
                values[i] = evaluator.evaluate(insert.values().get(i), combined, allColumns);
            }
        }

        return new Row(values);
    }
}

package ssg.pex.sql.olap.executor;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.olap.ast.GroupingSetSpec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluates CUBE, ROLLUP, and GROUPING SETS.
 * Runs multiple GROUP BY queries and unions the results, with NULL markers for non-grouped columns.
 */
public class GroupingSetExecutor {

    private final InMemoryDatabase database;

    public GroupingSetExecutor(InMemoryDatabase database) {
        this.database = database;
    }

    /**
     * Execute a query with CUBE, ROLLUP, or GROUPING SETS.
     *
     * @param tableName     the source table
     * @param spec          the grouping set specification
     * @param aggregates    list of aggregate expressions (e.g., "SUM(amount)")
     * @param whereClause   optional WHERE clause (null if none)
     * @return combined result with all grouping combinations
     */
    public Result<QueryResult> execute(String tableName, GroupingSetSpec spec,
                                       List<String> aggregates, String whereClause) {
        try {
            List<List<String>> groupingSets = expandGroupingSets(spec);

            var allColumnNames = new ArrayList<String>();
            var allRows = new ArrayList<Row>();

            // Collect all distinct grouping columns from all sets
            var allGroupCols = spec.sets().stream()
                    .flatMap(List::stream)
                    .distinct()
                    .toList();

            // Build output column names: grouping columns + aggregates
            allColumnNames.addAll(allGroupCols);
            for (String agg : aggregates) {
                allColumnNames.add(agg);
            }

            for (List<String> groupCols : groupingSets) {
                var sql = new StringBuilder("SELECT ");

                // For each allGroupCol, add it if it's in groupCols, else add NULL
                var selectParts = new ArrayList<String>();
                for (String col : allGroupCols) {
                    if (groupCols.contains(col)) {
                        selectParts.add(col);
                    } else {
                        selectParts.add("NULL AS " + col);
                    }
                }
                selectParts.addAll(aggregates);
                sql.append(String.join(", ", selectParts));

                sql.append(" FROM ").append(tableName);
                if (whereClause != null && !whereClause.isEmpty()) {
                    sql.append(" WHERE ").append(whereClause);
                }
                if (!groupCols.isEmpty()) {
                    sql.append(" GROUP BY ").append(String.join(", ", groupCols));
                }

                var result = database.execute(sql.toString());
                if (result.isSuccess() && result.value() instanceof QueryResult qr) {
                    allRows.addAll(qr.rows());
                }
            }

            return Result.success(new QueryResult(allColumnNames, allRows, 0));
        } catch (Exception e) {
            return Result.failure("GROUPING_SET_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Expand the grouping spec into a list of GROUP BY column lists.
     */
    private List<List<String>> expandGroupingSets(GroupingSetSpec spec) {
        return switch (spec.type()) {
            case CUBE -> expandCube(spec.sets().stream()
                    .flatMap(List::stream)
                    .distinct()
                    .toList());
            case ROLLUP -> expandRollup(spec.sets().stream()
                    .flatMap(List::stream)
                    .distinct()
                    .toList());
            case GROUPING_SETS -> spec.sets();
        };
    }

    /**
     * CUBE: all 2^n combinations of the grouping columns, including the empty set.
     */
    private List<List<String>> expandCube(List<String> columns) {
        var result = new ArrayList<List<String>>();
        int n = columns.size();
        for (int mask = (1 << n) - 1; mask >= 0; mask--) {
            var combo = new ArrayList<String>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    combo.add(columns.get(i));
                }
            }
            result.add(combo);
        }
        return result;
    }

    /**
     * ROLLUP: hierarchical aggregation from all columns down to none.
     * ROLLUP(a, b, c) = (a,b,c), (a,b), (a), ()
     */
    private List<List<String>> expandRollup(List<String> columns) {
        var result = new ArrayList<List<String>>();
        for (int i = columns.size(); i >= 0; i--) {
            result.add(columns.subList(0, i));
        }
        return result;
    }
}

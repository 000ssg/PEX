package ssg.pex.sql.dbms.executor;

import ssg.pex.sql.ast.SqlSupport.OrderByItem;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ORDER BY implementation with multi-column sort.
 */
public class SortEngine {

    public List<Row> sort(List<Row> rows, List<OrderByItem> orderByItems, List<Column> columns) {
        if (orderByItems == null || orderByItems.isEmpty()) {
            return rows;
        }

        var sorted = new ArrayList<>(rows);
        sorted.sort(buildComparator(orderByItems, columns));
        return sorted;
    }

    private Comparator<Row> buildComparator(List<OrderByItem> items, List<Column> columns) {
        Comparator<Row> comparator = null;

        for (OrderByItem item : items) {
            int colIdx = findColumnIndex(item.column(), columns);
            Comparator<Row> itemComparator = (r1, r2) -> {
                Object v1 = colIdx >= 0 ? r1.getValue(colIdx) : null;
                Object v2 = colIdx >= 0 ? r2.getValue(colIdx) : null;

                // Handle nulls
                if (v1 == null && v2 == null) return 0;
                if (v1 == null) return item.nullsFirst() ? -1 : 1;
                if (v2 == null) return item.nullsFirst() ? 1 : -1;

                int cmp = ExpressionEvaluator.compareValues(v1, v2);
                return item.ascending() ? cmp : -cmp;
            };

            comparator = comparator == null ? itemComparator : comparator.thenComparing(itemComparator);
        }

        return comparator != null ? comparator : (a, b) -> 0;
    }

    private int findColumnIndex(String name, List<Column> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}

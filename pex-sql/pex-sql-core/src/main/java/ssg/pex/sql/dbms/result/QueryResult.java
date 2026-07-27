package ssg.pex.sql.dbms.result;

import ssg.pex.sql.dbms.Row;

import java.util.List;

public record QueryResult(List<String> columnNames, List<Row> rows, long executionTimeNanos) {

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return columnNames.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}

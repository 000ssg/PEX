package ssg.pex.sql.dbms;

import ssg.pex.sql.ast.SqlSupport.TableConstraint;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Table {

    private final String name;
    private final List<Column> columns;
    private final List<Row> rows;
    private final List<Index> indexes;
    private final List<TableConstraint> constraints;
    private final List<Trigger> triggers;
    private final Map<String, AtomicLong> autoIncrementCounters;

    public Table(String name, List<Column> columns, List<TableConstraint> constraints) {
        this.name = name;
        this.columns = new ArrayList<>(columns);
        this.rows = new ArrayList<>();
        this.indexes = new ArrayList<>();
        this.constraints = new ArrayList<>(constraints);
        this.triggers = new ArrayList<>();
        this.autoIncrementCounters = new HashMap<>();
        for (Column col : columns) {
            if (col.autoIncrement()) {
                autoIncrementCounters.put(col.name(), new AtomicLong(0));
            }
        }
    }

    public String name() {
        return name;
    }

    public List<Column> columns() {
        return Collections.unmodifiableList(columns);
    }

    public List<Row> rows() {
        return Collections.unmodifiableList(rows);
    }

    public List<Row> scan() {
        return new ArrayList<>(rows);
    }

    public int rowCount() {
        return rows.size();
    }

    public Column getColumn(String name) {
        for (Column col : columns) {
            if (col.name().equalsIgnoreCase(name)) {
                return col;
            }
        }
        return null;
    }

    public int getColumnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    public void addRow(Row row) {
        rows.add(row);
        // Update indexes
        for (Index index : indexes) {
            int colIdx = getColumnIndex(index.columnNames().getFirst());
            Object key = row.getValue(colIdx);
            if (key instanceof Comparable<?> ck) {
                index.insert(ck, rows.size() - 1);
            }
        }
    }

    public void removeRow(int index) {
        if (index >= 0 && index < rows.size()) {
            rows.remove(index);
        }
    }

    public void updateRow(int index, Row newRow) {
        if (index >= 0 && index < rows.size()) {
            rows.set(index, newRow);
        }
    }

    public List<Index> indexes() {
        return Collections.unmodifiableList(indexes);
    }

    public void addIndex(Index index) {
        indexes.add(index);
        // Populate index with existing rows
        int colIdx = getColumnIndex(index.columnNames().getFirst());
        for (int i = 0; i < rows.size(); i++) {
            Object key = rows.get(i).getValue(colIdx);
            if (key instanceof Comparable<?> ck) {
                index.insert(ck, i);
            }
        }
    }

    public List<TableConstraint> constraints() {
        return Collections.unmodifiableList(constraints);
    }

    public void addConstraint(TableConstraint constraint) {
        constraints.add(constraint);
    }

    public List<Trigger> triggers() {
        return Collections.unmodifiableList(triggers);
    }

    public void addTrigger(Trigger trigger) {
        triggers.add(trigger);
    }

    public long nextAutoIncrement(String columnName) {
        var counter = autoIncrementCounters.get(columnName);
        if (counter == null) {
            throw new IllegalArgumentException("Column " + columnName + " is not auto-increment");
        }
        return counter.incrementAndGet();
    }

    public long currentAutoIncrement(String columnName) {
        var counter = autoIncrementCounters.get(columnName);
        return counter == null ? 0 : counter.get();
    }

    public void addColumn(Column column) {
        columns.add(column);
        // Extend existing rows
        for (int i = 0; i < rows.size(); i++) {
            Row old = rows.get(i);
            Object[] newValues = new Object[columns.size()];
            System.arraycopy(old.values(), 0, newValues, 0, old.columnCount());
            newValues[columns.size() - 1] = column.defaultValue();
            rows.set(i, new Row(newValues));
        }
    }

    public void dropColumn(String columnName) {
        int idx = getColumnIndex(columnName);
        if (idx < 0) return;
        columns.remove(idx);
        for (int i = 0; i < rows.size(); i++) {
            Row old = rows.get(i);
            Object[] newValues = new Object[columns.size()];
            int dest = 0;
            for (int src = 0; src < old.columnCount(); src++) {
                if (src != idx) {
                    newValues[dest++] = old.getValue(src);
                }
            }
            rows.set(i, new Row(newValues));
        }
        // Update ordinals
        for (int j = 0; j < columns.size(); j++) {
            Column c = columns.get(j);
            if (c.ordinal() != j) {
                columns.set(j, new Column(c.name(), c.dataType(), c.nullable(), c.defaultValue(), c.autoIncrement(), j));
            }
        }
    }
}

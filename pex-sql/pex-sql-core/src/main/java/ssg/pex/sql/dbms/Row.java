package ssg.pex.sql.dbms;

import java.util.Arrays;
import java.util.Objects;

public class Row {

    private final Object[] values;

    public Row(int columnCount) {
        this.values = new Object[columnCount];
    }

    public Row(Object[] values) {
        this.values = Arrays.copyOf(values, values.length);
    }

    public Object getValue(int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) {
            return null;
        }
        return values[ordinal];
    }

    public void setValue(int ordinal, Object value) {
        if (ordinal >= 0 && ordinal < values.length) {
            values[ordinal] = value;
        }
    }

    public int columnCount() {
        return values.length;
    }

    public Object[] values() {
        return values;
    }

    public Row copy() {
        return new Row(Arrays.copyOf(values, values.length));
    }

    /**
     * Merge two rows (for JOIN operations).
     */
    public static Row merge(Row left, Row right) {
        int leftLen = left.values.length;
        int rightLen = right.values.length;
        var merged = new Object[leftLen + rightLen];
        System.arraycopy(left.values, 0, merged, 0, leftLen);
        System.arraycopy(right.values, 0, merged, leftLen, rightLen);
        return new Row(merged);
    }

    /**
     * Creates a null-filled row with the given column count.
     */
    public static Row nullRow(int columnCount) {
        return new Row(columnCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Row row = (Row) o;
        return Arrays.deepEquals(values, row.values);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(values);
    }

    @Override
    public String toString() {
        return "Row" + Arrays.toString(values);
    }
}

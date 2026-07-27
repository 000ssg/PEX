package ssg.pex.sql.dbms.transaction;

import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.Schema;
import ssg.pex.sql.dbms.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Records row-level changes for rollback.
 */
public class ChangeLog {

    public sealed interface ChangeEntry permits InsertEntry, UpdateEntry, DeleteEntry {}

    public record InsertEntry(String tableName, int rowIndex) implements ChangeEntry {}

    public record UpdateEntry(String tableName, int rowIndex, Row previousValues) implements ChangeEntry {}

    public record DeleteEntry(String tableName, int rowIndex, Row deletedRow) implements ChangeEntry {}

    private final List<ChangeEntry> entries = new ArrayList<>();

    public void recordInsert(String tableName, int rowIndex) {
        entries.add(new InsertEntry(tableName, rowIndex));
    }

    public void recordUpdate(String tableName, int rowIndex, Row previousValues) {
        entries.add(new UpdateEntry(tableName, rowIndex, previousValues));
    }

    public void recordDelete(String tableName, int rowIndex, Row deletedRow) {
        entries.add(new DeleteEntry(tableName, rowIndex, deletedRow));
    }

    public int size() {
        return entries.size();
    }

    public List<ChangeEntry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Reverse all changes.
     */
    public void undo(Schema schema) {
        undoTo(0, schema);
    }

    /**
     * Partial undo for savepoints: undo changes from position onwards.
     */
    public void undoTo(int position, Schema schema) {
        for (int i = entries.size() - 1; i >= position; i--) {
            ChangeEntry entry = entries.get(i);
            switch (entry) {
                case InsertEntry ie -> {
                    Table table = schema.getTable(ie.tableName());
                    if (table != null && ie.rowIndex() < table.rowCount()) {
                        table.removeRow(ie.rowIndex());
                    }
                }
                case UpdateEntry ue -> {
                    Table table = schema.getTable(ue.tableName());
                    if (table != null && ue.rowIndex() < table.rowCount()) {
                        table.updateRow(ue.rowIndex(), ue.previousValues());
                    }
                }
                case DeleteEntry de -> {
                    Table table = schema.getTable(de.tableName());
                    if (table != null) {
                        // Re-insert at original position
                        // For simplicity, we add at the end
                        table.addRow(de.deletedRow());
                    }
                }
            }
        }
        // Trim the log
        while (entries.size() > position) {
            entries.removeLast();
        }
    }
}

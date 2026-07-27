package ssg.pex.sql.dbms;

import java.util.*;

public class Schema {

    private final String name;
    private final Map<String, Table> tables;
    private final Map<String, View> views;
    private final Map<String, StoredProcedure> procedures;
    private final Map<String, Index> indexes;

    public Schema(String name) {
        this.name = name;
        this.tables = new LinkedHashMap<>();
        this.views = new LinkedHashMap<>();
        this.procedures = new LinkedHashMap<>();
        this.indexes = new LinkedHashMap<>();
    }

    public String name() {
        return name;
    }

    public Map<String, Table> tables() {
        return Collections.unmodifiableMap(tables);
    }

    public Table getTable(String name) {
        return tables.get(name.toLowerCase());
    }

    public void addTable(Table table) {
        tables.put(table.name().toLowerCase(), table);
    }

    public void removeTable(String name) {
        tables.remove(name.toLowerCase());
    }

    public boolean hasTable(String name) {
        return tables.containsKey(name.toLowerCase());
    }

    public Map<String, View> views() {
        return Collections.unmodifiableMap(views);
    }

    public View getView(String name) {
        return views.get(name.toLowerCase());
    }

    public void addView(View view) {
        views.put(view.name().toLowerCase(), view);
    }

    public void removeView(String name) {
        views.remove(name.toLowerCase());
    }

    public Map<String, StoredProcedure> procedures() {
        return Collections.unmodifiableMap(procedures);
    }

    public StoredProcedure getProcedure(String name) {
        return procedures.get(name.toLowerCase());
    }

    public void addProcedure(StoredProcedure proc) {
        procedures.put(proc.name().toLowerCase(), proc);
    }

    public Map<String, Index> indexes() {
        return Collections.unmodifiableMap(indexes);
    }

    public void addIndex(Index index) {
        indexes.put(index.name().toLowerCase(), index);
        Table table = getTable(index.tableName());
        if (table != null) {
            table.addIndex(index);
        }
    }
}

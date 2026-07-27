package ssg.pex.sql.dbms;

import java.util.*;

public class Index {

    private final String name;
    private final String tableName;
    private final List<String> columnNames;
    private final boolean unique;
    @SuppressWarnings("rawtypes")
    private final TreeMap<Comparable, List<Integer>> btree;

    public Index(String name, String tableName, List<String> columnNames, boolean unique) {
        this.name = name;
        this.tableName = tableName;
        this.columnNames = List.copyOf(columnNames);
        this.unique = unique;
        this.btree = new TreeMap<>();
    }

    public String name() {
        return name;
    }

    public String tableName() {
        return tableName;
    }

    public List<String> columnNames() {
        return columnNames;
    }

    public boolean unique() {
        return unique;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Integer> lookup(Comparable key) {
        var list = btree.get(key);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Integer> rangeScan(Comparable from, Comparable to) {
        var result = new ArrayList<Integer>();
        var subMap = btree.subMap(from, true, to, true);
        for (var entry : subMap.values()) {
            result.addAll(entry);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void insert(Comparable key, int rowOrdinal) {
        btree.computeIfAbsent(key, k -> new ArrayList<>()).add(rowOrdinal);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void delete(Comparable key, int rowOrdinal) {
        var list = btree.get(key);
        if (list != null) {
            list.remove(Integer.valueOf(rowOrdinal));
            if (list.isEmpty()) {
                btree.remove(key);
            }
        }
    }

    public int size() {
        return btree.values().stream().mapToInt(List::size).sum();
    }

    public void clear() {
        btree.clear();
    }
}

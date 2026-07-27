package ssg.pex.nosql.dialects.cassandra;

import java.util.*;

/**
 * Tracks Cassandra table schema metadata: partition keys, clustering columns, and column types.
 * Used for CQL validation during DDL parsing.
 */
public final class CassandraSchema {

    private final String keyspace;
    private final String table;
    private final List<String> partitionKeys;
    private final List<String> clusteringKeys;
    private final Map<String, String> columns; // column name → CQL type

    public CassandraSchema(String keyspace, String table,
                           List<String> partitionKeys,
                           List<String> clusteringKeys,
                           Map<String, String> columns) {
        this.keyspace = keyspace;
        this.table = table;
        this.partitionKeys = List.copyOf(partitionKeys);
        this.clusteringKeys = List.copyOf(clusteringKeys);
        this.columns = Map.copyOf(columns);
    }

    public String keyspace() { return keyspace; }
    public String table() { return table; }
    public List<String> partitionKeys() { return partitionKeys; }
    public List<String> clusteringKeys() { return clusteringKeys; }
    public Map<String, String> columns() { return columns; }

    /** Returns the qualified name {@code keyspace.table}. */
    public String qualifiedName() {
        return keyspace + "." + table;
    }

    @Override
    public String toString() {
        return "CassandraSchema{" + qualifiedName() + ", pk=" + partitionKeys + ", ck=" + clusteringKeys + "}";
    }
}

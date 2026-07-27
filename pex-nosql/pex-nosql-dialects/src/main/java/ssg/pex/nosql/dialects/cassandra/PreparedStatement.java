package ssg.pex.nosql.dialects.cassandra;

/**
 * Represents a CQL prepared statement with placeholder ({@code ?}) parameters.
 */
public final class PreparedStatement {

    private final String cql;
    private final String keyspace;

    PreparedStatement(String cql, String keyspace) {
        this.cql = cql;
        this.keyspace = keyspace;
    }

    /** Returns the original CQL string. */
    public String cql() {
        return cql;
    }

    /** Returns the keyspace active when this statement was prepared. */
    public String keyspace() {
        return keyspace;
    }

    @Override
    public String toString() {
        return "PreparedStatement{cql='" + cql + "', keyspace='" + keyspace + "'}";
    }
}

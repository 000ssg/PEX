package ssg.pex.sql.dialects;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dialects.engines.*;
import ssg.pex.sql.dialects.mssql.MssqlExecutor;
import ssg.pex.sql.dialects.mssql.MssqlFunctions;
import ssg.pex.sql.dialects.mssql.MssqlParser;
import ssg.pex.sql.dialects.mysql.MysqlExecutor;
import ssg.pex.sql.dialects.mysql.MysqlFunctions;
import ssg.pex.sql.dialects.mysql.MysqlParser;
import ssg.pex.sql.dialects.oracle.OracleExecutor;
import ssg.pex.sql.dialects.oracle.OracleFunctions;
import ssg.pex.sql.dialects.oracle.OracleParser;
import ssg.pex.sql.dialects.postgresql.PostgresqlExecutor;
import ssg.pex.sql.dialects.postgresql.PostgresqlFunctions;
import ssg.pex.sql.dialects.postgresql.PostgresqlParser;

/**
 * Wraps InMemoryDatabase with dialect-specific features.
 * Routes SQL parsing and execution through dialect-specific parsers and executors.
 */
public class DialectDatabase implements AutoCloseable {

    public enum DialectType {
        ORACLE, MSSQL, MYSQL, POSTGRESQL, COLUMNAR, TIMESERIES, FULLTEXT, SPATIAL
    }

    private final InMemoryDatabase db;
    private final DialectType dialect;
    private final DialectFunctionRegistry functionRegistry;

    // Dialect-specific components
    private final OracleParser oracleParser;
    private final OracleExecutor oracleExecutor;
    private final MssqlParser mssqlParser;
    private final MssqlExecutor mssqlExecutor;
    private final MysqlParser mysqlParser;
    private final MysqlExecutor mysqlExecutor;
    private final PostgresqlParser postgresqlParser;
    private final PostgresqlExecutor postgresqlExecutor;

    // Special engines
    private final ColumnarEngine columnarEngine;
    private final TimeSeriesEngine timeSeriesEngine;
    private final FullTextEngine fullTextEngine;
    private final SpatialEngine spatialEngine;

    public DialectDatabase(InMemoryDatabase db, DialectType dialect) {
        this.db = db;
        this.dialect = dialect;
        this.functionRegistry = new DialectFunctionRegistry();

        this.oracleParser = new OracleParser();
        this.oracleExecutor = new OracleExecutor(db);
        this.mssqlParser = new MssqlParser();
        this.mssqlExecutor = new MssqlExecutor(db);
        this.mysqlParser = new MysqlParser();
        this.mysqlExecutor = new MysqlExecutor(db);
        this.postgresqlParser = new PostgresqlParser();
        this.postgresqlExecutor = new PostgresqlExecutor(db);

        this.columnarEngine = new ColumnarEngine();
        this.timeSeriesEngine = new TimeSeriesEngine();
        this.fullTextEngine = new FullTextEngine();
        this.spatialEngine = new SpatialEngine();

        registerDialectFunctions();
    }

    private void registerDialectFunctions() {
        switch (dialect) {
            case ORACLE -> OracleFunctions.register(functionRegistry);
            case MSSQL -> MssqlFunctions.register(functionRegistry);
            case MYSQL -> MysqlFunctions.register(functionRegistry);
            case POSTGRESQL -> PostgresqlFunctions.register(functionRegistry);
            default -> {}
        }
    }

    /**
     * Parse and execute SQL using the configured dialect.
     */
    public Result<Object> execute(String sql) {
        try {
            return switch (dialect) {
                case ORACLE -> oracleExecutor.execute(sql, functionRegistry);
                case MSSQL -> mssqlExecutor.execute(sql, functionRegistry);
                case MYSQL -> mysqlExecutor.execute(sql, functionRegistry);
                case POSTGRESQL -> postgresqlExecutor.execute(sql, functionRegistry);
                case COLUMNAR -> columnarEngine.execute(sql, db);
                case TIMESERIES -> timeSeriesEngine.execute(sql, db);
                case FULLTEXT -> fullTextEngine.execute(sql, db);
                case SPATIAL -> spatialEngine.execute(sql, db);
            };
        } catch (Exception e) {
            return Result.failure("DIALECT_ERROR", e.getMessage(), e);
        }
    }

    public InMemoryDatabase db() {
        return db;
    }

    public DialectType dialect() {
        return dialect;
    }

    public DialectFunctionRegistry functionRegistry() {
        return functionRegistry;
    }

    public ColumnarEngine columnarEngine() {
        return columnarEngine;
    }

    public TimeSeriesEngine timeSeriesEngine() {
        return timeSeriesEngine;
    }

    public FullTextEngine fullTextEngine() {
        return fullTextEngine;
    }

    public SpatialEngine spatialEngine() {
        return spatialEngine;
    }

    @Override
    public void close() {
        db.close();
    }
}

package ssg.pex.sql.dbms;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.*;
import ssg.pex.sql.dbms.executor.*;
import ssg.pex.sql.dbms.transaction.IsolationLevel;
import ssg.pex.sql.dbms.transaction.TransactionManager;
import ssg.pex.sql.grammar.SqlParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Top-level in-memory database. Manages schemas and dispatches SQL statements.
 */
public class InMemoryDatabase implements AutoCloseable {

    /** Maximum number of SQL strings kept in the parse cache. */
    private static final int PARSE_CACHE_MAX_SIZE = 1024;

    protected final Schema defaultSchema;
    private final Map<String, Schema> schemas;
    private final TransactionManager transactionManager;
    private final QueryExecutor queryExecutor;
    private final DmlExecutor dmlExecutor;
    private final DdlExecutor ddlExecutor;
    private final SqlParser parser;
    private String currentSessionId;

    /**
     * LRU cache: SQL string → parsed SqlNode.
     * Bounded to PARSE_CACHE_MAX_SIZE entries per database instance.
     */
    private final Map<String, SqlNode> parseCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SqlNode> eldest) {
                    return size() > PARSE_CACHE_MAX_SIZE;
                }
            }
    );

    public InMemoryDatabase() {
        this.defaultSchema = new Schema("public");
        this.schemas = new LinkedHashMap<>();
        this.schemas.put("public", defaultSchema);
        this.transactionManager = new TransactionManager(defaultSchema);
        this.queryExecutor = new QueryExecutor();
        this.dmlExecutor = new DmlExecutor(queryExecutor);
        this.ddlExecutor = new DdlExecutor();
        this.parser = new SqlParser();
        this.currentSessionId = "default";
    }

    protected InMemoryDatabase(Schema schema) {
        this.defaultSchema = schema;
        this.schemas = new LinkedHashMap<>();
        this.schemas.put(schema.name(), schema);
        this.transactionManager = new TransactionManager(schema);
        this.queryExecutor = new QueryExecutor();
        this.dmlExecutor = new DmlExecutor(queryExecutor);
        this.ddlExecutor = new DdlExecutor();
        this.parser = new SqlParser();
        this.currentSessionId = "default";
    }

    /**
     * Parse and execute a SQL string.
     * The parsed AST is cached so repeated identical SQL strings skip re-parsing.
     */
    public Result<Object> execute(String sql) {
        SqlNode cached = parseCache.get(sql);
        if (cached != null) {
            return execute(cached);
        }
        var parseResult = parser.parse(sql);
        if (parseResult.isFailure()) {
            return Result.failure(parseResult.error());
        }
        SqlNode node = parseResult.value();
        parseCache.put(sql, node);
        return execute(node);
    }

    /**
     * Parse the INSERT template once, then insert all rows from the stream.
     * The stream is consumed lazily — rows are not buffered in a list.
     *
     * <p>The template uses {@code ?} as positional parameter placeholders:
     * {@code INSERT INTO products (id, name, price) VALUES (?, ?, ?)}
     *
     * @param template SQL INSERT with positional {@code ?} parameters
     * @param rows     lazy stream of parameter arrays; each array matches the template params
     * @return total number of rows inserted
     * @throws IllegalArgumentException if the template is not a valid INSERT statement
     */
    public int executeBatch(String template, Stream<Object[]> rows) {
        // Resolve the template to a parsed InsertNode (column list + table name).
        // We use the cache key as-is and substitute ? with NULL for parsing.
        InsertNode insertTemplate = resolveInsertTemplate(template);

        final InsertNode tmpl = insertTemplate;
        final int[] count = {0};

        rows.forEach(paramArray -> {
            // Build a single-row InsertNode by substituting the parameter array.
            List<Object> values = new java.util.ArrayList<>(paramArray.length);
            for (Object v : paramArray) {
                values.add(v);
            }
            InsertNode singleRow = new InsertNode(
                    tmpl.tableName(),
                    tmpl.columns(),
                    List.of(values),
                    null,
                    tmpl.location()
            );
            var result = dmlExecutor.executeInsert(singleRow, defaultSchema);
            if (result.isSuccess()) {
                count[0] += result.value().affectedRows();
            }
        });

        return count[0];
    }

    /**
     * Resolve an INSERT template (possibly containing {@code ?} placeholders) to
     * a parsed {@link InsertNode}. The result is cached by the original template
     * string to avoid re-parsing on subsequent calls.
     */
    private InsertNode resolveInsertTemplate(String template) {
        // Fast path: already in cache
        SqlNode cached = parseCache.get(template);
        if (cached instanceof InsertNode in) {
            return in;
        }

        // Replace ? placeholders with NULL so the parser can handle them.
        String parseable = template.replace("?", "NULL");
        var parseResult = parser.parse(parseable);
        if (parseResult.isFailure()) {
            throw new IllegalArgumentException("Failed to parse INSERT template: " + parseResult.error());
        }
        if (!(parseResult.value() instanceof InsertNode in)) {
            throw new IllegalArgumentException("Template must be an INSERT statement");
        }
        // Cache under the original template string
        parseCache.put(template, in);
        return in;
    }

    /**
     * Backward-compatible overload — delegates to the Stream variant.
     *
     * @param template SQL INSERT template
     * @param rows     list of parameter arrays
     * @return total number of rows inserted
     */
    public int executeBatch(String template, List<Object[]> rows) {
        return executeBatch(template, rows.stream());
    }

    /**
     * Returns the current size of the parse cache (for testing / diagnostics).
     */
    public int parseCacheSize() {
        return parseCache.size();
    }

    /**
     * Clears the parse cache.
     */
    public void clearParseCache() {
        parseCache.clear();
    }

    /**
     * Execute a parsed SQL node.
     */
    public Result<Object> execute(SqlNode node) {
        return switch (node) {
            case SelectNode select -> queryExecutor.execute(select, defaultSchema).map(r -> r);
            case InsertNode insert -> dmlExecutor.executeInsert(insert, defaultSchema).map(r -> r);
            case UpdateNode update -> dmlExecutor.executeUpdate(update, defaultSchema).map(r -> r);
            case DeleteNode delete -> dmlExecutor.executeDelete(delete, defaultSchema).map(r -> r);
            case CreateTableNode ct -> ddlExecutor.executeCreateTable(ct, defaultSchema).map(r -> r);
            case DropTableNode dt -> ddlExecutor.executeDropTable(dt, defaultSchema).map(r -> r);
            case AlterTableNode at -> ddlExecutor.executeAlterTable(at, defaultSchema).map(r -> r);
            case CreateIndexNode ci -> ddlExecutor.executeCreateIndex(ci, defaultSchema).map(r -> r);
            case CreateViewNode cv -> ddlExecutor.executeCreateView(cv, defaultSchema).map(r -> r);
            case TransactionNode txn -> executeTransaction(txn);
            case CreateTriggerNode ct -> executeCreateTrigger(ct);
            case CreateProcedureNode cp -> executeCreateProcedure(cp);
            case CallNode call -> executeCall(call);
        };
    }

    private Result<Object> executeTransaction(TransactionNode node) {
        try {
            return switch (node.action()) {
                case BEGIN -> {
                    transactionManager.begin(currentSessionId, IsolationLevel.READ_COMMITTED);
                    yield Result.success(null);
                }
                case COMMIT -> {
                    transactionManager.commit(currentSessionId);
                    yield Result.success(null);
                }
                case ROLLBACK -> {
                    if (node.savepointName() != null) {
                        transactionManager.rollbackToSavepoint(currentSessionId, node.savepointName());
                    } else {
                        transactionManager.rollback(currentSessionId);
                    }
                    yield Result.success(null);
                }
                case SAVEPOINT -> {
                    transactionManager.savepoint(currentSessionId, node.savepointName());
                    yield Result.success(null);
                }
                case RELEASE_SAVEPOINT -> {
                    transactionManager.releaseSavepoint(currentSessionId, node.savepointName());
                    yield Result.success(null);
                }
            };
        } catch (Exception e) {
            return Result.failure("TRANSACTION_ERROR", e.getMessage(), e);
        }
    }

    private Result<Object> executeCreateTrigger(CreateTriggerNode node) {
        try {
            Table table = defaultSchema.getTable(node.tableName());
            if (table == null) {
                return Result.failure("SQL_ERROR", "Table not found: " + node.tableName());
            }
            var trigger = new Trigger(node.triggerName(), node.timing(), node.event(),
                    node.tableName(), node.body());
            table.addTrigger(trigger);
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    private Result<Object> executeCreateProcedure(CreateProcedureNode node) {
        try {
            var proc = new StoredProcedure(node.procedureName(), node.params(), node.body(), null);
            defaultSchema.addProcedure(proc);
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    private Result<Object> executeCall(CallNode node) {
        try {
            StoredProcedure proc = defaultSchema.getProcedure(node.procedureName());
            if (proc == null) {
                return Result.failure("SQL_ERROR", "Procedure not found: " + node.procedureName());
            }
            // Execute the body statements
            for (SqlNode stmt : proc.body()) {
                var result = execute(stmt);
                if (result.isFailure()) return result;
            }
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    // ---- Accessors ----

    public Schema defaultSchema() {
        return defaultSchema;
    }

    public Map<String, Schema> schemas() {
        return Map.copyOf(schemas);
    }

    public TransactionManager transactionManager() {
        return transactionManager;
    }

    public QueryExecutor queryExecutor() {
        return queryExecutor;
    }

    public DmlExecutor dmlExecutor() {
        return dmlExecutor;
    }

    public DdlExecutor ddlExecutor() {
        return ddlExecutor;
    }

    public String currentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    @Override
    public void close() {
        // Clean up resources
    }
}

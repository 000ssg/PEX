package ssg.pex.nosql.dialects.cassandra;

import ssg.pex.nosql.*;

import java.util.*;
import java.util.regex.*;

/**
 * Simple string-based CQL parser.
 * Maps CQL statements to {@link NoSqlCollection} operations.
 *
 * <p>Supported statements:
 * <ul>
 *   <li>CREATE KEYSPACE</li>
 *   <li>USE keyspace</li>
 *   <li>CREATE TABLE ks.tbl (columns...) [WITH CLUSTERING ORDER BY ...]</li>
 *   <li>INSERT INTO ks.tbl (cols) VALUES (vals) [USING TTL n]</li>
 *   <li>SELECT [cols] FROM ks.tbl [WHERE ...] [LIMIT n]</li>
 *   <li>UPDATE ks.tbl [USING TTL n] SET col = val WHERE ...</li>
 *   <li>DELETE [cols] FROM ks.tbl WHERE ...</li>
 *   <li>TRUNCATE ks.tbl</li>
 *   <li>DROP TABLE ks.tbl</li>
 *   <li>CREATE INDEX ON ks.tbl (col)</li>
 *   <li>SELECT COUNT(*) FROM ks.tbl</li>
 *   <li>SELECT DISTINCT col FROM ks.tbl</li>
 * </ul>
 */
public final class CqlParser {

    private final InMemoryNoSqlDatabase database;

    // Schema registry: keyspace.table → schema
    private final Map<String, CassandraSchema> schemas = new LinkedHashMap<>();

    /** Default keyspace (set by USE statement). */
    private String currentKeyspace = "default_ks";

    public CqlParser(InMemoryNoSqlDatabase database) {
        this.database = database;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String currentKeyspace() { return currentKeyspace; }
    public Map<String, CassandraSchema> schemas() { return Collections.unmodifiableMap(schemas); }

    /**
     * Parses and executes a CQL statement.
     *
     * @param cql    the CQL string (may contain {@code ?} placeholders)
     * @param params bound parameter values (in order)
     * @return result set
     */
    public CassandraResultSet execute(String cql, Object... params) {
        String normalized = normalizeCql(cql);
        String[] tokens = normalized.split("\\s+", 3);
        if (tokens.length == 0) return CassandraResultSet.empty();

        String verb = tokens[0].toUpperCase();
        return switch (verb) {
            case "CREATE" -> handleCreate(normalized, params);
            case "USE" -> handleUse(normalized);
            case "INSERT" -> handleInsert(normalized, params);
            case "SELECT" -> handleSelect(normalized, params);
            case "UPDATE" -> handleUpdate(normalized, params);
            case "DELETE" -> handleDelete(normalized, params);
            case "TRUNCATE" -> handleTruncate(normalized);
            case "DROP" -> handleDrop(normalized);
            case "BEGIN" -> CassandraResultSet.empty(); // handled by executeBatch
            case "APPLY" -> CassandraResultSet.empty();
            default -> throw new NoSqlException("CQL_PARSE_ERROR", "Unknown CQL verb: " + verb);
        };
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    private CassandraResultSet handleCreate(String cql, Object[] params) {
        String upper = cql.toUpperCase();
        if (upper.startsWith("CREATE KEYSPACE")) {
            // No-op in memory
            return CassandraResultSet.empty();
        }
        if (upper.startsWith("CREATE INDEX")) {
            return handleCreateIndex(cql);
        }
        if (upper.startsWith("CREATE TABLE")) {
            return handleCreateTable(cql);
        }
        return CassandraResultSet.empty();
    }

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" +
            "([\\w.]+)\\s*\\((.+?)\\)(?:\\s+WITH\\s+(.*))?$",
            Pattern.DOTALL);

    private CassandraResultSet handleCreateTable(String cql) {
        Matcher m = CREATE_TABLE_PATTERN.matcher(cql.trim());
        if (!m.find()) {
            throw new NoSqlException("CQL_PARSE_ERROR", "Cannot parse CREATE TABLE: " + cql);
        }
        String qualifiedName = m.group(1);
        String columnsDef = m.group(2);

        String[] parts = splitQualifiedName(qualifiedName);
        String keyspace = parts[0];
        String table = parts[1];
        String collectionName = keyspace + "." + table;

        // Parse columns
        List<String> partitionKeys = new ArrayList<>();
        List<String> clusteringKeys = new ArrayList<>();
        Map<String, String> columns = new LinkedHashMap<>();

        String primaryKeySpec = null;
        for (String col : splitColumns(columnsDef)) {
            col = col.trim();
            if (col.toUpperCase().startsWith("PRIMARY KEY")) {
                primaryKeySpec = col;
                continue;
            }
            String[] colParts = col.split("\\s+", 3);
            if (colParts.length >= 2) {
                String colName = colParts[0].toLowerCase();
                String colType = colParts[1].toUpperCase();
                columns.put(colName, colType);
                if (col.toUpperCase().contains("PRIMARY KEY")) {
                    partitionKeys.add(colName);
                }
            }
        }

        // Handle composite PRIMARY KEY (id, ts)
        if (primaryKeySpec != null) {
            parseCompositePrimaryKey(primaryKeySpec, partitionKeys, clusteringKeys);
        }

        CassandraSchema schema = new CassandraSchema(keyspace, table, partitionKeys, clusteringKeys, columns);
        schemas.put(collectionName, schema);

        if (!database.collectionExists(collectionName)) {
            database.createCollection(collectionName);
        }
        return CassandraResultSet.empty();
    }

    private void parseCompositePrimaryKey(String spec, List<String> partitionKeys, List<String> clusteringKeys) {
        // PRIMARY KEY ((col1), col2, col3) or PRIMARY KEY (col1, col2)
        Matcher m = Pattern.compile("(?i)PRIMARY\\s+KEY\\s*\\((.+)\\)").matcher(spec);
        if (!m.find()) return;
        String keyPart = m.group(1).trim();

        if (keyPart.startsWith("(")) {
            // Composite partition key
            int end = keyPart.indexOf(')');
            String pkPart = keyPart.substring(1, end);
            for (String pk : pkPart.split(",")) {
                partitionKeys.add(pk.trim().toLowerCase());
            }
            String remaining = keyPart.substring(end + 1).trim();
            if (remaining.startsWith(",")) {
                for (String ck : remaining.substring(1).split(",")) {
                    String ckTrimmed = ck.trim().toLowerCase();
                    if (!ckTrimmed.isEmpty()) clusteringKeys.add(ckTrimmed);
                }
            }
        } else {
            String[] keys = keyPart.split(",");
            partitionKeys.add(keys[0].trim().toLowerCase());
            for (int i = 1; i < keys.length; i++) {
                clusteringKeys.add(keys[i].trim().toLowerCase());
            }
        }
    }

    private CassandraResultSet handleCreateIndex(String cql) {
        // CREATE INDEX ON ks.tbl (col)
        Pattern p = Pattern.compile("(?i)CREATE\\s+INDEX\\s+(?:ON\\s+)?([\\w.]+)\\s*\\(([^)]+)\\)");
        Matcher m = p.matcher(cql);
        if (m.find()) {
            String tableName = m.group(1);
            String field = m.group(2).trim().toLowerCase();
            String[] parts = splitQualifiedName(tableName);
            String collectionName = parts[0] + "." + parts[1];
            if (database.collectionExists(collectionName)) {
                database.getCollection(collectionName).createIndex(Document.of(field, 1), false);
            }
        }
        return CassandraResultSet.empty();
    }

    // ── USE ───────────────────────────────────────────────────────────────────

    private CassandraResultSet handleUse(String cql) {
        Pattern p = Pattern.compile("(?i)USE\\s+(\\w+)");
        Matcher m = p.matcher(cql);
        if (m.find()) {
            currentKeyspace = m.group(1).toLowerCase();
        }
        return CassandraResultSet.empty();
    }

    // ── INSERT ────────────────────────────────────────────────────────────────

    // Values group must handle nested parens like uuid()
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "(?i)INSERT\\s+INTO\\s+([\\w.]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\((.+?)\\)(?:\\s+USING\\s+TTL\\s+(\\d+|\\?))?\\s*$",
            Pattern.DOTALL);

    private CassandraResultSet handleInsert(String cql, Object[] params) {
        // Pre-process: replace uuid() with a placeholder to avoid nested-paren issues
        String processedCql = cql.replaceAll("(?i)uuid\\(\\)", "##UUID##")
                                 .replaceAll("(?i)now\\(\\)", "##NOW##");
        Matcher m = INSERT_PATTERN.matcher(processedCql);
        if (!m.find()) {
            throw new NoSqlException("CQL_PARSE_ERROR", "Cannot parse INSERT: " + cql);
        }
        String tableName = m.group(1);
        String colList = m.group(2);
        String valList = m.group(3).replace("##UUID##", "uuid()").replace("##NOW##", "now()");
        String ttlToken = m.group(4);

        String[] parts = splitQualifiedName(tableName);
        String collectionName = parts[0] + "." + parts[1];
        NoSqlCollection col = database.getCollection(collectionName);

        String[] cols = colList.split(",");
        String[] vals = valList.split(",");

        int paramIdx = 0;
        Document doc = new Document();
        for (int i = 0; i < cols.length; i++) {
            String field = cols[i].trim().toLowerCase();
            String valToken = i < vals.length ? vals[i].trim() : "?";
            Object value;
            if ("?".equals(valToken)) {
                value = paramIdx < params.length ? params[paramIdx++] : null;
            } else {
                value = parseValue(valToken, null);
            }
            doc.put(field, value);
        }

        WriteResult wr = col.insertOne(doc);

        // Handle TTL
        if (ttlToken != null && col instanceof InMemoryCollection imc) {
            long ttlSeconds;
            if ("?".equals(ttlToken)) {
                Object ttlParam = paramIdx < params.length ? params[paramIdx++] : null;
                ttlSeconds = ttlParam instanceof Number n ? n.longValue() : 0;
            } else {
                ttlSeconds = Long.parseLong(ttlToken);
            }
            if (ttlSeconds > 0) {
                imc.setTtl(doc.id(), ttlSeconds * 1000L);
            }
        }

        return CassandraResultSet.empty();
    }

    // ── SELECT ────────────────────────────────────────────────────────────────

    private CassandraResultSet handleSelect(String cql, Object[] params) {
        // SELECT COUNT(*)
        if (cql.toUpperCase().contains("COUNT(*)")) {
            return handleSelectCount(cql, params);
        }
        // SELECT DISTINCT
        if (cql.toUpperCase().contains("SELECT DISTINCT")) {
            return handleSelectDistinct(cql, params);
        }

        Pattern p = Pattern.compile(
                "(?i)SELECT\\s+(.+?)\\s+FROM\\s+([\\w.]+)" +
                "(?:\\s+WHERE\\s+(.+?))?(?:\\s+LIMIT\\s+(\\d+|\\?))?(?:\\s+ALLOW\\s+FILTERING)?\\s*$");
        Matcher m = p.matcher(cql.trim());
        if (!m.find()) {
            throw new NoSqlException("CQL_PARSE_ERROR", "Cannot parse SELECT: " + cql);
        }
        String colList = m.group(1).trim();
        String tableName = m.group(2).trim();
        String whereClause = m.group(3);
        String limitToken = m.group(4);

        String[] parts = splitQualifiedName(tableName);
        String collectionName = parts[0] + "." + parts[1];
        NoSqlCollection col = database.getCollection(collectionName);

        int[] paramIdx = {0};
        Document filter = whereClause != null
                ? parseWhereClause(whereClause, params, paramIdx) : new Document();

        FindResult result = col.find(filter);

        if (limitToken != null) {
            int limit = "?".equals(limitToken)
                    ? ((Number) params[paramIdx[0]++]).intValue()
                    : Integer.parseInt(limitToken);
            result = result.limit(limit);
        }

        List<Document> rows = result.toList();

        // Apply column projection
        if (!"*".equals(colList)) {
            String[] projCols = Arrays.stream(colList.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toArray(String[]::new);
            rows = rows.stream().map(d -> {
                Document projected = new Document();
                for (String c : projCols) {
                    if (d.containsKey(c)) projected.put(c, d.get(c));
                }
                return projected;
            }).toList();
        }

        return CassandraResultSet.ofRows(rows);
    }

    private CassandraResultSet handleSelectCount(String cql, Object[] params) {
        Pattern p = Pattern.compile("(?i)SELECT\\s+COUNT\\(\\*\\)\\s+FROM\\s+([\\w.]+)(?:\\s+WHERE\\s+(.+))?");
        Matcher m = p.matcher(cql);
        if (!m.find()) return CassandraResultSet.ofCount(0);
        String tableName = m.group(1);
        String whereClause = m.group(2);

        String[] parts = splitQualifiedName(tableName);
        String collectionName = parts[0] + "." + parts[1];
        NoSqlCollection col = database.getCollection(collectionName);

        int[] paramIdx = {0};
        Document filter = whereClause != null
                ? parseWhereClause(whereClause, params, paramIdx) : new Document();
        long count = col.count(filter);
        return CassandraResultSet.ofCount(count);
    }

    private CassandraResultSet handleSelectDistinct(String cql, Object[] params) {
        Pattern p = Pattern.compile("(?i)SELECT\\s+DISTINCT\\s+(.+?)\\s+FROM\\s+([\\w.]+)");
        Matcher m = p.matcher(cql);
        if (!m.find()) return CassandraResultSet.empty();

        String field = m.group(1).trim().toLowerCase();
        String tableName = m.group(2).trim();
        String[] parts = splitQualifiedName(tableName);
        String collectionName = parts[0] + "." + parts[1];
        NoSqlCollection col = database.getCollection(collectionName);

        List<Object> distinctVals = col.distinct(field);
        List<Document> rows = distinctVals.stream()
                .map(v -> Document.of(field, v))
                .toList();
        return CassandraResultSet.ofRows(rows);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    private CassandraResultSet handleUpdate(String cql, Object[] params) {
        // UPDATE ks.tbl [USING TTL n] SET col=val,... WHERE col=val
        Pattern p = Pattern.compile(
                "(?i)UPDATE\\s+([\\w.]+)(?:\\s+USING\\s+TTL\\s+(\\d+|\\?))?" +
                "\\s+SET\\s+(.+?)\\s+WHERE\\s+(.+)$");
        Matcher m = p.matcher(cql.trim());
        if (!m.find()) {
            throw new NoSqlException("CQL_PARSE_ERROR", "Cannot parse UPDATE: " + cql);
        }
        String tableName = m.group(1);
        String ttlToken = m.group(2);
        String setClause = m.group(3);
        String whereClause = m.group(4);

        String[] parts = splitQualifiedName(tableName);
        String collectionName = parts[0] + "." + parts[1];
        NoSqlCollection col = database.getCollection(collectionName);

        int[] paramIdx = {0};
        Document setDoc = parseSetClause(setClause, params, paramIdx);
        Document filter = parseWhereClause(whereClause, params, paramIdx);
        Document update = new Document();
        update.put("$set", setDoc);

        WriteResult wr = col.updateMany(filter, update);

        // Handle TTL: apply to matched docs (simplified: re-query and set TTL)
        if (ttlToken != null && col instanceof InMemoryCollection imc) {
            long ttlSeconds;
            if ("?".equals(ttlToken)) {
                Object p2 = paramIdx[0] < params.length ? params[paramIdx[0]++] : null;
                ttlSeconds = p2 instanceof Number n ? n.longValue() : 0;
            } else {
                ttlSeconds = Long.parseLong(ttlToken);
            }
            if (ttlSeconds > 0) {
                long finalTtlSeconds = ttlSeconds;
                col.find(filter).toList().forEach(d ->
                        imc.setTtl(d.id(), finalTtlSeconds * 1000L));
            }
        }

        return CassandraResultSet.empty();
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    private CassandraResultSet handleDelete(String cql, Object[] params) {
        // DELETE [cols] FROM ks.tbl WHERE ...
        Pattern p = Pattern.compile(
                "(?i)DELETE\\s+(.*?)FROM\\s+([\\w.]+)\\s+WHERE\\s+(.+)$");
        Matcher m = p.matcher(cql.trim());
        if (!m.find()) {
            throw new NoSqlException("CQL_PARSE_ERROR", "Cannot parse DELETE: " + cql);
        }
        String colsPart = m.group(1).trim();
        String tableName = m.group(2).trim();
        String whereClause = m.group(3).trim();

        String[] parts = splitQualifiedName(tableName);
        String collectionName = parts[0] + "." + parts[1];
        NoSqlCollection col = database.getCollection(collectionName);

        int[] paramIdx = {0};
        Document filter = parseWhereClause(whereClause, params, paramIdx);

        if (colsPart.isEmpty()) {
            // Delete full documents
            col.deleteMany(filter);
        } else {
            // Unset specific fields ($unset)
            String[] fieldsToDelete = Arrays.stream(colsPart.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toArray(String[]::new);
            Document unsetDoc = new Document();
            for (String f : fieldsToDelete) {
                unsetDoc.put(f, "");
            }
            Document update = new Document();
            update.put("$unset", unsetDoc);
            col.updateMany(filter, update);
        }
        return CassandraResultSet.empty();
    }

    // ── TRUNCATE ─────────────────────────────────────────────────────────────

    private CassandraResultSet handleTruncate(String cql) {
        Pattern p = Pattern.compile("(?i)TRUNCATE\\s+([\\w.]+)");
        Matcher m = p.matcher(cql);
        if (m.find()) {
            String tableName = m.group(1);
            String[] parts = splitQualifiedName(tableName);
            String collectionName = parts[0] + "." + parts[1];
            database.getCollection(collectionName).deleteMany(new Document());
        }
        return CassandraResultSet.empty();
    }

    // ── DROP ─────────────────────────────────────────────────────────────────

    private CassandraResultSet handleDrop(String cql) {
        Pattern p = Pattern.compile("(?i)DROP\\s+(?:TABLE|KEYSPACE)\\s+([\\w.]+)");
        Matcher m = p.matcher(cql);
        if (m.find()) {
            String name = m.group(1);
            String[] parts = splitQualifiedName(name);
            if (parts[1].isEmpty()) {
                // DROP KEYSPACE — remove all tables in keyspace
                String ks = parts[0];
                List<String> toRemove = database.listCollectionNames().stream()
                        .filter(n -> n.startsWith(ks + "."))
                        .toList();
                toRemove.forEach(database::dropCollection);
            } else {
                String collectionName = parts[0] + "." + parts[1];
                database.dropCollection(collectionName);
                schemas.remove(collectionName);
            }
        }
        return CassandraResultSet.empty();
    }

    // ── WHERE parsing ─────────────────────────────────────────────────────────

    private Document parseWhereClause(String where, Object[] params, int[] paramIdx) {
        Document filter = new Document();
        // Split on AND
        String[] conditions = where.split("(?i)\\s+AND\\s+");
        for (String cond : conditions) {
            cond = cond.trim();
            // field operator value
            Matcher m = Pattern.compile("([\\w.]+)\\s*(!=|>=|<=|>|<|=|IN)\\s*(.+)").matcher(cond);
            if (m.find()) {
                String field = m.group(1).trim().toLowerCase();
                String op = m.group(2).trim();
                String valToken = m.group(3).trim();

                Object value;
                if ("?".equals(valToken)) {
                    value = paramIdx[0] < params.length ? params[paramIdx[0]++] : null;
                } else if (valToken.startsWith("(")) {
                    // IN list
                    value = parseInList(valToken, params, paramIdx);
                } else {
                    value = parseValue(valToken, params.length > paramIdx[0] ? params[paramIdx[0]] : null);
                }

                switch (op) {
                    case "=" -> filter.put(field, value);
                    case "!=" -> filter.put(field, Document.of("$ne", value));
                    case ">" -> filter.put(field, Document.of("$gt", value));
                    case ">=" -> filter.put(field, Document.of("$gte", value));
                    case "<" -> filter.put(field, Document.of("$lt", value));
                    case "<=" -> filter.put(field, Document.of("$lte", value));
                    case "IN" -> filter.put(field, Document.of("$in", value));
                }
            }
        }
        return filter;
    }

    private List<Object> parseInList(String token, Object[] params, int[] paramIdx) {
        List<Object> values = new ArrayList<>();
        String inner = token.replaceAll("^\\(|\\)$", "").trim();
        for (String v : inner.split(",")) {
            v = v.trim();
            if ("?".equals(v)) {
                values.add(paramIdx[0] < params.length ? params[paramIdx[0]++] : null);
            } else {
                values.add(parseValue(v, null));
            }
        }
        return values;
    }

    private Document parseSetClause(String setClause, Object[] params, int[] paramIdx) {
        Document doc = new Document();
        for (String assignment : setClause.split(",")) {
            assignment = assignment.trim();
            String[] parts = assignment.split("=", 2);
            if (parts.length == 2) {
                String field = parts[0].trim().toLowerCase();
                String valToken = parts[1].trim();
                Object value;
                if ("?".equals(valToken)) {
                    value = paramIdx[0] < params.length ? params[paramIdx[0]++] : null;
                } else {
                    value = parseValue(valToken, null);
                }
                doc.put(field, value);
            }
        }
        return doc;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String normalizeCql(String cql) {
        return cql.trim().replaceAll("\\s+", " ").replaceAll(";\\s*$", "");
    }

    private String[] splitQualifiedName(String name) {
        int dot = name.indexOf('.');
        if (dot < 0) {
            return new String[]{currentKeyspace, name.toLowerCase()};
        }
        return new String[]{name.substring(0, dot).toLowerCase(), name.substring(dot + 1).toLowerCase()};
    }

    /** Splits column definitions, respecting nested parentheses. */
    private List<String> splitColumns(String columnsDef) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < columnsDef.length(); i++) {
            char c = columnsDef.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(columnsDef.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < columnsDef.length()) {
            result.add(columnsDef.substring(start).trim());
        }
        return result;
    }

    private Object parseValue(String token, Object defaultValue) {
        if (token == null || "?".equals(token) || "null".equalsIgnoreCase(token)) return defaultValue;
        // Strip quotes
        if ((token.startsWith("'") && token.endsWith("'")) ||
            (token.startsWith("\"") && token.endsWith("\""))) {
            return token.substring(1, token.length() - 1);
        }
        // uuid() function
        if ("uuid()".equalsIgnoreCase(token)) {
            return java.util.UUID.randomUUID().toString();
        }
        // now() function
        if ("now()".equalsIgnoreCase(token)) {
            return System.currentTimeMillis();
        }
        // Boolean
        if ("true".equalsIgnoreCase(token)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(token)) return Boolean.FALSE;
        // Numeric
        try { return Long.parseLong(token); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(token); } catch (NumberFormatException ignored) {}
        return token;
    }
}

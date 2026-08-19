package ssg.pex.sql.streaming.engine;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.AggregateFunction;
import ssg.pex.sql.ast.SqlSupport.ColumnDef;
import ssg.pex.sql.ast.SqlSupport.SelectItem;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.streaming.ast.*;
import ssg.pex.sql.streaming.ast.WindowSpec.WindowType;

import java.util.*;

/**
 * Main streaming engine. Manages streams, continuous queries, and event processing.
 */
public class StreamSimulator {

    private final Map<String, StreamSource> streams;
    private final List<RegisteredQuery> activeQueries;
    private final StreamSqlParser parser;
    private final StreamAggregator aggregator;

    public StreamSimulator() {
        this.streams = new LinkedHashMap<>();
        this.activeQueries = new ArrayList<>();
        this.parser = new StreamSqlParser();
        this.aggregator = new StreamAggregator();
    }

    // ---- Stream Management ----

    public Result<Void> createStream(CreateStreamNode node) {
        String name = node.streamName().toLowerCase();
        if (streams.containsKey(name)) {
            return Result.failure("STREAM_EXISTS", "Stream already exists: " + node.streamName());
        }
        var columns = new ArrayList<Column>();
        int ordinal = 0;
        for (ColumnDef cd : node.columns()) {
            columns.add(new Column(cd.name(), cd.dataType(), cd.nullable(), cd.defaultValue(), cd.autoIncrement(), ordinal++));
        }
        streams.put(name, new StreamSource(node.streamName(), columns, node.properties()));
        return Result.success(null);
    }

    public Result<Void> dropStream(String name) {
        String key = name.toLowerCase();
        if (!streams.containsKey(key)) {
            return Result.failure("STREAM_NOT_FOUND", "Stream not found: " + name);
        }
        streams.remove(key);
        // Remove queries that reference this stream
        activeQueries.removeIf(q -> q.query().fromStream().equalsIgnoreCase(name));
        return Result.success(null);
    }

    public boolean hasStream(String name) {
        return streams.containsKey(name.toLowerCase());
    }

    public StreamSource getStream(String name) {
        return streams.get(name.toLowerCase());
    }

    public Collection<String> streamNames() {
        return streams.keySet();
    }

    // ---- Query Registration ----

    public Result<Void> registerQuery(StreamSelectNode query) {
        String streamName = query.fromStream().toLowerCase();
        if (!streams.containsKey(streamName)) {
            return Result.failure("STREAM_NOT_FOUND", "Stream not found: " + query.fromStream());
        }

        WindowManager wm = null;
        if (query.window() != null) {
            wm = WindowManager.from(query.window());
        }

        var watermark = new Watermark();
        activeQueries.add(new RegisteredQuery(query, wm, watermark, new ArrayList<>()));
        return Result.success(null);
    }

    public int activeQueryCount() {
        return activeQueries.size();
    }

    // ---- Event Ingestion ----

    public void ingestEvent(String streamName, StreamEvent event) {
        StreamSource source = streams.get(streamName.toLowerCase());
        if (source != null) {
            source.emit(event);
        }
    }

    // ---- Event Processing ----

    /**
     * Process all buffered events through registered queries.
     * Returns results from closed windows (for EMIT FINAL queries)
     * or per-event results (for EMIT CHANGES queries).
     */
    public List<QueryResult> processEvents() {
        var results = new ArrayList<QueryResult>();

        for (RegisteredQuery rq : activeQueries) {
            StreamSource source = streams.get(rq.query().fromStream().toLowerCase());
            if (source == null) continue;

            List<StreamEvent> events = source.poll(Integer.MAX_VALUE);
            if (events.isEmpty()) continue;

            // Apply WHERE filter
            List<StreamEvent> filtered = filterEvents(events, rq.query());

            for (StreamEvent event : filtered) {
                rq.watermark().advance(event.timestamp());

                if (rq.windowManager() != null) {
                    rq.windowManager().assignToWindows(event);
                } else {
                    // No window: accumulate all events
                    rq.accumulatedEvents().add(event);
                }
            }

            if (rq.windowManager() != null) {
                // Get closed windows and compute aggregates
                long watermark = rq.watermark().currentWatermark();
                List<Window> closed = rq.windowManager().getClosedWindows(watermark);

                for (Window window : closed) {
                    var result = computeWindowResult(rq.query(), window);
                    if (result != null) {
                        results.add(result);
                    }
                }

                // For EMIT CHANGES, also produce results for open windows
                if (rq.query().emit() == EmitStrategy.CHANGES) {
                    for (Window window : rq.windowManager().openWindows()) {
                        var result = computeWindowResult(rq.query(), window);
                        if (result != null) {
                            results.add(result);
                        }
                    }
                }
            } else {
                // No windowing: compute on accumulated events
                var result = computeNonWindowResult(rq.query(), rq.accumulatedEvents());
                if (result != null) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    /**
     * Execute a one-shot query against the current state of a stream.
     */
    public Result<QueryResult> executeQuery(String sql) {
        var parseResult = parser.parse(sql);
        if (parseResult.isFailure()) {
            return Result.failure(parseResult.error());
        }

        StreamNode node = parseResult.value();
        return switch (node) {
            case CreateStreamNode csn -> {
                var r = createStream(csn);
                if (r.isFailure()) yield Result.failure(r.error());
                yield Result.success(new QueryResult(List.of("result"), List.of(new Row(new Object[]{"Stream created"})), 0));
            }
            case DropStreamNode dsn -> {
                Result<Void> r;
                if (dsn.ifExists() && !hasStream(dsn.streamName())) {
                    r = Result.success(null);
                } else {
                    r = dropStream(dsn.streamName());
                }
                if (r.isFailure()) yield Result.failure(r.error());
                yield Result.success(new QueryResult(List.of("result"), List.of(new Row(new Object[]{"Stream dropped"})), 0));
            }
            case StreamSelectNode ssn -> executeStreamSelect(ssn);
            case InsertIntoStreamNode isn -> executeInsertInto(isn);
        };
    }

    private Result<QueryResult> executeStreamSelect(StreamSelectNode query) {
        String streamName = query.fromStream().toLowerCase();
        StreamSource source = streams.get(streamName);
        if (source == null) {
            return Result.failure("STREAM_NOT_FOUND", "Stream not found: " + query.fromStream());
        }

        // Snapshot current events
        List<StreamEvent> events = source.peekAll();
        events = filterEvents(events, query);

        if (query.window() != null) {
            // Build windows from snapshot
            WindowManager wm = WindowManager.from(query.window());
            for (StreamEvent event : events) {
                wm.assignToWindows(event);
            }
            // Force-close all windows for one-shot query
            long maxTs = events.stream().mapToLong(StreamEvent::timestamp).max().orElse(Long.MAX_VALUE);
            List<Window> allWindows = wm.getClosedWindows(maxTs + 1);
            allWindows.addAll(wm.openWindows());

            var allRows = new ArrayList<Row>();
            List<String> colNames = null;
            for (Window w : allWindows) {
                QueryResult wr = computeWindowResult(query, w);
                if (wr != null) {
                    allRows.addAll(wr.rows());
                    if (colNames == null) colNames = wr.columnNames();
                }
            }
            if (colNames == null) colNames = List.of();
            return Result.success(new QueryResult(colNames, allRows, 0));
        } else {
            QueryResult result = computeNonWindowResult(query, events);
            if (result == null) {
                return Result.success(new QueryResult(List.of(), List.of(), 0));
            }
            return Result.success(result);
        }
    }

    private Result<QueryResult> executeInsertInto(InsertIntoStreamNode node) {
        // Register the query as a continuous query
        var regResult = registerQuery(node.query());
        if (regResult.isFailure()) {
            return Result.failure(regResult.error());
        }
        return Result.success(new QueryResult(List.of("result"),
                List.of(new Row(new Object[]{"Query registered"})), 0));
    }

    // ---- Internal Helpers ----

    private List<StreamEvent> filterEvents(List<StreamEvent> events, StreamSelectNode query) {
        if (query.where() == null) return events;

        var filtered = new ArrayList<StreamEvent>();
        for (StreamEvent event : events) {
            if (evaluateCondition(query.where().condition(), event)) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    private boolean evaluateCondition(SqlExpression condition, StreamEvent event) {
        if (condition instanceof SqlExpression.BinaryExpr be) {
            if (be.operator().equals("AND")) {
                return evaluateCondition(be.left(), event) && evaluateCondition(be.right(), event);
            }
            if (be.operator().equals("OR")) {
                return evaluateCondition(be.left(), event) || evaluateCondition(be.right(), event);
            }
            // Comparison
            Object leftVal = resolveExprValue(be.left(), event);
            Object rightVal = resolveExprValue(be.right(), event);
            return compareValues(leftVal, be.operator(), rightVal);
        }
        return true;
    }

    private Object resolveExprValue(SqlExpression expr, StreamEvent event) {
        if (expr instanceof SqlExpression.ColumnRef cr) {
            return event.values().get(cr.column());
        }
        if (expr instanceof SqlExpression.LiteralExpr le) {
            return le.value();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean compareValues(Object left, String operator, Object right) {
        if (left == null || right == null) return false;

        // Coerce types
        if (left instanceof Number ln && right instanceof Number rn) {
            double ld = ln.doubleValue();
            double rd = rn.doubleValue();
            return switch (operator) {
                case "=" -> ld == rd;
                case "<>", "!=" -> ld != rd;
                case "<" -> ld < rd;
                case ">" -> ld > rd;
                case "<=" -> ld <= rd;
                case ">=" -> ld >= rd;
                default -> false;
            };
        }

        int cmp = 0;
        if (left instanceof Comparable cl) {
            try {
                cmp = cl.compareTo(right);
            } catch (ClassCastException e) {
                cmp = left.toString().compareTo(right.toString());
            }
        } else {
            cmp = left.toString().compareTo(right.toString());
        }

        return switch (operator) {
            case "=" -> cmp == 0;
            case "<>", "!=" -> cmp != 0;
            case "<" -> cmp < 0;
            case ">" -> cmp > 0;
            case "<=" -> cmp <= 0;
            case ">=" -> cmp >= 0;
            default -> false;
        };
    }

    private QueryResult computeWindowResult(StreamSelectNode query, Window window) {
        List<StreamEvent> events = window.events();
        if (events.isEmpty()) return null;
        return computeAggregateResult(query, events);
    }

    private QueryResult computeNonWindowResult(StreamSelectNode query, List<StreamEvent> events) {
        if (events.isEmpty()) return null;
        return computeAggregateResult(query, events);
    }

    private QueryResult computeAggregateResult(StreamSelectNode query, List<StreamEvent> events) {
        List<SelectItem> items = query.selectItems();

        // Check if we have aggregates
        boolean hasAggregates = false;
        for (SelectItem item : items) {
            if (!item.star() && isAggregateExpression(item.expression())) {
                hasAggregates = true;
                break;
            }
        }

        if (hasAggregates) {
            var funcs = new ArrayList<AggregateFunction>();
            var columns = new ArrayList<String>();
            var outputNames = new ArrayList<String>();

            for (SelectItem item : items) {
                if (item.star()) continue;
                if (isAggregateExpression(item.expression())) {
                    var parsed = parseAggregateExpression(item.expression());
                    funcs.add(parsed.func);
                    columns.add(parsed.column);
                    outputNames.add(item.alias() != null ? item.alias() : item.expression());
                }
            }

            List<String> groupByColumns = query.groupBy() != null ? query.groupBy().columns() : List.of();

            // Use a dummy window to wrap events
            var dummyWindow = new TumblingWindow(0, Long.MAX_VALUE);
            for (StreamEvent e : events) {
                dummyWindow.addEvent(e);
            }

            var result = aggregator.aggregateWindow(dummyWindow, funcs, columns, outputNames, groupByColumns);
            return result.isSuccess() ? result.value() : null;
        } else {
            // Non-aggregate: project columns
            var colNames = new ArrayList<String>();
            for (SelectItem item : items) {
                if (item.star()) {
                    // Add all columns from first event
                    if (!events.isEmpty()) {
                        colNames.addAll(events.getFirst().values().keySet());
                    }
                } else {
                    colNames.add(item.alias() != null ? item.alias() : item.expression());
                }
            }

            var rows = new ArrayList<Row>();
            for (StreamEvent event : events) {
                var values = new Object[colNames.size()];
                for (int i = 0; i < colNames.size(); i++) {
                    values[i] = event.values().get(colNames.get(i));
                }
                rows.add(new Row(values));
            }
            return new QueryResult(colNames, rows, 0);
        }
    }

    private boolean isAggregateExpression(String expr) {
        String upper = expr.toUpperCase();
        return upper.startsWith("COUNT(") || upper.startsWith("SUM(") ||
                upper.startsWith("AVG(") || upper.startsWith("MIN(") ||
                upper.startsWith("MAX(") || upper.startsWith("GROUP_CONCAT(");
    }

    private record ParsedAggregate(AggregateFunction func, String column) {}

    private ParsedAggregate parseAggregateExpression(String expr) {
        int openParen = expr.indexOf('(');
        int closeParen = expr.lastIndexOf(')');
        String funcName = expr.substring(0, openParen).toUpperCase();
        String column = expr.substring(openParen + 1, closeParen).trim();

        AggregateFunction func = AggregateFunction.valueOf(funcName);
        return new ParsedAggregate(func, column.equals("*") ? null : column);
    }

    /**
     * A registered continuous query with its state.
     */
    record RegisteredQuery(
            StreamSelectNode query,
            WindowManager windowManager,
            Watermark watermark,
            List<StreamEvent> accumulatedEvents
    ) {}

    /**
     * Perform a stream-to-stream join within a time window.
     * Returns matched event pairs from both streams that fall within the join window.
     */
    public List<StreamEvent> joinStreams(String leftStreamName, String rightStreamName,
                                         String joinColumn, long windowMs) {
        StreamSource left = streams.get(leftStreamName.toLowerCase());
        StreamSource right = streams.get(rightStreamName.toLowerCase());
        if (left == null || right == null) return List.of();

        List<StreamEvent> leftEvents = left.peekAll();
        List<StreamEvent> rightEvents = right.peekAll();

        var results = new ArrayList<StreamEvent>();
        for (StreamEvent le : leftEvents) {
            Object leftKey = le.values().get(joinColumn);
            for (StreamEvent re : rightEvents) {
                Object rightKey = re.values().get(joinColumn);
                if (leftKey != null && leftKey.equals(rightKey)) {
                    // Within time window?
                    if (Math.abs(le.timestamp() - re.timestamp()) <= windowMs) {
                        // Merge values
                        var merged = new LinkedHashMap<>(le.values());
                        for (var entry : re.values().entrySet()) {
                            merged.putIfAbsent("right_" + entry.getKey(), entry.getValue());
                        }
                        results.add(new StreamEvent(
                                Math.max(le.timestamp(), re.timestamp()),
                                le.key(),
                                merged));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Perform a stream-to-table lookup join.
     * For each stream event, looks up the matching row in the table data.
     */
    public List<StreamEvent> lookupJoin(String streamName, Map<Object, Map<String, Object>> lookupTable,
                                         String joinColumn) {
        StreamSource source = streams.get(streamName.toLowerCase());
        if (source == null) return List.of();

        var results = new ArrayList<StreamEvent>();
        for (StreamEvent event : source.peekAll()) {
            Object key = event.values().get(joinColumn);
            Map<String, Object> lookupRow = lookupTable.get(key);
            if (lookupRow != null) {
                var merged = new LinkedHashMap<>(event.values());
                merged.putAll(lookupRow);
                results.add(new StreamEvent(event.timestamp(), event.key(), merged));
            }
        }
        return results;
    }
}

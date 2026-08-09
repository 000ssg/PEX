package ssg.pex.nosql;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes an aggregation pipeline against an in-memory list of documents.
 *
 * <p>Supported pipeline stages:
 * <ul>
 *   <li>{@code $match}     — filter documents</li>
 *   <li>{@code $project}   — include/exclude fields</li>
 *   <li>{@code $sort}      — sort documents</li>
 *   <li>{@code $limit}     — limit document count</li>
 *   <li>{@code $skip}      — skip documents</li>
 *   <li>{@code $group}     — group and aggregate</li>
 *   <li>{@code $unwind}    — flatten array field</li>
 *   <li>{@code $lookup}    — left join with another collection</li>
 *   <li>{@code $addFields} — add or compute fields</li>
 *   <li>{@code $count}     — count into a named field</li>
 * </ul>
 */
public final class AggregationEngine {

    private final InMemoryNoSqlDatabase database;

    public AggregationEngine(InMemoryNoSqlDatabase database) {
        this.database = database;
    }

    /**
     * Executes the {@code pipeline} starting from {@code input}.
     *
     * @param input    initial document set
     * @param pipeline list of stage documents, each with one key = stage name
     * @return final document set after all stages
     */
    public List<Document> execute(List<Document> input, List<Document> pipeline) {
        List<Document> cursor = new ArrayList<>(input);
        for (Document stage : pipeline) {
            if (stage.size() != 1) {
                throw new NoSqlException("NOSQL_AGGR_ERROR",
                        "Each pipeline stage must have exactly one key, got: " + stage.keySet());
            }
            String stageName = stage.keySet().iterator().next();
            Object stageSpec = stage.get(stageName);
            cursor = applyStage(stageName, stageSpec, cursor);
        }
        return cursor;
    }

    @SuppressWarnings("unchecked")
    private List<Document> applyStage(String stageName, Object stageSpec, List<Document> docs) {
        return switch (stageName) {
            case "$match" -> {
                Document filter = toDocument(stageSpec);
                yield docs.stream().filter(d -> QueryEvaluator.matches(filter, d)).toList();
            }
            case "$project" -> {
                Document spec = toDocument(stageSpec);
                yield docs.stream().map(d -> applyProject(spec, d)).toList();
            }
            case "$sort" -> {
                Document sortSpec = toDocument(stageSpec);
                List<Document> sorted = new ArrayList<>(docs);
                Comparator<Document> comparator = buildSortComparator(sortSpec);
                sorted.sort(comparator);
                yield sorted;
            }
            case "$limit" -> {
                int n = ((Number) stageSpec).intValue();
                yield docs.stream().limit(n).toList();
            }
            case "$skip" -> {
                int n = ((Number) stageSpec).intValue();
                yield docs.stream().skip(n).toList();
            }
            case "$group" -> applyGroup(toDocument(stageSpec), docs);
            case "$unwind" -> applyUnwind(stageSpec.toString(), docs);
            case "$lookup" -> applyLookup(toDocument(stageSpec), docs);
            case "$addFields" -> {
                Document fieldsSpec = toDocument(stageSpec);
                yield docs.stream().map(d -> applyAddFields(fieldsSpec, d)).toList();
            }
            case "$count" -> {
                String fieldName = stageSpec.toString();
                Document countDoc = new Document();
                countDoc.put(fieldName, (long) docs.size());
                yield List.of(countDoc);
            }
            default -> throw new NoSqlException("NOSQL_AGGR_ERROR", "Unknown pipeline stage: " + stageName);
        };
    }

    @SuppressWarnings("unchecked")
    private Document applyProject(Document spec, Document doc) {
        // Determine mode: 1=include, 0=exclude
        boolean includeMode = spec.values().stream()
                .anyMatch(v -> v instanceof Number n && n.intValue() == 1);

        Document result = new Document();
        if (includeMode) {
            // Always include _id unless explicitly excluded
            if (!spec.containsKey(Document.ID_FIELD) || !isZero(spec.get(Document.ID_FIELD))) {
                if (doc.containsKey(Document.ID_FIELD)) {
                    result.put(Document.ID_FIELD, doc.get(Document.ID_FIELD));
                }
            }
            for (Map.Entry<String, Object> e : spec.entrySet()) {
                String key = e.getKey();
                if (Document.ID_FIELD.equals(key)) continue;
                if (!isZero(e.getValue())) {
                    Object val = e.getValue() instanceof Number ? doc.get(key) : resolveExpression(e.getValue(), doc);
                    if (val != null || doc.containsKey(key)) {
                        result.put(key, val != null ? val : doc.get(key));
                    }
                }
            }
        } else {
            // Exclude mode
            for (String key : doc.keySet()) {
                if (!spec.containsKey(key) || !isZero(spec.get(key))) {
                    result.put(key, doc.get(key));
                }
            }
        }
        return result;
    }

    private boolean isZero(Object v) {
        return v instanceof Number n && n.intValue() == 0;
    }

    private Comparator<Document> buildSortComparator(Document sortSpec) {
        Comparator<Document> comparator = null;
        for (Map.Entry<String, Object> e : sortSpec.entrySet()) {
            String field = e.getKey();
            int dir = ((Number) e.getValue()).intValue();
            Comparator<Document> c = (a, b) -> {
                Object va = QueryEvaluator.resolveField(field, a);
                Object vb = QueryEvaluator.resolveField(field, b);
                if (va == null && vb == null) return 0;
                if (va == null) return -1;
                if (vb == null) return 1;
                if (va instanceof Number na && vb instanceof Number nb) {
                    return Double.compare(na.doubleValue(), nb.doubleValue());
                }
                return va.toString().compareTo(vb.toString());
            };
            if (dir == -1) c = c.reversed();
            comparator = (comparator == null) ? c : comparator.thenComparing(c);
        }
        return comparator != null ? comparator : (a, b) -> 0;
    }

    @SuppressWarnings("unchecked")
    private List<Document> applyGroup(Document spec, List<Document> docs) {
        Object idExpr = spec.get("_id");

        // Build groups: group key string → accumulated docs
        Map<String, List<Document>> groups = new LinkedHashMap<>();
        Map<String, Object> groupKeyValues = new LinkedHashMap<>();

        for (Document doc : docs) {
            String groupKey = resolveGroupKey(idExpr, doc);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(doc);
            groupKeyValues.putIfAbsent(groupKey, resolveGroupKeyValue(idExpr, doc));
        }

        List<Document> result = new ArrayList<>();
        for (Map.Entry<String, List<Document>> groupEntry : groups.entrySet()) {
            String groupKey = groupEntry.getKey();
            List<Document> groupDocs = groupEntry.getValue();
            Document groupResult = new Document();
            groupResult.put("_id", groupKeyValues.get(groupKey));

            for (Map.Entry<String, Object> e : spec.entrySet()) {
                String field = e.getKey();
                if ("_id".equals(field)) continue;

                Object accumSpec = e.getValue();
                if (accumSpec instanceof Map<?, ?> accumMap) {
                    Map<String, Object> accum = (Map<String, Object>) accumMap;
                    String accumOp = accum.keySet().iterator().next();
                    Object accumExpr = accum.get(accumOp);
                    groupResult.put(field, applyAccumulator(accumOp, accumExpr, groupDocs));
                }
            }
            result.add(groupResult);
        }
        return result;
    }

    private String resolveGroupKey(Object idExpr, Document doc) {
        Object val = resolveGroupKeyValue(idExpr, doc);
        return val == null ? "null" : val.toString();
    }

    private Object resolveGroupKeyValue(Object idExpr, Document doc) {
        if (idExpr == null) return null;
        if (idExpr instanceof String s && s.startsWith("$")) {
            return QueryEvaluator.resolveField(s.substring(1), doc);
        }
        return idExpr;
    }

    private Object applyAccumulator(String op, Object expr, List<Document> docs) {
        return switch (op) {
            case "$sum" -> {
                if (expr instanceof Number n) {
                    // constant sum (e.g. $sum: 1 → count)
                    yield (long) docs.size() * n.longValue();
                }
                String field = exprToField(expr);
                double sum = 0;
                for (Document d : docs) {
                    Object v = QueryEvaluator.resolveField(field, d);
                    if (v instanceof Number n) sum += n.doubleValue();
                }
                yield sum;
            }
            case "$avg" -> {
                String field = exprToField(expr);
                double sum = 0;
                int cnt = 0;
                for (Document d : docs) {
                    Object v = QueryEvaluator.resolveField(field, d);
                    if (v instanceof Number n) { sum += n.doubleValue(); cnt++; }
                }
                yield cnt == 0 ? 0.0 : sum / cnt;
            }
            case "$min" -> {
                String field = exprToField(expr);
                Double min = null;
                for (Document d : docs) {
                    Object v = QueryEvaluator.resolveField(field, d);
                    if (v instanceof Number n) {
                        double dv = n.doubleValue();
                        if (min == null || dv < min) min = dv;
                    }
                }
                yield min;
            }
            case "$max" -> {
                String field = exprToField(expr);
                Double max = null;
                for (Document d : docs) {
                    Object v = QueryEvaluator.resolveField(field, d);
                    if (v instanceof Number n) {
                        double dv = n.doubleValue();
                        if (max == null || dv > max) max = dv;
                    }
                }
                yield max;
            }
            case "$count" -> (long) docs.size();
            case "$push" -> {
                String field = exprToField(expr);
                List<Object> list = new ArrayList<>();
                for (Document d : docs) {
                    Object v = QueryEvaluator.resolveField(field, d);
                    list.add(v);
                }
                yield list;
            }
            case "$first" -> {
                String field = exprToField(expr);
                yield docs.isEmpty() ? null : QueryEvaluator.resolveField(field, docs.get(0));
            }
            case "$last" -> {
                String field = exprToField(expr);
                yield docs.isEmpty() ? null : QueryEvaluator.resolveField(field, docs.get(docs.size() - 1));
            }
            default -> throw new NoSqlException("NOSQL_AGGR_ERROR", "Unknown accumulator: " + op);
        };
    }

    private String exprToField(Object expr) {
        if (expr instanceof String s && s.startsWith("$")) return s.substring(1);
        return expr.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Document> applyUnwind(String fieldRef, List<Document> docs) {
        String fieldName = fieldRef.startsWith("$") ? fieldRef.substring(1) : fieldRef;
        List<Document> result = new ArrayList<>();
        for (Document doc : docs) {
            Object val = doc.get(fieldName);
            if (val instanceof List<?> list) {
                for (Object item : list) {
                    Document unwound = doc.copy();
                    unwound.put(fieldName, item);
                    result.add(unwound);
                }
            } else if (val != null) {
                result.add(doc.copy());
            }
            // If field is null or absent, document is omitted (standard MongoDB behavior)
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Document> applyLookup(Document spec, List<Document> docs) {
        String from = (String) spec.get("from");
        String localField = (String) spec.get("localField");
        String foreignField = (String) spec.get("foreignField");
        String as = (String) spec.get("as");

        if (from == null || localField == null || foreignField == null || as == null) {
            throw new NoSqlException("NOSQL_AGGR_ERROR", "$lookup requires from, localField, foreignField, as");
        }

        List<Document> foreignDocs;
        if (database != null && database.collectionExists(from)) {
            foreignDocs = database.getCollection(from).find().toList();
        } else {
            foreignDocs = List.of();
        }

        List<Document> result = new ArrayList<>();
        for (Document doc : docs) {
            Object localVal = QueryEvaluator.resolveField(localField, doc);
            List<Document> matched = foreignDocs.stream()
                    .filter(fd -> {
                        Object fv = QueryEvaluator.resolveField(foreignField, fd);
                        if (localVal == null && fv == null) return true;
                        if (localVal == null || fv == null) return false;
                        return localVal.equals(fv) ||
                               (localVal instanceof Number la && fv instanceof Number fa &&
                                Double.compare(la.doubleValue(), fa.doubleValue()) == 0);
                    })
                    .toList();

            Document out = doc.copy();
            out.put(as, matched);
            result.add(out);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Document applyAddFields(Document fieldsSpec, Document doc) {
        Document result = doc.copy();
        for (Map.Entry<String, Object> e : fieldsSpec.entrySet()) {
            Object computed = resolveExpression(e.getValue(), doc);
            result.put(e.getKey(), computed);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object resolveExpression(Object expr, Document doc) {
        if (expr instanceof String s) {
            if (s.startsWith("$")) {
                return QueryEvaluator.resolveField(s.substring(1), doc);
            }
            return s;
        }
        if (expr instanceof Map<?, ?> exprMap) {
            Map<String, Object> m = (Map<String, Object>) exprMap;
            if (m.size() == 1) {
                String op = m.keySet().iterator().next();
                Object operand = m.get(op);
                return switch (op) {
                    case "$concat" -> {
                        List<?> parts = (List<?>) operand;
                        StringBuilder sb = new StringBuilder();
                        for (Object part : parts) {
                            Object resolved = resolveExpression(part, doc);
                            sb.append(resolved == null ? "" : resolved.toString());
                        }
                        yield sb.toString();
                    }
                    case "$multiply" -> {
                        List<?> parts = (List<?>) operand;
                        double product = 1.0;
                        for (Object part : parts) {
                            Object resolved = resolveExpression(part, doc);
                            if (resolved instanceof Number n) product *= n.doubleValue();
                        }
                        yield product;
                    }
                    case "$subtract" -> {
                        List<?> parts = (List<?>) operand;
                        Object a = resolveExpression(parts.get(0), doc);
                        Object b = resolveExpression(parts.get(1), doc);
                        if (a instanceof Number na && b instanceof Number nb) {
                            yield na.doubleValue() - nb.doubleValue();
                        }
                        yield null;
                    }
                    case "$add" -> {
                        List<?> parts = (List<?>) operand;
                        double sum = 0;
                        for (Object part : parts) {
                            Object resolved = resolveExpression(part, doc);
                            if (resolved instanceof Number n) sum += n.doubleValue();
                        }
                        yield sum;
                    }
                    case "$toLower" -> {
                        Object v = resolveExpression(operand, doc);
                        yield v == null ? null : v.toString().toLowerCase();
                    }
                    case "$toUpper" -> {
                        Object v = resolveExpression(operand, doc);
                        yield v == null ? null : v.toString().toUpperCase();
                    }
                    case "$toString" -> {
                        Object v = resolveExpression(operand, doc);
                        yield v == null ? null : v.toString();
                    }
                    default -> expr;
                };
            }
        }
        return expr;
    }

    @SuppressWarnings("unchecked")
    private Document toDocument(Object obj) {
        if (obj instanceof Document d) return d;
        if (obj instanceof Map<?, ?> m) return new Document((Map<String, Object>) m);
        throw new NoSqlException("NOSQL_AGGR_ERROR", "Expected document, got: " + obj);
    }
}

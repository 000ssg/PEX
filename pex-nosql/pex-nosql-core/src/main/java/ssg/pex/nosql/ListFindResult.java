package ssg.pex.nosql;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * In-memory implementation of {@link FindResult} backed by a {@link List}.
 * All transformation methods create a new instance — original data is never mutated.
 */
public final class ListFindResult implements FindResult {

    private final List<Document> documents;
    private final List<SortSpec> sorts;
    private final int limitValue;   // -1 = no limit
    private final int skipValue;    // 0 = no skip
    private final Projection projection;

    /** Creates a result wrapping the given documents (no transforms applied). */
    public ListFindResult(List<Document> documents) {
        this.documents = List.copyOf(documents);
        this.sorts = List.of();
        this.limitValue = -1;
        this.skipValue = 0;
        this.projection = Projection.none();
    }

    private ListFindResult(List<Document> documents, List<SortSpec> sorts, int limit, int skip, Projection projection) {
        this.documents = documents;
        this.sorts = sorts;
        this.limitValue = limit;
        this.skipValue = skip;
        this.projection = projection;
    }

    @Override
    public List<Document> toList() {
        List<Document> result = new ArrayList<>(documents.stream().map(Document::copy).toList());

        // Apply sorts
        if (!sorts.isEmpty()) {
            Comparator<Document> comparator = null;
            for (SortSpec spec : sorts) {
                Comparator<Document> c = Comparator.comparing(
                        doc -> {
                            Object val = QueryEvaluator.resolveField(spec.field(), doc);
                            if (val == null) return "";
                            return val.toString();
                        },
                        Comparator.naturalOrder()
                );
                // Use numeric comparison when possible
                Comparator<Document> numericComp = (a, b) -> {
                    Object va = QueryEvaluator.resolveField(spec.field(), a);
                    Object vb = QueryEvaluator.resolveField(spec.field(), b);
                    if (va instanceof Number na && vb instanceof Number nb) {
                        return Double.compare(na.doubleValue(), nb.doubleValue());
                    }
                    if (va == null && vb == null) return 0;
                    if (va == null) return -1;
                    if (vb == null) return 1;
                    return va.toString().compareTo(vb.toString());
                };
                Comparator<Document> dirComp = spec.isAscending() ? numericComp : numericComp.reversed();
                comparator = (comparator == null) ? dirComp : comparator.thenComparing(dirComp);
            }
            if (comparator != null) {
                result.sort(comparator);
            }
        }

        // Apply skip
        if (skipValue > 0) {
            result = result.subList(Math.min(skipValue, result.size()), result.size());
        }

        // Apply limit
        if (limitValue >= 0 && result.size() > limitValue) {
            result = result.subList(0, limitValue);
        }

        // Apply projection
        if (projection.mode() != Projection.Mode.NONE) {
            result = result.stream().map(projection::apply).toList();
        }

        return new ArrayList<>(result);
    }

    @Override
    public long count() {
        return toList().size();
    }

    @Override
    public FindResult sort(String field, int direction) {
        List<SortSpec> newSorts = new ArrayList<>(sorts);
        newSorts.add(new SortSpec(field, direction));
        return new ListFindResult(documents, List.copyOf(newSorts), limitValue, skipValue, projection);
    }

    @Override
    public FindResult limit(int n) {
        return new ListFindResult(documents, sorts, n, skipValue, projection);
    }

    @Override
    public FindResult skip(int n) {
        return new ListFindResult(documents, sorts, limitValue, n, projection);
    }

    @Override
    public FindResult project(String... includeFields) {
        return new ListFindResult(documents, sorts, limitValue, skipValue, Projection.include(includeFields));
    }

    @Override
    public FindResult projectExclude(String... excludeFields) {
        return new ListFindResult(documents, sorts, limitValue, skipValue, Projection.exclude(excludeFields));
    }

    @Override
    public Iterator<Document> iterator() {
        return toList().iterator();
    }
}

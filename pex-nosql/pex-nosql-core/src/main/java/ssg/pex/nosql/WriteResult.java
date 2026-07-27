package ssg.pex.nosql;

import java.util.List;

/**
 * Immutable result of a write operation (insert / update / delete / replace).
 */
public record WriteResult(
        int matchedCount,
        int modifiedCount,
        int insertedCount,
        int deletedCount,
        List<Object> insertedIds) {

    /** Result representing n inserted documents with the given ids. */
    public static WriteResult inserted(int n, List<Object> ids) {
        return new WriteResult(0, 0, n, 0, List.copyOf(ids));
    }

    /** Result representing n modified documents (matched = modified = n). */
    public static WriteResult modified(int n) {
        return new WriteResult(n, n, 0, 0, List.of());
    }

    /** Result representing n deleted documents. */
    public static WriteResult deleted(int n) {
        return new WriteResult(0, 0, 0, n, List.of());
    }

    /** No-op result (nothing matched, modified, inserted or deleted). */
    public static WriteResult none() {
        return new WriteResult(0, 0, 0, 0, List.of());
    }

    /** Returns true if at least one document was affected by this operation. */
    public boolean wasAcknowledged() {
        return insertedCount > 0 || modifiedCount > 0 || deletedCount > 0;
    }
}

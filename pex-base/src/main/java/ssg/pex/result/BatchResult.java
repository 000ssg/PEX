package ssg.pex.result;

import java.util.List;

public record BatchResult<T>(List<T> successes, List<PexError> errors, int totalAttempted) {

    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean isFullSuccess() { return errors.isEmpty(); }
    public int successCount() { return successes.size(); }
    public int errorCount() { return errors.size(); }

    @Override
    public String toString() {
        return "BatchResult{success=%d, errors=%d, total=%d}".formatted(successCount(), errorCount(), totalAttempted);
    }
}

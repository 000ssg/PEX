package ssg.pex.exec;

public record ExecutionConfig(int maxRecursionDepth, int maxLoopIterations, long timeoutMillis, ErrorMode errorMode) {

    public static ExecutionConfig defaults() {
        return new ExecutionConfig(256, 100_000, 30_000L, ErrorMode.RESULT);
    }

    public enum ErrorMode {
        RESULT,
        EXCEPTION
    }
}

package ssg.pex.result;

import ssg.pex.ast.SourceLocation;

public record PexError(String code, String message, SourceLocation location, Throwable cause) {

    public PexError(String code, String message) {
        this(code, message, null, null);
    }

    public PexError(String code, String message, SourceLocation location) {
        this(code, message, location, null);
    }

    public PexError(String code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public boolean hasLocation() {
        return location != null;
    }

    public boolean hasCause() {
        return cause != null;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("[").append(code).append("] ").append(message);
        if (location != null) sb.append(" at ").append(location);
        if (cause != null) sb.append(" caused by ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
        return sb.toString();
    }
}

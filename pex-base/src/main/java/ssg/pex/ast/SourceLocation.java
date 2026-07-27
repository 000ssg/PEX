package ssg.pex.ast;

public record SourceLocation(String source, int line, int column, int offset, int length) {

    public static SourceLocation of(String source, int line, int column) {
        return new SourceLocation(source, line, column, -1, -1);
    }

    public static SourceLocation of(int line, int column) {
        return new SourceLocation(null, line, column, -1, -1);
    }

    public static SourceLocation at(int offset, int length) {
        return new SourceLocation(null, -1, -1, offset, length);
    }

    public static final SourceLocation UNKNOWN = new SourceLocation(null, -1, -1, -1, -1);

    @Override
    public String toString() {
        var sb = new StringBuilder();
        if (source != null) sb.append(source).append(":");
        if (line >= 0) {
            sb.append(line);
            if (column >= 0) sb.append(":").append(column);
        } else if (offset >= 0) {
            sb.append("@").append(offset);
            if (length >= 0) sb.append("+").append(length);
        } else {
            sb.append("?");
        }
        return sb.toString();
    }
}

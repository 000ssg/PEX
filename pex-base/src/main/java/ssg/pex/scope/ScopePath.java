package ssg.pex.scope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ScopePath(List<String> segments) {

    public ScopePath {
        segments = List.copyOf(segments);
    }

    public static ScopePath of(String... segments) {
        return new ScopePath(Arrays.asList(segments));
    }

    public ScopePath append(String segment) {
        var newSegments = new ArrayList<>(segments);
        newSegments.add(segment);
        return new ScopePath(newSegments);
    }

    public boolean isAncestorOf(ScopePath other) {
        if (segments.size() >= other.segments.size()) {
            return false;
        }
        return other.segments.subList(0, segments.size()).equals(segments);
    }

    public int depth() {
        return segments.size();
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}

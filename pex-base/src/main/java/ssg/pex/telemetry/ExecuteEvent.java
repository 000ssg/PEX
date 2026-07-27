package ssg.pex.telemetry;

public record ExecuteEvent(String nodeType, long durationNanos, boolean success) implements PexEvent {
}

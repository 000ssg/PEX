package ssg.pex.telemetry;

public record ParseEvent(String grammarName, String input, long durationNanos, boolean success) implements PexEvent {
}

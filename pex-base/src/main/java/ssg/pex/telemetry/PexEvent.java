package ssg.pex.telemetry;

public sealed interface PexEvent permits ParseEvent, ExecuteEvent, ScopeEvent, ErrorEvent {
}

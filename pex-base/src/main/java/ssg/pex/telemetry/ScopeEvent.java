package ssg.pex.telemetry;

import ssg.pex.scope.ScopePath;

public record ScopeEvent(String scopeName, ScopePath path, boolean entering) implements PexEvent {
}

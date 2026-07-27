package ssg.pex.telemetry;

import ssg.pex.result.PexError;

public record ErrorEvent(PexError error, String context) implements PexEvent {
}

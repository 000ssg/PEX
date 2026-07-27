package ssg.pex.exec;

/**
 * Control-flow exception used to propagate return values out of function bodies.
 * Not an error condition -- caught by the function call handler.
 */
public class ReturnException extends RuntimeException {

    private final Object value;

    public ReturnException(Object value) {
        super(null, null, true, false); // Suppress stack trace for performance
        this.value = value;
    }

    public Object value() {
        return value;
    }
}

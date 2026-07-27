package ssg.pex.exec;

public class PexExecutionException extends RuntimeException {

    public PexExecutionException(String message) {
        super(message);
    }

    public PexExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

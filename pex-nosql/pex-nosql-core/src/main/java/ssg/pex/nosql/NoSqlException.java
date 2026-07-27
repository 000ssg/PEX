package ssg.pex.nosql;

/**
 * Runtime exception for NoSQL engine errors.
 */
public class NoSqlException extends RuntimeException {

    private final String errorCode;

    public NoSqlException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NoSqlException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "[" + errorCode + "] " + getMessage();
    }
}

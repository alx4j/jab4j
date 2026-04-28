package pro.alx4j.jab4j.output;

/**
 * Signals invalid export configuration or export IO failures.
 */
public final class ExportException extends RuntimeException {

    /**
     * Creates an export failure with one message.
     *
     * @param message failure detail
     */
    public ExportException(String message) {
        super(message);
    }

    /**
     * Creates an export failure with one message and cause.
     *
     * @param message failure detail
     * @param cause underlying cause
     */
    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}

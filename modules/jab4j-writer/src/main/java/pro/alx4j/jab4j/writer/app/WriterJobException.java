package pro.alx4j.jab4j.writer.app;

import java.util.Objects;

/**
 * Boundary-level failure for one writer run, annotated with the lifecycle stage that failed.
 */
public final class WriterJobException extends RuntimeException {

    private final WriterJobStatus status;

    /**
     * Creates a writer job failure with one lifecycle status and message.
     *
     * @param status lifecycle status that failed
     * @param message failure detail
     */
    public WriterJobException(WriterJobStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Creates a writer job failure with one lifecycle status, message, and cause.
     *
     * @param status lifecycle status that failed
     * @param message failure detail
     * @param cause underlying cause
     */
    public WriterJobException(WriterJobStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Returns the lifecycle status that failed.
     *
     * @return failed lifecycle status
     */
    public WriterJobStatus status() {
        return status;
    }
}

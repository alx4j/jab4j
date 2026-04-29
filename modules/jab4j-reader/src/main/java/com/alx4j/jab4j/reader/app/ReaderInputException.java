package com.alx4j.jab4j.reader.app;

/**
 * Signals invalid reader input or reader-side filesystem validation failures.
 */
public final class ReaderInputException extends RuntimeException {

    /**
     * Creates a reader input exception with a message.
     *
     * @param message failure detail
     */
    public ReaderInputException(String message) {
        super(message);
    }

    /**
     * Creates a reader input exception with a message and cause.
     *
     * @param message failure detail
     * @param cause underlying cause
     */
    public ReaderInputException(String message, Throwable cause) {
        super(message, cause);
    }
}

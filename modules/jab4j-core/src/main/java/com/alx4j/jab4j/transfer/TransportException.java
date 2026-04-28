package com.alx4j.jab4j.transfer;

/**
 * Runtime exception for deterministic transport-planning and envelope failures.
 */
public final class TransportException extends RuntimeException {

    /**
     * Creates a transport exception with a message.
     *
     * @param message failure message
     */
    public TransportException(String message) {
        super(message);
    }

    /**
     * Creates a transport exception with a message and cause.
     *
     * @param message failure message
     * @param cause underlying cause
     */
    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.alx4j.jab4j.reader.restore;

import java.util.Objects;

/**
 * Internal restore failure carrying the public restore status that should be returned to callers.
 */
final class ReaderRestoreException extends RuntimeException {

    private final ReaderRestoreStatus status;

    /**
     * Creates a restore failure without a cause.
     *
     * @param status public restore failure status
     * @param message failure detail
     */
    ReaderRestoreException(ReaderRestoreStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (status == ReaderRestoreStatus.RESTORED) {
            throw new IllegalArgumentException("restore failures must not use RESTORED status");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Creates a restore failure with the underlying cause.
     *
     * @param status public restore failure status
     * @param message failure detail
     * @param cause underlying failure
     */
    ReaderRestoreException(ReaderRestoreStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (status == ReaderRestoreStatus.RESTORED) {
            throw new IllegalArgumentException("restore failures must not use RESTORED status");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Returns the public restore failure status.
     *
     * @return restore failure status
     */
    ReaderRestoreStatus status() {
        return status;
    }
}

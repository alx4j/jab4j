package com.alx4j.jab4j.catalog;

/**
 * Raised when filesystem inputs cannot be packaged into a deterministic manifest.
 */
public final class PackagingException extends IllegalArgumentException {

    /**
     * Creates a packaging failure with a message only.
     *
     * @param message failure summary
     */
    public PackagingException(String message) {
        super(message);
    }

    /**
     * Creates a packaging failure with an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying error
     */
    public PackagingException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.alx4j.jab4j.writer.config;

/**
 * Raised when resolved runtime configuration violates mandatory architectural constraints.
 */
public final class ConfigValidationException extends IllegalArgumentException {

    /**
     * Creates a new config-validation failure.
     *
     * @param message failure summary
     */
    public ConfigValidationException(String message) {
        super(message);
    }
}

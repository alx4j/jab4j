package com.alx4j.jab4j.writer.config;

/**
 * Raised when a runtime configuration file cannot be loaded into the typed config patch model.
 */
public final class ConfigLoadingException extends RuntimeException {

    /**
     * Creates a new config-loading failure.
     *
     * @param message failure summary
     * @param cause underlying loader error
     */
    public ConfigLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.alx4j.jab4j.api.model;

/**
 * Human-readable and machine-readable transport protocol version.
 *
 * @param displayValue human-readable version such as {@code 1.0}
 * @param compatibilityVersion machine-readable compatibility version
 */
public record ProtocolVersion(String displayValue, int compatibilityVersion) {

    /**
     * Creates a validated protocol version.
     *
     * @param displayValue human-readable version string
     * @param compatibilityVersion numeric compatibility version
     */
    public ProtocolVersion {
        if (displayValue == null || displayValue.isBlank()) {
            throw new IllegalArgumentException("displayValue must not be blank");
        }
        if (compatibilityVersion <= 0) {
            throw new IllegalArgumentException("compatibilityVersion must be positive");
        }
    }
}

package com.alx4j.jab4j.reader.frame;

import java.util.Objects;

/**
 * Structured warning emitted when reader input is accepted with non-fatal normalization.
 *
 * @param code stable warning category
 * @param message human-readable warning detail
 */
public record ReaderWarning(ReaderWarningCode code, String message) {

    /**
     * Creates a validated reader warning.
     *
     * @param code stable warning category
     * @param message warning detail
     */
    public ReaderWarning {
        Objects.requireNonNull(code, "code must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}

package com.alx4j.jab4j.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier for a transfer session.
 *
 * @param value UUID value of the session identifier
 */
public record SessionId(UUID value) {

    /**
     * Creates a validated session identifier.
     *
     * @param value UUID value
     */
    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Creates a new random session identifier.
     *
     * @return random session identifier
     */
    public static SessionId random() {
        return new SessionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

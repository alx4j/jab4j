package com.alx4j.jab4j.api.model;

/**
 * Required transport frame types for the writer milestone.
 */
public enum FrameType {
    SYNC,
    SESSION_HEADER,
    MANIFEST,
    DATA,
    PARITY,
    END
}

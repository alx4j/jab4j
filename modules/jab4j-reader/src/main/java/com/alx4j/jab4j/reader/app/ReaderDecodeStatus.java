package com.alx4j.jab4j.reader.app;

/**
 * Lifecycle status for one reader decode attempt request.
 */
public enum ReaderDecodeStatus {
    CONTENT_DECODED,
    DECODE_ATTEMPT_STARTED,
    INPUT_REJECTED,
    UNSUPPORTED_LAYOUT,
    UNSUPPORTED_VERSION,
    CONTENT_CORRUPTED,
    INCONSISTENT_CONTENT
}

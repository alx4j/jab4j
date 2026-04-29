package com.alx4j.jab4j.reader.restore;

/**
 * Lifecycle status for one reader restore request.
 */
public enum ReaderRestoreStatus {
    RESTORED,
    INVALID_DECODED_CONTENT,
    INCOMPLETE_CONTENT,
    INCONSISTENT_CONTENT,
    UNSAFE_PATH,
    OUTPUT_CONFLICT,
    IO_FAILURE
}

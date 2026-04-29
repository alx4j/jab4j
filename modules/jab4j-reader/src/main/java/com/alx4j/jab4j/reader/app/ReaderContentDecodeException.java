package com.alx4j.jab4j.reader.app;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Structured reader-side failure raised after writer-export input validation succeeds.
 */
final class ReaderContentDecodeException extends RuntimeException {

    private static final EnumSet<ReaderDecodeStatus> CONTENT_FAILURE_STATUSES = EnumSet.of(
            ReaderDecodeStatus.UNSUPPORTED_LAYOUT,
            ReaderDecodeStatus.UNSUPPORTED_VERSION,
            ReaderDecodeStatus.CONTENT_CORRUPTED,
            ReaderDecodeStatus.INCONSISTENT_CONTENT
    );

    private final ReaderDecodeStatus status;

    /**
     * Creates a content decode failure with a public reader result status.
     *
     * @param status content decode failure status
     * @param message human-readable failure detail
     */
    ReaderContentDecodeException(ReaderDecodeStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (!CONTENT_FAILURE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be a content-decode failure status");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Returns the reader result status that should be exposed for this failure.
     *
     * @return content decode failure status
     */
    ReaderDecodeStatus status() {
        return status;
    }
}

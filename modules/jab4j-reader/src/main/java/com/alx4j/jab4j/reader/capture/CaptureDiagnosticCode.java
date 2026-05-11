package com.alx4j.jab4j.reader.capture;

/**
 * Stable machine-readable reason code for capture receiver diagnostics.
 */
public enum CaptureDiagnosticCode {
    /**
     * Input source uses an image format outside the supported capture boundary.
     */
    UNSUPPORTED_FORMAT,

    /**
     * Input source could not be opened or decoded as an image.
     */
    UNREADABLE_IMAGE,

    /**
     * Image dimensions do not match any supported rendered capture layout.
     */
    UNSUPPORTED_DIMENSIONS,

    /**
     * The receiver could not find candidate JAB barcode content in the image.
     */
    NO_CANDIDATE_BARCODE_CONTENT,

    /**
     * A duplicate frame or payload is byte-equivalent to content already accepted.
     */
    DUPLICATE_EQUIVALENT_FRAME,

    /**
     * A duplicate frame or payload conflicts with content already accepted for the same identity.
     */
    DUPLICATE_CONFLICTING_FRAME,

    /**
     * Tile content could not be decoded or failed integrity checks.
     */
    CORRUPTED_OR_UNREADABLE_TILE_CONTENT,

    /**
     * Required capture, frame, tile, session, or manifest content is missing.
     */
    MISSING_REQUIRED_CONTENT,

    /**
     * Decoded capture content has conflicting session, frame, digest, or layout identity.
     */
    INCONSISTENT_SESSION_CONTENT,

    /**
     * Capture qualification succeeded, but the restore attempt failed.
     */
    RESTORE_FAILURE;

    /**
     * Indicates whether this code describes duplicate capture content.
     *
     * @return true for duplicate-equivalent or duplicate-conflicting content
     */
    public boolean duplicate() {
        return this == DUPLICATE_EQUIVALENT_FRAME || this == DUPLICATE_CONFLICTING_FRAME;
    }
}

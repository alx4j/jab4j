package com.alx4j.jab4j.reader.capture.media;

/**
 * Stable machine-readable reason code for media receiver diagnostics.
 */
public enum CaptureMediaDiagnosticCode {
    /**
     * No screen region or JAB frame content could be found in the media.
     */
    SCREEN_OR_FRAME_NOT_FOUND,

    /**
     * The monitor or detected JAB frame is too small to recover reliably.
     */
    MONITOR_TOO_SMALL,

    /**
     * Perspective distortion is outside the supported recovery boundary.
     */
    PERSPECTIVE_TOO_SEVERE,

    /**
     * Required frame content appears to be cropped, off-screen, or otherwise partial.
     */
    FRAME_PARTIALLY_OUTSIDE_IMAGE,

    /**
     * Blur or motion blur prevents reliable frame recovery.
     */
    BLUR,

    /**
     * Glare, reflection, or overexposure prevents reliable frame recovery.
     */
    GLARE_OR_OVEREXPOSURE,

    /**
     * Camera color processing or compression shifted samples outside reliable recovery thresholds.
     */
    COLOR_OR_COMPRESSION_SHIFT,

    /**
     * A media frame duplicates content already accepted for the same recovery attempt.
     */
    DUPLICATE_MEDIA_FRAME,

    /**
     * Required unique frame content is missing from the submitted media.
     */
    MISSING_UNIQUE_FRAME,

    /**
     * Image format is outside the supported media image boundary.
     */
    UNSUPPORTED_IMAGE_FORMAT,

    /**
     * Video container is outside the supported direct-video boundary.
     */
    UNSUPPORTED_CONTAINER,

    /**
     * Video codec or pixel format is outside the supported direct-video boundary.
     */
    UNSUPPORTED_CODEC,

    /**
     * Media could not be opened, decoded, adapted, or read.
     */
    UNREADABLE_MEDIA,

    /**
     * Media appears to contain multiple possible sessions and no single session can be selected safely.
     */
    AMBIGUOUS_SESSIONS,

    /**
     * Media qualification succeeded, but the restore attempt failed.
     */
    RESTORE_FAILURE,

    /**
     * Requested normalized-candidate debug output could not be written.
     */
    DEBUG_EXPORT_FAILURE;

    /**
     * Indicates whether this code describes duplicate media content.
     *
     * @return true for duplicate media frame diagnostics
     */
    public boolean duplicate() {
        return this == DUPLICATE_MEDIA_FRAME;
    }

    /**
     * Indicates whether this code describes unsupported media format, container, or codec input.
     *
     * @return true for unsupported media diagnostics
     */
    public boolean unsupportedMedia() {
        return this == UNSUPPORTED_IMAGE_FORMAT || this == UNSUPPORTED_CONTAINER || this == UNSUPPORTED_CODEC;
    }

    /**
     * Indicates whether this code describes media quality rather than session completeness or restore behavior.
     *
     * @return true for screen, size, perspective, partial-frame, blur, glare, or color diagnostics
     */
    public boolean qualityIssue() {
        return this == SCREEN_OR_FRAME_NOT_FOUND
                || this == MONITOR_TOO_SMALL
                || this == PERSPECTIVE_TOO_SEVERE
                || this == FRAME_PARTIALLY_OUTSIDE_IMAGE
                || this == BLUR
                || this == GLARE_OR_OVEREXPOSURE
                || this == COLOR_OR_COMPRESSION_SHIFT;
    }
}

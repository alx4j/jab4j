package com.alx4j.jab4j.reader.capture.media.sample;

/**
 * Confidence category for one media palette sample.
 */
public enum CaptureMediaPaletteSampleStatus {
    /**
     * Source color exactly matched one rendered palette entry.
     */
    EXACT(true, false),

    /**
     * Source color was shifted but remained inside the high-confidence nearest-palette threshold.
     */
    TOLERANT(true, false),

    /**
     * Source color was accepted only at the low-confidence boundary and should produce a warning.
     */
    LOW_CONFIDENCE(true, true),

    /**
     * Source color was too far from every rendered palette entry and must block decode.
     */
    REJECTED(false, true);

    private final boolean accepted;
    private final boolean diagnostic;

    CaptureMediaPaletteSampleStatus(boolean accepted, boolean diagnostic) {
        this.accepted = accepted;
        this.diagnostic = diagnostic;
    }

    /**
     * Indicates whether the sample can be mapped to a palette index.
     *
     * @return true when the sample is usable by a later decode attempt
     */
    public boolean accepted() {
        return accepted;
    }

    /**
     * Indicates whether callers should emit a color/compression diagnostic for the sample.
     *
     * @return true for warning-level and rejected samples
     */
    public boolean diagnostic() {
        return diagnostic;
    }
}

package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Backend-neutral grid-phase evidence for a normalized capture-media frame.
 *
 * @param offsetXPx estimated horizontal offset from the rendered layout grid in normalized pixels
 * @param offsetYPx estimated vertical offset from the rendered layout grid in normalized pixels
 * @param confidence confidence that the offset improves module-center sampling
 * @param localContrast local black/white contrast confidence near sampled grid evidence
 * @param localWhiteReferenceArgb local white reference color, when measured
 * @param localBlackReferenceArgb local black reference color, when measured
 */
public record CvGridPhase(
        double offsetXPx,
        double offsetYPx,
        double confidence,
        double localContrast,
        OptionalInt localWhiteReferenceArgb,
        OptionalInt localBlackReferenceArgb
) {

    /**
     * Creates validated grid-phase evidence.
     *
     * @param offsetXPx estimated horizontal offset
     * @param offsetYPx estimated vertical offset
     * @param confidence offset confidence
     * @param localContrast local contrast confidence
     * @param localWhiteReferenceArgb local white reference color
     * @param localBlackReferenceArgb local black reference color
     */
    public CvGridPhase {
        requireFinite(offsetXPx, "offsetXPx");
        requireFinite(offsetYPx, "offsetYPx");
        requireUnitScore(confidence, "confidence");
        requireUnitScore(localContrast, "localContrast");
        localWhiteReferenceArgb = Objects.requireNonNull(localWhiteReferenceArgb, "localWhiteReferenceArgb");
        localBlackReferenceArgb = Objects.requireNonNull(localBlackReferenceArgb, "localBlackReferenceArgb");
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }

    private static void requireUnitScore(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}

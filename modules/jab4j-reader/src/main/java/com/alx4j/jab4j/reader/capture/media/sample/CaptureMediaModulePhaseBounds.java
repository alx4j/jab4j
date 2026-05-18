package com.alx4j.jab4j.reader.capture.media.sample;

/**
 * Explicit limits for bounded capture-media module phase search.
 *
 * @param maxCenterShiftXPx maximum horizontal shift to test around each base center offset
 * @param maxCenterShiftYPx maximum vertical shift to test around each base center offset
 * @param centerShiftStepPx center-shift increment in normalized pixels
 * @param maxModuleScaleDelta maximum module-size scale delta around the nominal module size
 * @param moduleScaleStep module-size scale increment
 * @param maxVariantCount maximum number of phase variants to evaluate for one slot and side version
 * @param minimumEvidenceConfidence minimum confidence required before CV or other external evidence is included
 */
public record CaptureMediaModulePhaseBounds(
        double maxCenterShiftXPx,
        double maxCenterShiftYPx,
        double centerShiftStepPx,
        double maxModuleScaleDelta,
        double moduleScaleStep,
        int maxVariantCount,
        double minimumEvidenceConfidence
) {

    /**
     * Creates validated phase-search limits.
     *
     * @param maxCenterShiftXPx maximum horizontal center shift
     * @param maxCenterShiftYPx maximum vertical center shift
     * @param centerShiftStepPx center-shift increment
     * @param maxModuleScaleDelta maximum module-size scale delta
     * @param moduleScaleStep module-size scale increment
     * @param maxVariantCount maximum variant count
     * @param minimumEvidenceConfidence minimum external-evidence confidence
     */
    public CaptureMediaModulePhaseBounds {
        requireNonNegative(maxCenterShiftXPx, "maxCenterShiftXPx");
        requireNonNegative(maxCenterShiftYPx, "maxCenterShiftYPx");
        requirePositive(centerShiftStepPx, "centerShiftStepPx");
        requireNonNegative(maxModuleScaleDelta, "maxModuleScaleDelta");
        if (maxModuleScaleDelta >= 1.0d) {
            throw new IllegalArgumentException("maxModuleScaleDelta must keep module scales positive");
        }
        requirePositive(moduleScaleStep, "moduleScaleStep");
        if (maxVariantCount <= 0) {
            throw new IllegalArgumentException("maxVariantCount must be positive");
        }
        requireUnitScore(minimumEvidenceConfidence, "minimumEvidenceConfidence");
    }

    /**
     * Returns conservative default bounds for camera-derived phase search.
     *
     * @return default bounded phase-search limits
     */
    public static CaptureMediaModulePhaseBounds defaults() {
        return new CaptureMediaModulePhaseBounds(
                1.5d,
                1.5d,
                0.5d,
                0.06d,
                0.03d,
                64,
                0.75d
        );
    }

    private static void requirePositive(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }

    private static void requireUnitScore(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}

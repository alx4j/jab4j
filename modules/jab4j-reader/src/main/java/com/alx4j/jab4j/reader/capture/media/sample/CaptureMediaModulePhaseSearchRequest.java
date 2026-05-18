package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.Objects;
import java.util.Optional;

/**
 * Input geometry and limits for one bounded module phase search.
 *
 * @param slotIndex zero-based rendered tile slot index
 * @param sideVersion JAB side version for the candidate tile
 * @param nominalCenterXPx nominal horizontal module center in normalized pixels
 * @param nominalCenterYPx nominal vertical module center in normalized pixels
 * @param nominalModuleSizePx layout-derived nominal module size in normalized pixels
 * @param evidence optional external module-center offset evidence
 * @param bounds bounded phase-search limits
 */
public record CaptureMediaModulePhaseSearchRequest(
        int slotIndex,
        int sideVersion,
        double nominalCenterXPx,
        double nominalCenterYPx,
        double nominalModuleSizePx,
        Optional<CaptureMediaModulePhaseEvidence> evidence,
        CaptureMediaModulePhaseBounds bounds
) {

    /**
     * Creates a validated phase-search request.
     *
     * @param slotIndex zero-based rendered tile slot index
     * @param sideVersion JAB side version
     * @param nominalCenterXPx nominal horizontal module center
     * @param nominalCenterYPx nominal vertical module center
     * @param nominalModuleSizePx nominal module size
     * @param evidence optional external offset evidence
     * @param bounds phase-search limits
     */
    public CaptureMediaModulePhaseSearchRequest {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be non-negative");
        }
        if (sideVersion <= 0) {
            throw new IllegalArgumentException("sideVersion must be positive");
        }
        requireFinite(nominalCenterXPx, "nominalCenterXPx");
        requireFinite(nominalCenterYPx, "nominalCenterYPx");
        if (!Double.isFinite(nominalModuleSizePx) || nominalModuleSizePx <= 0.0d) {
            throw new IllegalArgumentException("nominalModuleSizePx must be finite and positive");
        }
        evidence = Objects.requireNonNull(evidence, "evidence must not be null");
        bounds = Objects.requireNonNull(bounds, "bounds must not be null");
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}

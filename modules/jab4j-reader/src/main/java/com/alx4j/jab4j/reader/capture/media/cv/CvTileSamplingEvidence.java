package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Backend-neutral sampling evidence for one rendered tile slot.
 *
 * @param tileIndex zero-based rendered tile slot index
 * @param moduleCenterOffsetXPx estimated horizontal offset from the nominal module centers in normalized pixels
 * @param moduleCenterOffsetYPx estimated vertical offset from the nominal module centers in normalized pixels
 * @param confidence confidence that the offset improves module-center sampling
 * @param localContrast local black/white contrast confidence for this tile slot
 * @param localWhiteReferenceArgb local white reference color, when measured
 * @param localBlackReferenceArgb local black reference color, when measured
 * @param metrics backend-neutral evidence metrics
 */
public record CvTileSamplingEvidence(
        int tileIndex,
        double moduleCenterOffsetXPx,
        double moduleCenterOffsetYPx,
        double confidence,
        double localContrast,
        OptionalInt localWhiteReferenceArgb,
        OptionalInt localBlackReferenceArgb,
        Map<String, Double> metrics
) {

    /**
     * Creates validated tile-slot sampling evidence.
     *
     * @param tileIndex zero-based tile slot index
     * @param moduleCenterOffsetXPx horizontal module-center offset
     * @param moduleCenterOffsetYPx vertical module-center offset
     * @param confidence offset confidence
     * @param localContrast local contrast confidence
     * @param localWhiteReferenceArgb local white reference color
     * @param localBlackReferenceArgb local black reference color
     * @param metrics evidence metrics
     */
    public CvTileSamplingEvidence {
        if (tileIndex < 0) {
            throw new IllegalArgumentException("tileIndex must be non-negative");
        }
        requireFinite(moduleCenterOffsetXPx, "moduleCenterOffsetXPx");
        requireFinite(moduleCenterOffsetYPx, "moduleCenterOffsetYPx");
        requireUnitScore(confidence, "confidence");
        requireUnitScore(localContrast, "localContrast");
        localWhiteReferenceArgb = Objects.requireNonNull(localWhiteReferenceArgb, "localWhiteReferenceArgb");
        localBlackReferenceArgb = Objects.requireNonNull(localBlackReferenceArgb, "localBlackReferenceArgb");
        metrics = copyMetrics(metrics);
    }

    private static Map<String, Double> copyMetrics(Map<String, Double> metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        Map<String, Double> copied = new LinkedHashMap<>();
        metrics.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("metric names must not be blank");
            }
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("metric values must be finite");
            }
            copied.put(name, value);
        });
        return Map.copyOf(copied);
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

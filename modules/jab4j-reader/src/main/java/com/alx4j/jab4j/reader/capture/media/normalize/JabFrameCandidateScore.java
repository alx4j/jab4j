package com.alx4j.jab4j.reader.capture.media.normalize;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JAB-specific evidence metrics for one source-space frame candidate.
 *
 * @param totalScore weighted detector score
 * @param frameCoverageRatio candidate area divided by source image area
 * @param skewScore normalized skew estimate
 * @param borderContrastScore outer border and quiet-zone contrast confidence
 * @param syncBandScore top sync-band cadence confidence
 * @param gridScore tile-slot grid evidence confidence
 * @param layoutAspectScore supported layout aspect-ratio confidence
 * @param blurScore sync-band blur estimate
 * @param glareScore near-white overexposure estimate
 * @param paletteDistanceConfidence confidence that sampled evidence remains near the rendered palette
 */
record JabFrameCandidateScore(
        double totalScore,
        double frameCoverageRatio,
        double skewScore,
        double borderContrastScore,
        double syncBandScore,
        double gridScore,
        double layoutAspectScore,
        double blurScore,
        double glareScore,
        double paletteDistanceConfidence
) {

    static final double NOT_MEASURED = -1.0d;

    /**
     * Creates validated candidate-score metrics.
     */
    JabFrameCandidateScore {
        requireUnitScore(totalScore, "totalScore");
        requireUnitScore(frameCoverageRatio, "frameCoverageRatio");
        requireUnitScore(skewScore, "skewScore");
        requireUnitScore(borderContrastScore, "borderContrastScore");
        requireUnitScore(syncBandScore, "syncBandScore");
        requireUnitScore(gridScore, "gridScore");
        requireUnitScore(layoutAspectScore, "layoutAspectScore");
        requireUnitScoreOrPlaceholder(blurScore, "blurScore");
        requireUnitScoreOrPlaceholder(glareScore, "glareScore");
        requireUnitScoreOrPlaceholder(paletteDistanceConfidence, "paletteDistanceConfidence");
    }

    /**
     * Returns stable metric names for diagnostics.
     *
     * @return candidate metrics
     */
    Map<String, Double> metrics() {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("candidateScore", totalScore);
        metrics.put("frameCoverageRatio", frameCoverageRatio);
        metrics.put("skewScore", skewScore);
        metrics.put("borderContrastScore", borderContrastScore);
        metrics.put("syncBandScore", syncBandScore);
        metrics.put("gridScore", gridScore);
        metrics.put("layoutAspectScore", layoutAspectScore);
        putMeasured(metrics, "blurScore", blurScore);
        putMeasured(metrics, "glareScore", glareScore);
        putMeasured(metrics, "paletteDistanceConfidence", paletteDistanceConfidence);
        return Map.copyOf(metrics);
    }

    private static void putMeasured(Map<String, Double> metrics, String name, double value) {
        if (value != NOT_MEASURED) {
            metrics.put(name, value);
        }
    }

    private static void requireUnitScore(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static void requireUnitScoreOrPlaceholder(double value, String fieldName) {
        if (value == NOT_MEASURED) {
            return;
        }
        requireUnitScore(value, fieldName);
    }
}

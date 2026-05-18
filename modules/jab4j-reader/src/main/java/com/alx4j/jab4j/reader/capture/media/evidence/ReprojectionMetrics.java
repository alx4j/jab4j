package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;

/**
 * Immutable reprojection error summary in source pixels and module-relative units.
 *
 * @param meanErrorPixels mean reprojection error in source pixels
 * @param medianErrorPixels median reprojection error in source pixels
 * @param p95ErrorPixels p95 reprojection error in source pixels
 * @param maxErrorPixels maximum reprojection error in source pixels
 * @param meanErrorModules mean reprojection error in module units
 * @param medianErrorModules median reprojection error in module units
 * @param p95ErrorModules p95 reprojection error in module units
 * @param maxErrorModules maximum reprojection error in module units
 * @param pointErrorsPixels bounded per-control-point errors in source pixels
 * @param pointErrorsModules bounded per-control-point errors in module units
 */
public record ReprojectionMetrics(
        double meanErrorPixels,
        double medianErrorPixels,
        double p95ErrorPixels,
        double maxErrorPixels,
        double meanErrorModules,
        double medianErrorModules,
        double p95ErrorModules,
        double maxErrorModules,
        List<Double> pointErrorsPixels,
        List<Double> pointErrorsModules
) {

    /**
     * Creates validated reprojection metrics.
     *
     * @param meanErrorPixels mean pixel error
     * @param medianErrorPixels median pixel error
     * @param p95ErrorPixels p95 pixel error
     * @param maxErrorPixels maximum pixel error
     * @param meanErrorModules mean module error
     * @param medianErrorModules median module error
     * @param p95ErrorModules p95 module error
     * @param maxErrorModules maximum module error
     * @param pointErrorsPixels per-point pixel errors
     * @param pointErrorsModules per-point module errors
     */
    public ReprojectionMetrics {
        EvidenceValidation.requireNonNegativeFinite(meanErrorPixels, "meanErrorPixels");
        EvidenceValidation.requireNonNegativeFinite(medianErrorPixels, "medianErrorPixels");
        EvidenceValidation.requireNonNegativeFinite(p95ErrorPixels, "p95ErrorPixels");
        EvidenceValidation.requireNonNegativeFinite(maxErrorPixels, "maxErrorPixels");
        EvidenceValidation.requireNonNegativeFinite(meanErrorModules, "meanErrorModules");
        EvidenceValidation.requireNonNegativeFinite(medianErrorModules, "medianErrorModules");
        EvidenceValidation.requireNonNegativeFinite(p95ErrorModules, "p95ErrorModules");
        EvidenceValidation.requireNonNegativeFinite(maxErrorModules, "maxErrorModules");
        requireSummaryOrder(meanErrorPixels, medianErrorPixels, p95ErrorPixels, maxErrorPixels, "Pixels");
        requireSummaryOrder(meanErrorModules, medianErrorModules, p95ErrorModules, maxErrorModules, "Modules");
        pointErrorsPixels = EvidenceValidation.copyNonNegativeDoubles(pointErrorsPixels, "pointErrorsPixels");
        pointErrorsModules = EvidenceValidation.copyNonNegativeDoubles(pointErrorsModules, "pointErrorsModules");
    }

    /**
     * Returns an all-zero reprojection summary for unavailable or intentionally withheld metrics.
     *
     * @return zero reprojection metrics
     */
    public static ReprojectionMetrics zero() {
        return new ReprojectionMetrics(
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                List.of(),
                List.of()
        );
    }

    private static void requireSummaryOrder(
            double mean,
            double median,
            double p95,
            double max,
            String suffix
    ) {
        if (median > max || p95 > max || mean > max) {
            throw new IllegalArgumentException("maxError" + suffix + " must be at least each summary error");
        }
        if (median > p95) {
            throw new IllegalArgumentException("p95Error" + suffix + " must be at least medianError" + suffix);
        }
    }
}


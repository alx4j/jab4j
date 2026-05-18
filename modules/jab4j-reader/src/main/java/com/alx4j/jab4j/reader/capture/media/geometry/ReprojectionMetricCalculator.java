package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;

/**
 * Calculates deterministic reprojection summaries in source pixels and module-relative units.
 */
public final class ReprojectionMetricCalculator {

    private static final int MAX_RETAINED_POINT_ERRORS = 64;

    /**
     * Calculates aggregate reprojection metrics from per-control-point pixel errors.
     *
     * @param pointErrorsPixels per-control-point reprojection errors in source pixels
     * @param moduleSizePixels estimated source-pixel size of one module
     * @return immutable reprojection metric summary
     */
    public ReprojectionMetrics calculate(List<Double> pointErrorsPixels, double moduleSizePixels) {
        Objects.requireNonNull(pointErrorsPixels, "pointErrorsPixels must not be null");
        if (pointErrorsPixels.isEmpty()) {
            return ReprojectionMetrics.zero();
        }
        requirePositiveFinite(moduleSizePixels, "moduleSizePixels");

        List<Double> errors = new ArrayList<>(pointErrorsPixels.size());
        for (Double pointError : pointErrorsPixels) {
            Objects.requireNonNull(pointError, "point error must not be null");
            requireNonNegativeFinite(pointError, "point error");
            errors.add(pointError);
        }

        List<Double> sorted = new ArrayList<>(errors);
        Collections.sort(sorted);

        double meanPixels = mean(errors);
        double medianPixels = median(sorted);
        double p95Pixels = percentileNearestRank(sorted, 0.95d);
        double maxPixels = sorted.get(sorted.size() - 1);

        List<Double> errorsModules = errors.stream()
                .map(error -> error / moduleSizePixels)
                .toList();
        List<Double> sortedModules = new ArrayList<>(errorsModules);
        Collections.sort(sortedModules);

        return new ReprojectionMetrics(
                meanPixels,
                medianPixels,
                p95Pixels,
                maxPixels,
                mean(errorsModules),
                median(sortedModules),
                percentileNearestRank(sortedModules, 0.95d),
                sortedModules.get(sortedModules.size() - 1),
                retained(errors),
                retained(errorsModules)
        );
    }

    private static double mean(List<Double> values) {
        double sum = 0.0d;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double median(List<Double> sorted) {
        int size = sorted.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0d;
    }

    private static double percentileNearestRank(List<Double> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static List<Double> retained(List<Double> values) {
        return List.copyOf(values.subList(0, Math.min(values.size(), MAX_RETAINED_POINT_ERRORS)));
    }

    private static void requirePositiveFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be non-negative and finite");
        }
    }
}

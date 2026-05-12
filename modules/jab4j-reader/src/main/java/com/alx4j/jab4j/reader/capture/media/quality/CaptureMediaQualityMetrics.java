package com.alx4j.jab4j.reader.capture.media.quality;

/**
 * Deterministic quality metrics for one normalized capture-media candidate.
 *
 * @param frameCoverageRatio normalized frame area relative to the source image, or {@link #NOT_MEASURED}
 * @param skewScore normalized skew estimate where zero means no detected skew, or {@link #NOT_MEASURED}
 * @param blurScore normalized blur score, or {@link #NOT_MEASURED}
 * @param glareScore normalized glare or overexposure score, or {@link #NOT_MEASURED}
 * @param exposureScore normalized exposure quality score, or {@link #NOT_MEASURED}
 * @param colorDistanceScore normalized color-distance score, or {@link #NOT_MEASURED}
 */
public record CaptureMediaQualityMetrics(
        double frameCoverageRatio,
        double skewScore,
        double blurScore,
        double glareScore,
        double exposureScore,
        double colorDistanceScore
) {

    /**
     * Placeholder value used when a metric is intentionally not measured in this slice.
     */
    public static final double NOT_MEASURED = -1.0d;

    /**
     * Creates validated deterministic quality metrics.
     *
     * @param frameCoverageRatio normalized frame coverage ratio
     * @param skewScore normalized skew score
     * @param blurScore normalized blur score
     * @param glareScore normalized glare score
     * @param exposureScore normalized exposure score
     * @param colorDistanceScore normalized color-distance score
     */
    public CaptureMediaQualityMetrics {
        requireScoreOrPlaceholder(frameCoverageRatio, "frameCoverageRatio");
        requireScoreOrPlaceholder(skewScore, "skewScore");
        requireScoreOrPlaceholder(blurScore, "blurScore");
        requireScoreOrPlaceholder(glareScore, "glareScore");
        requireScoreOrPlaceholder(exposureScore, "exposureScore");
        requireScoreOrPlaceholder(colorDistanceScore, "colorDistanceScore");
    }

    /**
     * Creates placeholder metrics for an exact rendered-frame pass-through candidate.
     *
     * @return metrics with measured full-frame coverage and no skew, plus placeholders for unmeasured values
     */
    public static CaptureMediaQualityMetrics exactRenderedFrame() {
        return new CaptureMediaQualityMetrics(1.0d, 0.0d, NOT_MEASURED, NOT_MEASURED, NOT_MEASURED, NOT_MEASURED);
    }

    /**
     * Creates metrics for an exact axis-aligned crop from a larger source image.
     *
     * @param frameCoverageRatio normalized frame area divided by source image area
     * @return metrics with measured coverage and no skew, plus placeholders for unmeasured values
     */
    public static CaptureMediaQualityMetrics axisAlignedInset(double frameCoverageRatio) {
        return new CaptureMediaQualityMetrics(
                frameCoverageRatio,
                0.0d,
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED
        );
    }

    /**
     * Creates metrics for a perspective-corrected crop from a larger source image.
     *
     * @param frameCoverageRatio detected quadrilateral area divided by source image area
     * @param skewScore normalized perspective skew estimate
     * @return metrics with measured coverage and skew, plus placeholders for unmeasured values
     */
    public static CaptureMediaQualityMetrics perspectiveCorrected(double frameCoverageRatio, double skewScore) {
        return new CaptureMediaQualityMetrics(
                frameCoverageRatio,
                skewScore,
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED
        );
    }

    /**
     * Creates metrics where no quality dimension has been measured.
     *
     * @return all-placeholder metrics
     */
    public static CaptureMediaQualityMetrics unmeasured() {
        return new CaptureMediaQualityMetrics(
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED,
                NOT_MEASURED
        );
    }

    /**
     * Indicates whether a metric value is measured rather than a placeholder.
     *
     * @param value metric value
     * @return true when the metric is measured
     */
    public boolean measured(double value) {
        return value != NOT_MEASURED;
    }

    private static void requireScoreOrPlaceholder(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        if (value == NOT_MEASURED) {
            return;
        }
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0 or NOT_MEASURED");
        }
    }
}

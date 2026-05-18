package com.alx4j.jab4j.reader.capture.media.sample;

/**
 * One indexed color center in a capture-media palette model.
 *
 * @param paletteIndex rendered palette index
 * @param expectedArgb exact rendered ARGB color for the palette index
 * @param modelArgb ARGB color used as the current sampling center
 * @param sampleCount number of reference samples used for this color center
 * @param maximumRgbDistance largest RGB distance from an observed reference sample to the exact rendered color
 * @param confidence normalized calibration confidence for this color center
 */
public record CaptureMediaPaletteModelColor(
        int paletteIndex,
        int expectedArgb,
        int modelArgb,
        int sampleCount,
        double maximumRgbDistance,
        double confidence
) {

    /**
     * Creates a validated palette model color.
     *
     * @param paletteIndex rendered palette index
     * @param expectedArgb exact rendered ARGB color
     * @param modelArgb ARGB sampling center
     * @param sampleCount reference sample count
     * @param maximumRgbDistance maximum RGB distance from observed reference samples to the exact rendered color
     * @param confidence normalized calibration confidence
     */
    public CaptureMediaPaletteModelColor {
        if (paletteIndex < 0) {
            throw new IllegalArgumentException("paletteIndex must be non-negative");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }
        requireScore(maximumRgbDistance, "maximumRgbDistance");
        requireUnitScore(confidence, "confidence");
    }

    /**
     * Indicates whether this color center came from observed calibration samples.
     *
     * @return true when at least one reference sample contributed to this color center
     */
    public boolean calibrated() {
        return sampleCount > 0;
    }

    private static void requireScore(double value, String fieldName) {
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

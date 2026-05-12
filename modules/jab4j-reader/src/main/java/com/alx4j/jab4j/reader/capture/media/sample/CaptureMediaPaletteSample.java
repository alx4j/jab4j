package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.Objects;

/**
 * Result of mapping one media pixel to the rendered eight-color palette.
 *
 * @param sourceArgb source ARGB color
 * @param paletteIndex nearest rendered palette index
 * @param paletteArgb nearest rendered palette ARGB color
 * @param rgbDistance Euclidean RGB distance to the nearest palette color
 * @param colorDistanceScore normalized RGB distance where zero is exact and one is maximum RGB distance
 * @param confidence normalized confidence where one is exact and zero is the configured acceptance boundary
 * @param status sample confidence category
 */
public record CaptureMediaPaletteSample(
        int sourceArgb,
        int paletteIndex,
        int paletteArgb,
        double rgbDistance,
        double colorDistanceScore,
        double confidence,
        CaptureMediaPaletteSampleStatus status
) {

    /**
     * Creates a validated palette sample result.
     *
     * @param sourceArgb source ARGB color
     * @param paletteIndex nearest palette index
     * @param paletteArgb nearest palette ARGB color
     * @param rgbDistance Euclidean RGB distance
     * @param colorDistanceScore normalized color-distance score
     * @param confidence normalized confidence
     * @param status sample confidence category
     */
    public CaptureMediaPaletteSample {
        if (paletteIndex < 0) {
            throw new IllegalArgumentException("paletteIndex must be non-negative");
        }
        requireScore(rgbDistance, "rgbDistance");
        requireUnitScore(colorDistanceScore, "colorDistanceScore");
        requireUnitScore(confidence, "confidence");
        Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Indicates whether the sample can provide a palette index to a later decoder.
     *
     * @return true when the sample is exact, tolerant, or low-confidence accepted
     */
    public boolean accepted() {
        return status.accepted();
    }

    /**
     * Indicates whether the sample should produce a color/compression diagnostic.
     *
     * @return true when the sample is low-confidence or rejected
     */
    public boolean diagnostic() {
        return status.diagnostic();
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

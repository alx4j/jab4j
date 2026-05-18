package com.alx4j.jab4j.reader.capture.media.sample;

/**
 * Known observed color sample for one expected rendered palette index.
 *
 * @param expectedPaletteIndex expected rendered palette index
 * @param observedArgb observed ARGB color from a normalized capture candidate
 */
public record CaptureMediaPaletteCalibrationSample(int expectedPaletteIndex, int observedArgb) {

    /**
     * Creates a validated calibration reference sample.
     *
     * @param expectedPaletteIndex expected rendered palette index
     * @param observedArgb observed ARGB color
     */
    public CaptureMediaPaletteCalibrationSample {
        if (expectedPaletteIndex < 0) {
            throw new IllegalArgumentException("expectedPaletteIndex must be non-negative");
        }
    }
}

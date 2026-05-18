package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of attempting to build a calibrated capture-media palette.
 *
 * @param model palette model selected for sampling
 */
public record CaptureMediaCalibratedPalette(CaptureMediaPaletteModel model) {

    /**
     * Creates a calibrated palette result.
     *
     * @param model selected palette model
     */
    public CaptureMediaCalibratedPalette {
        Objects.requireNonNull(model, "model must not be null");
    }

    /**
     * Indicates whether calibration was rejected and exact palette sampling should be used.
     *
     * @return true when the result carries an exact-palette fallback reason
     */
    public boolean fallbackToExact() {
        return model.fallbackReason().isPresent();
    }

    /**
     * Returns the aggregate confidence for the selected model.
     *
     * @return normalized model confidence
     */
    public double confidence() {
        return model.confidence();
    }

    /**
     * Returns the number of distinct palette colors observed during calibration.
     *
     * @return observed palette color count
     */
    public int observedColorCount() {
        return model.observedColorCount();
    }

    /**
     * Returns the maximum observed RGB distance from an exact rendered palette color.
     *
     * @return maximum observed RGB distance
     */
    public double maximumRgbDistance() {
        return model.maximumRgbDistance();
    }

    /**
     * Returns the reason calibration fell back to the exact palette.
     *
     * @return fallback reason, when calibration was not used
     */
    public Optional<String> fallbackReason() {
        return model.fallbackReason();
    }

    /**
     * Returns the per-index calibrated color entries.
     *
     * @return observed palette color entries
     */
    public List<CaptureMediaPaletteModelColor> observedColors() {
        return model.observedColors();
    }
}

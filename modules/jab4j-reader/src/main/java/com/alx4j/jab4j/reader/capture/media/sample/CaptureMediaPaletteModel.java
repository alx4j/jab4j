package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable palette model used by capture-media sampling.
 *
 * @param colors ordered palette colors indexed by rendered palette index
 * @param calibrated true when observed reference colors are active
 * @param confidence aggregate model confidence
 * @param observedColorCount number of distinct palette indexes observed during calibration
 * @param maximumRgbDistance largest observed RGB distance from an exact rendered palette color
 * @param fallbackReason reason calibration fell back to the exact palette, when applicable
 */
public record CaptureMediaPaletteModel(
        List<CaptureMediaPaletteModelColor> colors,
        boolean calibrated,
        double confidence,
        int observedColorCount,
        double maximumRgbDistance,
        Optional<String> fallbackReason
) {

    /**
     * Creates a validated immutable palette model.
     *
     * @param colors ordered palette colors
     * @param calibrated true when observed reference colors are active
     * @param confidence aggregate model confidence
     * @param observedColorCount distinct observed palette color count
     * @param maximumRgbDistance maximum observed RGB distance
     * @param fallbackReason optional exact-palette fallback reason
     */
    public CaptureMediaPaletteModel {
        Objects.requireNonNull(colors, "colors must not be null");
        if (colors.isEmpty()) {
            throw new IllegalArgumentException("colors must not be empty");
        }
        colors = List.copyOf(colors);
        for (int index = 0; index < colors.size(); index++) {
            if (colors.get(index).paletteIndex() != index) {
                throw new IllegalArgumentException("colors must be ordered by contiguous palette index");
            }
        }
        requireUnitScore(confidence, "confidence");
        if (observedColorCount < 0 || observedColorCount > colors.size()) {
            throw new IllegalArgumentException("observedColorCount must be between zero and the palette size");
        }
        requireScore(maximumRgbDistance, "maximumRgbDistance");
        fallbackReason = Objects.requireNonNull(fallbackReason, "fallbackReason must not be null");
        fallbackReason.ifPresent(reason -> {
            if (reason.isBlank()) {
                throw new IllegalArgumentException("fallbackReason must not be blank when present");
            }
        });
        if (calibrated && fallbackReason.isPresent()) {
            throw new IllegalArgumentException("calibrated models must not carry a fallback reason");
        }
    }

    /**
     * Creates the exact rendered palette model.
     *
     * @param expectedPaletteArgb exact rendered ARGB values ordered by palette index
     * @return exact palette model
     */
    public static CaptureMediaPaletteModel exact(List<Integer> expectedPaletteArgb) {
        return new CaptureMediaPaletteModel(
                exactColors(expectedPaletteArgb),
                false,
                1.0d,
                0,
                0.0d,
                Optional.empty()
        );
    }

    /**
     * Creates an exact-palette fallback model that retains why calibration was not used.
     *
     * @param expectedPaletteArgb exact rendered ARGB values ordered by palette index
     * @param fallbackReason reason calibration fell back to the exact model
     * @param observedColorCount distinct observed palette color count
     * @param maximumRgbDistance maximum observed RGB distance before fallback
     * @return exact-palette fallback model
     */
    public static CaptureMediaPaletteModel fallbackToExact(
            List<Integer> expectedPaletteArgb,
            String fallbackReason,
            int observedColorCount,
            double maximumRgbDistance
    ) {
        if (fallbackReason == null || fallbackReason.isBlank()) {
            throw new IllegalArgumentException("fallbackReason must not be blank");
        }
        return new CaptureMediaPaletteModel(
                exactColors(expectedPaletteArgb),
                false,
                0.0d,
                observedColorCount,
                maximumRgbDistance,
                Optional.of(fallbackReason)
        );
    }

    /**
     * Returns the exact rendered ARGB palette values for index output.
     *
     * @return rendered ARGB palette values ordered by palette index
     */
    public List<Integer> paletteArgb() {
        return colors.stream()
                .map(CaptureMediaPaletteModelColor::expectedArgb)
                .toList();
    }

    /**
     * Returns the palette size.
     *
     * @return number of palette colors
     */
    public int size() {
        return colors.size();
    }

    /**
     * Returns one indexed palette color.
     *
     * @param paletteIndex rendered palette index
     * @return palette color model entry
     */
    public CaptureMediaPaletteModelColor color(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex >= colors.size()) {
            throw new IndexOutOfBoundsException("paletteIndex is outside the palette model");
        }
        return colors.get(paletteIndex);
    }

    /**
     * Returns only the colors that were built from observed calibration samples.
     *
     * @return observed palette color entries
     */
    public List<CaptureMediaPaletteModelColor> observedColors() {
        return colors.stream()
                .filter(CaptureMediaPaletteModelColor::calibrated)
                .toList();
    }

    private static List<CaptureMediaPaletteModelColor> exactColors(List<Integer> expectedPaletteArgb) {
        Objects.requireNonNull(expectedPaletteArgb, "expectedPaletteArgb must not be null");
        if (expectedPaletteArgb.isEmpty()) {
            throw new IllegalArgumentException("expectedPaletteArgb must not be empty");
        }
        List<CaptureMediaPaletteModelColor> colors = new ArrayList<>(expectedPaletteArgb.size());
        for (int index = 0; index < expectedPaletteArgb.size(); index++) {
            int argb = expectedPaletteArgb.get(index);
            colors.add(new CaptureMediaPaletteModelColor(
                    index,
                    argb,
                    argb,
                    0,
                    0.0d,
                    1.0d
            ));
        }
        return colors;
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

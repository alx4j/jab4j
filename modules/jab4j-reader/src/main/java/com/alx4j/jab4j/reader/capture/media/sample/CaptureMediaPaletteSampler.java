package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;

/**
 * Conservative media sampler scaffold that accepts only exact rendered palette colors.
 */
public final class CaptureMediaPaletteSampler {

    private static final List<Integer> PALETTE = List.of(
            0xFF000000,
            0xFF0000FF,
            0xFF00FF00,
            0xFF00FFFF,
            0xFFFF0000,
            0xFFFF00FF,
            0xFFFFFF00,
            0xFFFFFFFF
    );

    /**
     * Returns a copy of the exact eight-color rendered palette.
     *
     * @return ARGB palette values
     */
    public List<Integer> paletteArgb() {
        return List.copyOf(PALETTE);
    }

    /**
     * Maps one ARGB color to the exact rendered palette.
     *
     * @param argb ARGB color
     * @return palette index, or empty when the color is not an exact palette member
     */
    public OptionalInt exactPaletteIndex(int argb) {
        int index = PALETTE.indexOf(argb);
        return index < 0 ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /**
     * Samples one normalized frame pixel using exact palette matching only.
     *
     * @param frame normalized frame
     * @param row zero-based row
     * @param col zero-based column
     * @return exact palette index, or empty when the pixel is off-palette
     */
    public OptionalInt sampleExactPaletteIndex(NormalizedCaptureFrame frame, int row, int col) {
        Objects.requireNonNull(frame, "frame must not be null");
        return exactPaletteIndex(frame.argbPixelAt(row, col));
    }
}

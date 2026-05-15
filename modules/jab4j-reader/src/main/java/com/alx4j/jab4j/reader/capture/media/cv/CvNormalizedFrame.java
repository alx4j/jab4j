package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.Arrays;
import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

/**
 * Backend-neutral normalized ARGB frame emitted when a CV backend owns perspective correction.
 *
 * @param layoutProfile matched rendered layout profile
 * @param frameCorners detected source-space frame corners
 * @param qualityMetrics deterministic quality metrics for this candidate
 * @param argbPixels row-major normalized ARGB pixels
 */
public record CvNormalizedFrame(
        LayoutProfile layoutProfile,
        FrameCorners frameCorners,
        CaptureMediaQualityMetrics qualityMetrics,
        int[] argbPixels
) {

    /**
     * Creates a validated normalized frame with a defensive copy of its ARGB buffer.
     *
     * @param layoutProfile matched rendered layout profile
     * @param frameCorners detected source-space frame corners
     * @param qualityMetrics deterministic quality metrics
     * @param argbPixels row-major normalized ARGB pixels
     */
    public CvNormalizedFrame {
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        Objects.requireNonNull(frameCorners, "frameCorners must not be null");
        Objects.requireNonNull(qualityMetrics, "qualityMetrics must not be null");
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        if (argbPixels.length != expectedPixelCount(layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx())) {
            throw new IllegalArgumentException("argbPixels length must equal layoutProfile frame dimensions");
        }
        argbPixels = Arrays.copyOf(argbPixels, argbPixels.length);
    }

    /**
     * Returns a defensive copy of normalized row-major ARGB pixels.
     *
     * @return copied normalized pixels
     */
    @Override
    public int[] argbPixels() {
        return Arrays.copyOf(argbPixels, argbPixels.length);
    }

    private static int expectedPixelCount(int widthPixels, int heightPixels) {
        long expectedPixels = (long) widthPixels * heightPixels;
        if (expectedPixels > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("frame dimensions exceed supported pixel count");
        }
        return (int) expectedPixels;
    }
}

package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
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
 * @param samplingEvidence backend-neutral sampling evidence measured by the CV backend, when available
 * @param geometrySource optional source of the candidate quadrilateral geometry
 * @param sourceRegionRank one-based rank of the source-space region before profile alternatives are expanded
 * @param profileAlternativeRank one-based rank of this profile alternative within the source region
 * @param profileAlternativeCount number of profile alternatives emitted for the source region
 */
public record CvNormalizedFrame(
        LayoutProfile layoutProfile,
        FrameCorners frameCorners,
        CaptureMediaQualityMetrics qualityMetrics,
        int[] argbPixels,
        Optional<CvSamplingEvidence> samplingEvidence,
        Optional<String> geometrySource,
        int sourceRegionRank,
        int profileAlternativeRank,
        int profileAlternativeCount
) {

    /**
     * Creates a normalized frame without backend sampling evidence or profile-alternative metadata.
     *
     * @param layoutProfile matched rendered layout profile
     * @param frameCorners detected source-space frame corners
     * @param qualityMetrics deterministic quality metrics
     * @param argbPixels row-major normalized ARGB pixels
     */
    public CvNormalizedFrame(
            LayoutProfile layoutProfile,
            FrameCorners frameCorners,
            CaptureMediaQualityMetrics qualityMetrics,
            int[] argbPixels
    ) {
        this(
                layoutProfile,
                frameCorners,
                qualityMetrics,
                argbPixels,
                Optional.empty(),
                Optional.empty(),
                1,
                1,
                1
        );
    }

    /**
     * Creates a validated normalized frame with a defensive copy of its ARGB buffer.
     *
     * @param layoutProfile matched rendered layout profile
     * @param frameCorners detected source-space frame corners
     * @param qualityMetrics deterministic quality metrics
     * @param argbPixels row-major normalized ARGB pixels
     * @param samplingEvidence backend-neutral sampling evidence measured by the CV backend, when available
     * @param geometrySource optional source of the candidate quadrilateral geometry
     * @param sourceRegionRank one-based rank of the source-space region before profile alternatives are expanded
     * @param profileAlternativeRank one-based rank of this profile alternative within the source region
     * @param profileAlternativeCount number of profile alternatives emitted for the source region
     */
    public CvNormalizedFrame {
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        Objects.requireNonNull(frameCorners, "frameCorners must not be null");
        Objects.requireNonNull(qualityMetrics, "qualityMetrics must not be null");
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        samplingEvidence = Objects.requireNonNull(samplingEvidence, "samplingEvidence must not be null");
        geometrySource = Objects.requireNonNull(geometrySource, "geometrySource must not be null");
        geometrySource.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("geometrySource must not be blank when present");
            }
        });
        if (argbPixels.length != expectedPixelCount(layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx())) {
            throw new IllegalArgumentException("argbPixels length must equal layoutProfile frame dimensions");
        }
        if (sourceRegionRank <= 0) {
            throw new IllegalArgumentException("sourceRegionRank must be positive");
        }
        if (profileAlternativeRank <= 0 || profileAlternativeCount <= 0) {
            throw new IllegalArgumentException("profile alternative ranks must be positive");
        }
        if (profileAlternativeRank > profileAlternativeCount) {
            throw new IllegalArgumentException("profileAlternativeRank must not exceed profileAlternativeCount");
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

package com.alx4j.jab4j.reader.capture.media.video;

/**
 * Direct-video guardrails supplied to optional video frame-source adapters.
 *
 * <p>The baseline reader does not decode direct video. These limits define the contract an optional adapter must
 * honor before yielding frames into the existing capture-media normalization path.</p>
 *
 * @param maxFileSizeBytes maximum accepted container file size in bytes
 * @param maxDurationMillis maximum accepted container duration in milliseconds
 * @param maxWidthPixels maximum decoded frame width
 * @param maxHeightPixels maximum decoded frame height
 * @param maxContainerFrameCount maximum container frame count before sampling
 * @param maxSampledFrameCount maximum decoded frames yielded to the media receiver
 * @param minSampleIntervalMillis minimum interval between yielded frames
 */
public record CaptureMediaVideoLimits(
        long maxFileSizeBytes,
        long maxDurationMillis,
        int maxWidthPixels,
        int maxHeightPixels,
        long maxContainerFrameCount,
        int maxSampledFrameCount,
        long minSampleIntervalMillis
) {

    /**
     * Creates validated direct-video adapter limits.
     *
     * @param maxFileSizeBytes maximum file size in bytes
     * @param maxDurationMillis maximum duration in milliseconds
     * @param maxWidthPixels maximum decoded frame width
     * @param maxHeightPixels maximum decoded frame height
     * @param maxContainerFrameCount maximum source frame count
     * @param maxSampledFrameCount maximum yielded frame count
     * @param minSampleIntervalMillis minimum interval between yielded frames
     */
    public CaptureMediaVideoLimits {
        requirePositive(maxFileSizeBytes, "maxFileSizeBytes");
        requirePositive(maxDurationMillis, "maxDurationMillis");
        requirePositive(maxWidthPixels, "maxWidthPixels");
        requirePositive(maxHeightPixels, "maxHeightPixels");
        requirePositive(maxContainerFrameCount, "maxContainerFrameCount");
        requirePositive(maxSampledFrameCount, "maxSampledFrameCount");
        if (minSampleIntervalMillis < 0L) {
            throw new IllegalArgumentException("minSampleIntervalMillis must be non-negative");
        }
        if (maxSampledFrameCount > maxContainerFrameCount) {
            throw new IllegalArgumentException("maxSampledFrameCount must not exceed maxContainerFrameCount");
        }
    }

    /**
     * Returns conservative baseline limits for future adapter implementations.
     *
     * @return default direct-video adapter limits
     */
    public static CaptureMediaVideoLimits conservativeDefaults() {
        return new CaptureMediaVideoLimits(
                512L * 1024L * 1024L,
                10L * 60L * 1000L,
                3840,
                2160,
                36_000L,
                1_200,
                100L
        );
    }

    private static void requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}

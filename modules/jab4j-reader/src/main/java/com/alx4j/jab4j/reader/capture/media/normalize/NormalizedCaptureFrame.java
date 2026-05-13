package com.alx4j.jab4j.reader.capture.media.normalize;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

/**
 * Canonical capture-media frame with immutable source context and a releasable normalized ARGB pixel buffer.
 */
public final class NormalizedCaptureFrame {

    private final String sourceId;
    private final CaptureMediaSourceKind sourceKind;
    private final int callerOrder;
    private final int originalWidthPixels;
    private final int originalHeightPixels;
    private final int normalizedWidthPixels;
    private final int normalizedHeightPixels;
    private final String formatName;
    private final String pixelSha256;
    private final String layoutProfileId;
    private final Optional<Long> timestampMillis;
    private final Optional<Long> frameNumber;
    private final FrameCorners frameCorners;
    private final CaptureMediaQualityMetrics qualityMetrics;
    private volatile int[] argbPixels;

    /**
     * Creates a normalized capture frame with immutable metadata and a retained normalized ARGB buffer.
     *
     * @param sourceId caller-visible source identifier
     * @param sourceKind media source kind
     * @param callerOrder deterministic source order
     * @param originalWidthPixels source image width in pixels
     * @param originalHeightPixels source image height in pixels
     * @param normalizedWidthPixels normalized frame width in pixels
     * @param normalizedHeightPixels normalized frame height in pixels
     * @param formatName decoded image format name
     * @param pixelSha256 source pixel hash
     * @param layoutProfileId matched rendered layout profile id
     * @param frameCorners detected source-space frame corners
     * @param qualityMetrics deterministic quality metrics
     * @param argbPixels row-major normalized ARGB pixels
     */
    public NormalizedCaptureFrame(
            String sourceId,
            CaptureMediaSourceKind sourceKind,
            int callerOrder,
            int originalWidthPixels,
            int originalHeightPixels,
            int normalizedWidthPixels,
            int normalizedHeightPixels,
            String formatName,
            String pixelSha256,
            String layoutProfileId,
            FrameCorners frameCorners,
            CaptureMediaQualityMetrics qualityMetrics,
            int[] argbPixels
    ) {
        this(
                sourceId,
                sourceKind,
                callerOrder,
                originalWidthPixels,
                originalHeightPixels,
                normalizedWidthPixels,
                normalizedHeightPixels,
                formatName,
                pixelSha256,
                layoutProfileId,
                Optional.empty(),
                Optional.empty(),
                frameCorners,
                qualityMetrics,
                argbPixels
        );
    }

    /**
     * Creates a normalized capture frame with optional source timing metadata and a retained normalized ARGB buffer.
     *
     * @param sourceId caller-visible source identifier
     * @param sourceKind media source kind
     * @param callerOrder deterministic source order
     * @param originalWidthPixels source image width in pixels
     * @param originalHeightPixels source image height in pixels
     * @param normalizedWidthPixels normalized frame width in pixels
     * @param normalizedHeightPixels normalized frame height in pixels
     * @param formatName decoded image format name
     * @param pixelSha256 source pixel hash
     * @param layoutProfileId matched rendered layout profile id
     * @param timestampMillis optional source timestamp in milliseconds
     * @param frameNumber optional source frame number
     * @param frameCorners detected source-space frame corners
     * @param qualityMetrics deterministic quality metrics
     * @param argbPixels row-major normalized ARGB pixels
     */
    public NormalizedCaptureFrame(
            String sourceId,
            CaptureMediaSourceKind sourceKind,
            int callerOrder,
            int originalWidthPixels,
            int originalHeightPixels,
            int normalizedWidthPixels,
            int normalizedHeightPixels,
            String formatName,
            String pixelSha256,
            String layoutProfileId,
            Optional<Long> timestampMillis,
            Optional<Long> frameNumber,
            FrameCorners frameCorners,
            CaptureMediaQualityMetrics qualityMetrics,
            int[] argbPixels
    ) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        if (callerOrder < 0) {
            throw new IllegalArgumentException("callerOrder must be non-negative");
        }
        if (originalWidthPixels <= 0 || originalHeightPixels <= 0) {
            throw new IllegalArgumentException("original dimensions must be positive");
        }
        if (normalizedWidthPixels <= 0 || normalizedHeightPixels <= 0) {
            throw new IllegalArgumentException("normalized dimensions must be positive");
        }
        if (formatName == null || formatName.isBlank()) {
            throw new IllegalArgumentException("formatName must not be blank");
        }
        if (pixelSha256 == null || pixelSha256.isBlank()) {
            throw new IllegalArgumentException("pixelSha256 must not be blank");
        }
        if (layoutProfileId == null || layoutProfileId.isBlank()) {
            throw new IllegalArgumentException("layoutProfileId must not be blank");
        }
        this.timestampMillis = nonNegativeOptional(timestampMillis, "timestampMillis");
        this.frameNumber = nonNegativeOptional(frameNumber, "frameNumber");
        Objects.requireNonNull(frameCorners, "frameCorners must not be null");
        Objects.requireNonNull(qualityMetrics, "qualityMetrics must not be null");
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        if (argbPixels.length != expectedPixelCount(normalizedWidthPixels, normalizedHeightPixels)) {
            throw new IllegalArgumentException("argbPixels length must equal normalizedWidthPixels * normalizedHeightPixels");
        }
        this.sourceId = sourceId;
        this.sourceKind = sourceKind;
        this.callerOrder = callerOrder;
        this.originalWidthPixels = originalWidthPixels;
        this.originalHeightPixels = originalHeightPixels;
        this.normalizedWidthPixels = normalizedWidthPixels;
        this.normalizedHeightPixels = normalizedHeightPixels;
        this.formatName = formatName;
        this.pixelSha256 = pixelSha256;
        this.layoutProfileId = layoutProfileId;
        this.frameCorners = frameCorners;
        this.qualityMetrics = qualityMetrics;
        this.argbPixels = Arrays.copyOf(argbPixels, argbPixels.length);
    }

    /**
     * Creates a normalized frame by passing through an already rendered frame unchanged.
     *
     * @param frame decoded media input frame
     * @param layoutProfile matched rendered layout profile
     * @return normalized pass-through frame
     */
    public static NormalizedCaptureFrame fromExactRenderedFrame(MediaInputFrame frame, LayoutProfile layoutProfile) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        if (frame.widthPixels() != layoutProfile.frameWidthPx()
                || frame.heightPixels() != layoutProfile.frameHeightPx()) {
            throw new IllegalArgumentException("frame dimensions must match the rendered layout profile");
        }
        return new NormalizedCaptureFrame(
                frame.sourceId(),
                frame.sourceKind(),
                frame.callerOrder(),
                frame.widthPixels(),
                frame.heightPixels(),
                frame.widthPixels(),
                frame.heightPixels(),
                frame.formatName(),
                frame.pixelSha256(),
                layoutProfile.profileId(),
                frame.timestampMillis(),
                frame.frameNumber(),
                FrameCorners.exactFrame(frame.widthPixels(), frame.heightPixels()),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                frame.copyArgbPixels()
        );
    }

    /**
     * Creates a normalized frame by cropping an exact axis-aligned rendered frame from a larger source image.
     *
     * @param frame decoded media input frame
     * @param layoutProfile matched rendered layout profile
     * @param leftPx left coordinate of the rendered frame in source pixels
     * @param topPx top coordinate of the rendered frame in source pixels
     * @return normalized cropped frame with original source context retained
     */
    public static NormalizedCaptureFrame fromAxisAlignedInset(
            MediaInputFrame frame,
            LayoutProfile layoutProfile,
            int leftPx,
            int topPx
    ) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        if (leftPx < 0 || topPx < 0) {
            throw new IllegalArgumentException("inset coordinates must be non-negative");
        }
        int normalizedWidth = layoutProfile.frameWidthPx();
        int normalizedHeight = layoutProfile.frameHeightPx();
        if (leftPx + normalizedWidth > frame.widthPixels()
                || topPx + normalizedHeight > frame.heightPixels()) {
            throw new IllegalArgumentException("inset must remain inside the source frame");
        }
        double coverageRatio = ((double) normalizedWidth * normalizedHeight)
                / ((double) frame.widthPixels() * frame.heightPixels());
        return new NormalizedCaptureFrame(
                frame.sourceId(),
                frame.sourceKind(),
                frame.callerOrder(),
                frame.widthPixels(),
                frame.heightPixels(),
                normalizedWidth,
                normalizedHeight,
                frame.formatName(),
                frame.pixelSha256(),
                layoutProfile.profileId(),
                frame.timestampMillis(),
                frame.frameNumber(),
                new FrameCorners(
                        leftPx,
                        topPx,
                        leftPx + normalizedWidth,
                        topPx,
                        leftPx + normalizedWidth,
                        topPx + normalizedHeight,
                        leftPx,
                        topPx + normalizedHeight
                ),
                CaptureMediaQualityMetrics.axisAlignedInset(coverageRatio),
                crop(frame, leftPx, topPx, normalizedWidth, normalizedHeight)
        );
    }

    /**
     * Creates a normalized frame from perspective-corrected ARGB pixels.
     *
     * @param frame decoded media input frame
     * @param layoutProfile matched rendered layout profile
     * @param frameCorners detected source-space quadrilateral corners
     * @param frameCoverageRatio detected quadrilateral area divided by source image area
     * @param skewScore normalized perspective skew estimate
     * @param correctedArgbPixels row-major perspective-corrected ARGB pixels
     * @return normalized perspective-corrected frame with source context retained
     */
    public static NormalizedCaptureFrame fromPerspectiveCorrectedFrame(
            MediaInputFrame frame,
            LayoutProfile layoutProfile,
            FrameCorners frameCorners,
            double frameCoverageRatio,
            double skewScore,
            int[] correctedArgbPixels
    ) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        Objects.requireNonNull(frameCorners, "frameCorners must not be null");
        int normalizedWidth = layoutProfile.frameWidthPx();
        int normalizedHeight = layoutProfile.frameHeightPx();
        return new NormalizedCaptureFrame(
                frame.sourceId(),
                frame.sourceKind(),
                frame.callerOrder(),
                frame.widthPixels(),
                frame.heightPixels(),
                normalizedWidth,
                normalizedHeight,
                frame.formatName(),
                frame.pixelSha256(),
                layoutProfile.profileId(),
                frame.timestampMillis(),
                frame.frameNumber(),
                frameCorners,
                CaptureMediaQualityMetrics.perspectiveCorrected(frameCoverageRatio, skewScore),
                correctedArgbPixels
        );
    }

    /**
     * Returns the caller-visible source identifier.
     *
     * @return source identifier
     */
    public String sourceId() {
        return sourceId;
    }

    /**
     * Returns the media source kind name.
     *
     * @return source kind
     */
    public CaptureMediaSourceKind sourceKind() {
        return sourceKind;
    }

    /**
     * Returns the deterministic source order.
     *
     * @return zero-based source order
     */
    public int callerOrder() {
        return callerOrder;
    }

    /**
     * Returns the source image width in pixels.
     *
     * @return original width
     */
    public int originalWidthPixels() {
        return originalWidthPixels;
    }

    /**
     * Returns the source image height in pixels.
     *
     * @return original height
     */
    public int originalHeightPixels() {
        return originalHeightPixels;
    }

    /**
     * Returns the normalized frame width in pixels.
     *
     * @return normalized width
     */
    public int normalizedWidthPixels() {
        return normalizedWidthPixels;
    }

    /**
     * Returns the normalized frame height in pixels.
     *
     * @return normalized height
     */
    public int normalizedHeightPixels() {
        return normalizedHeightPixels;
    }

    /**
     * Returns the decoded image format name.
     *
     * @return format name
     */
    public String formatName() {
        return formatName;
    }

    /**
     * Returns the source pixel hash.
     *
     * @return SHA-256 hash over source ARGB pixels
     */
    public String pixelSha256() {
        return pixelSha256;
    }

    /**
     * Returns the optional source timestamp in milliseconds.
     *
     * @return source timestamp, when available
     */
    public Optional<Long> timestampMillis() {
        return timestampMillis;
    }

    /**
     * Returns the optional source frame number.
     *
     * @return source frame number, when available
     */
    public Optional<Long> frameNumber() {
        return frameNumber;
    }

    /**
     * Returns the matched rendered layout profile id.
     *
     * @return layout profile id
     */
    public String layoutProfileId() {
        return layoutProfileId;
    }

    /**
     * Returns the detected source-space frame corners.
     *
     * @return frame corners
     */
    public FrameCorners frameCorners() {
        return frameCorners;
    }

    /**
     * Returns deterministic quality metrics for this candidate.
     *
     * @return quality metrics
     */
    public CaptureMediaQualityMetrics qualityMetrics() {
        return qualityMetrics;
    }

    /**
     * Returns one normalized ARGB pixel without exposing the backing buffer.
     *
     * @param row zero-based row
     * @param col zero-based column
     * @return ARGB pixel value
     * @throws IllegalStateException when the normalized ARGB buffer has already been released
     */
    public int argbPixelAt(int row, int col) {
        if (row < 0 || row >= normalizedHeightPixels || col < 0 || col >= normalizedWidthPixels) {
            throw new IndexOutOfBoundsException("pixel coordinates are outside the normalized frame dimensions");
        }
        return retainedArgbPixels()[(row * normalizedWidthPixels) + col];
    }

    /**
     * Returns a defensive copy of normalized row-major ARGB pixels.
     *
     * @return copied normalized pixels
     * @throws IllegalStateException when the normalized ARGB buffer has already been released
     */
    public int[] copyArgbPixels() {
        int[] retainedPixels = retainedArgbPixels();
        return Arrays.copyOf(retainedPixels, retainedPixels.length);
    }

    /**
     * Releases the retained normalized ARGB buffer after media decode has sampled this frame.
     *
     * <p>Source context, timing metadata, corners, and quality metrics remain available after release, but pixel
     * accessors throw {@link IllegalStateException}. The operation is idempotent so cleanup can safely run from
     * failure paths.</p>
     */
    public void releaseArgbPixels() {
        argbPixels = null;
    }

    private int[] retainedArgbPixels() {
        int[] retainedPixels = argbPixels;
        if (retainedPixels == null) {
            throw new IllegalStateException("ARGB pixels have been released");
        }
        return retainedPixels;
    }

    private static int expectedPixelCount(int widthPixels, int heightPixels) {
        long expectedPixels = (long) widthPixels * heightPixels;
        if (expectedPixels > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("frame dimensions exceed supported pixel count");
        }
        return (int) expectedPixels;
    }

    private static Optional<Long> nonNegativeOptional(Optional<Long> value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        value.ifPresent(present -> {
            if (present < 0L) {
                throw new IllegalArgumentException(fieldName + " must be non-negative when present");
            }
        });
        return value;
    }

    private static int[] crop(MediaInputFrame frame, int leftPx, int topPx, int widthPixels, int heightPixels) {
        int[] cropped = new int[expectedPixelCount(widthPixels, heightPixels)];
        for (int row = 0; row < heightPixels; row++) {
            int destinationOffset = row * widthPixels;
            frame.copyArgbRow(topPx + row, leftPx, cropped, destinationOffset, widthPixels);
        }
        return cropped;
    }
}

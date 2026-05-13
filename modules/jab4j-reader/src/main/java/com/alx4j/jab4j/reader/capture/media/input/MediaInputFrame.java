package com.alx4j.jab4j.reader.capture.media.input;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;

/**
 * Decoded media frame with immutable source context and a releasable row-major ARGB pixel buffer.
 */
public final class MediaInputFrame {

    private final String sourceId;
    private final CaptureMediaSourceKind sourceKind;
    private final int callerOrder;
    private final int widthPixels;
    private final int heightPixels;
    private final String formatName;
    private final String pixelSha256;
    private final Optional<Long> timestampMillis;
    private final Optional<Long> frameNumber;
    private volatile int[] argbPixels;

    /**
     * Creates a decoded media frame with immutable metadata and a retained ARGB buffer.
     *
     * @param sourceId caller-visible source identifier
     * @param sourceKind media source kind supplied by the caller
     * @param callerOrder deterministic zero-based traversal order
     * @param widthPixels image width in pixels
     * @param heightPixels image height in pixels
     * @param formatName decoded image format name
     * @param pixelSha256 SHA-256 hash over row-major ARGB integers
     * @param argbPixels row-major ARGB pixels
     */
    public MediaInputFrame(
            String sourceId,
            CaptureMediaSourceKind sourceKind,
            int callerOrder,
            int widthPixels,
            int heightPixels,
            String formatName,
            String pixelSha256,
            int[] argbPixels
    ) {
        this(
                sourceId,
                sourceKind,
                callerOrder,
                widthPixels,
                heightPixels,
                formatName,
                pixelSha256,
                Optional.empty(),
                Optional.empty(),
                argbPixels
        );
    }

    /**
     * Creates a decoded media frame with optional direct-video timing metadata and a retained ARGB buffer.
     *
     * @param sourceId caller-visible source identifier
     * @param sourceKind media source kind supplied by the caller
     * @param callerOrder deterministic zero-based traversal order
     * @param widthPixels image width in pixels
     * @param heightPixels image height in pixels
     * @param formatName decoded image format name
     * @param pixelSha256 SHA-256 hash over row-major ARGB integers
     * @param timestampMillis optional source timestamp in milliseconds
     * @param frameNumber optional source frame number
     * @param argbPixels row-major ARGB pixels
     */
    public MediaInputFrame(
            String sourceId,
            CaptureMediaSourceKind sourceKind,
            int callerOrder,
            int widthPixels,
            int heightPixels,
            String formatName,
            String pixelSha256,
            Optional<Long> timestampMillis,
            Optional<Long> frameNumber,
            int[] argbPixels
    ) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        if (callerOrder < 0) {
            throw new IllegalArgumentException("callerOrder must be non-negative");
        }
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new IllegalArgumentException("frame dimensions must be positive");
        }
        if (formatName == null || formatName.isBlank()) {
            throw new IllegalArgumentException("formatName must not be blank");
        }
        if (pixelSha256 == null || pixelSha256.isBlank()) {
            throw new IllegalArgumentException("pixelSha256 must not be blank");
        }
        this.timestampMillis = nonNegativeOptional(timestampMillis, "timestampMillis");
        this.frameNumber = nonNegativeOptional(frameNumber, "frameNumber");
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        if (argbPixels.length != expectedPixelCount(widthPixels, heightPixels)) {
            throw new IllegalArgumentException("argbPixels length must equal widthPixels * heightPixels");
        }
        this.sourceId = sourceId;
        this.sourceKind = sourceKind;
        this.callerOrder = callerOrder;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.formatName = formatName;
        this.pixelSha256 = pixelSha256;
        this.argbPixels = Arrays.copyOf(argbPixels, argbPixels.length);
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
     * Returns the deterministic traversal order assigned by intake.
     *
     * @return zero-based caller order
     */
    public int callerOrder() {
        return callerOrder;
    }

    /**
     * Returns the image width in pixels.
     *
     * @return image width
     */
    public int widthPixels() {
        return widthPixels;
    }

    /**
     * Returns the image height in pixels.
     *
     * @return image height
     */
    public int heightPixels() {
        return heightPixels;
    }

    /**
     * Returns the decoded image format name.
     *
     * @return lower-case format name
     */
    public String formatName() {
        return formatName;
    }

    /**
     * Returns the source pixel hash computed during intake.
     *
     * @return SHA-256 hash over row-major ARGB integers
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
     * Returns one ARGB pixel without exposing the backing buffer.
     *
     * @param row zero-based row
     * @param col zero-based column
     * @return ARGB pixel value
     * @throws IllegalStateException when the ARGB buffer has already been released
     */
    public int argbPixelAt(int row, int col) {
        if (row < 0 || row >= heightPixels || col < 0 || col >= widthPixels) {
            throw new IndexOutOfBoundsException("pixel coordinates are outside the frame dimensions");
        }
        return retainedArgbPixels()[(row * widthPixels) + col];
    }

    /**
     * Copies one contiguous row segment into the caller-provided destination buffer.
     *
     * @param row zero-based source row
     * @param col zero-based source column where the segment starts
     * @param destination destination pixel buffer
     * @param destinationOffset zero-based destination offset
     * @param pixelCount number of pixels to copy
     * @throws IllegalStateException when the ARGB buffer has already been released
     */
    public void copyArgbRow(int row, int col, int[] destination, int destinationOffset, int pixelCount) {
        Objects.requireNonNull(destination, "destination must not be null");
        if (row < 0 || row >= heightPixels || col < 0 || pixelCount < 0
                || (long) col + pixelCount > widthPixels) {
            throw new IndexOutOfBoundsException("pixel row segment is outside the frame dimensions");
        }
        if (destinationOffset < 0 || (long) destinationOffset + pixelCount > destination.length) {
            throw new IndexOutOfBoundsException("destination range is outside the destination buffer");
        }
        System.arraycopy(retainedArgbPixels(), (row * widthPixels) + col, destination, destinationOffset, pixelCount);
    }

    /**
     * Returns a defensive copy of the row-major ARGB pixels.
     *
     * @return copied ARGB pixels
     * @throws IllegalStateException when the ARGB buffer has already been released
     */
    public int[] copyArgbPixels() {
        int[] retainedPixels = retainedArgbPixels();
        return Arrays.copyOf(retainedPixels, retainedPixels.length);
    }

    /**
     * Releases the retained full-frame ARGB buffer after the media receiver has normalized this frame.
     *
     * <p>Source metadata remains available after release, but pixel accessors throw {@link IllegalStateException}.
     * The operation is idempotent so cleanup can safely run from failure paths.</p>
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
}

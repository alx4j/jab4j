package com.alx4j.jab4j.reader.capture.media.input;

import java.util.Arrays;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;

/**
 * Decoded still-image media frame with source context and immutable row-major ARGB pixels.
 */
public final class MediaInputFrame {

    private final String sourceId;
    private final CaptureMediaSourceKind sourceKind;
    private final int callerOrder;
    private final int widthPixels;
    private final int heightPixels;
    private final String formatName;
    private final String pixelSha256;
    private final int[] argbPixels;

    /**
     * Creates an immutable decoded media frame.
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
     * Returns one ARGB pixel without exposing the backing buffer.
     *
     * @param row zero-based row
     * @param col zero-based column
     * @return ARGB pixel value
     */
    public int argbPixelAt(int row, int col) {
        if (row < 0 || row >= heightPixels || col < 0 || col >= widthPixels) {
            throw new IndexOutOfBoundsException("pixel coordinates are outside the frame dimensions");
        }
        return argbPixels[(row * widthPixels) + col];
    }

    /**
     * Returns a defensive copy of the row-major ARGB pixels.
     *
     * @return copied ARGB pixels
     */
    public int[] copyArgbPixels() {
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

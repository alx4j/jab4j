package com.alx4j.jab4j.reader.capture.media.video;

import java.util.Arrays;
import java.util.Objects;

/**
 * One decoded direct-video frame produced by an optional adapter.
 *
 * <p>Frames are ordered by {@code callerOrder}. The timestamp and frame number describe the frame's source-video
 * position and are intended for diagnostics and future duplicate/quality analysis.</p>
 */
public final class CaptureMediaVideoFrame {

    private final String sourceId;
    private final int callerOrder;
    private final long timestampMillis;
    private final long frameNumber;
    private final int widthPixels;
    private final int heightPixels;
    private final String pixelFormat;
    private final int[] argbPixels;

    /**
     * Creates an immutable decoded video frame.
     *
     * @param sourceId caller-visible frame identifier
     * @param callerOrder zero-based frame order yielded by the adapter
     * @param timestampMillis source-video timestamp in milliseconds
     * @param frameNumber source-video frame number
     * @param widthPixels decoded frame width
     * @param heightPixels decoded frame height
     * @param pixelFormat decoded pixel format name
     * @param argbPixels row-major ARGB pixels
     */
    public CaptureMediaVideoFrame(
            String sourceId,
            int callerOrder,
            long timestampMillis,
            long frameNumber,
            int widthPixels,
            int heightPixels,
            String pixelFormat,
            int[] argbPixels
    ) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (callerOrder < 0) {
            throw new IllegalArgumentException("callerOrder must be non-negative");
        }
        if (timestampMillis < 0L) {
            throw new IllegalArgumentException("timestampMillis must be non-negative");
        }
        if (frameNumber < 0L) {
            throw new IllegalArgumentException("frameNumber must be non-negative");
        }
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new IllegalArgumentException("frame dimensions must be positive");
        }
        if (pixelFormat == null || pixelFormat.isBlank()) {
            throw new IllegalArgumentException("pixelFormat must not be blank");
        }
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        if (argbPixels.length != expectedPixelCount(widthPixels, heightPixels)) {
            throw new IllegalArgumentException("argbPixels length must equal widthPixels * heightPixels");
        }
        this.sourceId = sourceId;
        this.callerOrder = callerOrder;
        this.timestampMillis = timestampMillis;
        this.frameNumber = frameNumber;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.pixelFormat = pixelFormat;
        this.argbPixels = Arrays.copyOf(argbPixels, argbPixels.length);
    }

    /**
     * Returns the caller-visible frame identifier.
     *
     * @return frame identifier
     */
    public String sourceId() {
        return sourceId;
    }

    /**
     * Returns the zero-based frame order yielded by the adapter.
     *
     * @return frame order
     */
    public int callerOrder() {
        return callerOrder;
    }

    /**
     * Returns the source-video timestamp in milliseconds.
     *
     * @return timestamp in milliseconds
     */
    public long timestampMillis() {
        return timestampMillis;
    }

    /**
     * Returns the source-video frame number.
     *
     * @return frame number
     */
    public long frameNumber() {
        return frameNumber;
    }

    /**
     * Returns the decoded frame width.
     *
     * @return width in pixels
     */
    public int widthPixels() {
        return widthPixels;
    }

    /**
     * Returns the decoded frame height.
     *
     * @return height in pixels
     */
    public int heightPixels() {
        return heightPixels;
    }

    /**
     * Returns the adapter-provided pixel format name.
     *
     * @return pixel format
     */
    public String pixelFormat() {
        return pixelFormat;
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
            throw new IndexOutOfBoundsException("pixel coordinates are outside the video frame dimensions");
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

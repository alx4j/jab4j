package com.alx4j.jab4j.reader.capture.input;

import java.util.Arrays;
import java.util.Objects;

/**
 * Decoded PNG capture frame with row-major ARGB pixels and caller-visible source context.
 */
public final class CaptureInputFrame {

    private final String sourceId;
    private final int callerOrder;
    private final int widthPixels;
    private final int heightPixels;
    private final int[] argbPixels;
    private final String pixelSha256;

    /**
     * Creates an immutable decoded capture frame.
     *
     * @param sourceId caller-visible source identifier
     * @param callerOrder deterministic zero-based traversal order
     * @param widthPixels frame width in pixels
     * @param heightPixels frame height in pixels
     * @param argbPixels row-major ARGB pixels
     * @param pixelSha256 SHA-256 hash over row-major ARGB integers
     */
    public CaptureInputFrame(
            String sourceId,
            int callerOrder,
            int widthPixels,
            int heightPixels,
            int[] argbPixels,
            String pixelSha256
    ) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (callerOrder < 0) {
            throw new IllegalArgumentException("callerOrder must be non-negative");
        }
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new IllegalArgumentException("frame dimensions must be positive");
        }
        int expectedPixels = expectedPixelCount(widthPixels, heightPixels);
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        if (argbPixels.length != expectedPixels) {
            throw new IllegalArgumentException("argbPixels length must equal widthPixels * heightPixels");
        }
        if (pixelSha256 == null || pixelSha256.isBlank()) {
            throw new IllegalArgumentException("pixelSha256 must not be blank");
        }
        this.sourceId = sourceId;
        this.callerOrder = callerOrder;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.argbPixels = Arrays.copyOf(argbPixels, argbPixels.length);
        this.pixelSha256 = pixelSha256;
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
     * Returns the deterministic traversal order assigned by intake.
     *
     * @return zero-based caller order
     */
    public int callerOrder() {
        return callerOrder;
    }

    /**
     * Returns the frame width in pixels.
     *
     * @return frame width
     */
    public int widthPixels() {
        return widthPixels;
    }

    /**
     * Returns the frame height in pixels.
     *
     * @return frame height
     */
    public int heightPixels() {
        return heightPixels;
    }

    /**
     * Returns the ARGB pixel hash computed by intake.
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

    private static int expectedPixelCount(int widthPixels, int heightPixels) {
        long expectedPixels = (long) widthPixels * heightPixels;
        if (expectedPixels > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("frame dimensions exceed supported pixel count");
        }
        return (int) expectedPixels;
    }
}

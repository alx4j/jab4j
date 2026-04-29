package com.alx4j.jab4j.reader.frame;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import com.alx4j.jab4j.api.model.FrameType;

/**
 * Source-neutral full-frame raster accepted by the reader input boundary.
 *
 * @param frameIndex zero-based frame index in the transfer sequence
 * @param frameType logical frame type
 * @param widthPixels raster width in pixels
 * @param heightPixels raster height in pixels
 * @param argbPixels immutable row-major ARGB pixel buffer
 * @param pixelSha256 SHA-256 hash over row-major ARGB integers supplied by the accepting input boundary
 */
public record ReaderFrame(
        long frameIndex,
        FrameType frameType,
        int widthPixels,
        int heightPixels,
        List<Integer> argbPixels,
        String pixelSha256
) {

    /**
     * Creates a validated reader frame.
     *
     * @param frameIndex zero-based frame index
     * @param frameType logical frame type
     * @param widthPixels raster width in pixels
     * @param heightPixels raster height in pixels
     * @param argbPixels row-major ARGB pixels
     * @param pixelSha256 accepted ARGB pixel hash
     */
    public ReaderFrame {
        if (frameIndex < 0) {
            throw new IllegalArgumentException("frameIndex must be non-negative");
        }
        Objects.requireNonNull(frameType, "frameType must not be null");
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new IllegalArgumentException("frame dimensions must be positive");
        }
        int expectedPixels = expectedPixelCount(widthPixels, heightPixels);
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        if (argbPixels.size() != expectedPixels) {
            throw new IllegalArgumentException("argbPixels size must equal widthPixels * heightPixels");
        }
        argbPixels = packArgbPixels(argbPixels);
        if (pixelSha256 == null || pixelSha256.isBlank()) {
            throw new IllegalArgumentException("pixelSha256 must not be blank");
        }
    }

    /**
     * Creates a reader frame from a primitive ARGB pixel buffer without storing boxed pixel values.
     *
     * @param frameIndex zero-based frame index
     * @param frameType logical frame type
     * @param widthPixels raster width in pixels
     * @param heightPixels raster height in pixels
     * @param argbPixels row-major ARGB pixels
     * @param pixelSha256 accepted ARGB pixel hash
     * @return immutable reader frame
     */
    public static ReaderFrame fromArgbPixels(
            long frameIndex,
            FrameType frameType,
            int widthPixels,
            int heightPixels,
            int[] argbPixels,
            String pixelSha256
    ) {
        Objects.requireNonNull(argbPixels, "argbPixels must not be null");
        return new ReaderFrame(
                frameIndex,
                frameType,
                widthPixels,
                heightPixels,
                new PackedArgbPixels(argbPixels, false),
                pixelSha256
        );
    }

    /**
     * Returns one ARGB pixel without boxing.
     *
     * @param row zero-based row
     * @param col zero-based column
     * @return ARGB pixel value
     */
    public int argbPixelAt(int row, int col) {
        if (row < 0 || row >= heightPixels || col < 0 || col >= widthPixels) {
            throw new IndexOutOfBoundsException("pixel coordinates are outside the frame dimensions");
        }
        return ((PackedArgbPixels) argbPixels).pixelAt((row * widthPixels) + col);
    }

    private static int expectedPixelCount(int widthPixels, int heightPixels) {
        long expectedPixels = (long) widthPixels * heightPixels;
        if (expectedPixels > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("frame dimensions exceed supported pixel count");
        }
        return (int) expectedPixels;
    }

    private static List<Integer> packArgbPixels(List<Integer> pixels) {
        if (pixels instanceof PackedArgbPixels) {
            return pixels;
        }
        int[] packedPixels = new int[pixels.size()];
        for (int index = 0; index < pixels.size(); index++) {
            packedPixels[index] = Objects.requireNonNull(pixels.get(index), "argbPixels must not contain null values");
        }
        return new PackedArgbPixels(packedPixels, true);
    }

    private static final class PackedArgbPixels extends AbstractList<Integer> implements RandomAccess {

        private final int[] pixels;

        private PackedArgbPixels(int[] pixels, boolean trusted) {
            this.pixels = trusted ? pixels : Arrays.copyOf(pixels, pixels.length);
        }

        @Override
        public Integer get(int index) {
            return pixelAt(index);
        }

        @Override
        public int size() {
            return pixels.length;
        }

        private int pixelAt(int index) {
            Objects.checkIndex(index, pixels.length);
            return pixels[index];
        }
    }
}

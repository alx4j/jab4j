package com.alx4j.jab4j.reader.frame;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.FrameType;

/**
 * Source-neutral full-frame raster accepted by the reader input boundary.
 *
 * @param frameIndex zero-based frame index in the transfer sequence
 * @param frameType logical frame type
 * @param widthPixels raster width in pixels
 * @param heightPixels raster height in pixels
 * @param argbPixels row-major ARGB pixel buffer
 * @param pixelSha256 writer-compatible hash over row-major ARGB integers
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
     * @param pixelSha256 writer-compatible ARGB pixel hash
     */
    public ReaderFrame {
        if (frameIndex < 0) {
            throw new IllegalArgumentException("frameIndex must be non-negative");
        }
        Objects.requireNonNull(frameType, "frameType must not be null");
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new IllegalArgumentException("frame dimensions must be positive");
        }
        argbPixels = List.copyOf(Objects.requireNonNull(argbPixels, "argbPixels must not be null"));
        if (argbPixels.size() != widthPixels * heightPixels) {
            throw new IllegalArgumentException("argbPixels size must equal widthPixels * heightPixels");
        }
        if (pixelSha256 == null || pixelSha256.isBlank()) {
            throw new IllegalArgumentException("pixelSha256 must not be blank");
        }
    }
}

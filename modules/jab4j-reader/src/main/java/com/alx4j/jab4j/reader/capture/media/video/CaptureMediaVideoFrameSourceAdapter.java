package com.alx4j.jab4j.reader.capture.media.video;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Optional SPI for adapting direct video containers into ordered capture-media frames.
 *
 * <p>Implementations may live outside the baseline reader module so native, FFmpeg-backed, or other codec-heavy
 * dependencies do not become transitive requirements. Implementations must honor {@link CaptureMediaVideoLimits} and
 * report stable media diagnostics instead of throwing codec-specific exceptions for normal unsupported input.</p>
 */
public interface CaptureMediaVideoFrameSourceAdapter {

    /**
     * Decodes or samples ordered frames from one direct-video source.
     *
     * @param request direct-video source and extraction limits
     * @return ordered frames and stable diagnostics
     */
    CaptureMediaVideoFrameReadResult read(CaptureMediaVideoFrameReadRequest request);

    /**
     * Loads the first service-provider adapter visible to the reader module.
     *
     * @return first configured adapter, or empty when no adapter module is present
     */
    static Optional<CaptureMediaVideoFrameSourceAdapter> loadFirstAvailable() {
        return ServiceLoader.load(CaptureMediaVideoFrameSourceAdapter.class).findFirst();
    }

    /**
     * Returns the baseline adapter used when no direct-video decoder is configured.
     *
     * @return unsupported-video adapter
     */
    static CaptureMediaVideoFrameSourceAdapter unsupported() {
        return UnsupportedCaptureMediaVideoFrameSourceAdapter.INSTANCE;
    }
}

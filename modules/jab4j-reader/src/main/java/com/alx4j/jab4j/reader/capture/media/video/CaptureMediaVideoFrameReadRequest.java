package com.alx4j.jab4j.reader.capture.media.video;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Request passed to an optional direct-video frame-source adapter.
 *
 * @param sourceFile normalized direct-video source file
 * @param callerOrder zero-based order of the submitted video source
 * @param limits direct-video extraction limits
 */
public record CaptureMediaVideoFrameReadRequest(
        Path sourceFile,
        int callerOrder,
        CaptureMediaVideoLimits limits
) {

    /**
     * Creates a validated direct-video adapter request.
     *
     * @param sourceFile direct-video source file
     * @param callerOrder zero-based source order
     * @param limits extraction limits
     */
    public CaptureMediaVideoFrameReadRequest {
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile must not be null")
                .toAbsolutePath()
                .normalize();
        if (callerOrder < 0) {
            throw new IllegalArgumentException("callerOrder must be non-negative");
        }
        Objects.requireNonNull(limits, "limits must not be null");
    }
}

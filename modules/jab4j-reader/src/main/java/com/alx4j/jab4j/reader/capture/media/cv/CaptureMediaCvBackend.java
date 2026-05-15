package com.alx4j.jab4j.reader.capture.media.cv;

import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;

/**
 * Backend-neutral capture-media computer-vision boundary for non-exact media frame analysis.
 */
public interface CaptureMediaCvBackend {

    /**
     * Returns stable backend identity metadata for diagnostics, tests, and developer smoke selection.
     *
     * @return backend identity metadata
     */
    default CvBackendIdentity identity() {
        return CvBackendIdentity.unspecified("unknown");
    }

    /**
     * Detects or normalizes plausible JAB frame evidence in one decoded media frame.
     *
     * <p>Implementations must not release or mutate the supplied frame. Backend-specific failures should be returned as
     * {@link CvDetectionResult#backendFailure(java.util.Map, String)} rather than leaking backend exception types.</p>
     *
     * @param frame decoded media input frame
     * @return backend-neutral detection result
     */
    CvDetectionResult detect(MediaInputFrame frame);
}

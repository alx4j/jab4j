package com.alx4j.jab4j.reader.capture.media.decode;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.decode.DecodedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;

/**
 * Result of decoding normalized media candidates into validated tile-payload frame envelopes.
 *
 * @param decodedFrames decoded media frames whose identity came from tile payload envelopes
 * @param diagnostics media diagnostics emitted while sampling and decoding candidates
 * @param rejectedCandidateCount normalized candidates rejected before frame assembly
 */
public record CaptureMediaFrameDecodeResult(
        List<DecodedCaptureFrame> decodedFrames,
        List<CaptureMediaDiagnostic> diagnostics,
        int rejectedCandidateCount
) {

    /**
     * Creates an immutable media frame decode result.
     *
     * @param decodedFrames decoded media frames
     * @param diagnostics media diagnostics
     * @param rejectedCandidateCount rejected normalized candidate count
     */
    public CaptureMediaFrameDecodeResult {
        decodedFrames = List.copyOf(Objects.requireNonNull(decodedFrames, "decodedFrames must not be null"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        if (decodedFrames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("decodedFrames must not contain null values");
        }
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
        if (rejectedCandidateCount < 0) {
            throw new IllegalArgumentException("rejectedCandidateCount must be non-negative");
        }
    }

    /**
     * Returns the number of normalized candidates decoded into frame envelopes.
     *
     * @return decoded candidate count
     */
    public int decodedCandidateCount() {
        return decodedFrames.size();
    }
}

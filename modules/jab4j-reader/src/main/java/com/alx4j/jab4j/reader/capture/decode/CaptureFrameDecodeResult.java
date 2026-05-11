package com.alx4j.jab4j.reader.capture.decode;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;

/**
 * Result of decoding readable capture frames into tile payload envelopes.
 *
 * @param decodedFrames frames with at least one decoded payload
 * @param diagnostics qualification or decode diagnostics
 */
public record CaptureFrameDecodeResult(
        List<DecodedCaptureFrame> decodedFrames,
        List<CaptureFrameDiagnostic> diagnostics
) {

    /**
     * Creates an immutable frame decode result.
     *
     * @param decodedFrames decoded capture frames
     * @param diagnostics diagnostics emitted during qualification or decode
     */
    public CaptureFrameDecodeResult {
        decodedFrames = List.copyOf(Objects.requireNonNull(decodedFrames, "decodedFrames must not be null"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        if (decodedFrames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("decodedFrames must not contain null values");
        }
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
    }
}

package com.alx4j.jab4j.reader.capture.input;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;

/**
 * Result of deterministic capture source discovery and PNG intake.
 *
 * @param submittedSourceCount number of file sources considered by intake
 * @param readableFrames decoded PNG frames
 * @param diagnostics intake diagnostics for unsupported or unreadable sources
 */
public record CaptureIntakeResult(
        int submittedSourceCount,
        List<CaptureInputFrame> readableFrames,
        List<CaptureFrameDiagnostic> diagnostics
) {

    /**
     * Creates a validated intake result.
     *
     * @param submittedSourceCount number of considered frame sources
     * @param readableFrames decoded frames
     * @param diagnostics diagnostics emitted by intake
     */
    public CaptureIntakeResult {
        if (submittedSourceCount < 0) {
            throw new IllegalArgumentException("submittedSourceCount must be non-negative");
        }
        readableFrames = List.copyOf(Objects.requireNonNull(readableFrames, "readableFrames must not be null"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        if (readableFrames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("readableFrames must not contain null values");
        }
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
        if (readableFrames.size() > submittedSourceCount) {
            throw new IllegalArgumentException("readable frame count must not exceed submitted source count");
        }
    }
}

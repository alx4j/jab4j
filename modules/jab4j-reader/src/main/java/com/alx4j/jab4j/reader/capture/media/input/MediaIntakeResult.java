package com.alx4j.jab4j.reader.capture.media.input;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;

/**
 * Result of deterministic still-image media discovery and PNG intake.
 *
 * @param submittedSourceCount number of regular file sources considered by intake
 * @param readableFrames decoded PNG still-image frames
 * @param diagnostics stable diagnostics for unsupported, unreadable, or empty sources
 */
public record MediaIntakeResult(
        int submittedSourceCount,
        List<MediaInputFrame> readableFrames,
        List<CaptureMediaDiagnostic> diagnostics
) {

    /**
     * Creates a validated immutable intake result.
     *
     * @param submittedSourceCount number of considered regular file sources
     * @param readableFrames decoded frames
     * @param diagnostics intake diagnostics
     */
    public MediaIntakeResult {
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

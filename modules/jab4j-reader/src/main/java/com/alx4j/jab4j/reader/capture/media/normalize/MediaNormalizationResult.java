package com.alx4j.jab4j.reader.capture.media.normalize;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;

/**
 * Result of conservative capture-media frame normalization.
 *
 * @param frame normalized frame when accepted
 * @param diagnostics normalization diagnostics
 */
public record MediaNormalizationResult(
        Optional<NormalizedCaptureFrame> frame,
        List<CaptureMediaDiagnostic> diagnostics
) {

    /**
     * Creates a validated normalization result.
     *
     * @param frame normalized frame when accepted
     * @param diagnostics normalization diagnostics
     */
    public MediaNormalizationResult {
        frame = Objects.requireNonNull(frame, "frame must not be null");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
        if (frame.isPresent() && diagnostics.stream().anyMatch(CaptureMediaDiagnostic::blocking)) {
            throw new IllegalArgumentException("accepted normalization results must not contain blocking diagnostics");
        }
    }

    /**
     * Creates an accepted normalization result.
     *
     * @param frame normalized frame
     * @return accepted result
     */
    public static MediaNormalizationResult accepted(NormalizedCaptureFrame frame) {
        return new MediaNormalizationResult(Optional.of(Objects.requireNonNull(frame, "frame must not be null")), List.of());
    }

    /**
     * Creates a rejected normalization result with one blocking diagnostic.
     *
     * @param diagnostic blocking diagnostic
     * @return rejected result
     */
    public static MediaNormalizationResult rejected(CaptureMediaDiagnostic diagnostic) {
        return new MediaNormalizationResult(Optional.empty(), List.of(Objects.requireNonNull(diagnostic, "diagnostic must not be null")));
    }

    /**
     * Indicates whether normalization produced a candidate frame.
     *
     * @return true when a normalized frame is present
     */
    public boolean accepted() {
        return frame.isPresent();
    }
}

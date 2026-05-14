package com.alx4j.jab4j.reader.capture.media.normalize;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;

/**
 * Result of conservative capture-media frame normalization.
 *
 * @param frames normalized frames when accepted
 * @param diagnostics normalization diagnostics
 */
public record MediaNormalizationResult(
        List<NormalizedCaptureFrame> frames,
        List<CaptureMediaDiagnostic> diagnostics
) {

    /**
     * Creates a validated normalization result.
     *
     * @param frames normalized frames when accepted
     * @param diagnostics normalization diagnostics
     */
    public MediaNormalizationResult {
        frames = List.copyOf(Objects.requireNonNull(frames, "frames must not be null"));
        if (frames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("frames must not contain null values");
        }
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
        if (!frames.isEmpty() && diagnostics.stream().anyMatch(CaptureMediaDiagnostic::blocking)) {
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
        return accepted(List.of(Objects.requireNonNull(frame, "frame must not be null")));
    }

    /**
     * Creates an accepted normalization result with one or more normalized candidates.
     *
     * @param frames normalized frames
     * @return accepted result
     */
    public static MediaNormalizationResult accepted(List<NormalizedCaptureFrame> frames) {
        if (Objects.requireNonNull(frames, "frames must not be null").isEmpty()) {
            throw new IllegalArgumentException("accepted normalization requires at least one frame");
        }
        return new MediaNormalizationResult(frames, List.of());
    }

    /**
     * Creates a rejected normalization result with one blocking diagnostic.
     *
     * @param diagnostic blocking diagnostic
     * @return rejected result
     */
    public static MediaNormalizationResult rejected(CaptureMediaDiagnostic diagnostic) {
        return new MediaNormalizationResult(List.of(), List.of(Objects.requireNonNull(diagnostic, "diagnostic must not be null")));
    }

    /**
     * Returns the first normalized frame for legacy single-candidate callers.
     *
     * @return first normalized frame when accepted
     */
    public Optional<NormalizedCaptureFrame> frame() {
        return frames.stream().findFirst();
    }

    /**
     * Indicates whether normalization produced a candidate frame.
     *
     * @return true when a normalized frame is present
     */
    public boolean accepted() {
        return !frames.isEmpty();
    }
}

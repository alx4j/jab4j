package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;

/**
 * Backend-neutral outcome of CV analysis for one media input frame.
 *
 * @param status coarse CV result status
 * @param candidates ranked source-space candidates, when the backend returns geometric evidence
 * @param normalizedFrames normalized ARGB frames, when the backend owns perspective correction
 * @param diagnosticCode stable media diagnostic code for non-accepted results
 * @param metrics result-level metrics
 * @param message diagnostic detail for non-accepted results
 */
public record CvDetectionResult(
        CvDetectionStatus status,
        List<CvFrameCandidate> candidates,
        List<CvNormalizedFrame> normalizedFrames,
        Optional<CaptureMediaDiagnosticCode> diagnosticCode,
        Map<String, Double> metrics,
        String message
) {

    /**
     * Creates a validated CV detection result.
     *
     * @param status coarse CV result status
     * @param candidates ranked source-space candidates
     * @param normalizedFrames normalized ARGB frames
     * @param diagnosticCode stable media diagnostic code for non-accepted results
     * @param metrics result-level metrics
     * @param message diagnostic detail
     */
    public CvDetectionResult {
        Objects.requireNonNull(status, "status must not be null");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("candidates must not contain null values");
        }
        normalizedFrames = List.copyOf(Objects.requireNonNull(normalizedFrames, "normalizedFrames must not be null"));
        if (normalizedFrames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("normalizedFrames must not contain null values");
        }
        diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode must not be null");
        metrics = copyMetrics(metrics);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        validateAcceptedShape(status, candidates, normalizedFrames, diagnosticCode);
        validateRejectedShape(status, normalizedFrames, diagnosticCode);
    }

    /**
     * Creates an accepted result with ranked source-space candidates.
     *
     * @param candidates ranked frame candidates
     * @return accepted candidate result
     */
    public static CvDetectionResult acceptedCandidates(List<CvFrameCandidate> candidates) {
        return new CvDetectionResult(
                CvDetectionStatus.ACCEPTED,
                candidates,
                List.of(),
                Optional.empty(),
                Map.of(),
                "CV backend accepted frame candidates"
        );
    }

    /**
     * Creates an accepted result with backend-normalized frames.
     *
     * @param normalizedFrames normalized frame candidates
     * @return accepted normalized-frame result
     */
    public static CvDetectionResult acceptedNormalizedFrames(List<CvNormalizedFrame> normalizedFrames) {
        return new CvDetectionResult(
                CvDetectionStatus.ACCEPTED,
                List.of(),
                normalizedFrames,
                Optional.empty(),
                Map.of(),
                "CV backend accepted normalized frames"
        );
    }

    /**
     * Creates a rejected result with a stable media diagnostic code.
     *
     * @param diagnosticCode diagnostic code to expose through media normalization
     * @param metrics result-level metrics
     * @param message diagnostic detail
     * @return rejected result
     */
    public static CvDetectionResult rejected(
            CaptureMediaDiagnosticCode diagnosticCode,
            Map<String, Double> metrics,
            String message
    ) {
        return new CvDetectionResult(
                CvDetectionStatus.REJECTED,
                List.of(),
                List.of(),
                Optional.of(Objects.requireNonNull(diagnosticCode, "diagnosticCode must not be null")),
                metrics,
                message
        );
    }

    /**
     * Creates a too-small result with optional ranked evidence candidates.
     *
     * @param candidates ranked evidence candidates
     * @param metrics result-level metrics
     * @param message diagnostic detail
     * @return too-small result
     */
    public static CvDetectionResult tooSmall(
            List<CvFrameCandidate> candidates,
            Map<String, Double> metrics,
            String message
    ) {
        return new CvDetectionResult(
                CvDetectionStatus.TOO_SMALL,
                candidates,
                List.of(),
                Optional.of(CaptureMediaDiagnosticCode.MONITOR_TOO_SMALL),
                metrics,
                message
        );
    }

    /**
     * Creates an ambiguous result with ranked ambiguous candidates.
     *
     * @param candidates ranked ambiguous candidates
     * @param metrics result-level metrics
     * @param message diagnostic detail
     * @return ambiguous result
     */
    public static CvDetectionResult ambiguous(
            List<CvFrameCandidate> candidates,
            Map<String, Double> metrics,
            String message
    ) {
        return new CvDetectionResult(
                CvDetectionStatus.AMBIGUOUS,
                candidates,
                List.of(),
                Optional.of(CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS),
                metrics,
                message
        );
    }

    /**
     * Creates a backend-failure result without exposing backend-specific exception types.
     *
     * @param metrics result-level metrics
     * @param message diagnostic detail
     * @return backend-failure result
     */
    public static CvDetectionResult backendFailure(Map<String, Double> metrics, String message) {
        return new CvDetectionResult(
                CvDetectionStatus.BACKEND_FAILURE,
                List.of(),
                List.of(),
                Optional.of(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA),
                metrics,
                message
        );
    }

    /**
     * Returns diagnostic metrics enriched with candidate count and top-candidate evidence.
     *
     * @return diagnostic metrics
     */
    public Map<String, Double> diagnosticMetrics() {
        Map<String, Double> diagnosticMetrics = new LinkedHashMap<>(metrics);
        diagnosticMetrics.put("detectedCandidateCount", (double) candidates.size());
        candidates.stream().findFirst().ifPresent(candidate -> diagnosticMetrics.putAll(candidate.score().metrics()));
        return Map.copyOf(diagnosticMetrics);
    }

    private static void validateAcceptedShape(
            CvDetectionStatus status,
            List<CvFrameCandidate> candidates,
            List<CvNormalizedFrame> normalizedFrames,
            Optional<CaptureMediaDiagnosticCode> diagnosticCode
    ) {
        if (status != CvDetectionStatus.ACCEPTED) {
            return;
        }
        if (diagnosticCode.isPresent()) {
            throw new IllegalArgumentException("accepted CV results must not include a diagnostic code");
        }
        boolean hasCandidates = !candidates.isEmpty();
        boolean hasNormalizedFrames = !normalizedFrames.isEmpty();
        if (hasCandidates == hasNormalizedFrames) {
            throw new IllegalArgumentException("accepted CV results require candidates or normalizedFrames, but not both");
        }
    }

    private static void validateRejectedShape(
            CvDetectionStatus status,
            List<CvNormalizedFrame> normalizedFrames,
            Optional<CaptureMediaDiagnosticCode> diagnosticCode
    ) {
        if (status == CvDetectionStatus.ACCEPTED) {
            return;
        }
        if (!normalizedFrames.isEmpty()) {
            throw new IllegalArgumentException("non-accepted CV results must not include normalized frames");
        }
        if (diagnosticCode.isEmpty()) {
            throw new IllegalArgumentException("non-accepted CV results require a diagnostic code");
        }
    }

    private static Map<String, Double> copyMetrics(Map<String, Double> metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        metrics.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("metric names must not be blank");
            }
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("metric values must be finite");
            }
        });
        return Map.copyOf(metrics);
    }
}

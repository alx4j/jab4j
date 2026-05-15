package com.alx4j.jab4j.reader.capture.media.cv.legacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of bounded JAB frame region detection for one source image.
 *
 * @param status coarse detection status
 * @param rankedCandidates candidates sorted strongest first
 */
record JabFrameDetectionResult(
        JabFrameDetectionStatus status,
        List<JabFrameCandidate> rankedCandidates
) {

    /**
     * Creates an immutable detection result.
     */
    JabFrameDetectionResult {
        Objects.requireNonNull(status, "status must not be null");
        rankedCandidates = List.copyOf(Objects.requireNonNull(rankedCandidates, "rankedCandidates must not be null"));
        if (rankedCandidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("rankedCandidates must not contain null values");
        }
        if (status == JabFrameDetectionStatus.ACCEPTED && rankedCandidates.isEmpty()) {
            throw new IllegalArgumentException("accepted detection requires at least one candidate");
        }
    }

    /**
     * Creates an accepted detection result.
     *
     * @param candidates ranked accepted candidates
     * @return accepted result
     */
    static JabFrameDetectionResult accepted(List<JabFrameCandidate> candidates) {
        return new JabFrameDetectionResult(JabFrameDetectionStatus.ACCEPTED, candidates);
    }

    /**
     * Creates a too-small detection result.
     *
     * @param candidates ranked evidence candidates
     * @return too-small result
     */
    static JabFrameDetectionResult tooSmall(List<JabFrameCandidate> candidates) {
        return new JabFrameDetectionResult(JabFrameDetectionStatus.TOO_SMALL, candidates);
    }

    /**
     * Creates an ambiguous detection result.
     *
     * @param candidates ranked ambiguous candidates
     * @return ambiguous result
     */
    static JabFrameDetectionResult ambiguous(List<JabFrameCandidate> candidates) {
        return new JabFrameDetectionResult(JabFrameDetectionStatus.AMBIGUOUS, candidates);
    }

    /**
     * Creates a not-found detection result.
     *
     * @param candidates ranked rejected candidates
     * @return not-found result
     */
    static JabFrameDetectionResult notFound(List<JabFrameCandidate> candidates) {
        return new JabFrameDetectionResult(JabFrameDetectionStatus.NOT_FOUND, candidates);
    }

    /**
     * Returns the selected candidate for accepted or diagnostic contexts.
     *
     * @return top-ranked candidate, when present
     */
    Optional<JabFrameCandidate> selectedCandidate() {
        return rankedCandidates.stream().findFirst();
    }

    /**
     * Returns summary metrics for diagnostics.
     *
     * @return result-level metrics
     */
    Map<String, Double> metrics() {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("detectedCandidateCount", (double) rankedCandidates.size());
        selectedCandidate().ifPresent(candidate -> metrics.putAll(candidate.score().metrics()));
        return Map.copyOf(metrics);
    }
}

/**
 * Coarse status for bounded JAB frame region detection.
 */
enum JabFrameDetectionStatus {
    /**
     * One or more ranked candidates are plausible enough to normalize and pass to decode.
     */
    ACCEPTED,

    /**
     * JAB evidence exists, but the candidate is below the supported coverage threshold.
     */
    TOO_SMALL,

    /**
     * Multiple plausible candidates survive and no dominant candidate can be selected safely.
     */
    AMBIGUOUS,

    /**
     * No candidate contains enough JAB-specific evidence.
     */
    NOT_FOUND
}

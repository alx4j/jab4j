package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.List;
import java.util.Objects;

/**
 * Ranked result of a bounded module phase search.
 *
 * @param rankedAttempts attempts ordered from strongest to weakest reader-owned evidence
 * @param variantCap maximum phase variants allowed for the request
 * @param capReached true when additional unique variants were available but not evaluated
 */
public record CaptureMediaModulePhaseSearchResult(
        List<CaptureMediaModulePhaseAttempt> rankedAttempts,
        int variantCap,
        boolean capReached
) {

    /**
     * Creates a validated phase-search result.
     *
     * @param rankedAttempts ranked attempts
     * @param variantCap maximum phase variants allowed for the request
     * @param capReached whether the cap truncated candidate generation
     */
    public CaptureMediaModulePhaseSearchResult {
        rankedAttempts = List.copyOf(Objects.requireNonNull(rankedAttempts, "rankedAttempts must not be null"));
        if (rankedAttempts.isEmpty()) {
            throw new IllegalArgumentException("rankedAttempts must not be empty");
        }
        if (rankedAttempts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("rankedAttempts must not contain null values");
        }
        if (variantCap <= 0) {
            throw new IllegalArgumentException("variantCap must be positive");
        }
        if (rankedAttempts.size() > variantCap) {
            throw new IllegalArgumentException("rankedAttempts must not exceed variantCap");
        }
    }

    /**
     * Returns the highest-ranked phase attempt.
     *
     * @return selected phase attempt
     */
    public CaptureMediaModulePhaseAttempt selectedAttempt() {
        return rankedAttempts.get(0);
    }

    /**
     * Returns the selected phase candidate.
     *
     * @return selected phase candidate
     */
    public CaptureMediaModulePhaseCandidate selectedCandidate() {
        return selectedAttempt().candidate();
    }

    /**
     * Returns the number of evaluated variants.
     *
     * @return attempted variant count
     */
    public int attemptedVariantCount() {
        return rankedAttempts.size();
    }

    /**
     * Returns ranked candidates in the same order as ranked attempts.
     *
     * @return ranked phase candidates
     */
    public List<CaptureMediaModulePhaseCandidate> rankedCandidates() {
        return rankedAttempts.stream()
                .map(CaptureMediaModulePhaseAttempt::candidate)
                .toList();
    }
}

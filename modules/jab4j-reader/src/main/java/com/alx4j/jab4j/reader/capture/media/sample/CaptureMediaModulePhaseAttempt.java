package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.Objects;
import java.util.Optional;

/**
 * Reader-owned evidence collected for one module phase candidate.
 *
 * @param candidate generated phase candidate
 * @param outcome final reader-owned attempt outcome
 * @param failureDetail specific tile, envelope, or slot failure detail, when available
 * @param finderCandidateCount count of finder candidates observed for this phase
 * @param finderExact true when finder evidence matched exactly
 * @param borderStrength normalized border signature strength
 * @param paletteCalibrationConfidence normalized selected-palette calibration confidence
 * @param rejectedSampleCount number of rejected palette samples for this phase
 */
public record CaptureMediaModulePhaseAttempt(
        CaptureMediaModulePhaseCandidate candidate,
        CaptureMediaModulePhaseOutcome outcome,
        Optional<String> failureDetail,
        int finderCandidateCount,
        boolean finderExact,
        double borderStrength,
        double paletteCalibrationConfidence,
        int rejectedSampleCount
) {

    /**
     * Creates a validated phase attempt.
     *
     * @param candidate generated phase candidate
     * @param outcome reader-owned attempt outcome
     * @param failureDetail optional specific failure detail
     * @param finderCandidateCount count of finder candidates
     * @param finderExact whether finder evidence matched exactly
     * @param borderStrength normalized border signature strength
     * @param paletteCalibrationConfidence normalized palette calibration confidence
     * @param rejectedSampleCount rejected palette sample count
     */
    public CaptureMediaModulePhaseAttempt {
        candidate = Objects.requireNonNull(candidate, "candidate must not be null");
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail must not be null")
                .map(String::trim);
        failureDetail.ifPresent(detail -> {
            if (detail.isBlank()) {
                throw new IllegalArgumentException("failureDetail must not be blank when present");
            }
        });
        if (outcome.acceptedPayload() && failureDetail.isPresent()) {
            throw new IllegalArgumentException("accepted attempts must not include a failure detail");
        }
        if (finderCandidateCount < 0) {
            throw new IllegalArgumentException("finderCandidateCount must be non-negative");
        }
        requireUnitScore(borderStrength, "borderStrength");
        requireUnitScore(paletteCalibrationConfidence, "paletteCalibrationConfidence");
        if (rejectedSampleCount < 0) {
            throw new IllegalArgumentException("rejectedSampleCount must be non-negative");
        }
    }

    /**
     * Creates an accepted attempt.
     *
     * @param candidate generated phase candidate
     * @param finderCandidateCount count of finder candidates
     * @param finderExact whether finder evidence matched exactly
     * @param borderStrength normalized border signature strength
     * @param paletteCalibrationConfidence normalized palette calibration confidence
     * @param rejectedSampleCount rejected palette sample count
     * @return accepted phase attempt
     */
    public static CaptureMediaModulePhaseAttempt accepted(
            CaptureMediaModulePhaseCandidate candidate,
            int finderCandidateCount,
            boolean finderExact,
            double borderStrength,
            double paletteCalibrationConfidence,
            int rejectedSampleCount
    ) {
        return new CaptureMediaModulePhaseAttempt(
                candidate,
                CaptureMediaModulePhaseOutcome.ACCEPTED_PAYLOAD,
                Optional.empty(),
                finderCandidateCount,
                finderExact,
                borderStrength,
                paletteCalibrationConfidence,
                rejectedSampleCount
        );
    }

    /**
     * Creates a rejected attempt.
     *
     * @param candidate generated phase candidate
     * @param outcome reader-owned rejection stage
     * @param failureDetail optional specific failure detail
     * @param finderCandidateCount count of finder candidates
     * @param finderExact whether finder evidence matched exactly
     * @param borderStrength normalized border signature strength
     * @param paletteCalibrationConfidence normalized palette calibration confidence
     * @param rejectedSampleCount rejected palette sample count
     * @return rejected phase attempt
     */
    public static CaptureMediaModulePhaseAttempt rejected(
            CaptureMediaModulePhaseCandidate candidate,
            CaptureMediaModulePhaseOutcome outcome,
            Optional<String> failureDetail,
            int finderCandidateCount,
            boolean finderExact,
            double borderStrength,
            double paletteCalibrationConfidence,
            int rejectedSampleCount
    ) {
        if (outcome == CaptureMediaModulePhaseOutcome.ACCEPTED_PAYLOAD) {
            throw new IllegalArgumentException("rejected attempts must not use ACCEPTED_PAYLOAD");
        }
        return new CaptureMediaModulePhaseAttempt(
                candidate,
                outcome,
                failureDetail,
                finderCandidateCount,
                finderExact,
                borderStrength,
                paletteCalibrationConfidence,
                rejectedSampleCount
        );
    }

    /**
     * Returns whether this attempt accepted a payload.
     *
     * @return true when payload validation passed
     */
    public boolean acceptedPayload() {
        return outcome.acceptedPayload();
    }

    /**
     * Returns whether this attempt reached tile decode or a later validation stage.
     *
     * @return true when post-palette validation was attempted
     */
    public boolean tileDecodeAttempted() {
        return outcome.tileDecodeAttempted();
    }

    /**
     * Returns whether this failed attempt carries a specific rejection detail.
     *
     * @return true when a nonblank failure detail is present
     */
    public boolean specificFailureDetail() {
        return failureDetail.isPresent();
    }

    /**
     * Returns the reader-owned validation progress rank for this attempt.
     *
     * @return higher value for later validation stages
     */
    public int protocolProgressRank() {
        return outcome.protocolProgressRank();
    }

    private static void requireUnitScore(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}

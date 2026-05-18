package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable candidate-scoped finder/alignment evidence for one proposal candidate.
 *
 * @param schemaVersion evidence schema version
 * @param candidateId pattern-scoped candidate identity
 * @param layoutProfileId layout profile evaluated for this candidate
 * @param status pattern evidence status
 * @param features bounded finder or alignment feature evidence
 * @param orientationCandidates deterministic orientation candidate scores
 * @param layoutProfileCandidates deterministic layout/profile candidate scores
 * @param alignmentExpected whether the current profile expects independent alignment features
 * @param reasonCodes pattern-stage reason codes
 * @param downstreamConflictReasons later-stage reason codes that conflict with pattern evidence
 * @param confidence deterministic aggregate confidence between 0 and 1
 * @param dominanceMargin score margin over the next plausible arrangement
 */
public record PatternEvidence(
        int schemaVersion,
        CaptureMediaCandidateId candidateId,
        String layoutProfileId,
        PatternEvidenceStatus status,
        List<PatternFeatureEvidence> features,
        Map<String, Double> orientationCandidates,
        Map<String, Double> layoutProfileCandidates,
        boolean alignmentExpected,
        List<CaptureMediaEvidenceReasonCode> reasonCodes,
        List<CaptureMediaEvidenceReasonCode> downstreamConflictReasons,
        double confidence,
        double dominanceMargin
) {

    /**
     * Creates validated pattern evidence with defensive copies of collection fields.
     *
     * @param schemaVersion evidence schema version
     * @param candidateId pattern-scoped candidate identity
     * @param layoutProfileId layout profile identifier
     * @param status pattern evidence status
     * @param features feature evidence records
     * @param orientationCandidates orientation candidate scores
     * @param layoutProfileCandidates layout/profile candidate scores
     * @param alignmentExpected whether independent alignment features are expected
     * @param reasonCodes pattern-stage reason codes
     * @param downstreamConflictReasons downstream conflict reason codes
     * @param confidence aggregate confidence
     * @param dominanceMargin dominance margin
     */
    public PatternEvidence {
        EvidenceValidation.requirePositive(schemaVersion, "schemaVersion");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        if (candidateId.patternEvidenceId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must be pattern-scoped");
        }
        layoutProfileId = EvidenceValidation.requireText(layoutProfileId, "layoutProfileId");
        Objects.requireNonNull(status, "status must not be null");
        features = EvidenceValidation.copyList(features, "features");
        orientationCandidates = EvidenceValidation.copyScoreMap(orientationCandidates, "orientationCandidates");
        layoutProfileCandidates = EvidenceValidation.copyScoreMap(layoutProfileCandidates, "layoutProfileCandidates");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
        downstreamConflictReasons = EvidenceValidation.copyReasonCodes(
                downstreamConflictReasons,
                "downstreamConflictReasons"
        );
        EvidenceValidation.requireUnitScore(confidence, "confidence");
        EvidenceValidation.requireUnitScore(dominanceMargin, "dominanceMargin");
    }
}


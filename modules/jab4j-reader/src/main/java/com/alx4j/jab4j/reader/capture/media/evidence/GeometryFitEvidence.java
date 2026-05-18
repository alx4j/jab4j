package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable aggregate geometry fit evidence for one proposal candidate.
 *
 * @param schemaVersion evidence schema version
 * @param candidateId proposal- or pattern-scoped candidate identity
 * @param status aggregate geometry fit status
 * @param retainedCandidates retained geometry candidates, capped by MVP-9
 * @param selectedGeometryCandidateId selected geometry candidate ID, when available
 * @param downstreamSelectedGeometryCandidateId downstream-selected geometry candidate ID, when available
 * @param reasonCodes aggregate geometry reason codes
 */
public record GeometryFitEvidence(
        int schemaVersion,
        CaptureMediaCandidateId candidateId,
        GeometryFitStatus status,
        List<GeometryCandidateEvidence> retainedCandidates,
        Optional<String> selectedGeometryCandidateId,
        Optional<String> downstreamSelectedGeometryCandidateId,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated geometry fit evidence with defensive collection copies.
     *
     * @param schemaVersion evidence schema version
     * @param candidateId proposal- or pattern-scoped candidate identity
     * @param status aggregate geometry status
     * @param retainedCandidates retained geometry candidates
     * @param selectedGeometryCandidateId optional selected geometry candidate ID
     * @param downstreamSelectedGeometryCandidateId optional downstream-selected geometry candidate ID
     * @param reasonCodes aggregate geometry reason codes
     */
    public GeometryFitEvidence {
        EvidenceValidation.requirePositive(schemaVersion, "schemaVersion");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        if (candidateId.proposalCandidateId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must be at least proposal-scoped");
        }
        Objects.requireNonNull(status, "status must not be null");
        retainedCandidates = EvidenceValidation.copyList(retainedCandidates, "retainedCandidates");
        if (retainedCandidates.size() > CaptureMediaCandidateId.MAX_GEOMETRY_CANDIDATE_RANK) {
            throw new IllegalArgumentException("retainedCandidates must not exceed "
                    + CaptureMediaCandidateId.MAX_GEOMETRY_CANDIDATE_RANK);
        }
        selectedGeometryCandidateId = EvidenceValidation.copyOptionalText(
                selectedGeometryCandidateId,
                "selectedGeometryCandidateId"
        );
        downstreamSelectedGeometryCandidateId = EvidenceValidation.copyOptionalText(
                downstreamSelectedGeometryCandidateId,
                "downstreamSelectedGeometryCandidateId"
        );
        if (selectedGeometryCandidateId.isPresent()) {
            requireRetainedCandidate(retainedCandidates, selectedGeometryCandidateId.orElseThrow());
        }
        if (downstreamSelectedGeometryCandidateId.isPresent()) {
            requireRetainedCandidate(retainedCandidates, downstreamSelectedGeometryCandidateId.orElseThrow());
        }
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }

    private static void requireRetainedCandidate(List<GeometryCandidateEvidence> retainedCandidates, String selected) {
        boolean present = retainedCandidates.stream()
                .map(GeometryCandidateEvidence::candidateId)
                .map(CaptureMediaCandidateId::geometryCandidateId)
                .flatMap(Optional::stream)
                .anyMatch(selected::equals);
        if (!present) {
            throw new IllegalArgumentException("selected geometry candidate must be retained");
        }
    }
}

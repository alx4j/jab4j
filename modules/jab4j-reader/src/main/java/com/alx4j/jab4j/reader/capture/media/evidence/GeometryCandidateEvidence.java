package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Objects;

/**
 * Immutable evidence for one fitted barcode-plane geometry candidate.
 *
 * @param candidateId geometry-scoped candidate identity
 * @param rank one-based deterministic geometry rank
 * @param status geometry candidate status
 * @param fitModelType geometry model type used for this candidate
 * @param canonicalCoordinateSystem canonical coordinate system description
 * @param imageCoordinateSystem source-image coordinate system description
 * @param transformParameters transform parameters or stable transform summary values
 * @param transformConditionScore finite numeric conditioning summary
 * @param degeneracyFlags stable degeneracy flags observed during fitting
 * @param invertible whether the transform can be inverted
 * @param observedPointCount observed feature/control-point count
 * @param expectedPointCount expected feature/control-point count
 * @param matchedPointCount matched point-role count
 * @param inlierCount retained inlier count
 * @param outlierCount rejected outlier count
 * @param missingExpectedPointCount expected points not observed
 * @param reprojectionMetrics reprojection error summary
 * @param score deterministic fit score
 * @param dominanceMargin score margin over the next plausible candidate
 * @param retainedForSampling whether this candidate may be sampled downstream
 * @param downstreamSelected whether downstream validation selected this candidate
 * @param reasonCodes geometry-candidate reason codes
 */
public record GeometryCandidateEvidence(
        CaptureMediaCandidateId candidateId,
        int rank,
        GeometryFitStatus status,
        GeometryFitModelType fitModelType,
        String canonicalCoordinateSystem,
        String imageCoordinateSystem,
        List<Double> transformParameters,
        double transformConditionScore,
        List<String> degeneracyFlags,
        boolean invertible,
        int observedPointCount,
        int expectedPointCount,
        int matchedPointCount,
        int inlierCount,
        int outlierCount,
        int missingExpectedPointCount,
        ReprojectionMetrics reprojectionMetrics,
        double score,
        double dominanceMargin,
        boolean retainedForSampling,
        boolean downstreamSelected,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated geometry candidate evidence.
     *
     * @param candidateId geometry-scoped candidate identity
     * @param rank one-based deterministic rank
     * @param status geometry fit status
     * @param fitModelType geometry fit model
     * @param canonicalCoordinateSystem canonical coordinate system
     * @param imageCoordinateSystem image coordinate system
     * @param transformParameters transform parameters
     * @param transformConditionScore transform conditioning summary
     * @param degeneracyFlags degeneracy flags
     * @param invertible whether the transform is invertible
     * @param observedPointCount observed point count
     * @param expectedPointCount expected point count
     * @param matchedPointCount matched point count
     * @param inlierCount inlier count
     * @param outlierCount outlier count
     * @param missingExpectedPointCount missing expected point count
     * @param reprojectionMetrics reprojection metrics
     * @param score deterministic fit score
     * @param dominanceMargin score dominance margin
     * @param retainedForSampling whether retained for sampling
     * @param downstreamSelected whether selected downstream
     * @param reasonCodes geometry candidate reason codes
     */
    public GeometryCandidateEvidence {
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        if (candidateId.geometryCandidateId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must be geometry-scoped");
        }
        EvidenceValidation.requirePositive(rank, "rank");
        if (rank > CaptureMediaCandidateId.MAX_GEOMETRY_CANDIDATE_RANK) {
            throw new IllegalArgumentException("rank must not exceed "
                    + CaptureMediaCandidateId.MAX_GEOMETRY_CANDIDATE_RANK);
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(fitModelType, "fitModelType must not be null");
        canonicalCoordinateSystem = EvidenceValidation.requireText(
                canonicalCoordinateSystem,
                "canonicalCoordinateSystem"
        );
        imageCoordinateSystem = EvidenceValidation.requireText(imageCoordinateSystem, "imageCoordinateSystem");
        transformParameters = EvidenceValidation.copyFiniteDoubles(transformParameters, "transformParameters");
        EvidenceValidation.requireNonNegativeFinite(transformConditionScore, "transformConditionScore");
        degeneracyFlags = EvidenceValidation.copyTextList(degeneracyFlags, "degeneracyFlags");
        EvidenceValidation.requireNonNegative(observedPointCount, "observedPointCount");
        EvidenceValidation.requireNonNegative(expectedPointCount, "expectedPointCount");
        EvidenceValidation.requireNonNegative(matchedPointCount, "matchedPointCount");
        EvidenceValidation.requireNonNegative(inlierCount, "inlierCount");
        EvidenceValidation.requireNonNegative(outlierCount, "outlierCount");
        EvidenceValidation.requireNonNegative(missingExpectedPointCount, "missingExpectedPointCount");
        if (matchedPointCount > observedPointCount || matchedPointCount > expectedPointCount) {
            throw new IllegalArgumentException("matchedPointCount must not exceed observed or expected point counts");
        }
        if (inlierCount + outlierCount > matchedPointCount) {
            throw new IllegalArgumentException("inlierCount and outlierCount must not exceed matchedPointCount");
        }
        if (missingExpectedPointCount > expectedPointCount) {
            throw new IllegalArgumentException("missingExpectedPointCount must not exceed expectedPointCount");
        }
        Objects.requireNonNull(reprojectionMetrics, "reprojectionMetrics must not be null");
        EvidenceValidation.requireFinite(score, "score");
        EvidenceValidation.requireUnitScore(dominanceMargin, "dominanceMargin");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}


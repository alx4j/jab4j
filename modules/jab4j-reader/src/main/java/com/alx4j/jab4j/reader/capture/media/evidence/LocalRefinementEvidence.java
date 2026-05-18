package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable local residual and bounded-refinement evidence for a sampled geometry candidate.
 *
 * @param schemaVersion evidence schema version
 * @param candidateId refinement-scoped candidate identity
 * @param status local refinement decision status
 * @param baseGeometryCandidateId global geometry candidate ID used as the baseline
 * @param baseSamplingCandidateId source-space sampling candidate ID used as the baseline
 * @param baseReprojectionMetrics global reprojection metrics before refinement
 * @param baseSamplingMetrics bounded sampling metrics before refinement
 * @param controlPoints bounded local control-point evidence
 * @param modelType local refinement model type
 * @param gridColumns control-grid column count
 * @param gridRows control-grid row count
 * @param maxLocalDisplacementModules maximum local displacement in module units
 * @param maxLocalDisplacementPixels maximum local displacement in source pixels
 * @param smoothnessConstraint stable smoothness constraint label
 * @param smoothnessScore normalized smoothness score
 * @param regularizationScore normalized regularization score
 * @param improvementMetrics bounded before/after improvement metrics
 * @param appliedToSampling whether refined positions were used by source-space sampling
 * @param reasonCodes local-refinement reason codes
 */
public record LocalRefinementEvidence(
        int schemaVersion,
        CaptureMediaCandidateId candidateId,
        LocalRefinementStatus status,
        String baseGeometryCandidateId,
        String baseSamplingCandidateId,
        ReprojectionMetrics baseReprojectionMetrics,
        Map<String, Double> baseSamplingMetrics,
        List<LocalControlPointEvidence> controlPoints,
        LocalRefinementModelType modelType,
        int gridColumns,
        int gridRows,
        double maxLocalDisplacementModules,
        double maxLocalDisplacementPixels,
        String smoothnessConstraint,
        double smoothnessScore,
        double regularizationScore,
        Map<String, Double> improvementMetrics,
        boolean appliedToSampling,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated local-refinement evidence with defensive collection copies.
     *
     * @param schemaVersion evidence schema version
     * @param candidateId refinement-scoped candidate identity
     * @param status local refinement status
     * @param baseGeometryCandidateId baseline geometry candidate ID
     * @param baseSamplingCandidateId baseline sampling candidate ID
     * @param baseReprojectionMetrics baseline reprojection metrics
     * @param baseSamplingMetrics baseline sampling metrics
     * @param controlPoints local control-point evidence
     * @param modelType local refinement model
     * @param gridColumns grid column count
     * @param gridRows grid row count
     * @param maxLocalDisplacementModules maximum module displacement
     * @param maxLocalDisplacementPixels maximum pixel displacement
     * @param smoothnessConstraint smoothness constraint label
     * @param smoothnessScore normalized smoothness score
     * @param regularizationScore normalized regularization score
     * @param improvementMetrics improvement metrics
     * @param appliedToSampling whether positions were applied to sampling
     * @param reasonCodes local-refinement reason codes
     */
    public LocalRefinementEvidence {
        EvidenceValidation.requirePositive(schemaVersion, "schemaVersion");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        String chainGeometryId = candidateId.geometryCandidateId()
                .orElseThrow(() -> new IllegalArgumentException("candidateId must include a geometry candidate ID"));
        String chainSamplingId = candidateId.samplingCandidateId()
                .orElseThrow(() -> new IllegalArgumentException("candidateId must include a sampling candidate ID"));
        if (candidateId.refinementCandidateId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must be refinement-scoped");
        }
        Objects.requireNonNull(status, "status must not be null");
        if (appliedToSampling != (status == LocalRefinementStatus.APPLIED)) {
            throw new IllegalArgumentException("appliedToSampling must be true only for APPLIED status");
        }
        baseGeometryCandidateId = EvidenceValidation.requireText(baseGeometryCandidateId, "baseGeometryCandidateId");
        baseSamplingCandidateId = EvidenceValidation.requireText(baseSamplingCandidateId, "baseSamplingCandidateId");
        if (!baseGeometryCandidateId.equals(chainGeometryId)) {
            throw new IllegalArgumentException("baseGeometryCandidateId must match candidateId geometryCandidateId");
        }
        if (!baseSamplingCandidateId.equals(chainSamplingId)) {
            throw new IllegalArgumentException("baseSamplingCandidateId must match candidateId samplingCandidateId");
        }
        Objects.requireNonNull(baseReprojectionMetrics, "baseReprojectionMetrics must not be null");
        baseSamplingMetrics = EvidenceValidation.copyMetricMap(baseSamplingMetrics, "baseSamplingMetrics");
        controlPoints = EvidenceValidation.copyList(controlPoints, "controlPoints");
        Objects.requireNonNull(modelType, "modelType must not be null");
        EvidenceValidation.requirePositive(gridColumns, "gridColumns");
        EvidenceValidation.requirePositive(gridRows, "gridRows");
        EvidenceValidation.requireNonNegativeFinite(maxLocalDisplacementModules, "maxLocalDisplacementModules");
        EvidenceValidation.requireNonNegativeFinite(maxLocalDisplacementPixels, "maxLocalDisplacementPixels");
        smoothnessConstraint = EvidenceValidation.requireText(smoothnessConstraint, "smoothnessConstraint");
        EvidenceValidation.requireUnitScore(smoothnessScore, "smoothnessScore");
        EvidenceValidation.requireUnitScore(regularizationScore, "regularizationScore");
        improvementMetrics = EvidenceValidation.copyMetricMap(improvementMetrics, "improvementMetrics");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}

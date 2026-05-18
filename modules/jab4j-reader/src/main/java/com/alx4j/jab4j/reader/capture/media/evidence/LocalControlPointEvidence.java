package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable local control-point evidence used for residual diagnostics or local refinement.
 *
 * @param controlPointId stable local control-point identifier
 * @param canonicalPoint expected canonical control-point position
 * @param expectedSourcePoint global-geometry source prediction
 * @param observedSourcePoint observed source point, when matched
 * @param residualBeforePixels residual before local correction in source pixels
 * @param residualAfterPixels residual after local correction in source pixels
 * @param residualBeforeModules residual before local correction in module units
 * @param residualAfterModules residual after local correction in module units
 * @param inlier whether this control point was retained as an inlier
 * @param distributed whether this control point contributes to distributed local evidence
 * @param confidence deterministic control-point confidence
 * @param reasonCodes control-point reason codes
 */
public record LocalControlPointEvidence(
        String controlPointId,
        CanonicalPoint canonicalPoint,
        SourcePoint expectedSourcePoint,
        Optional<SourcePoint> observedSourcePoint,
        double residualBeforePixels,
        double residualAfterPixels,
        double residualBeforeModules,
        double residualAfterModules,
        boolean inlier,
        boolean distributed,
        double confidence,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated local control-point evidence.
     *
     * @param controlPointId stable control-point identifier
     * @param canonicalPoint expected canonical position
     * @param expectedSourcePoint global-geometry source prediction
     * @param observedSourcePoint optional observed source position
     * @param residualBeforePixels pixel residual before correction
     * @param residualAfterPixels pixel residual after correction
     * @param residualBeforeModules module residual before correction
     * @param residualAfterModules module residual after correction
     * @param inlier whether retained as an inlier
     * @param distributed whether distributed across the candidate
     * @param confidence normalized control-point confidence
     * @param reasonCodes control-point reason codes
     */
    public LocalControlPointEvidence {
        controlPointId = EvidenceValidation.requireText(controlPointId, "controlPointId");
        Objects.requireNonNull(canonicalPoint, "canonicalPoint must not be null");
        Objects.requireNonNull(expectedSourcePoint, "expectedSourcePoint must not be null");
        observedSourcePoint = Objects.requireNonNull(observedSourcePoint, "observedSourcePoint must not be null");
        EvidenceValidation.requireNonNegativeFinite(residualBeforePixels, "residualBeforePixels");
        EvidenceValidation.requireNonNegativeFinite(residualAfterPixels, "residualAfterPixels");
        EvidenceValidation.requireNonNegativeFinite(residualBeforeModules, "residualBeforeModules");
        EvidenceValidation.requireNonNegativeFinite(residualAfterModules, "residualAfterModules");
        EvidenceValidation.requireUnitScore(confidence, "confidence");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}


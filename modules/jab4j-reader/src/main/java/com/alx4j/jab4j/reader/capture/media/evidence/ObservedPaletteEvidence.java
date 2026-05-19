package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Candidate-scoped evidence for observed palette extraction, safety, and selected classification mode.
 *
 * @param schemaVersion evidence schema version
 * @param candidateId sampling-scoped candidate identity
 * @param status observed palette status
 * @param paletteSource stable source label for observed reference samples
 * @param colorMethod selected color-distance method for classification or comparison
 * @param classificationMode selected palette classification mode
 * @param observedCoverageRatio fraction of palette indexes with observed centers
 * @param separationScore normalized observed-center separation score
 * @param weakestPalettePair weakest separated palette-index pair, when measurable
 * @param minimumConfidence minimum calibrated center confidence
 * @param thresholdVersion stable threshold set label
 * @param fallbackReason exact fallback or withholding reason
 * @param safetyDecision explicit decision about whether observed evidence can drive classification
 * @param colors bounded per-index palette center evidence
 * @param reasonCodes palette-stage reason codes
 */
public record ObservedPaletteEvidence(
        int schemaVersion,
        CaptureMediaCandidateId candidateId,
        ObservedPaletteStatus status,
        String paletteSource,
        ObservedPaletteColorMethod colorMethod,
        ObservedPaletteClassificationMode classificationMode,
        double observedCoverageRatio,
        double separationScore,
        Optional<String> weakestPalettePair,
        double minimumConfidence,
        String thresholdVersion,
        Optional<String> fallbackReason,
        ObservedPaletteSafetyDecision safetyDecision,
        List<ObservedPaletteColorEvidence> colors,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated observed palette evidence.
     */
    public ObservedPaletteEvidence {
        EvidenceValidation.requirePositive(schemaVersion, "schemaVersion");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        if (candidateId.samplingCandidateId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must be sampling-scoped");
        }
        Objects.requireNonNull(status, "status must not be null");
        paletteSource = EvidenceValidation.requireText(paletteSource, "paletteSource");
        Objects.requireNonNull(colorMethod, "colorMethod must not be null");
        Objects.requireNonNull(classificationMode, "classificationMode must not be null");
        EvidenceValidation.requireUnitScore(observedCoverageRatio, "observedCoverageRatio");
        EvidenceValidation.requireUnitScore(separationScore, "separationScore");
        weakestPalettePair = EvidenceValidation.copyOptionalText(weakestPalettePair, "weakestPalettePair");
        EvidenceValidation.requireUnitScore(minimumConfidence, "minimumConfidence");
        thresholdVersion = EvidenceValidation.requireText(thresholdVersion, "thresholdVersion");
        fallbackReason = EvidenceValidation.copyOptionalText(fallbackReason, "fallbackReason");
        Objects.requireNonNull(safetyDecision, "safetyDecision must not be null");
        colors = EvidenceValidation.copyList(colors, "colors");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}

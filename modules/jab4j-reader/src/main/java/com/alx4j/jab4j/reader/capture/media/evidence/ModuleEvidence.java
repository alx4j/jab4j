package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable source-space sampling evidence for one expected module.
 *
 * @param moduleX zero-based module x coordinate
 * @param moduleY zero-based module y coordinate
 * @param canonicalPolygon full canonical module polygon
 * @param sourcePolygon full source-space module polygon
 * @param innerSourcePolygon central source-space sampling polygon
 * @param sampleCount number of source samples used
 * @param aggregateArgb robust aggregate observed color
 * @param colorVariance aggregate color variance
 * @param assignedPaletteIndex assigned palette index when readable
 * @param bestPaletteDistance distance to the best palette color
 * @param secondBestPaletteDistance distance to the second-best palette color
 * @param confidenceMargin palette confidence margin
 * @param status module sample status
 * @param reasonCodes module-level reason codes
 */
public record ModuleEvidence(
        int moduleX,
        int moduleY,
        CanonicalPolygon canonicalPolygon,
        SourcePolygon sourcePolygon,
        SourcePolygon innerSourcePolygon,
        int sampleCount,
        int aggregateArgb,
        double colorVariance,
        OptionalInt assignedPaletteIndex,
        double bestPaletteDistance,
        double secondBestPaletteDistance,
        double confidenceMargin,
        ModuleSampleStatus status,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated per-module sampling evidence.
     *
     * @param moduleX zero-based module x coordinate
     * @param moduleY zero-based module y coordinate
     * @param canonicalPolygon full canonical module polygon
     * @param sourcePolygon full source-space module polygon
     * @param innerSourcePolygon inner sampling polygon
     * @param sampleCount number of samples used
     * @param aggregateArgb aggregate observed color
     * @param colorVariance aggregate color variance
     * @param assignedPaletteIndex optional assigned palette index
     * @param bestPaletteDistance best palette distance
     * @param secondBestPaletteDistance second-best palette distance
     * @param confidenceMargin palette confidence margin
     * @param status module sample status
     * @param reasonCodes module-level reason codes
     */
    public ModuleEvidence {
        EvidenceValidation.requireNonNegative(moduleX, "moduleX");
        EvidenceValidation.requireNonNegative(moduleY, "moduleY");
        Objects.requireNonNull(canonicalPolygon, "canonicalPolygon must not be null");
        Objects.requireNonNull(sourcePolygon, "sourcePolygon must not be null");
        Objects.requireNonNull(innerSourcePolygon, "innerSourcePolygon must not be null");
        EvidenceValidation.requireNonNegative(sampleCount, "sampleCount");
        EvidenceValidation.requireNonNegativeFinite(colorVariance, "colorVariance");
        assignedPaletteIndex = Objects.requireNonNull(assignedPaletteIndex, "assignedPaletteIndex must not be null");
        assignedPaletteIndex.ifPresent(index -> EvidenceValidation.requireNonNegative(index, "assignedPaletteIndex"));
        EvidenceValidation.requireNonNegativeFinite(bestPaletteDistance, "bestPaletteDistance");
        EvidenceValidation.requireNonNegativeFinite(secondBestPaletteDistance, "secondBestPaletteDistance");
        if (secondBestPaletteDistance < bestPaletteDistance) {
            throw new IllegalArgumentException("secondBestPaletteDistance must be at least bestPaletteDistance");
        }
        EvidenceValidation.requireNonNegativeFinite(confidenceMargin, "confidenceMargin");
        Objects.requireNonNull(status, "status must not be null");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}


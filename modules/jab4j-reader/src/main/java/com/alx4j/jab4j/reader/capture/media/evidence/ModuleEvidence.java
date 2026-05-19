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
 * @param moduleConfidence combined confidence from sample count, color, variance, and source footprint
 * @param geometryFootprintQuality normalized quality of the projected source-space sampling footprint
 * @param sourceFootprintAreaPx full projected module area in source pixels
 * @param innerFootprintAreaPx central sampled footprint area in source pixels
 * @param minimumFootprintEdgePx shortest central-footprint edge in source pixels
 * @param footprintAspectRatio projected central-footprint edge aspect ratio
 * @param clippedFraction fraction of projected sample points rejected or outside source bounds
 * @param colorMethod color-distance method used for palette classification
 * @param classificationMode palette classification mode used for this module
 * @param paletteModelSource stable source label for the selected palette model
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
        double moduleConfidence,
        double geometryFootprintQuality,
        double sourceFootprintAreaPx,
        double innerFootprintAreaPx,
        double minimumFootprintEdgePx,
        double footprintAspectRatio,
        double clippedFraction,
        ObservedPaletteColorMethod colorMethod,
        ObservedPaletteClassificationMode classificationMode,
        String paletteModelSource,
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
     * @param moduleConfidence combined module confidence
     * @param geometryFootprintQuality normalized source-footprint quality
     * @param sourceFootprintAreaPx full projected module area in source pixels
     * @param innerFootprintAreaPx central sampled footprint area in source pixels
     * @param minimumFootprintEdgePx shortest central-footprint edge in source pixels
     * @param footprintAspectRatio projected central-footprint edge aspect ratio
     * @param clippedFraction fraction of rejected or outside source-space samples
     * @param colorMethod color-distance method used for classification
     * @param classificationMode palette classification mode used for this module
     * @param paletteModelSource stable palette model source label
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
        EvidenceValidation.requireUnitScore(moduleConfidence, "moduleConfidence");
        EvidenceValidation.requireUnitScore(geometryFootprintQuality, "geometryFootprintQuality");
        EvidenceValidation.requireNonNegativeFinite(sourceFootprintAreaPx, "sourceFootprintAreaPx");
        EvidenceValidation.requireNonNegativeFinite(innerFootprintAreaPx, "innerFootprintAreaPx");
        EvidenceValidation.requireNonNegativeFinite(minimumFootprintEdgePx, "minimumFootprintEdgePx");
        EvidenceValidation.requireNonNegativeFinite(footprintAspectRatio, "footprintAspectRatio");
        EvidenceValidation.requireUnitScore(clippedFraction, "clippedFraction");
        Objects.requireNonNull(colorMethod, "colorMethod must not be null");
        Objects.requireNonNull(classificationMode, "classificationMode must not be null");
        paletteModelSource = EvidenceValidation.requireText(paletteModelSource, "paletteModelSource");
        Objects.requireNonNull(status, "status must not be null");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}

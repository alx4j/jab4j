package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.Objects;

/**
 * Evidence for one palette center in a candidate-local observed or hybrid palette.
 *
 * @param paletteIndex rendered palette index
 * @param expectedArgb exact rendered ARGB color for the palette index
 * @param modelArgb ARGB center selected for classification or fallback
 * @param sampleCount observed reference sample count for this index
 * @param maximumDistanceToCentroid largest observed distance to the selected center
 * @param maximumDistanceToExpected largest observed distance to the exact rendered color
 * @param confidence normalized center confidence
 * @param centerSource source of the selected center
 */
public record ObservedPaletteColorEvidence(
        int paletteIndex,
        int expectedArgb,
        int modelArgb,
        int sampleCount,
        double maximumDistanceToCentroid,
        double maximumDistanceToExpected,
        double confidence,
        ObservedPaletteCenterSource centerSource
) {

    /**
     * Creates validated per-color palette evidence.
     */
    public ObservedPaletteColorEvidence {
        EvidenceValidation.requireNonNegative(paletteIndex, "paletteIndex");
        EvidenceValidation.requireNonNegative(sampleCount, "sampleCount");
        EvidenceValidation.requireNonNegativeFinite(maximumDistanceToCentroid, "maximumDistanceToCentroid");
        EvidenceValidation.requireNonNegativeFinite(maximumDistanceToExpected, "maximumDistanceToExpected");
        EvidenceValidation.requireUnitScore(confidence, "confidence");
        Objects.requireNonNull(centerSource, "centerSource must not be null");
    }
}

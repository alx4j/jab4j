package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable evidence for one observed finder or alignment feature.
 *
 * @param featureType supported pattern feature family
 * @param finderRole finder role when the feature is a finder
 * @param tileIndex optional rendered tile index that supplied the feature observation
 * @param sideVersion stable side or profile-version label for diagnostics
 * @param canonicalFootprint feature footprint in canonical candidate coordinates
 * @param sourceFootprint feature footprint in source-image coordinates
 * @param matchedModuleCount matched finder modules, when applicable
 * @param expectedModuleCount expected finder modules, when applicable
 * @param confidence deterministic confidence between 0 and 1
 * @param scale estimated feature scale in source pixels or canonical units
 * @param rotationDegrees estimated feature rotation in degrees
 * @param observationSource source of the measured feature coordinates
 * @param reasonCodes feature-level reason codes
 */
public record PatternFeatureEvidence(
        PatternFeatureType featureType,
        Optional<FinderRole> finderRole,
        OptionalInt tileIndex,
        String sideVersion,
        CanonicalPolygon canonicalFootprint,
        SourcePolygon sourceFootprint,
        int matchedModuleCount,
        int expectedModuleCount,
        double confidence,
        double scale,
        double rotationDegrees,
        CoordinateObservationSource observationSource,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated pattern feature evidence.
     *
     * @param featureType supported pattern feature family
     * @param finderRole optional finder role
     * @param tileIndex optional tile index
     * @param sideVersion stable side or profile-version label
     * @param canonicalFootprint canonical feature footprint
     * @param sourceFootprint source-space feature footprint
     * @param matchedModuleCount matched module count
     * @param expectedModuleCount expected module count
     * @param confidence normalized confidence
     * @param scale estimated feature scale
     * @param rotationDegrees estimated feature rotation
     * @param observationSource feature observation source
     * @param reasonCodes feature-level reason codes
     */
    public PatternFeatureEvidence {
        Objects.requireNonNull(featureType, "featureType must not be null");
        finderRole = Objects.requireNonNull(finderRole, "finderRole must not be null");
        tileIndex = Objects.requireNonNull(tileIndex, "tileIndex must not be null");
        if (featureType == PatternFeatureType.FINDER && finderRole.isEmpty()) {
            throw new IllegalArgumentException("finderRole must be present for finder features");
        }
        if (featureType == PatternFeatureType.ALIGNMENT && finderRole.isPresent()) {
            throw new IllegalArgumentException("finderRole must be empty for alignment features");
        }
        tileIndex.ifPresent(index -> EvidenceValidation.requireNonNegative(index, "tileIndex"));
        sideVersion = EvidenceValidation.requireText(sideVersion, "sideVersion");
        Objects.requireNonNull(canonicalFootprint, "canonicalFootprint must not be null");
        Objects.requireNonNull(sourceFootprint, "sourceFootprint must not be null");
        EvidenceValidation.requireNonNegative(matchedModuleCount, "matchedModuleCount");
        EvidenceValidation.requirePositive(expectedModuleCount, "expectedModuleCount");
        if (matchedModuleCount > expectedModuleCount) {
            throw new IllegalArgumentException("matchedModuleCount must not exceed expectedModuleCount");
        }
        EvidenceValidation.requireUnitScore(confidence, "confidence");
        EvidenceValidation.requireNonNegativeFinite(scale, "scale");
        EvidenceValidation.requireFinite(rotationDegrees, "rotationDegrees");
        Objects.requireNonNull(observationSource, "observationSource must not be null");
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}


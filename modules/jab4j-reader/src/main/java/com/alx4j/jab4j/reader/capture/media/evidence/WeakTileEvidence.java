package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostic summary for weak source-space module evidence inside one logical tile region.
 *
 * @param tileIndex zero-based logical tile index
 * @param moduleStartX first module x coordinate covered by this tile
 * @param moduleStartY first module y coordinate covered by this tile
 * @param moduleWidth tile width in modules
 * @param moduleHeight tile height in modules
 * @param moduleCount total modules covered by the tile region
 * @param weakModuleCount modules that were not strong enough for confident tile construction
 * @param ambiguousModuleCount ambiguous sampled modules in the tile region
 * @param unreadableModuleCount unreadable sampled modules in the tile region
 * @param clippedModuleCount clipped modules in the tile region
 * @param outOfBoundsModuleCount out-of-bounds modules in the tile region
 * @param poorFootprintModuleCount modules below the footprint-quality threshold
 * @param weakModuleDensity weak modules divided by total modules
 * @param minimumModuleConfidence minimum module confidence observed in this tile region
 * @param minimumGeometryFootprintQuality minimum geometry footprint quality observed in this tile region
 * @param tileDecodeAttempted whether this tile region was passed to tile/envelope validation
 * @param acceptedPayload whether strict downstream validation accepted this tile payload
 * @param failureStages downstream or module-evidence stages explaining why the tile did not validate
 * @param reasonCodes stable reason codes for weak modules and downstream rejection
 */
public record WeakTileEvidence(
        int tileIndex,
        int moduleStartX,
        int moduleStartY,
        int moduleWidth,
        int moduleHeight,
        int moduleCount,
        int weakModuleCount,
        int ambiguousModuleCount,
        int unreadableModuleCount,
        int clippedModuleCount,
        int outOfBoundsModuleCount,
        int poorFootprintModuleCount,
        double weakModuleDensity,
        double minimumModuleConfidence,
        double minimumGeometryFootprintQuality,
        boolean tileDecodeAttempted,
        boolean acceptedPayload,
        List<String> failureStages,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated weak-tile diagnostic evidence.
     */
    public WeakTileEvidence {
        EvidenceValidation.requireNonNegative(tileIndex, "tileIndex");
        EvidenceValidation.requireNonNegative(moduleStartX, "moduleStartX");
        EvidenceValidation.requireNonNegative(moduleStartY, "moduleStartY");
        EvidenceValidation.requirePositive(moduleWidth, "moduleWidth");
        EvidenceValidation.requirePositive(moduleHeight, "moduleHeight");
        EvidenceValidation.requirePositive(moduleCount, "moduleCount");
        EvidenceValidation.requireNonNegative(weakModuleCount, "weakModuleCount");
        EvidenceValidation.requireNonNegative(ambiguousModuleCount, "ambiguousModuleCount");
        EvidenceValidation.requireNonNegative(unreadableModuleCount, "unreadableModuleCount");
        EvidenceValidation.requireNonNegative(clippedModuleCount, "clippedModuleCount");
        EvidenceValidation.requireNonNegative(outOfBoundsModuleCount, "outOfBoundsModuleCount");
        EvidenceValidation.requireNonNegative(poorFootprintModuleCount, "poorFootprintModuleCount");
        if (weakModuleCount > moduleCount
                || ambiguousModuleCount > moduleCount
                || unreadableModuleCount > moduleCount
                || clippedModuleCount > moduleCount
                || outOfBoundsModuleCount > moduleCount
                || poorFootprintModuleCount > moduleCount) {
            throw new IllegalArgumentException("weak tile counts must not exceed moduleCount");
        }
        EvidenceValidation.requireUnitScore(weakModuleDensity, "weakModuleDensity");
        EvidenceValidation.requireUnitScore(minimumModuleConfidence, "minimumModuleConfidence");
        EvidenceValidation.requireUnitScore(minimumGeometryFootprintQuality, "minimumGeometryFootprintQuality");
        failureStages = EvidenceValidation.copyTextList(
                Objects.requireNonNull(failureStages, "failureStages must not be null"),
                "failureStages"
        );
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}

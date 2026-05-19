package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable candidate-scoped source-space module sampling evidence.
 *
 * @param schemaVersion evidence schema version
 * @param candidateId sampling-scoped candidate identity
 * @param status source-space sampling status
 * @param geometryCandidateId geometry candidate ID used for source-space projection
 * @param layoutProfileId layout profile used for the module lattice
 * @param moduleWidth expected lattice width in modules
 * @param moduleHeight expected lattice height in modules
 * @param coordinateSystem canonical coordinate system used by the lattice
 * @param geometrySource stable geometry source label
 * @param centralScale central module-region sampling scale
 * @param aggregationMethod robust color aggregation method
 * @param centralRegionPolicy stable central-region sampling policy label
 * @param totalModuleCount total modules evaluated
 * @param sampledModuleCount modules with usable sample evidence
 * @param readableModuleCount modules classified as readable
 * @param ambiguousModuleCount modules classified as ambiguous
 * @param unreadableModuleCount modules classified as unreadable
 * @param clippedModuleCount modules clipped by source boundaries
 * @param outOfBoundsModuleCount modules outside source boundaries
 * @param confidenceMarginSummary bounded confidence-margin metrics
 * @param colorVarianceSummary bounded color-variance metrics
 * @param moduleConfidenceSummary bounded combined module-confidence metrics
 * @param geometryFootprintQualitySummary bounded source-footprint quality metrics
 * @param weakModuleCount modules sampled but not strong enough for confident tile construction
 * @param poorFootprintModuleCount modules whose geometry footprint quality is below the readable threshold
 * @param observedPaletteEvidence observed palette extraction and safety evidence for this sampling candidate
 * @param tileDecodeAttempted whether sampled modules were sent to the existing tile decoder
 * @param tileDecodeAttemptCount number of logical tile candidates sent to the existing tile decoder
 * @param acceptedPayloadCount number of tile payloads accepted by tile, envelope, and slot validation
 * @param weakTileEvidence bounded weak-tile diagnostics for logical tile regions
 * @param tileDecodeFailureStages downstream validation stages that rejected attempted candidates
 * @param modules bounded per-module evidence details
 * @param reasonCodes sampling-stage reason codes
 */
public record ModuleSamplingEvidence(
        int schemaVersion,
        CaptureMediaCandidateId candidateId,
        ModuleSamplingStatus status,
        String geometryCandidateId,
        String layoutProfileId,
        int moduleWidth,
        int moduleHeight,
        String coordinateSystem,
        String geometrySource,
        double centralScale,
        ModuleSamplingAggregationMethod aggregationMethod,
        String centralRegionPolicy,
        int totalModuleCount,
        int sampledModuleCount,
        int readableModuleCount,
        int ambiguousModuleCount,
        int unreadableModuleCount,
        int clippedModuleCount,
        int outOfBoundsModuleCount,
        Map<String, Double> confidenceMarginSummary,
        Map<String, Double> colorVarianceSummary,
        Map<String, Double> moduleConfidenceSummary,
        Map<String, Double> geometryFootprintQualitySummary,
        int weakModuleCount,
        int poorFootprintModuleCount,
        ObservedPaletteEvidence observedPaletteEvidence,
        boolean tileDecodeAttempted,
        int tileDecodeAttemptCount,
        int acceptedPayloadCount,
        List<WeakTileEvidence> weakTileEvidence,
        List<String> tileDecodeFailureStages,
        List<ModuleEvidence> modules,
        List<CaptureMediaEvidenceReasonCode> reasonCodes
) {

    /**
     * Creates validated source-space sampling evidence.
     *
     * @param schemaVersion evidence schema version
     * @param candidateId sampling-scoped candidate identity
     * @param status sampling status
     * @param geometryCandidateId geometry candidate ID
     * @param layoutProfileId layout profile ID
     * @param moduleWidth lattice width in modules
     * @param moduleHeight lattice height in modules
     * @param coordinateSystem canonical coordinate system label
     * @param geometrySource geometry source label
     * @param centralScale central sampling scale
     * @param aggregationMethod aggregation method
     * @param centralRegionPolicy central region policy
     * @param totalModuleCount total module count
     * @param sampledModuleCount sampled module count
     * @param readableModuleCount readable module count
     * @param ambiguousModuleCount ambiguous module count
     * @param unreadableModuleCount unreadable module count
     * @param clippedModuleCount clipped module count
     * @param outOfBoundsModuleCount out-of-bounds module count
     * @param confidenceMarginSummary confidence-margin summary metrics
     * @param colorVarianceSummary color-variance summary metrics
     * @param moduleConfidenceSummary module-confidence summary metrics
     * @param geometryFootprintQualitySummary geometry-footprint quality summary metrics
     * @param weakModuleCount weak module count
     * @param poorFootprintModuleCount poor-footprint module count
     * @param observedPaletteEvidence observed palette evidence
     * @param tileDecodeAttempted whether tile decode was attempted
     * @param tileDecodeAttemptCount tile decode attempt count
     * @param acceptedPayloadCount accepted payload count
     * @param weakTileEvidence weak-tile diagnostics
     * @param tileDecodeFailureStages downstream failure stages
     * @param modules bounded module details
     * @param reasonCodes sampling-stage reason codes
     */
    public ModuleSamplingEvidence {
        EvidenceValidation.requirePositive(schemaVersion, "schemaVersion");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        String chainGeometryId = candidateId.geometryCandidateId()
                .orElseThrow(() -> new IllegalArgumentException("candidateId must include a geometry candidate ID"));
        if (candidateId.samplingCandidateId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must be sampling-scoped");
        }
        Objects.requireNonNull(status, "status must not be null");
        geometryCandidateId = EvidenceValidation.requireText(geometryCandidateId, "geometryCandidateId");
        if (!geometryCandidateId.equals(chainGeometryId)) {
            throw new IllegalArgumentException("geometryCandidateId must match candidateId geometryCandidateId");
        }
        layoutProfileId = EvidenceValidation.requireText(layoutProfileId, "layoutProfileId");
        EvidenceValidation.requirePositive(moduleWidth, "moduleWidth");
        EvidenceValidation.requirePositive(moduleHeight, "moduleHeight");
        coordinateSystem = EvidenceValidation.requireText(coordinateSystem, "coordinateSystem");
        geometrySource = EvidenceValidation.requireText(geometrySource, "geometrySource");
        EvidenceValidation.requireUnitScore(centralScale, "centralScale");
        Objects.requireNonNull(aggregationMethod, "aggregationMethod must not be null");
        centralRegionPolicy = EvidenceValidation.requireText(centralRegionPolicy, "centralRegionPolicy");
        EvidenceValidation.requireNonNegative(totalModuleCount, "totalModuleCount");
        EvidenceValidation.requireNonNegative(sampledModuleCount, "sampledModuleCount");
        EvidenceValidation.requireNonNegative(readableModuleCount, "readableModuleCount");
        EvidenceValidation.requireNonNegative(ambiguousModuleCount, "ambiguousModuleCount");
        EvidenceValidation.requireNonNegative(unreadableModuleCount, "unreadableModuleCount");
        EvidenceValidation.requireNonNegative(clippedModuleCount, "clippedModuleCount");
        EvidenceValidation.requireNonNegative(outOfBoundsModuleCount, "outOfBoundsModuleCount");
        int expectedTotal = Math.multiplyExact(moduleWidth, moduleHeight);
        if (totalModuleCount != expectedTotal) {
            throw new IllegalArgumentException("totalModuleCount must equal moduleWidth * moduleHeight");
        }
        int classifiedTotal = readableModuleCount
                + ambiguousModuleCount
                + unreadableModuleCount
                + clippedModuleCount
                + outOfBoundsModuleCount;
        if (sampledModuleCount > totalModuleCount || classifiedTotal > totalModuleCount) {
            throw new IllegalArgumentException("module counts must not exceed totalModuleCount");
        }
        confidenceMarginSummary = EvidenceValidation.copyMetricMap(
                confidenceMarginSummary,
                "confidenceMarginSummary"
        );
        colorVarianceSummary = EvidenceValidation.copyMetricMap(colorVarianceSummary, "colorVarianceSummary");
        moduleConfidenceSummary = EvidenceValidation.copyMetricMap(
                moduleConfidenceSummary,
                "moduleConfidenceSummary"
        );
        geometryFootprintQualitySummary = EvidenceValidation.copyMetricMap(
                geometryFootprintQualitySummary,
                "geometryFootprintQualitySummary"
        );
        EvidenceValidation.requireNonNegative(weakModuleCount, "weakModuleCount");
        EvidenceValidation.requireNonNegative(poorFootprintModuleCount, "poorFootprintModuleCount");
        if (weakModuleCount > totalModuleCount || poorFootprintModuleCount > totalModuleCount) {
            throw new IllegalArgumentException("weak module counts must not exceed totalModuleCount");
        }
        Objects.requireNonNull(observedPaletteEvidence, "observedPaletteEvidence must not be null");
        if (!observedPaletteEvidence.candidateId().equals(candidateId)) {
            throw new IllegalArgumentException("observedPaletteEvidence candidateId must match sampling candidateId");
        }
        EvidenceValidation.requireNonNegative(tileDecodeAttemptCount, "tileDecodeAttemptCount");
        EvidenceValidation.requireNonNegative(acceptedPayloadCount, "acceptedPayloadCount");
        if (!tileDecodeAttempted && tileDecodeAttemptCount != 0) {
            throw new IllegalArgumentException("tileDecodeAttemptCount must be zero when tileDecodeAttempted is false");
        }
        if (tileDecodeAttempted && tileDecodeAttemptCount == 0) {
            throw new IllegalArgumentException("tileDecodeAttemptCount must be positive when tileDecodeAttempted is true");
        }
        if (acceptedPayloadCount > tileDecodeAttemptCount) {
            throw new IllegalArgumentException("acceptedPayloadCount must not exceed tileDecodeAttemptCount");
        }
        weakTileEvidence = EvidenceValidation.copyList(weakTileEvidence, "weakTileEvidence");
        if (weakTileEvidence.size() > Math.max(1, totalModuleCount)) {
            throw new IllegalArgumentException("weakTileEvidence must be bounded by totalModuleCount");
        }
        tileDecodeFailureStages = EvidenceValidation.copyTextList(
                tileDecodeFailureStages,
                "tileDecodeFailureStages"
        );
        if (!tileDecodeAttempted && !tileDecodeFailureStages.isEmpty()) {
            throw new IllegalArgumentException("tileDecodeFailureStages must be empty when tile decode was not attempted");
        }
        if (tileDecodeFailureStages.size() > tileDecodeAttemptCount) {
            throw new IllegalArgumentException("tileDecodeFailureStages must not exceed tileDecodeAttemptCount");
        }
        modules = EvidenceValidation.copyList(modules, "modules");
        if (modules.size() > totalModuleCount) {
            throw new IllegalArgumentException("modules must not exceed totalModuleCount");
        }
        reasonCodes = EvidenceValidation.copyReasonCodes(reasonCodes, "reasonCodes");
    }
}

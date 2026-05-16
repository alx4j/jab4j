package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Backend-neutral sampling evidence for module-grid phase, tile-slot offsets, and local color references.
 *
 * @param backendId stable evidence backend identifier
 * @param gridPhase candidate frame-level grid phase evidence, when available
 * @param tileEvidence per-tile slot sampling evidence
 * @param metrics frame-level evidence metrics
 */
public record CvSamplingEvidence(
        String backendId,
        Optional<CvGridPhase> gridPhase,
        List<CvTileSamplingEvidence> tileEvidence,
        Map<String, Double> metrics
) {

    /**
     * Creates validated sampling evidence.
     *
     * @param backendId stable evidence backend identifier
     * @param gridPhase optional frame-level grid phase evidence
     * @param tileEvidence per-tile slot evidence
     * @param metrics frame-level evidence metrics
     */
    public CvSamplingEvidence {
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId must not be blank");
        }
        gridPhase = Objects.requireNonNull(gridPhase, "gridPhase must not be null");
        tileEvidence = List.copyOf(Objects.requireNonNull(tileEvidence, "tileEvidence must not be null"));
        if (tileEvidence.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tileEvidence must not contain null values");
        }
        requireUniqueTileIndexes(tileEvidence);
        metrics = copyMetrics(metrics);
    }

    /**
     * Returns an empty evidence model for a backend that did not measure sampling evidence.
     *
     * @param backendId stable backend identifier
     * @return empty sampling evidence
     */
    public static CvSamplingEvidence none(String backendId) {
        return new CvSamplingEvidence(backendId, Optional.empty(), List.of(), Map.of());
    }

    /**
     * Returns true when any actionable grid or tile evidence is present.
     *
     * @return whether evidence is available
     */
    public boolean available() {
        return gridPhase.isPresent() || !tileEvidence.isEmpty() || !metrics.isEmpty();
    }

    /**
     * Returns the strongest confidence score available from grid-phase or tile-slot evidence.
     *
     * @return strongest evidence confidence, or zero when no confidence was measured
     */
    public double confidence() {
        double best = gridPhase.map(CvGridPhase::confidence).orElse(0.0d);
        for (CvTileSamplingEvidence evidence : tileEvidence) {
            best = Math.max(best, evidence.confidence());
        }
        return best;
    }

    /**
     * Returns the frame-level grid-phase horizontal offset, or zero when not measured.
     *
     * @return horizontal grid-phase offset
     */
    public double gridPhaseOffsetXPx() {
        return gridPhase.map(CvGridPhase::offsetXPx).orElse(0.0d);
    }

    /**
     * Returns the frame-level grid-phase vertical offset, or zero when not measured.
     *
     * @return vertical grid-phase offset
     */
    public double gridPhaseOffsetYPx() {
        return gridPhase.map(CvGridPhase::offsetYPx).orElse(0.0d);
    }

    /**
     * Returns a frame-level module-center horizontal offset derived from tile evidence when available.
     *
     * @return horizontal module-center offset
     */
    public double moduleCenterOffsetXPx() {
        return tileEvidence.stream()
                .mapToDouble(CvTileSamplingEvidence::moduleCenterOffsetXPx)
                .average()
                .orElse(gridPhaseOffsetXPx());
    }

    /**
     * Returns a frame-level module-center vertical offset derived from tile evidence when available.
     *
     * @return vertical module-center offset
     */
    public double moduleCenterOffsetYPx() {
        return tileEvidence.stream()
                .mapToDouble(CvTileSamplingEvidence::moduleCenterOffsetYPx)
                .average()
                .orElse(gridPhaseOffsetYPx());
    }

    /**
     * Finds sampling evidence for one rendered tile slot.
     *
     * @param tileIndex zero-based tile slot index
     * @return tile evidence, when available
     */
    public Optional<CvTileSamplingEvidence> tileEvidence(int tileIndex) {
        if (tileIndex < 0) {
            throw new IllegalArgumentException("tileIndex must be non-negative");
        }
        return tileEvidence.stream()
                .filter(evidence -> evidence.tileIndex() == tileIndex)
                .findFirst();
    }

    private static void requireUniqueTileIndexes(List<CvTileSamplingEvidence> tileEvidence) {
        Set<Integer> seen = new HashSet<>();
        for (CvTileSamplingEvidence evidence : tileEvidence) {
            if (!seen.add(evidence.tileIndex())) {
                throw new IllegalArgumentException("tileEvidence must not contain duplicate tileIndex values");
            }
        }
    }

    private static Map<String, Double> copyMetrics(Map<String, Double> metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        Map<String, Double> copied = new LinkedHashMap<>();
        metrics.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("metric names must not be blank");
            }
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("metric values must be finite");
            }
            copied.put(name, value);
        });
        return Map.copyOf(copied);
    }
}

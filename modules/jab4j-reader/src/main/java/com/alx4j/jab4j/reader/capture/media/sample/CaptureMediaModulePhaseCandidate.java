package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.Objects;
import java.util.Optional;

/**
 * One generated module phase candidate for a tile slot and side version.
 *
 * @param ordinal deterministic generation order used as the final ranking tie-breaker
 * @param slotIndex zero-based rendered tile slot index
 * @param sideVersion JAB side version for the candidate tile
 * @param source candidate source category
 * @param geometry concrete sampling geometry
 * @param evidence external evidence used as the candidate base, when applicable
 */
public record CaptureMediaModulePhaseCandidate(
        int ordinal,
        int slotIndex,
        int sideVersion,
        CaptureMediaModulePhaseCandidateSource source,
        CaptureMediaModulePhaseGeometry geometry,
        Optional<CaptureMediaModulePhaseEvidence> evidence
) {

    /**
     * Creates a validated phase candidate.
     *
     * @param ordinal deterministic generation order
     * @param slotIndex zero-based rendered tile slot index
     * @param sideVersion JAB side version
     * @param source candidate source category
     * @param geometry concrete sampling geometry
     * @param evidence external evidence used as the candidate base
     */
    public CaptureMediaModulePhaseCandidate {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be non-negative");
        }
        if (sideVersion <= 0) {
            throw new IllegalArgumentException("sideVersion must be positive");
        }
        source = Objects.requireNonNull(source, "source must not be null");
        geometry = Objects.requireNonNull(geometry, "geometry must not be null");
        evidence = Objects.requireNonNull(evidence, "evidence must not be null");
        if (source == CaptureMediaModulePhaseCandidateSource.CV_EVIDENCE && evidence.isEmpty()) {
            throw new IllegalArgumentException("CV_EVIDENCE candidates must include evidence");
        }
        if (source == CaptureMediaModulePhaseCandidateSource.NOMINAL && evidence.isPresent()) {
            throw new IllegalArgumentException("NOMINAL candidates must not include evidence");
        }
    }

    /**
     * Returns true when this candidate was derived from external evidence.
     *
     * @return whether external evidence was used as a base offset
     */
    public boolean evidenceDerived() {
        return evidence.isPresent();
    }

    /**
     * Returns true when this candidate shifts the nominal center or module size.
     *
     * @return whether center or scale phase differs from the nominal geometry
     */
    public boolean shifted() {
        return geometry.centerShifted() || geometry.scaleShifted();
    }
}

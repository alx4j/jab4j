package com.alx4j.jab4j.reader.capture.media.sample;

/**
 * Reader-owned outcome stage for a module phase sampling attempt.
 */
public enum CaptureMediaModulePhaseOutcome {
    /**
     * Payload passed tile decode, envelope CRC, and slot validation.
     */
    ACCEPTED_PAYLOAD(true, true, 4),

    /**
     * Tile and envelope checks passed, but slot validation rejected the payload.
     */
    SLOT_VALIDATION_FAILURE(false, true, 3),

    /**
     * Tile decode succeeded, but envelope parsing or CRC validation failed.
     */
    ENVELOPE_VALIDATION_FAILURE(false, true, 2),

    /**
     * Palette and finder checks reached tile decode, but tile decode failed.
     */
    TILE_DECODE_FAILURE(false, true, 1),

    /**
     * Palette and finder checks reached post-palette validation, but an unexpected reader-owned failure occurred.
     */
    UNEXPECTED_POST_PALETTE_FAILURE(false, true, 1),

    /**
     * Palette or finder sampling failed before tile decode was attempted.
     */
    FINDER_OR_PALETTE_FAILURE(false, false, 0),

    /**
     * Candidate was generated but no sampling attempt was completed.
     */
    NOT_EVALUATED(false, false, 0);

    private final boolean acceptedPayload;
    private final boolean tileDecodeAttempted;
    private final int protocolProgressRank;

    CaptureMediaModulePhaseOutcome(
            boolean acceptedPayload,
            boolean tileDecodeAttempted,
            int protocolProgressRank
    ) {
        this.acceptedPayload = acceptedPayload;
        this.tileDecodeAttempted = tileDecodeAttempted;
        this.protocolProgressRank = protocolProgressRank;
    }

    /**
     * Returns whether this outcome contains an accepted payload.
     *
     * @return true when the payload passed all reader-owned gates
     */
    public boolean acceptedPayload() {
        return acceptedPayload;
    }

    /**
     * Returns whether the attempt reached tile decode or a later validation gate.
     *
     * @return true when post-palette validation was attempted
     */
    public boolean tileDecodeAttempted() {
        return tileDecodeAttempted;
    }

    /**
     * Returns a deterministic progress rank for comparing failed attempts.
     *
     * @return higher value for later reader-owned validation stages
     */
    public int protocolProgressRank() {
        return protocolProgressRank;
    }
}

package com.alx4j.jab4j.player.core;

import com.alx4j.jab4j.api.model.FrameType;

/**
 * Deterministic playback progress for one presentation step.
 *
 * @param presentationIndex zero-based presentation index after hold-count expansion
 * @param totalPresentations total presentation count after hold-count expansion
 * @param sourceFrameIndex source frame index from the prepared frame list
 * @param frameType prepared frame type
 * @param holdIteration zero-based hold iteration for the source frame
 * @param holdIterationsPerFrame total hold iterations per source frame
 */
public record PlaybackProgress(
        long presentationIndex,
        long totalPresentations,
        long sourceFrameIndex,
        FrameType frameType,
        int holdIteration,
        int holdIterationsPerFrame
) {

    /**
     * Creates a validated playback progress event.
     *
     * @param presentationIndex zero-based presentation index
     * @param totalPresentations total presentation count
     * @param sourceFrameIndex source frame index
     * @param frameType frame type
     * @param holdIteration zero-based hold iteration
     * @param holdIterationsPerFrame total hold iterations
     */
    public PlaybackProgress {
        if (presentationIndex < 0 || totalPresentations < 0 || sourceFrameIndex < 0) {
            throw new PlaybackException("playback indexes must be non-negative");
        }
        if (frameType == null) {
            throw new PlaybackException("frameType must not be null");
        }
        if (holdIteration < 0 || holdIterationsPerFrame <= 0) {
            throw new PlaybackException("hold iteration values must be positive");
        }
    }
}

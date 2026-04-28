package com.alx4j.jab4j.player.core;

/**
 * Metadata for one playback underrun event.
 *
 * @param presentationIndex zero-based presentation index
 * @param frameIndex source frame index
 * @param overrunNanos amount by which presentation work exceeded the frame budget
 */
public record PlaybackUnderrun(long presentationIndex, long frameIndex, long overrunNanos) {

    /**
     * Creates a validated underrun record.
     *
     * @param presentationIndex zero-based presentation index
     * @param frameIndex source frame index
     * @param overrunNanos overrun amount in nanoseconds
     */
    public PlaybackUnderrun {
        if (presentationIndex < 0 || frameIndex < 0 || overrunNanos < 0) {
            throw new PlaybackException("underrun values must be non-negative");
        }
    }
}

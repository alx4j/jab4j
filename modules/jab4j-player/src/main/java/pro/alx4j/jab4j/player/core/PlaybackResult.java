package pro.alx4j.jab4j.player.core;

/**
 * Summary for one deterministic playback run.
 *
 * @param displayedPresentationCount total number of presented frames after hold-count expansion
 * @param sourceFrameCount number of prepared source frames
 * @param underrunCount number of detected underruns
 * @param initialPreloadCount number of frames preloaded into the bounded buffer before playback started
 * @param stoppedEarly whether playback ended because stop was requested before the full plan completed
 * @param totalDurationNanos playback duration in nanoseconds
 */
public record PlaybackResult(
        long displayedPresentationCount,
        long sourceFrameCount,
        long underrunCount,
        int initialPreloadCount,
        boolean stoppedEarly,
        long totalDurationNanos
) {

    /**
     * Creates a validated playback result.
     *
     * @param displayedPresentationCount displayed presentation count
     * @param sourceFrameCount source frame count
     * @param underrunCount underrun count
     * @param initialPreloadCount initial preload count
     * @param stoppedEarly whether playback stopped early
     * @param totalDurationNanos total playback duration in nanoseconds
     */
    public PlaybackResult {
        if (displayedPresentationCount < 0 || sourceFrameCount < 0 || underrunCount < 0 || totalDurationNanos < 0) {
            throw new PlaybackException("playback result values must be non-negative");
        }
        if (initialPreloadCount < 0) {
            throw new PlaybackException("initialPreloadCount must be non-negative");
        }
    }
}

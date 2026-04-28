package pro.alx4j.jab4j.api.model;

/**
 * Playback profile values that control deterministic player timing.
 *
 * @param profileId stable playback profile identifier
 * @param fps frames per second
 * @param holdFrames hold count per frame
 * @param warmupSyncFrames number of warmup sync frames
 * @param endFrames number of terminal end frames
 * @param fullscreen whether fullscreen is requested
 */
public record PlaybackProfile(
        String profileId,
        int fps,
        int holdFrames,
        int warmupSyncFrames,
        int endFrames,
        boolean fullscreen
) {

    /**
     * Creates a validated playback profile.
     *
     * @param profileId stable playback profile identifier
     * @param fps frames per second
     * @param holdFrames hold count per frame
     * @param warmupSyncFrames number of warmup sync frames
     * @param endFrames number of terminal end frames
     * @param fullscreen whether fullscreen is requested
     */
    public PlaybackProfile {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        if (fps <= 0) {
            throw new IllegalArgumentException("fps must be positive");
        }
        if (holdFrames < 0 || warmupSyncFrames < 0 || endFrames < 0) {
            throw new IllegalArgumentException("frame counters must be non-negative");
        }
    }
}

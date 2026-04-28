package pro.alx4j.jab4j.player.core;

/**
 * Observer hooks for deterministic playback progress and underrun reporting.
 */
public interface PlaybackObserver {

    /**
     * Called after one presentation step completes.
     *
     * @param progress deterministic playback progress
     */
    default void onProgress(PlaybackProgress progress) {
    }

    /**
     * Called after one frame presentation and before pacing sleep is applied.
     *
     * @param progress deterministic playback progress
     * @param renderDurationNanos presentation duration in nanoseconds
     */
    default void onFramePresented(PlaybackProgress progress, long renderDurationNanos) {
    }

    /**
     * Called when presentation work overruns the frame budget.
     *
     * @param underrun underrun metadata
     */
    default void onUnderrun(PlaybackUnderrun underrun) {
    }

    /**
     * Returns a no-op observer.
     *
     * @return no-op observer
     */
    static PlaybackObserver noOp() {
        return new PlaybackObserver() {
        };
    }
}

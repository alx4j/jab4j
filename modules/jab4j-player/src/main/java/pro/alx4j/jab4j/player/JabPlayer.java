package pro.alx4j.jab4j.player;

import java.util.List;
import java.util.Objects;
import pro.alx4j.jab4j.api.model.PlaybackProfile;
import pro.alx4j.jab4j.player.core.DeterministicPlaybackEngine;
import pro.alx4j.jab4j.player.core.PlaybackControl;
import pro.alx4j.jab4j.player.core.PlaybackObserver;
import pro.alx4j.jab4j.player.core.PlaybackResult;
import pro.alx4j.jab4j.render.frame.RenderedFrame;

/**
 * Client-facing entry point for playing prepared JAB frames.
 */
public final class JabPlayer {

    private JabPlayer() {
    }

    /**
     * Plays prepared frames with the supplied playback profile.
     *
     * @param preparedFrames prepared full-frame rasters
     * @param playbackProfile playback timing profile
     * @return deterministic playback result
     */
    public static PlaybackResult play(List<RenderedFrame> preparedFrames, PlaybackProfile playbackProfile) {
        return playback(preparedFrames, playbackProfile).run();
    }

    /**
     * Starts a playback builder for prepared frames and a playback profile.
     *
     * @param preparedFrames prepared full-frame rasters
     * @param playbackProfile playback timing profile
     * @return playback builder
     */
    public static Builder playback(List<RenderedFrame> preparedFrames, PlaybackProfile playbackProfile) {
        return new Builder(new DeterministicPlaybackEngine(), preparedFrames, playbackProfile);
    }

    /**
     * Fluent builder for one playback run.
     */
    public static final class Builder {

        private final DeterministicPlaybackEngine playbackEngine;
        private final List<RenderedFrame> preparedFrames;
        private final PlaybackProfile playbackProfile;
        private boolean dryRun;
        private PlaybackObserver observer = PlaybackObserver.noOp();
        private PlaybackControl control = new PlaybackControl();

        private Builder(
                DeterministicPlaybackEngine playbackEngine,
                List<RenderedFrame> preparedFrames,
                PlaybackProfile playbackProfile
        ) {
            this.playbackEngine = Objects.requireNonNull(playbackEngine, "playbackEngine must not be null");
            this.preparedFrames = List.copyOf(Objects.requireNonNull(preparedFrames, "preparedFrames must not be null"));
            this.playbackProfile = Objects.requireNonNull(playbackProfile, "playbackProfile must not be null");
        }

        /**
         * Enables dry-run playback.
         *
         * @return this builder
         */
        public Builder dryRun() {
            return dryRun(true);
        }

        /**
         * Controls whether playback should bypass display output.
         *
         * @param dryRun dry-run flag
         * @return this builder
         */
        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        /**
         * Receives playback progress and underrun callbacks.
         *
         * @param observer playback observer, or {@code null} for no callbacks
         * @return this builder
         */
        public Builder observer(PlaybackObserver observer) {
            this.observer = observer == null ? PlaybackObserver.noOp() : observer;
            return this;
        }

        /**
         * Uses caller-owned playback controls for pause, resume, and stop.
         *
         * @param control playback control, or {@code null} for a new control
         * @return this builder
         */
        public Builder control(PlaybackControl control) {
            this.control = control == null ? new PlaybackControl() : control;
            return this;
        }

        /**
         * Runs playback.
         *
         * @return deterministic playback result
         */
        public PlaybackResult run() {
            return playbackEngine.play(preparedFrames, playbackProfile, dryRun, observer, control);
        }
    }
}

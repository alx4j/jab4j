package com.alx4j.jab4j.player.core;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.PlaybackProfile;
import com.alx4j.jab4j.render.frame.RenderedFrame;

/**
 * Deterministic player for prepared full-frame rasters with hold-count pacing and dry-run support.
 */
public final class DeterministicPlaybackEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeterministicPlaybackEngine.class);
    private static final int DEFAULT_PRELOAD_CAPACITY = 8;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final PlaybackClock clock;
    private final DisplaySurfaceFactory displaySurfaceFactory;
    private final int preloadCapacity;

    /**
     * Creates a player that uses the system clock and the internal AWT display adapter.
     */
    public DeterministicPlaybackEngine() {
        this(new SystemPlaybackClock(), new AwtDisplaySurfaceFactory(), DEFAULT_PRELOAD_CAPACITY);
    }

    DeterministicPlaybackEngine(
            PlaybackClock clock,
            DisplaySurfaceFactory displaySurfaceFactory,
            int preloadCapacity
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.displaySurfaceFactory = Objects.requireNonNull(displaySurfaceFactory, "displaySurfaceFactory must not be null");
        if (preloadCapacity <= 0) {
            throw new PlaybackException("preloadCapacity must be positive");
        }
        this.preloadCapacity = preloadCapacity;
    }

    /**
     * Plays prepared frames with deterministic order and profile-driven pacing.
     *
     * @param preparedFrames immutable prepared full-frame rasters
     * @param playbackProfile playback profile with pacing and fullscreen values
     * @param dryRun whether to bypass actual display presentation
     * @param observer playback observer hooks
     * @param control pause, resume, and stop controls
     * @return deterministic playback result summary
     */
    public PlaybackResult play(
            List<RenderedFrame> preparedFrames,
            PlaybackProfile playbackProfile,
            boolean dryRun,
            PlaybackObserver observer,
            PlaybackControl control
    ) {
        List<RenderedFrame> frames = List.copyOf(Objects.requireNonNull(preparedFrames, "preparedFrames must not be null"));
        if (frames.isEmpty()) {
            throw new PlaybackException("preparedFrames must not be empty");
        }
        playbackProfile = Objects.requireNonNull(playbackProfile, "playbackProfile must not be null");
        PlaybackObserver effectiveObserver = observer == null ? PlaybackObserver.noOp() : observer;
        PlaybackControl effectiveControl = control == null ? new PlaybackControl() : control;

        validatePreparedFrames(frames, playbackProfile);

        int holdIterationsPerFrame = Math.max(1, playbackProfile.holdFrames() + 1);
        long frameBudgetNanos = NANOS_PER_SECOND / playbackProfile.fps();
        long totalPresentations = Math.multiplyExact((long) frames.size(), holdIterationsPerFrame);
        PreparedFrameBuffer buffer = new PreparedFrameBuffer(frames, Math.min(preloadCapacity, frames.size()));
        DisplaySurface surface = dryRun ? DryRunDisplaySurface.INSTANCE : displaySurfaceFactory.create(playbackProfile.fullscreen());
        RenderedFrame firstFrame = frames.get(0);
        long startedAtNanos = clock.nanoTime();
        long presentationIndex = 0;
        long underrunCount = 0;
        PlaybackUnderrun firstUnderrun = null;
        boolean stoppedEarly = false;
        boolean surfaceOpened = false;
        PlaybackResult result;
        LOGGER.info(
                "Starting playback profileId={} frameCount={} totalPresentations={} frameSize={}x{} fps={} holdIterationsPerFrame={} warmupSyncFrames={} endFrames={} dryRun={} fullscreen={} preloadCount={}",
                playbackProfile.profileId(),
                frames.size(),
                totalPresentations,
                firstFrame.widthPixels(),
                firstFrame.heightPixels(),
                playbackProfile.fps(),
                holdIterationsPerFrame,
                playbackProfile.warmupSyncFrames(),
                playbackProfile.endFrames(),
                dryRun,
                playbackProfile.fullscreen(),
                buffer.initialPreloadCount()
        );
        try {
            surface.open(firstFrame.widthPixels(), firstFrame.heightPixels(), playbackProfile.fullscreen());
            surfaceOpened = true;
            while (buffer.hasNext() && !effectiveControl.isStopRequested()) {
                RenderedFrame frame = buffer.next();
                for (int holdIteration = 0; holdIteration < holdIterationsPerFrame; holdIteration++) {
                    if (effectiveControl.isStopRequested()) {
                        stoppedEarly = true;
                        break;
                    }
                    effectiveControl.awaitIfPaused();
                    if (effectiveControl.isStopRequested()) {
                        stoppedEarly = true;
                        break;
                    }

                    long presentationStartedAt = clock.nanoTime();
                    surface.present(frame);
                    long renderDurationNanos = clock.nanoTime() - presentationStartedAt;

                    PlaybackProgress progress = new PlaybackProgress(
                            presentationIndex,
                            totalPresentations,
                            frame.frameIndex(),
                            frame.frameType(),
                            holdIteration,
                            holdIterationsPerFrame
                    );
                    effectiveObserver.onProgress(progress);
                    effectiveObserver.onFramePresented(progress, renderDurationNanos);

                    if (renderDurationNanos > frameBudgetNanos) {
                        underrunCount++;
                        PlaybackUnderrun underrun = new PlaybackUnderrun(
                                presentationIndex,
                                frame.frameIndex(),
                                renderDurationNanos - frameBudgetNanos
                        );
                        if (firstUnderrun == null) {
                            firstUnderrun = underrun;
                        }
                        effectiveObserver.onUnderrun(underrun);
                    } else {
                        clock.sleepNanos(frameBudgetNanos - renderDurationNanos);
                    }
                    presentationIndex++;
                }
            }
            if (!stoppedEarly && effectiveControl.isStopRequested()) {
                stoppedEarly = true;
            }
            result = new PlaybackResult(
                    presentationIndex,
                    frames.size(),
                    underrunCount,
                    buffer.initialPreloadCount(),
                    stoppedEarly,
                    clock.nanoTime() - startedAtNanos
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Playback failed profileId={} displayedPresentations={} sourceFrameCount={} underrunCount={} dryRun={} fullscreen={} elapsedNanos={}",
                    playbackProfile.profileId(),
                    presentationIndex,
                    frames.size(),
                    underrunCount,
                    dryRun,
                    playbackProfile.fullscreen(),
                    clock.nanoTime() - startedAtNanos,
                    exception
            );
            throw exception;
        } finally {
            if (surfaceOpened) {
                try {
                    surface.close();
                } catch (RuntimeException closeException) {
                    LOGGER.warn(
                            "Playback surface close failed profileId={} displayedPresentations={} dryRun={} fullscreen={}",
                            playbackProfile.profileId(),
                            presentationIndex,
                            dryRun,
                            playbackProfile.fullscreen(),
                            closeException
                    );
                    throw closeException;
                }
            }
        }
        if (result.underrunCount() > 0 && firstUnderrun != null) {
            LOGGER.warn(
                    "Playback completed with underruns profileId={} displayedPresentations={} sourceFrameCount={} underrunCount={} firstUnderrunFrameIndex={} firstOverrunNanos={}",
                    playbackProfile.profileId(),
                    result.displayedPresentationCount(),
                    result.sourceFrameCount(),
                    result.underrunCount(),
                    firstUnderrun.frameIndex(),
                    firstUnderrun.overrunNanos()
            );
        }
        LOGGER.info(
                "Playback completed profileId={} displayedPresentations={} sourceFrameCount={} underrunCount={} stoppedEarly={} dryRun={} fullscreen={} totalDurationNanos={}",
                playbackProfile.profileId(),
                result.displayedPresentationCount(),
                result.sourceFrameCount(),
                result.underrunCount(),
                result.stoppedEarly(),
                dryRun,
                playbackProfile.fullscreen(),
                result.totalDurationNanos()
        );
        return result;
    }

    private void validatePreparedFrames(List<RenderedFrame> frames, PlaybackProfile playbackProfile) {
        RenderedFrame firstFrame = frames.get(0);
        for (RenderedFrame frame : frames) {
            if (frame.widthPixels() != firstFrame.widthPixels() || frame.heightPixels() != firstFrame.heightPixels()) {
                throw new PlaybackException("prepared frames must share one raster size");
            }
        }

        int requiredWarmupSyncFrames = playbackProfile.warmupSyncFrames();
        if (requiredWarmupSyncFrames > 0) {
            if (frames.size() < requiredWarmupSyncFrames) {
                throw new PlaybackException("prepared frame sequence must start with "
                        + requiredWarmupSyncFrames + " SYNC frames");
            }
            for (int index = 0; index < requiredWarmupSyncFrames; index++) {
                if (frames.get(index).frameType() != FrameType.SYNC) {
                    throw new PlaybackException("prepared frame sequence must start with "
                            + requiredWarmupSyncFrames + " SYNC frames");
                }
            }
        }

        int requiredEndFrames = playbackProfile.endFrames();
        if (requiredEndFrames > 0) {
            if (frames.size() < requiredEndFrames) {
                throw new PlaybackException("prepared frame sequence must end with "
                        + requiredEndFrames + " END frames");
            }
            for (int index = frames.size() - requiredEndFrames; index < frames.size(); index++) {
                if (frames.get(index).frameType() != FrameType.END) {
                    throw new PlaybackException("prepared frame sequence must end with "
                            + requiredEndFrames + " END frames");
                }
            }
        }
    }
}

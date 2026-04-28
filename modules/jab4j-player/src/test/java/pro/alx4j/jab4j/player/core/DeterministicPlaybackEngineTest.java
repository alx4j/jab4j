package pro.alx4j.jab4j.player.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.alx4j.jab4j.api.model.FrameType;
import pro.alx4j.jab4j.api.model.PlaybackProfile;
import pro.alx4j.jab4j.render.frame.RenderedFrame;

@DisplayName("Deterministic playback engine")
class DeterministicPlaybackEngineTest {

    private static final int BUFFER_CAPACITY = 2;

    @Test
    @DisplayName("Prepared frames play in deterministic order with the configured hold count")
    void playsPreparedFramesInDeterministicOrderWithHoldCount() {
        FakePlaybackClock clock = new FakePlaybackClock();
        RecordingDisplaySurface surface = new RecordingDisplaySurface(clock, 0);
        DeterministicPlaybackEngine engine = playbackEngine(clock, fullscreen -> surface);
        PlaybackProfile profile = playbackProfile("baseline", 10, 1, 1, 1, false);

        PlaybackResult result = engine.play(sampleFrames(), profile, false, PlaybackObserver.noOp(), new PlaybackControl());

        assertAll(
                () -> assertEquals(List.of(0L, 0L, 1L, 1L, 2L, 2L), surface.presentedFrameIndexes()),
                () -> assertEquals(6, result.displayedPresentationCount()),
                () -> assertEquals(3, result.sourceFrameCount()),
                () -> assertEquals(0, result.underrunCount()),
                () -> assertFalse(result.stoppedEarly()),
                () -> assertEquals(BUFFER_CAPACITY, result.initialPreloadCount()),
                () -> assertEquals(Duration.ofMillis(600).toNanos(), result.totalDurationNanos())
        );
    }

    @Test
    @DisplayName("Warmup sync and end-frame bursts are validated against the profile")
    void validatesWarmupSyncAndEndFrameBurstsAgainstProfile() {
        DeterministicPlaybackEngine engine = new DeterministicPlaybackEngine(
                new FakePlaybackClock(),
                fullscreen -> new RecordingDisplaySurface(new FakePlaybackClock(), 0),
                BUFFER_CAPACITY
        );
        PlaybackProfile profile = playbackProfile("strict", 10, 0, 2, 1, false);

        PlaybackException exception = assertThrows(
                PlaybackException.class,
                () -> engine.play(sampleFrames(), profile, true, PlaybackObserver.noOp(), new PlaybackControl())
        );

        assertEquals("prepared frame sequence must start with 2 SYNC frames", exception.getMessage());
    }

    @Test
    @DisplayName("Pause and resume gate further presentations")
    void pauseAndResumeGateFurtherPresentations() throws Exception {
        FakePlaybackClock clock = new FakePlaybackClock();
        RecordingDisplaySurface surface = new RecordingDisplaySurface(clock, 0);
        DeterministicPlaybackEngine engine = playbackEngine(clock, fullscreen -> surface);
        PlaybackProfile profile = playbackProfile("baseline", 10, 0, 1, 1, false);
        PlaybackControl control = new PlaybackControl();
        CountDownLatch firstProgress = new CountDownLatch(1);
        CountDownLatch secondProgress = new CountDownLatch(1);
        AtomicInteger progressCount = new AtomicInteger();

        PlaybackObserver observer = new PlaybackObserver() {
            @Override
            public void onProgress(PlaybackProgress progress) {
                int count = progressCount.incrementAndGet();
                if (count == 1) {
                    control.pause();
                    firstProgress.countDown();
                    return;
                }
                if (count == 2) {
                    secondProgress.countDown();
                }
            }
        };

        CompletableFuture<PlaybackResult> future = CompletableFuture.supplyAsync(
                () -> engine.play(sampleFrames(), profile, false, observer, control)
        );

        assertTrue(firstProgress.await(1, TimeUnit.SECONDS));
        assertFalse(secondProgress.await(150, TimeUnit.MILLISECONDS));
        control.resume();
        PlaybackResult result = future.get(1, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals(3, result.displayedPresentationCount()),
                () -> assertEquals(List.of(0L, 1L, 2L), surface.presentedFrameIndexes())
        );
    }

    @Test
    @DisplayName("Stop terminates playback early")
    void stopTerminatesPlaybackEarly() {
        FakePlaybackClock clock = new FakePlaybackClock();
        RecordingDisplaySurface surface = new RecordingDisplaySurface(clock, 0);
        DeterministicPlaybackEngine engine = playbackEngine(clock, fullscreen -> surface);
        PlaybackControl control = new PlaybackControl();
        AtomicInteger progressCount = new AtomicInteger();

        PlaybackObserver observer = new PlaybackObserver() {
            @Override
            public void onProgress(PlaybackProgress progress) {
                if (progressCount.incrementAndGet() == 1) {
                    control.stop();
                }
            }
        };

        PlaybackResult result = engine.play(
                sampleFrames(),
                playbackProfile("baseline", 10, 0, 1, 1, false),
                false,
                observer,
                control
        );

        assertAll(
                () -> assertTrue(result.stoppedEarly()),
                () -> assertEquals(1, result.displayedPresentationCount()),
                () -> assertEquals(List.of(0L), surface.presentedFrameIndexes())
        );
    }

    @Test
    @DisplayName("Dry runs bypass the display surface while preserving playback semantics")
    void dryRunBypassesDisplaySurfaceButKeepsSemantics() {
        FakePlaybackClock clock = new FakePlaybackClock();
        TrackingDisplaySurfaceFactory factory = new TrackingDisplaySurfaceFactory(clock);
        DeterministicPlaybackEngine engine = playbackEngine(clock, factory);

        PlaybackResult result = engine.play(
                sampleFrames(),
                playbackProfile("baseline", 10, 1, 1, 1, true),
                true,
                PlaybackObserver.noOp(),
                new PlaybackControl()
        );

        assertAll(
                () -> assertEquals(0, factory.createCount()),
                () -> assertEquals(6, result.displayedPresentationCount()),
                () -> assertEquals(Duration.ofMillis(600).toNanos(), result.totalDurationNanos())
        );
    }

    @Test
    @DisplayName("Playback reports underruns when render time exceeds the frame budget")
    void reportsUnderrunWhenRenderTimeExceedsFrameBudget() {
        FakePlaybackClock clock = new FakePlaybackClock();
        RecordingDisplaySurface surface = new RecordingDisplaySurface(clock, Duration.ofMillis(150).toNanos());
        DeterministicPlaybackEngine engine = playbackEngine(clock, fullscreen -> surface);
        AtomicInteger underruns = new AtomicInteger();
        AtomicReference<PlaybackUnderrun> firstUnderrun = new AtomicReference<>();

        PlaybackObserver observer = new PlaybackObserver() {
            @Override
            public void onUnderrun(PlaybackUnderrun underrun) {
                underruns.incrementAndGet();
                firstUnderrun.compareAndSet(null, underrun);
            }
        };

        PlaybackResult result = engine.play(
                sampleFrames(),
                playbackProfile("baseline", 10, 0, 1, 1, false),
                false,
                observer,
                new PlaybackControl()
        );

        assertAll(
                () -> assertEquals(3, underruns.get()),
                () -> assertNotNull(firstUnderrun.get()),
                () -> assertEquals(Duration.ofMillis(50).toNanos(), firstUnderrun.get().overrunNanos()),
                () -> assertEquals(3, result.underrunCount())
        );
    }

    private DeterministicPlaybackEngine playbackEngine(FakePlaybackClock clock, DisplaySurfaceFactory surfaceFactory) {
        return new DeterministicPlaybackEngine(clock, surfaceFactory, BUFFER_CAPACITY);
    }

    private PlaybackProfile playbackProfile(
            String profileId,
            int fps,
            int holdFrames,
            int warmupSyncFrames,
            int endFrames,
            boolean fullscreen
    ) {
        return new PlaybackProfile(profileId, fps, holdFrames, warmupSyncFrames, endFrames, fullscreen);
    }

    private List<RenderedFrame> sampleFrames() {
        return List.of(
                renderedFrame(0L, FrameType.SYNC, 0xFF111111),
                renderedFrame(1L, FrameType.DATA, 0xFF222222),
                renderedFrame(2L, FrameType.END, 0xFF333333)
        );
    }

    private RenderedFrame renderedFrame(long frameIndex, FrameType frameType, int color) {
        return new RenderedFrame(
                frameIndex,
                frameType,
                4,
                4,
                List.copyOf(java.util.Collections.nCopies(16, color)),
                Map.of("fixtureFrameType", frameType.name())
        );
    }

    private static final class FakePlaybackClock implements PlaybackClock {

        private long nowNanos;

        @Override
        public long nanoTime() {
            return nowNanos;
        }

        @Override
        public void sleepNanos(long nanos) {
            nowNanos += nanos;
        }

        void advance(long nanos) {
            nowNanos += nanos;
        }
    }

    private static final class RecordingDisplaySurface implements DisplaySurface {

        private final FakePlaybackClock clock;
        private final long renderCostNanos;
        private final List<Long> presentedFrameIndexes = new ArrayList<>();

        private RecordingDisplaySurface(FakePlaybackClock clock, long renderCostNanos) {
            this.clock = clock;
            this.renderCostNanos = renderCostNanos;
        }

        @Override
        public void open(int widthPixels, int heightPixels, boolean fullscreen) {
            assertTrue(widthPixels > 0);
            assertTrue(heightPixels > 0);
        }

        @Override
        public void present(RenderedFrame frame) {
            presentedFrameIndexes.add(frame.frameIndex());
            clock.advance(renderCostNanos);
        }

        @Override
        public void close() {
        }

        List<Long> presentedFrameIndexes() {
            return List.copyOf(presentedFrameIndexes);
        }
    }

    private static final class TrackingDisplaySurfaceFactory implements DisplaySurfaceFactory {

        private final FakePlaybackClock clock;
        private int createCount;

        private TrackingDisplaySurfaceFactory(FakePlaybackClock clock) {
            this.clock = clock;
        }

        @Override
        public DisplaySurface create(boolean fullscreen) {
            createCount++;
            return new RecordingDisplaySurface(clock, 0);
        }

        int createCount() {
            return createCount;
        }
    }
}

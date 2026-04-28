package pro.alx4j.jab4j.player;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.alx4j.jab4j.api.model.FrameType;
import pro.alx4j.jab4j.api.model.PlaybackProfile;
import pro.alx4j.jab4j.player.core.PlaybackObserver;
import pro.alx4j.jab4j.player.core.PlaybackProgress;
import pro.alx4j.jab4j.player.core.PlaybackResult;
import pro.alx4j.jab4j.render.frame.RenderedFrame;

@DisplayName("JAB player facade")
class JabPlayerTest {

    @Test
    @DisplayName("Dry-run playback is available through the client facade")
    void dryRunPlaybackThroughFacade() {
        AtomicInteger progressEvents = new AtomicInteger();
        PlaybackObserver observer = new PlaybackObserver() {
            @Override
            public void onProgress(PlaybackProgress progress) {
                progressEvents.incrementAndGet();
            }
        };

        PlaybackResult result = JabPlayer.playback(sampleFrames(), playbackProfile())
                .dryRun()
                .observer(observer)
                .run();

        assertAll(
                () -> assertEquals(3, result.displayedPresentationCount()),
                () -> assertEquals(3, result.sourceFrameCount()),
                () -> assertFalse(result.stoppedEarly()),
                () -> assertEquals(3, progressEvents.get())
        );
    }

    private PlaybackProfile playbackProfile() {
        return new PlaybackProfile("facade-test", Integer.MAX_VALUE, 0, 1, 1, false);
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
                List.copyOf(Collections.nCopies(16, color)),
                Map.of("fixtureFrameType", frameType.name())
        );
    }
}

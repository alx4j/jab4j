package com.alx4j.jab4j.player.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.render.frame.RenderedFrame;

@DisplayName("Prepared frame buffering")
class PreparedFrameBufferTest {

    @Test
    @DisplayName("Buffers preload the configured window and refill in order")
    void preloadsConfiguredWindowAndRefillsInOrder() {
        PreparedFrameBuffer buffer = new PreparedFrameBuffer(sampleFrames(), 2);

        assertAll(
                () -> assertEquals(2, buffer.initialPreloadCount()),
                () -> assertEquals(List.of(0L, 1L, 2L), drainFrameIndexes(buffer)),
                () -> assertFalse(buffer.hasNext())
        );
    }

    private List<RenderedFrame> sampleFrames() {
        return List.of(
                frame(0L, FrameType.SYNC),
                frame(1L, FrameType.DATA),
                frame(2L, FrameType.END)
        );
    }

    private List<Long> drainFrameIndexes(PreparedFrameBuffer buffer) {
        List<Long> frameIndexes = new ArrayList<>();
        while (buffer.hasNext()) {
            frameIndexes.add(buffer.next().frameIndex());
        }
        return List.copyOf(frameIndexes);
    }

    private RenderedFrame frame(long frameIndex, FrameType frameType) {
        return new RenderedFrame(
                frameIndex,
                frameType,
                2,
                2,
                List.of(0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000),
                Map.of("fixture", frameType.name())
        );
    }
}

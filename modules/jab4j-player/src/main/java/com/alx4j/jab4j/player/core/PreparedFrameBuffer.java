package com.alx4j.jab4j.player.core;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.render.frame.RenderedFrame;

final class PreparedFrameBuffer {

    private final List<RenderedFrame> sourceFrames;
    private final ArrayDeque<RenderedFrame> queue;
    private final int capacity;
    private final int initialPreloadCount;
    private int nextSourceIndex;

    PreparedFrameBuffer(List<RenderedFrame> sourceFrames, int capacity) {
        this.sourceFrames = List.copyOf(Objects.requireNonNull(sourceFrames, "sourceFrames must not be null"));
        if (this.sourceFrames.isEmpty()) {
            throw new PlaybackException("preparedFrames must not be empty");
        }
        if (capacity <= 0) {
            throw new PlaybackException("preload capacity must be positive");
        }
        this.capacity = capacity;
        this.queue = new ArrayDeque<>(Math.min(capacity, this.sourceFrames.size()));
        fillQueue();
        this.initialPreloadCount = queue.size();
    }

    boolean hasNext() {
        return !queue.isEmpty();
    }

    RenderedFrame next() {
        RenderedFrame frame = queue.pollFirst();
        if (frame == null) {
            throw new PlaybackException("no prepared frames remain in the buffer");
        }
        fillQueue();
        return frame;
    }

    int initialPreloadCount() {
        return initialPreloadCount;
    }

    private void fillQueue() {
        while (queue.size() < capacity && nextSourceIndex < sourceFrames.size()) {
            queue.addLast(sourceFrames.get(nextSourceIndex++));
        }
    }
}

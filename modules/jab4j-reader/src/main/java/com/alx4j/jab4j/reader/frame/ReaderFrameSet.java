package com.alx4j.jab4j.reader.frame;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.SessionId;

/**
 * Ordered source-neutral frame set accepted by the reader input boundary.
 *
 * @param sessionId transfer session id
 * @param finalSessionDigest final writer session digest associated with the export
 * @param frames ordered accepted reader frames
 */
public record ReaderFrameSet(SessionId sessionId, String finalSessionDigest, List<ReaderFrame> frames) {

    /**
     * Creates a validated reader frame set.
     *
     * @param sessionId transfer session id
     * @param finalSessionDigest final writer session digest
     * @param frames ordered accepted frames
     */
    public ReaderFrameSet {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new IllegalArgumentException("finalSessionDigest must not be blank");
        }
        frames = List.copyOf(Objects.requireNonNull(frames, "frames must not be null"));
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }

        Set<FrameIdentity> identities = new HashSet<>();
        long previousFrameIndex = -1;
        for (ReaderFrame frame : frames) {
            Objects.requireNonNull(frame, "frames must not contain null values");
            if (frame.frameIndex() <= previousFrameIndex) {
                throw new IllegalArgumentException("frames must be ordered by increasing frameIndex");
            }
            if (!identities.add(new FrameIdentity(frame.frameIndex(), frame.frameType()))) {
                throw new IllegalArgumentException("frames must not contain duplicate frame identities");
            }
            previousFrameIndex = frame.frameIndex();
        }
    }

    private record FrameIdentity(long frameIndex, FrameType frameType) {
    }
}

package com.alx4j.jab4j.reader.content;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.SessionId;

/**
 * Source-neutral decoded content for one accepted reader frame set.
 *
 * @param sessionId accepted frame-set session id
 * @param layoutProfileId resolved layout profile shared by the decoded input set
 * @param frames decoded frame content in accepted frame order
 */
public record DecodedFrameSetContent(
        SessionId sessionId,
        String layoutProfileId,
        List<DecodedFrameContent> frames
) {

    /**
     * Creates decoded content for one accepted frame set.
     *
     * @param sessionId accepted frame-set session id
     * @param layoutProfileId resolved layout profile shared by the set
     * @param frames decoded frame content in accepted frame order
     */
    public DecodedFrameSetContent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (layoutProfileId == null || layoutProfileId.isBlank()) {
            throw new IllegalArgumentException("layoutProfileId must not be blank");
        }
        frames = List.copyOf(Objects.requireNonNull(frames, "frames must not be null"));
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        for (DecodedFrameContent frame : frames) {
            Objects.requireNonNull(frame, "frames must not contain null values");
            if (!layoutProfileId.equals(frame.layoutProfileId())) {
                throw new IllegalArgumentException("all decoded frames must share the set layoutProfileId");
            }
        }
    }

    /**
     * Returns the total number of decoded tile payloads across all frames.
     *
     * @return decoded tile payload count
     */
    public int decodedTileCount() {
        return frames.stream().mapToInt(frame -> frame.tilePayloads().size()).sum();
    }
}

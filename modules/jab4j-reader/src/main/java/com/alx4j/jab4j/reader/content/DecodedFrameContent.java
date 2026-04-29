package com.alx4j.jab4j.reader.content;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.TilePayload;

/**
 * Source-neutral payload content decoded from one accepted reader frame.
 *
 * @param frameIndex accepted zero-based frame index
 * @param frameType accepted frame type
 * @param layoutProfileId resolved rendered layout profile id
 * @param tilePayloads decoded tile payload envelopes in row-major tile-slot order
 */
public record DecodedFrameContent(
        long frameIndex,
        FrameType frameType,
        String layoutProfileId,
        List<TilePayload> tilePayloads
) {

    /**
     * Creates decoded content for one accepted frame.
     *
     * @param frameIndex accepted zero-based frame index
     * @param frameType accepted frame type
     * @param layoutProfileId resolved rendered layout profile id
     * @param tilePayloads decoded tile payload envelopes
     */
    public DecodedFrameContent {
        if (frameIndex < 0) {
            throw new IllegalArgumentException("frameIndex must be non-negative");
        }
        Objects.requireNonNull(frameType, "frameType must not be null");
        if (layoutProfileId == null || layoutProfileId.isBlank()) {
            throw new IllegalArgumentException("layoutProfileId must not be blank");
        }
        tilePayloads = List.copyOf(Objects.requireNonNull(tilePayloads, "tilePayloads must not be null"));
        if (tilePayloads.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tilePayloads must not contain null values");
        }
    }
}

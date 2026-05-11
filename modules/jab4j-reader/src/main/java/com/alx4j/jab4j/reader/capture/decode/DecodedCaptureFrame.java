package com.alx4j.jab4j.reader.capture.decode;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;

/**
 * Decoded capture frame whose authoritative identity comes from tile payload envelopes.
 *
 * @param sourceId caller-visible source identifier
 * @param callerOrder deterministic traversal order assigned by intake
 * @param pixelSha256 ARGB pixel hash from intake
 * @param sessionId decoded session identifier
 * @param frameIndex decoded frame index
 * @param frameType decoded frame type
 * @param layoutProfileId decoded layout profile id
 * @param tilePayloads decoded tile payloads in tile-slot order
 */
public record DecodedCaptureFrame(
        String sourceId,
        int callerOrder,
        String pixelSha256,
        SessionId sessionId,
        long frameIndex,
        FrameType frameType,
        String layoutProfileId,
        List<TilePayload> tilePayloads
) {

    /**
     * Creates a validated decoded capture frame.
     *
     * @param sourceId caller-visible source identifier
     * @param callerOrder deterministic traversal order
     * @param pixelSha256 ARGB pixel hash
     * @param sessionId decoded session id
     * @param frameIndex decoded frame index
     * @param frameType decoded frame type
     * @param layoutProfileId decoded layout profile id
     * @param tilePayloads decoded payloads
     */
    public DecodedCaptureFrame {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (callerOrder < 0) {
            throw new IllegalArgumentException("callerOrder must be non-negative");
        }
        if (pixelSha256 == null || pixelSha256.isBlank()) {
            throw new IllegalArgumentException("pixelSha256 must not be blank");
        }
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (frameIndex < 0) {
            throw new IllegalArgumentException("frameIndex must be non-negative");
        }
        Objects.requireNonNull(frameType, "frameType must not be null");
        if (layoutProfileId == null || layoutProfileId.isBlank()) {
            throw new IllegalArgumentException("layoutProfileId must not be blank");
        }
        tilePayloads = List.copyOf(Objects.requireNonNull(tilePayloads, "tilePayloads must not be null"));
        if (tilePayloads.isEmpty()) {
            throw new IllegalArgumentException("tilePayloads must not be empty");
        }
        for (TilePayload payload : tilePayloads) {
            Objects.requireNonNull(payload, "tilePayloads must not contain null values");
            if (!sessionId.equals(payload.sessionId())
                    || payload.frameIndex() != frameIndex
                    || payload.frameType() != frameType
                    || !layoutProfileId.equals(payload.layoutProfileId())) {
                throw new IllegalArgumentException("tile payload identity must match decoded frame identity");
            }
        }
    }

    /**
     * Indicates whether this frame carries the same decoded content as another frame.
     *
     * @param other candidate duplicate frame
     * @return true when decoded layout and payloads are equivalent
     */
    public boolean sameDecodedContent(DecodedCaptureFrame other) {
        return other != null
                && layoutProfileId.equals(other.layoutProfileId())
                && tilePayloads.equals(other.tilePayloads);
    }
}

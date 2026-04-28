package com.alx4j.jab4j.api.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Envelope-level tile payload metadata plus payload bytes.
 *
 * @param protocolCompatibilityVersion machine-readable protocol compatibility version
 * @param sessionId owning session identifier
 * @param frameType owning frame type
 * @param frameIndex zero-based frame index
 * @param tileIndex zero-based tile index in frame
 * @param totalTilesInFrame total tiles in frame
 * @param layoutProfileId owning layout profile identifier
 * @param payloadKind logical payload category
 * @param payloadSequenceNumber zero-based payload sequence number
 * @param payloadByteLength payload length in bytes
 * @param payloadCrc32c payload CRC32C
 * @param flags bit flags reserved for transport semantics
 * @param body payload bytes
 */
public record TilePayload(
        int protocolCompatibilityVersion,
        SessionId sessionId,
        FrameType frameType,
        long frameIndex,
        TileIndex tileIndex,
        int totalTilesInFrame,
        String layoutProfileId,
        PayloadKind payloadKind,
        long payloadSequenceNumber,
        int payloadByteLength,
        int payloadCrc32c,
        int flags,
        byte[] body
) {

    /**
     * Creates a validated tile payload envelope.
     *
     * @param protocolCompatibilityVersion protocol compatibility version
     * @param sessionId owning session identifier
     * @param frameType owning frame type
     * @param frameIndex zero-based frame index
     * @param tileIndex zero-based tile index in frame
     * @param totalTilesInFrame total tiles in frame
     * @param layoutProfileId layout profile identifier
     * @param payloadKind logical payload category
     * @param payloadSequenceNumber zero-based payload sequence number
     * @param payloadByteLength payload length in bytes
     * @param payloadCrc32c payload CRC32C
     * @param flags reserved bit flags
     * @param body payload bytes
     */
    public TilePayload {
        if (protocolCompatibilityVersion <= 0) {
            throw new IllegalArgumentException("protocolCompatibilityVersion must be positive");
        }
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(frameType, "frameType must not be null");
        Objects.requireNonNull(tileIndex, "tileIndex must not be null");
        Objects.requireNonNull(payloadKind, "payloadKind must not be null");
        if (frameIndex < 0 || payloadSequenceNumber < 0) {
            throw new IllegalArgumentException("frame indexes must be non-negative");
        }
        if (totalTilesInFrame <= 0) {
            throw new IllegalArgumentException("totalTilesInFrame must be positive");
        }
        if (layoutProfileId == null || layoutProfileId.isBlank()) {
            throw new IllegalArgumentException("layoutProfileId must not be blank");
        }
        if (payloadByteLength < 0) {
            throw new IllegalArgumentException("payloadByteLength must be non-negative");
        }
        if (flags < 0) {
            throw new IllegalArgumentException("flags must be non-negative");
        }
        body = body == null ? new byte[0] : body.clone();
        if (body.length != payloadByteLength) {
            throw new IllegalArgumentException("payloadByteLength must match body length");
        }
    }

    /**
     * Returns a defensive copy of the payload bytes.
     *
     * @return defensive copy of payload bytes
     */
    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TilePayload that
                && frameIndex == that.frameIndex
                && totalTilesInFrame == that.totalTilesInFrame
                && payloadSequenceNumber == that.payloadSequenceNumber
                && payloadByteLength == that.payloadByteLength
                && payloadCrc32c == that.payloadCrc32c
                && flags == that.flags
                && sessionId.equals(that.sessionId)
                && frameType == that.frameType
                && tileIndex.equals(that.tileIndex)
                && layoutProfileId.equals(that.layoutProfileId)
                && payloadKind == that.payloadKind
                && Arrays.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(sessionId, frameType, frameIndex, tileIndex, totalTilesInFrame,
                layoutProfileId, payloadKind, payloadSequenceNumber, payloadByteLength, payloadCrc32c, flags);
        return 31 * result + Arrays.hashCode(body);
    }
}

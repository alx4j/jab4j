package pro.alx4j.jab4j.transfer;

import java.util.Arrays;
import java.util.Objects;
import pro.alx4j.jab4j.api.model.FileChunk;

/**
 * Deterministic chunk bytes paired with already-validated chunk metadata.
 *
 * @param chunk chunk metadata
 * @param payload payload bytes for the chunk
 */
public record ChunkPayload(FileChunk chunk, byte[] payload) {

    /**
     * Creates a validated chunk payload.
     *
     * @param chunk chunk metadata
     * @param payload payload bytes
     */
    public ChunkPayload {
        Objects.requireNonNull(chunk, "chunk must not be null");
        payload = payload == null ? new byte[0] : payload.clone();
        if (payload.length != chunk.payloadLength()) {
            throw new IllegalArgumentException("payload length must match chunk metadata");
        }
    }

    /**
     * Returns a defensive copy of the chunk payload bytes.
     *
     * @return defensive payload copy
     */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChunkPayload that
                && chunk.equals(that.chunk)
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return 31 * chunk.hashCode() + Arrays.hashCode(payload);
    }
}

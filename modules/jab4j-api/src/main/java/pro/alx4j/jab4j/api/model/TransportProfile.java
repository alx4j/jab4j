package pro.alx4j.jab4j.api.model;

import java.util.Objects;

/**
 * Transport-layer profile values that drive chunking, parity planning, and replay cadence.
 *
 * @param profileId stable transport profile identifier
 * @param protocolVersion transport protocol version
 * @param chunkBytes configured chunk size in bytes
 * @param dataShardsPerGroup data shards per parity group
 * @param parityShardsPerGroup parity shards per parity group
 * @param parityGroupSizingStrategy deterministic parity-group sizing strategy
 * @param syncEveryFrames cadence of sync frames
 * @param sessionHeaderRepeatEveryFrames cadence of session-header replay
 * @param manifestRepeatEveryFrames cadence of manifest replay
 */
public record TransportProfile(
        String profileId,
        ProtocolVersion protocolVersion,
        int chunkBytes,
        int dataShardsPerGroup,
        int parityShardsPerGroup,
        ParityGroupSizingStrategy parityGroupSizingStrategy,
        int syncEveryFrames,
        int sessionHeaderRepeatEveryFrames,
        int manifestRepeatEveryFrames
) {

    /**
     * Creates a validated transport profile.
     *
     * @param profileId stable transport profile identifier
     * @param protocolVersion transport protocol version
     * @param chunkBytes configured chunk size in bytes
     * @param dataShardsPerGroup data shards per parity group
     * @param parityShardsPerGroup parity shards per parity group
     * @param parityGroupSizingStrategy deterministic parity-group sizing strategy
     * @param syncEveryFrames cadence of sync frames
     * @param sessionHeaderRepeatEveryFrames cadence of session-header replay
     * @param manifestRepeatEveryFrames cadence of manifest replay
     */
    public TransportProfile {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");
        Objects.requireNonNull(parityGroupSizingStrategy, "parityGroupSizingStrategy must not be null");
        if (chunkBytes <= 0) {
            throw new IllegalArgumentException("chunkBytes must be positive");
        }
        if (dataShardsPerGroup <= 0) {
            throw new IllegalArgumentException("dataShardsPerGroup must be positive");
        }
        if (parityShardsPerGroup < 0) {
            throw new IllegalArgumentException("parityShardsPerGroup must be non-negative");
        }
        if (syncEveryFrames <= 0 || sessionHeaderRepeatEveryFrames <= 0 || manifestRepeatEveryFrames <= 0) {
            throw new IllegalArgumentException("frame repeat intervals must be positive");
        }
    }
}

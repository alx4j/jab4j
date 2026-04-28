package pro.alx4j.jab4j.transfer;

import pro.alx4j.jab4j.api.model.ParityGroupSizingStrategy;

/**
 * Deterministic parity-group metadata fixed by transport planning before parity rendering.
 *
 * @param groupIndex zero-based parity-group index
 * @param dataShardCount configured data-shard count for the group
 * @param parityShardCount configured parity-shard count for the group
 * @param groupSizingStrategy deterministic parity-group sizing strategy
 * @param sourceChunkStartInclusive first source chunk index covered by the group
 * @param sourceChunkEndExclusive end-exclusive source chunk index covered by the group
 * @param sourceChunkCount number of source chunks assigned to the group
 */
public record ParityGroupPlan(
        long groupIndex,
        int dataShardCount,
        int parityShardCount,
        ParityGroupSizingStrategy groupSizingStrategy,
        long sourceChunkStartInclusive,
        long sourceChunkEndExclusive,
        int sourceChunkCount
) {

    /**
     * Creates a validated parity-group plan.
     *
     * @param groupIndex zero-based parity-group index
     * @param dataShardCount configured data-shard count
     * @param parityShardCount configured parity-shard count
     * @param groupSizingStrategy deterministic parity-group sizing strategy
     * @param sourceChunkStartInclusive first covered source chunk index
     * @param sourceChunkEndExclusive end-exclusive source chunk index
     * @param sourceChunkCount source chunk count assigned to the group
     */
    public ParityGroupPlan {
        if (groupIndex < 0) {
            throw new IllegalArgumentException("groupIndex must be non-negative");
        }
        if (dataShardCount <= 0) {
            throw new IllegalArgumentException("dataShardCount must be positive");
        }
        if (parityShardCount < 0 || sourceChunkStartInclusive < 0 || sourceChunkEndExclusive < 0 || sourceChunkCount < 0) {
            throw new IllegalArgumentException("parity indexes and counts must be non-negative");
        }
        if (groupSizingStrategy == null) {
            throw new IllegalArgumentException("groupSizingStrategy must not be null");
        }
        if (sourceChunkEndExclusive < sourceChunkStartInclusive) {
            throw new IllegalArgumentException("sourceChunkEndExclusive must be greater than or equal to sourceChunkStartInclusive");
        }
        if (sourceChunkEndExclusive - sourceChunkStartInclusive != sourceChunkCount) {
            throw new IllegalArgumentException("source chunk range must match sourceChunkCount");
        }
    }
}

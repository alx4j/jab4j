package pro.alx4j.jab4j.transfer;

/**
 * Placeholder logical parity record metadata for later parity-generation work.
 *
 * @param sequenceNumber zero-based logical record sequence
 * @param parityGroupIndex zero-based parity-group index
 * @param sourceChunkStartInclusive first covered source chunk index
 * @param sourceChunkEndExclusive end-exclusive covered source chunk index
 * @param parityShardIndex zero-based shard index inside the parity group
 * @param payloadLength payload length in bytes
 * @param payloadCrc32c payload CRC32C
 */
public record ParityRecord(
        long sequenceNumber,
        long parityGroupIndex,
        long sourceChunkStartInclusive,
        long sourceChunkEndExclusive,
        int parityShardIndex,
        int payloadLength,
        int payloadCrc32c
) implements TransportRecord {

    /**
     * Creates a validated parity-record placeholder.
     *
     * @param sequenceNumber zero-based logical record sequence
     * @param parityGroupIndex zero-based parity-group index
     * @param sourceChunkStartInclusive first covered source chunk index
     * @param sourceChunkEndExclusive end-exclusive covered source chunk index
     * @param parityShardIndex zero-based shard index
     * @param payloadLength payload length in bytes
     * @param payloadCrc32c payload CRC32C
     */
    public ParityRecord {
        if (sequenceNumber < 0 || parityGroupIndex < 0 || sourceChunkStartInclusive < 0 || sourceChunkEndExclusive < 0) {
            throw new IllegalArgumentException("parity indexes must be non-negative");
        }
        if (sourceChunkEndExclusive < sourceChunkStartInclusive) {
            throw new IllegalArgumentException("sourceChunkEndExclusive must be greater than or equal to sourceChunkStartInclusive");
        }
        if (parityShardIndex < 0 || payloadLength < 0) {
            throw new IllegalArgumentException("parityShardIndex and payloadLength must be non-negative");
        }
    }

    @Override
    public TransportRecordCategory category() {
        return TransportRecordCategory.PARITY_RECORD;
    }
}

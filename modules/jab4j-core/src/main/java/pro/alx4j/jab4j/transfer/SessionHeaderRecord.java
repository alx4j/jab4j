package pro.alx4j.jab4j.transfer;

import java.time.Instant;
import java.util.Objects;
import pro.alx4j.jab4j.api.model.ParityGroupSizingStrategy;
import pro.alx4j.jab4j.api.model.ProtocolVersion;
import pro.alx4j.jab4j.api.model.SessionId;

/**
 * Top-level logical record that announces a transport session.
 *
 * @param sequenceNumber zero-based logical record sequence
 * @param sessionId session identifier
 * @param createdAt session creation timestamp
 * @param protocolVersion protocol version
 * @param writerBuildId writer build identifier
 * @param layoutProfileId layout profile id
 * @param codecProfileId codec profile id
 * @param transportProfileId transport profile id
 * @param manifestSummary deterministic manifest summary
 * @param dataShardsPerGroup configured data shards per group
 * @param parityShardsPerGroup configured parity shards per group
 * @param parityGroupSizingStrategy deterministic parity-group sizing strategy
 */
public record SessionHeaderRecord(
        long sequenceNumber,
        SessionId sessionId,
        Instant createdAt,
        ProtocolVersion protocolVersion,
        String writerBuildId,
        String layoutProfileId,
        String codecProfileId,
        String transportProfileId,
        ManifestSummary manifestSummary,
        int dataShardsPerGroup,
        int parityShardsPerGroup,
        ParityGroupSizingStrategy parityGroupSizingStrategy
) implements TransportRecord {

    /**
     * Creates a validated session-header record.
     *
     * @param sequenceNumber zero-based logical record sequence
     * @param sessionId session identifier
     * @param createdAt session creation timestamp
     * @param protocolVersion protocol version
     * @param writerBuildId writer build identifier
     * @param layoutProfileId layout profile id
     * @param codecProfileId codec profile id
     * @param transportProfileId transport profile id
     * @param manifestSummary deterministic manifest summary
     * @param dataShardsPerGroup configured data shards per group
     * @param parityShardsPerGroup configured parity shards per group
     * @param parityGroupSizingStrategy deterministic parity-group sizing strategy
     */
    public SessionHeaderRecord {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be non-negative");
        }
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");
        requireText(writerBuildId, "writerBuildId");
        requireText(layoutProfileId, "layoutProfileId");
        requireText(codecProfileId, "codecProfileId");
        requireText(transportProfileId, "transportProfileId");
        Objects.requireNonNull(manifestSummary, "manifestSummary must not be null");
        Objects.requireNonNull(parityGroupSizingStrategy, "parityGroupSizingStrategy must not be null");
        if (dataShardsPerGroup <= 0 || parityShardsPerGroup < 0) {
            throw new IllegalArgumentException("parity configuration is invalid");
        }
    }

    @Override
    public TransportRecordCategory category() {
        return TransportRecordCategory.SESSION_HEADER_RECORD;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

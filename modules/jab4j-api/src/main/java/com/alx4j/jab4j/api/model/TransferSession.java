package com.alx4j.jab4j.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable top-level transfer-session model.
 *
 * @param sessionId stable session identifier
 * @param createdAt creation timestamp
 * @param protocolVersion transport protocol version
 * @param writerBuildId writer build identifier
 * @param profile aggregate session profile
 * @param manifest immutable manifest snapshot
 * @param files ordered file records
 */
public record TransferSession(
        SessionId sessionId,
        Instant createdAt,
        ProtocolVersion protocolVersion,
        String writerBuildId,
        SessionProfile profile,
        Manifest manifest,
        List<FileRecord> files
) {

    /**
     * Creates a validated transfer session.
     *
     * @param sessionId stable session identifier
     * @param createdAt creation timestamp
     * @param protocolVersion transport protocol version
     * @param writerBuildId writer build identifier
     * @param profile aggregate session profile
     * @param manifest immutable manifest snapshot
     * @param files ordered file records
     */
    public TransferSession {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        if (writerBuildId == null || writerBuildId.isBlank()) {
            throw new IllegalArgumentException("writerBuildId must not be blank");
        }
        files = List.copyOf(files == null ? List.of() : files);
    }
}

package com.alx4j.jab4j.api.model;

/**
 * Logical payload categories carried inside tile payload envelopes.
 */
public enum PayloadKind {
    SYNC_METADATA,
    SESSION_HEADER,
    MANIFEST_FRAGMENT,
    FILE_CHUNK,
    PARITY_SHARD,
    SESSION_END
}

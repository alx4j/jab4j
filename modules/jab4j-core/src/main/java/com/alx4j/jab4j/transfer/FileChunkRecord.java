package com.alx4j.jab4j.transfer;

import java.util.Objects;
import com.alx4j.jab4j.api.model.FileChunk;

/**
 * Logical data record for one deterministic file chunk.
 *
 * @param sequenceNumber zero-based logical record sequence
 * @param fileIndex zero-based file index within the manifest
 * @param chunk chunk metadata
 */
public record FileChunkRecord(long sequenceNumber, long fileIndex, FileChunk chunk) implements TransportRecord {

    /**
     * Creates a validated file-chunk record.
     *
     * @param sequenceNumber zero-based logical record sequence
     * @param fileIndex zero-based file index
     * @param chunk chunk metadata
     */
    public FileChunkRecord {
        if (sequenceNumber < 0 || fileIndex < 0) {
            throw new IllegalArgumentException("sequenceNumber and fileIndex must be non-negative");
        }
        Objects.requireNonNull(chunk, "chunk must not be null");
        if (chunk.fileIndex() != fileIndex) {
            throw new IllegalArgumentException("chunk fileIndex must match record fileIndex");
        }
    }

    @Override
    public TransportRecordCategory category() {
        return TransportRecordCategory.FILE_CHUNK_RECORD;
    }
}

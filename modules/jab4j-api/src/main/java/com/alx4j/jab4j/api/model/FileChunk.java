package com.alx4j.jab4j.api.model;

/**
 * Deterministic chunk metadata for a file payload segment.
 *
 * @param fileIndex owning file index within the manifest
 * @param chunkIndex zero-based chunk index within the file
 * @param offset byte offset in the original file
 * @param payloadLength payload length in bytes
 * @param crc32c CRC32C of the chunk payload
 */
public record FileChunk(long fileIndex, long chunkIndex, long offset, int payloadLength, int crc32c) {

    /**
     * Creates a validated file-chunk descriptor.
     *
     * @param fileIndex owning file index
     * @param chunkIndex zero-based chunk index
     * @param offset byte offset
     * @param payloadLength payload length in bytes
     * @param crc32c CRC32C value
     */
    public FileChunk {
        if (fileIndex < 0 || chunkIndex < 0 || offset < 0) {
            throw new IllegalArgumentException("chunk indexes and offset must be non-negative");
        }
        if (payloadLength < 0) {
            throw new IllegalArgumentException("payloadLength must be non-negative");
        }
    }
}

package com.alx4j.jab4j.api.model;

import java.util.List;

/**
 * Immutable manifest entry for a filesystem object.
 *
 * @param rootAlias logical root alias
 * @param relativePath normalized relative path
 * @param fileType entry type
 * @param sizeBytes size in bytes
 * @param sha256 SHA-256 digest for regular files, or null for directories
 * @param chunks deterministic chunk plan for regular files
 */
public record FileRecord(
        String rootAlias,
        String relativePath,
        FileType fileType,
        long sizeBytes,
        String sha256,
        List<FileChunk> chunks
) {

    /**
     * Creates a validated file record.
     *
     * @param rootAlias logical root alias
     * @param relativePath normalized relative path
     * @param fileType entry type
     * @param sizeBytes size in bytes
     * @param sha256 SHA-256 digest for regular files
     * @param chunks deterministic chunk plan
     */
    public FileRecord {
        requireText(rootAlias, "rootAlias");
        requireText(relativePath, "relativePath");
        if (fileType == null) {
            throw new IllegalArgumentException("fileType must not be null");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be non-negative");
        }
        chunks = List.copyOf(chunks == null ? List.of() : chunks);
        if (fileType == FileType.REGULAR_FILE && (sha256 == null || sha256.isBlank())) {
            throw new IllegalArgumentException("sha256 must be provided for regular files");
        }
        if (fileType == FileType.DIRECTORY && sizeBytes != 0) {
            throw new IllegalArgumentException("directory sizeBytes must be zero");
        }
        if (fileType == FileType.DIRECTORY && !chunks.isEmpty()) {
            throw new IllegalArgumentException("directories must not carry file chunks");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

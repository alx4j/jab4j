package com.alx4j.jab4j.transfer;

import java.util.Objects;
import com.alx4j.jab4j.api.model.FileRecord;

/**
 * Logical record announcing one packaged file or directory entry.
 *
 * @param sequenceNumber zero-based logical record sequence
 * @param fileIndex zero-based file index within the manifest
 * @param fileRecord file metadata
 */
public record FileHeaderRecord(long sequenceNumber, long fileIndex, FileRecord fileRecord) implements TransportRecord {

    /**
     * Creates a validated file-header record.
     *
     * @param sequenceNumber zero-based logical record sequence
     * @param fileIndex zero-based file index
     * @param fileRecord file metadata
     */
    public FileHeaderRecord {
        if (sequenceNumber < 0 || fileIndex < 0) {
            throw new IllegalArgumentException("sequenceNumber and fileIndex must be non-negative");
        }
        Objects.requireNonNull(fileRecord, "fileRecord must not be null");
    }

    @Override
    public TransportRecordCategory category() {
        return TransportRecordCategory.FILE_HEADER_RECORD;
    }
}

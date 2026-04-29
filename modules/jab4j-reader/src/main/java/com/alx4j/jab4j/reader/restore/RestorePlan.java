package com.alx4j.jab4j.reader.restore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alx4j.jab4j.api.model.FileChunk;
import com.alx4j.jab4j.api.model.FileRecord;
import com.alx4j.jab4j.api.model.Manifest;

/**
 * Validated restore work plan with safe final paths and decoded chunk bytes.
 */
record RestorePlan(
        ReaderRestoreRequest request,
        Manifest manifest,
        List<RestoreEntry> entries,
        Set<String> rootAliases,
        Map<ChunkKey, RestoredChunk> chunks,
        long restoredFileCount,
        long restoredDirectoryCount,
        long totalRestoredBytes
) {
}

/**
 * One manifest entry paired with its final destination path.
 */
record RestoreEntry(int fileIndex, FileRecord fileRecord, Path finalPath) {
}

/**
 * Logical chunk identity within the reconstructed manifest.
 */
record ChunkKey(long fileIndex, long chunkIndex) {

    /**
     * Creates a chunk identity from manifest chunk metadata.
     *
     * @param chunk manifest chunk metadata
     * @return logical chunk identity
     */
    static ChunkKey from(FileChunk chunk) {
        return new ChunkKey(chunk.fileIndex(), chunk.chunkIndex());
    }
}

/**
 * Decoded file chunk metadata and defensive-copy payload bytes.
 */
record RestoredChunk(FileChunk metadata, byte[] payload) {

    RestoredChunk {
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Compares decoded chunk metadata and raw bytes.
     *
     * @param other other decoded chunk
     * @return true when both metadata and payload bytes match
     */
    boolean sameContent(RestoredChunk other) {
        return metadata.equals(other.metadata) && java.util.Arrays.equals(payload, other.payload);
    }
}

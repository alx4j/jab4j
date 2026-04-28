package com.alx4j.jab4j.transfer;

/**
 * Stable manifest summary carried in the transport session header.
 *
 * @param totalFileCount total manifest file count
 * @param totalLogicalChunkCount total logical chunk count across regular files
 * @param totalSizeBytes total bytes across regular files
 * @param manifestFingerprint deterministic manifest fingerprint
 */
public record ManifestSummary(
        int totalFileCount,
        long totalLogicalChunkCount,
        long totalSizeBytes,
        String manifestFingerprint
) {

    /**
     * Creates a validated manifest summary.
     *
     * @param totalFileCount total manifest file count
     * @param totalLogicalChunkCount total logical chunk count
     * @param totalSizeBytes total bytes across regular files
     * @param manifestFingerprint deterministic manifest fingerprint
     */
    public ManifestSummary {
        if (totalFileCount < 0) {
            throw new IllegalArgumentException("totalFileCount must be non-negative");
        }
        if (totalLogicalChunkCount < 0 || totalSizeBytes < 0) {
            throw new IllegalArgumentException("summary counts must be non-negative");
        }
        if (manifestFingerprint == null || manifestFingerprint.isBlank()) {
            throw new IllegalArgumentException("manifestFingerprint must not be blank");
        }
    }
}

package pro.alx4j.jab4j.api.model;

import java.util.List;

/**
 * Immutable manifest snapshot for a transfer session.
 *
 * @param files ordered manifest entries
 * @param rootAliases ordered logical root aliases
 * @param totalSizeBytes total bytes across regular files
 * @param manifestFingerprint stable manifest fingerprint when available
 */
public record Manifest(
        List<FileRecord> files,
        List<String> rootAliases,
        long totalSizeBytes,
        String manifestFingerprint
) {

    /**
     * Creates a validated manifest snapshot.
     *
     * @param files ordered manifest entries
     * @param rootAliases ordered logical root aliases
     * @param totalSizeBytes total bytes across regular files
     * @param manifestFingerprint stable manifest fingerprint
     */
    public Manifest {
        files = List.copyOf(files == null ? List.of() : files);
        rootAliases = List.copyOf(rootAliases == null ? List.of() : rootAliases);
        if (totalSizeBytes < 0) {
            throw new IllegalArgumentException("totalSizeBytes must be non-negative");
        }
    }
}

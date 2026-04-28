package pro.alx4j.jab4j.catalog;

import java.util.List;
import java.util.Objects;
import pro.alx4j.jab4j.api.model.Manifest;

/**
 * Deterministic packaging output that keeps the manifest and the ordered source references aligned for later chunking.
 *
 * @param manifest deterministic manifest snapshot
 * @param entries ordered packaged entries
 */
public record PackagingResult(Manifest manifest, List<PackagedEntry> entries) {

    /**
     * Creates a validated packaging result.
     *
     * @param manifest deterministic manifest snapshot
     * @param entries ordered packaged entries
     */
    public PackagingResult {
        Objects.requireNonNull(manifest, "manifest must not be null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    }
}

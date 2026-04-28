package pro.alx4j.jab4j.catalog;

import java.nio.file.Path;
import java.util.Objects;
import pro.alx4j.jab4j.api.model.FileRecord;

/**
 * Stable pairing between one manifest entry and the normalized source path that produced it.
 *
 * @param fileRecord manifest-ready metadata
 * @param sourcePath normalized absolute source path
 */
public record PackagedEntry(FileRecord fileRecord, Path sourcePath) {

    /**
     * Creates a validated packaged entry.
     *
     * @param fileRecord manifest-ready metadata
     * @param sourcePath normalized absolute source path
     */
    public PackagedEntry {
        Objects.requireNonNull(fileRecord, "fileRecord must not be null");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        sourcePath = sourcePath.toAbsolutePath().normalize();
    }
}

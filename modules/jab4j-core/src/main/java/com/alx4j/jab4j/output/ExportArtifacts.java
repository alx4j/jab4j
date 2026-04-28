package com.alx4j.jab4j.output;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Export artifact metadata for one optional prepared-frame export run.
 *
 * @param exportDirectory deterministic export directory for the run
 * @param exportedFiles deterministic keyed artifact paths
 */
public record ExportArtifacts(Path exportDirectory, Map<String, Path> exportedFiles) {

    /**
     * Creates validated export artifact metadata.
     *
     * @param exportDirectory export directory
     * @param exportedFiles deterministic keyed artifact paths
     */
    public ExportArtifacts {
        Objects.requireNonNull(exportDirectory, "exportDirectory must not be null");
        exportedFiles = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(exportedFiles, "exportedFiles must not be null")
        ));
        if (exportedFiles.containsKey(null) || exportedFiles.values().stream().anyMatch(Objects::isNull)) {
            throw new ExportException("exportedFiles must contain only non-null entries");
        }
    }

    /**
     * Creates a disabled-export artifact snapshot with no exported files.
     *
     * @param exportDirectory deterministic export directory that would be used if enabled
     * @return empty artifact metadata
     */
    public static ExportArtifacts disabled(Path exportDirectory) {
        return new ExportArtifacts(exportDirectory, Map.of());
    }
}

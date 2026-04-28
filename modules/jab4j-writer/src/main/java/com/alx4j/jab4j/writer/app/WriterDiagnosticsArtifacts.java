package com.alx4j.jab4j.writer.app;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Optional diagnostics artifacts emitted for one writer run.
 *
 * @param artifactDirectory deterministic run-scoped diagnostics directory
 * @param artifactFiles deterministic keyed artifact paths written for the run
 */
public record WriterDiagnosticsArtifacts(
        Path artifactDirectory,
        Map<String, Path> artifactFiles
) {

    /**
     * Creates validated artifact metadata for one writer run.
     *
     * @param artifactDirectory deterministic diagnostics directory for the run
     * @param artifactFiles deterministic keyed artifact paths
     */
    public WriterDiagnosticsArtifacts {
        Objects.requireNonNull(artifactDirectory, "artifactDirectory must not be null");
        artifactFiles = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(artifactFiles, "artifactFiles must not be null")
        ));
        if (artifactFiles.containsKey(null) || artifactFiles.values().stream().anyMatch(Objects::isNull)) {
            throw new WriterJobException(WriterJobStatus.FAILED, "artifactFiles must contain only non-null entries");
        }
    }
}

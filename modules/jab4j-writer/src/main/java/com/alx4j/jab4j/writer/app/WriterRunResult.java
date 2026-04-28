package com.alx4j.jab4j.writer.app;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.output.ExportArtifacts;
import com.alx4j.jab4j.writer.config.RuntimeConfig;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.player.core.PlaybackResult;

/**
 * Summary and runtime artifacts for one completed writer run.
 *
 * @param sessionId session id used for the run
 * @param effectiveConfig resolved effective config
 * @param effectiveConfigJson deterministic effective-config JSON
 * @param finalSessionDigest deterministic final transport digest
 * @param renderedFrameHashes ordered frame raster hashes emitted for playback
 * @param metrics lightweight runtime metrics captured for the run
 * @param artifacts optional diagnostics artifacts emitted for the run
 * @param exportArtifacts optional export artifacts emitted for the run
 * @param reproducibilityMetadata reproducibility metadata tying the run to config and lineage
 * @param playbackResult playback summary
 * @param dryRun whether playback bypassed actual display output
 */
public record WriterRunResult(
        SessionId sessionId,
        RuntimeConfig effectiveConfig,
        String effectiveConfigJson,
        String finalSessionDigest,
        List<String> renderedFrameHashes,
        WriterRunMetrics metrics,
        WriterDiagnosticsArtifacts artifacts,
        ExportArtifacts exportArtifacts,
        WriterReproducibilityMetadata reproducibilityMetadata,
        PlaybackResult playbackResult,
        boolean dryRun
) {

    /**
     * Creates a validated writer run result.
     *
     * @param sessionId session id used for the run
     * @param effectiveConfig resolved effective config
     * @param effectiveConfigJson deterministic effective-config JSON
     * @param finalSessionDigest deterministic final transport digest
     * @param renderedFrameHashes ordered frame raster hashes
     * @param metrics lightweight runtime metrics
     * @param artifacts optional diagnostics artifacts
     * @param exportArtifacts optional export artifacts
     * @param reproducibilityMetadata reproducibility metadata for the run
     * @param playbackResult playback summary
     * @param dryRun dry-run flag
     */
    public WriterRunResult {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(effectiveConfig, "effectiveConfig must not be null");
        if (effectiveConfigJson == null || effectiveConfigJson.isBlank()) {
            throw new WriterJobException(WriterJobStatus.FAILED, "effectiveConfigJson must not be blank");
        }
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new WriterJobException(WriterJobStatus.FAILED, "finalSessionDigest must not be blank");
        }
        renderedFrameHashes = List.copyOf(Objects.requireNonNull(renderedFrameHashes, "renderedFrameHashes must not be null"));
        if (renderedFrameHashes.isEmpty()) {
            throw new WriterJobException(WriterJobStatus.FAILED, "renderedFrameHashes must not be empty");
        }
        if (renderedFrameHashes.stream().anyMatch(hash -> hash == null || hash.isBlank())) {
            throw new WriterJobException(WriterJobStatus.FAILED, "renderedFrameHashes must contain only non-blank hashes");
        }
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(artifacts, "artifacts must not be null");
        Objects.requireNonNull(exportArtifacts, "exportArtifacts must not be null");
        Objects.requireNonNull(reproducibilityMetadata, "reproducibilityMetadata must not be null");
        Objects.requireNonNull(playbackResult, "playbackResult must not be null");
    }
}

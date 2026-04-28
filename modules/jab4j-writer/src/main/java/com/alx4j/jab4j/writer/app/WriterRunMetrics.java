package com.alx4j.jab4j.writer.app;

/**
 * Lightweight runtime metrics captured for one writer run.
 *
 * @param filesScanned number of packaged filesystem entries counted against the run
 * @param bytesScanned total packaged regular-file bytes
 * @param chunksCreated number of deterministic chunk payloads planned
 * @param parityShardsCreated number of parity shards planned
 * @param tilesEncoded number of tile payloads encoded for frame rendering
 * @param framesRendered number of prepared full-frame rasters rendered
 * @param averageFrameRenderNanos average per-frame render time in nanoseconds
 * @param playbackUnderruns playback underrun count reported by jab4j-player
 * @param validationFailures validation or limit-enforcement failures observed during the run
 */
public record WriterRunMetrics(
        long filesScanned,
        long bytesScanned,
        long chunksCreated,
        long parityShardsCreated,
        long tilesEncoded,
        long framesRendered,
        long averageFrameRenderNanos,
        long playbackUnderruns,
        long validationFailures
) {

    /**
     * Creates validated runtime metrics for one writer run.
     *
     * @param filesScanned packaged file count
     * @param bytesScanned total packaged bytes
     * @param chunksCreated planned chunk count
     * @param parityShardsCreated planned parity-shard count
     * @param tilesEncoded rendered tile count
     * @param framesRendered prepared frame count
     * @param averageFrameRenderNanos average render duration
     * @param playbackUnderruns playback underrun count
     * @param validationFailures validation-failure count
     */
    public WriterRunMetrics {
        if (filesScanned < 0
                || bytesScanned < 0
                || chunksCreated < 0
                || parityShardsCreated < 0
                || tilesEncoded < 0
                || framesRendered < 0
                || averageFrameRenderNanos < 0
                || playbackUnderruns < 0
                || validationFailures < 0) {
            throw new WriterJobException(WriterJobStatus.FAILED, "Writer run metrics must be non-negative");
        }
    }
}

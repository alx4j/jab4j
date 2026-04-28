package pro.alx4j.jab4j.writer.app;

/**
 * Lifecycle states exposed by the writer application boundary.
 */
public enum WriterJobStatus {
    RESOLVING_CONFIG,
    CONFIG_RESOLVED,
    VALIDATING_INPUTS,
    PACKAGING_INPUTS,
    SESSION_PLANNED,
    FRAMES_RENDERED,
    STARTING_PLAYBACK,
    PLAYBACK_PROGRESS,
    WRITING_DIAGNOSTICS,
    EXPORTING_FRAMES,
    COMPLETED,
    FAILED
}

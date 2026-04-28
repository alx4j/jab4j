package com.alx4j.jab4j.writer.app;

import java.util.Objects;
import com.alx4j.jab4j.writer.config.RuntimeConfigPatch;

/**
 * One writer run request driven by CLI overrides and optional dry-run execution.
 *
 * @param cliOverrides typed CLI override patch applied on top of built-in profiles
 * @param dryRun whether playback should bypass actual display output
 */
public record WriterRunRequest(RuntimeConfigPatch cliOverrides, boolean dryRun) {

    /**
     * Creates a validated writer run request.
     *
     * @param cliOverrides typed CLI override patch
     * @param dryRun dry-run flag
     */
    public WriterRunRequest {
        Objects.requireNonNull(cliOverrides, "cliOverrides must not be null");
    }
}

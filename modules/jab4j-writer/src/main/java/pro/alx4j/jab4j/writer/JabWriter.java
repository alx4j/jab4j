package pro.alx4j.jab4j.writer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pro.alx4j.jab4j.writer.config.RuntimeConfig;
import pro.alx4j.jab4j.writer.config.RuntimeConfigPatch;
import pro.alx4j.jab4j.writer.app.WriterApplicationService;
import pro.alx4j.jab4j.writer.app.WriterJobObserver;
import pro.alx4j.jab4j.writer.app.WriterRunRequest;
import pro.alx4j.jab4j.writer.app.WriterRunResult;

/**
 * Client-facing entry point for building and running JAB writer jobs.
 */
public final class JabWriter {

    private JabWriter() {
    }

    /**
     * Starts a writer job builder with one input root.
     *
     * @param inputRoot file or directory root to package for transfer
     * @return writer job builder
     */
    public static Builder write(Path inputRoot) {
        return builder().input(inputRoot);
    }

    /**
     * Starts an empty writer job builder.
     *
     * @return writer job builder
     */
    public static Builder builder() {
        return new Builder(new WriterApplicationService());
    }

    static Builder builder(WriterApplicationService writerApplicationService) {
        return new Builder(writerApplicationService);
    }

    /**
     * Fluent builder for one writer job.
     */
    public static final class Builder {

        private final WriterApplicationService writerApplicationService;
        private final List<RuntimeConfig.InputRootConfig> inputRoots = new ArrayList<>();
        private String profile;
        private Integer rows;
        private Integer cols;
        private Integer fps;
        private Integer chunkBytes;
        private Boolean fullscreen;
        private Boolean exportFrames;
        private boolean dryRun;
        private WriterJobObserver observer = WriterJobObserver.noOp();

        private Builder(WriterApplicationService writerApplicationService) {
            this.writerApplicationService = Objects.requireNonNull(
                    writerApplicationService,
                    "writerApplicationService must not be null"
            );
        }

        /**
         * Adds an input root to the job.
         *
         * @param inputRoot file or directory root to package for transfer
         * @return this builder
         */
        public Builder input(Path inputRoot) {
            Path path = Objects.requireNonNull(inputRoot, "inputRoot must not be null");
            inputRoots.add(new RuntimeConfig.InputRootConfig(path.toString(), null));
            return this;
        }

        /**
         * Selects a built-in writer profile.
         *
         * @param profile built-in profile id
         * @return this builder
         */
        public Builder profile(String profile) {
            if (profile == null || profile.isBlank()) {
                throw new IllegalArgumentException("profile must not be blank");
            }
            this.profile = profile;
            return this;
        }

        /**
         * Overrides the fixed tile grid.
         *
         * @param rows tile rows
         * @param cols tile columns
         * @return this builder
         */
        public Builder grid(int rows, int cols) {
            requirePositive(rows, "rows");
            requirePositive(cols, "cols");
            this.rows = rows;
            this.cols = cols;
            return this;
        }

        /**
         * Overrides playback frames per second.
         *
         * @param fps frames per second
         * @return this builder
         */
        public Builder fps(int fps) {
            requirePositive(fps, "fps");
            this.fps = fps;
            return this;
        }

        /**
         * Overrides transfer chunk size in bytes.
         *
         * @param chunkBytes chunk size in bytes
         * @return this builder
         */
        public Builder chunkBytes(int chunkBytes) {
            requirePositive(chunkBytes, "chunkBytes");
            this.chunkBytes = chunkBytes;
            return this;
        }

        /**
         * Controls whether playback should request fullscreen display.
         *
         * @param fullscreen fullscreen flag
         * @return this builder
         */
        public Builder fullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            return this;
        }

        /**
         * Enables frame-image export for the run.
         *
         * @return this builder
         */
        public Builder exportFrames() {
            return exportFrames(true);
        }

        /**
         * Controls whether the run exports rendered frame images.
         *
         * @param exportFrames export flag
         * @return this builder
         */
        public Builder exportFrames(boolean exportFrames) {
            this.exportFrames = exportFrames;
            return this;
        }

        /**
         * Enables dry-run playback.
         *
         * @return this builder
         */
        public Builder dryRun() {
            return dryRun(true);
        }

        /**
         * Controls whether playback should bypass display output.
         *
         * @param dryRun dry-run flag
         * @return this builder
         */
        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        /**
         * Receives writer lifecycle progress and failure callbacks.
         *
         * @param observer writer observer, or {@code null} for no callbacks
         * @return this builder
         */
        public Builder observer(WriterJobObserver observer) {
            this.observer = observer == null ? WriterJobObserver.noOp() : observer;
            return this;
        }

        /**
         * Runs the configured writer job.
         *
         * @return completed writer run summary
         */
        public WriterRunResult run() {
            if (inputRoots.isEmpty()) {
                throw new IllegalStateException("At least one input root is required");
            }
            return writerApplicationService.run(new WriterRunRequest(runtimeConfigPatch(), dryRun), observer);
        }

        private RuntimeConfigPatch runtimeConfigPatch() {
            return new RuntimeConfigPatch(
                    profile == null ? null : new RuntimeConfigPatch.AppPatch(profile, null, null),
                    new RuntimeConfigPatch.InputPatch(inputRoots),
                    (rows == null && cols == null)
                            ? null
                            : new RuntimeConfigPatch.LayoutPatch(
                                    null,
                                    null,
                                    rows,
                                    cols,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                            ),
                    null,
                    chunkBytes == null
                            ? null
                            : new RuntimeConfigPatch.TransportPatch(null, chunkBytes, null, null, null, null, null),
                    (fps == null && fullscreen == null)
                            ? null
                            : new RuntimeConfigPatch.PlaybackPatch(fps, null, null, null, fullscreen),
                    exportFrames == null
                            ? null
                            : new RuntimeConfigPatch.ExportPatch(
                                    exportFrames,
                                    exportFrames ? "imageSequence" : null
                            ),
                    null
            );
        }

        private void requirePositive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }
}

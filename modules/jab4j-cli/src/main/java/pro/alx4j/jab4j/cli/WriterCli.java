package pro.alx4j.jab4j.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.alx4j.jab4j.writer.config.RuntimeConfig;
import pro.alx4j.jab4j.writer.config.RuntimeConfigPatch;
import pro.alx4j.jab4j.writer.app.WriterApplicationService;
import pro.alx4j.jab4j.writer.app.WriterJobEvent;
import pro.alx4j.jab4j.writer.app.WriterJobObserver;
import pro.alx4j.jab4j.writer.app.WriterRunRequest;
import pro.alx4j.jab4j.writer.app.WriterRunResult;

/**
 * Command-line entry point for the milestone-one writer runtime.
 */
public final class WriterCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(WriterCli.class);
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_RUNTIME_FAILURE = 1;
    private static final int EXIT_USAGE_ERROR = 2;

    private final WriterApplicationService writerApplicationService;
    private final WriterCliParser parser;

    /**
     * Creates a CLI with the baseline writer application service and parser.
     */
    public WriterCli() {
        this(new WriterApplicationService(), new WriterCliParser());
    }

    WriterCli(WriterApplicationService writerApplicationService, WriterCliParser parser) {
        this.writerApplicationService = Objects.requireNonNull(
                writerApplicationService,
                "writerApplicationService must not be null"
        );
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    /**
     * Runs the CLI against the supplied argument array and output streams.
     *
     * @param args raw CLI arguments
     * @param stdout standard output sink
     * @param stderr standard error sink
     * @return process-style exit code
     */
    public int run(String[] args, PrintStream stdout, PrintStream stderr) {
        Objects.requireNonNull(stdout, "stdout must not be null");
        Objects.requireNonNull(stderr, "stderr must not be null");

        try {
            WriterRunRequest request = parser.parse(args);
            logStart(request);
            WriterRunResult result = writerApplicationService.run(request, new PrintingObserver(stdout));
            LOGGER.info(
                    "Writer CLI completed sessionId={} frames={} finalSessionDigest={} dryRun={} diagnosticsDirectory={} exportDirectory={}",
                    result.sessionId(),
                    result.renderedFrameHashes().size(),
                    result.finalSessionDigest(),
                    result.dryRun(),
                    result.artifacts().artifactDirectory(),
                    result.exportArtifacts().exportDirectory()
            );
            stdout.printf(
                    "COMPLETED sessionId=%s frames=%d finalSessionDigest=%s dryRun=%s exportDirectory=%s%n",
                    result.sessionId(),
                    result.renderedFrameHashes().size(),
                    result.finalSessionDigest(),
                    result.dryRun(),
                    result.exportArtifacts().exportDirectory()
            );
            return EXIT_SUCCESS;
        } catch (WriterCliException exception) {
            LOGGER.warn("Writer CLI usage error message={}", exception.getMessage());
            stderr.println(exception.getMessage());
            stderr.println(usage());
            return EXIT_USAGE_ERROR;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Writer CLI execution failed message={} causeType={}",
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            stderr.println("FAILED " + exception.getMessage());
            return EXIT_RUNTIME_FAILURE;
        }
    }

    /**
     * Runs the writer CLI as a process entry point.
     *
     * @param args raw CLI arguments
     */
    public static void main(String[] args) {
        System.exit(new WriterCli().run(args, System.out, System.err));
    }

    private static String usage() {
        return "Usage: jab4j-cli --input <path> [--input <path> ...] [--profile <id>] [--grid <rows>x<cols>]"
                + " [--fps <value>] [--chunk-bytes <value>] [--fullscreen] [--dry-run] [--export-frames]";
    }

    private void logStart(WriterRunRequest request) {
        RuntimeConfigPatch overrides = request.cliOverrides();
        LOGGER.info(
                "Writer CLI starting dryRun={} inputRoots={} profileOverride={} gridOverride={} fpsOverride={} chunkBytesOverride={} fullscreenOverride={} exportEnabled={} exportMode={}",
                request.dryRun(),
                formatInputRoots(overrides.input()),
                overrides.app() == null ? null : overrides.app().profile(),
                formatGridOverride(overrides.layout()),
                overrides.playback() == null ? null : overrides.playback().fps(),
                overrides.transport() == null ? null : overrides.transport().chunkBytes(),
                overrides.playback() == null ? null : overrides.playback().fullscreen(),
                overrides.export() != null && Boolean.TRUE.equals(overrides.export().enabled()),
                overrides.export() == null ? null : overrides.export().mode()
        );
    }

    private String formatInputRoots(RuntimeConfigPatch.InputPatch inputPatch) {
        List<RuntimeConfig.InputRootConfig> roots = inputPatch == null ? List.of() : inputPatch.roots();
        return roots == null
                ? "[]"
                : roots.stream()
                        .map(RuntimeConfig.InputRootConfig::path)
                        .toList()
                        .toString();
    }

    private String formatGridOverride(RuntimeConfigPatch.LayoutPatch layoutPatch) {
        if (layoutPatch == null || layoutPatch.rows() == null || layoutPatch.cols() == null) {
            return null;
        }
        return layoutPatch.rows() + "x" + layoutPatch.cols();
    }

    private static final class PrintingObserver implements WriterJobObserver {

        private final PrintStream stdout;

        private PrintingObserver(PrintStream stdout) {
            this.stdout = stdout;
        }

        @Override
        public void onEvent(WriterJobEvent event) {
            stdout.println(format(event));
        }

        private String format(WriterJobEvent event) {
            if (event.completedUnits() == null) {
                return event.status() + " " + event.message();
            }
            return event.status()
                    + " "
                    + event.completedUnits()
                    + "/"
                    + event.totalUnits()
                    + " "
                    + event.message();
        }
    }
}

package com.alx4j.jab4j.reader.cli;

import java.io.PrintStream;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.reader.app.ReaderApplicationService;
import com.alx4j.jab4j.reader.app.ReaderDecodeAttempt;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarning;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;

/**
 * Command-line entry point for restoring decoded writer-exported frame sets to a local output directory.
 */
public final class ReaderCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderCli.class);
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_RUNTIME_FAILURE = 1;
    private static final int EXIT_USAGE_ERROR = 2;

    private final ReaderApplicationService readerApplicationService;
    private final ReaderCliParser parser;

    /**
     * Creates a CLI with the baseline reader application service and parser.
     */
    public ReaderCli() {
        this(new ReaderApplicationService(), new ReaderCliParser());
    }

    ReaderCli(ReaderApplicationService readerApplicationService, ReaderCliParser parser) {
        this.readerApplicationService = Objects.requireNonNull(
                readerApplicationService,
                "readerApplicationService must not be null"
        );
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    /**
     * Runs the reader CLI against supplied arguments and output streams.
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
            ReaderCliOptions options = parser.parse(args);
            LOGGER.info("Reader CLI starting inputPath={} outputPath={}", options.inputPath(), options.outputPath());
            ReaderDecodeAttempt attempt = readerApplicationService.startDecode(options.inputPath());
            if (!attempt.decoded()) {
                LOGGER.warn(
                        "Reader CLI decode failed inputPath={} status={} message={}",
                        options.inputPath(),
                        attempt.status(),
                        attempt.message()
                );
                renderDecodeFailure(stderr, attempt);
                return EXIT_RUNTIME_FAILURE;
            }

            ReaderFrameSet frameSet = attempt.frameSet().orElseThrow();
            DecodedFrameSetContent decodedContent = attempt.decodedContent().orElseThrow();
            ReaderRestoreResult restoreResult =
                    readerApplicationService.restoreDecodedContent(attempt, options.outputPath());
            if (!restoreResult.restored()) {
                LOGGER.warn(
                        "Reader CLI restore failed inputDirectory={} outputDirectory={} sessionId={} status={} message={}",
                        attempt.inputDirectory(),
                        restoreResult.outputDirectory(),
                        restoreResult.sessionId(),
                        restoreResult.status(),
                        restoreResult.message()
                );
                renderRestoreFailure(stderr, attempt, restoreResult);
                return EXIT_RUNTIME_FAILURE;
            }

            renderRestored(stdout, attempt, frameSet, decodedContent, restoreResult);
            LOGGER.info(
                    "Reader CLI restored inputDirectory={} outputDirectory={} sessionId={} frames={} decodedTiles={} restoredFiles={} restoredDirectories={} totalRestoredBytes={} warnings={}",
                    attempt.inputDirectory(),
                    restoreResult.outputDirectory(),
                    restoreResult.sessionId(),
                    frameSet.frames().size(),
                    decodedContent.decodedTileCount(),
                    restoreResult.restoredFileCount(),
                    restoreResult.restoredDirectoryCount(),
                    restoreResult.totalRestoredBytes(),
                    attempt.warnings().size()
            );
            return EXIT_SUCCESS;
        } catch (ReaderCliException exception) {
            LOGGER.warn("Reader CLI usage error message={}", exception.getMessage());
            stderr.println("USAGE_ERROR message=" + exception.getMessage());
            stderr.println(usage());
            return EXIT_USAGE_ERROR;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Reader CLI execution failed message={} causeType={}",
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            stderr.println("FAILED message=" + exception.getMessage());
            return EXIT_RUNTIME_FAILURE;
        }
    }

    /**
     * Runs the reader CLI as a process entry point.
     *
     * @param args raw CLI arguments
     */
    public static void main(String[] args) {
        System.exit(new ReaderCli().run(args, System.out, System.err));
    }

    private void renderRestored(
            PrintStream stdout,
            ReaderDecodeAttempt attempt,
            ReaderFrameSet frameSet,
            DecodedFrameSetContent decodedContent,
            ReaderRestoreResult restoreResult
    ) {
        stdout.printf(
                "RESTORED sessionId=%s inputDirectory=%s outputDirectory=%s frames=%d decodedTiles=%d restoredFiles=%d restoredDirectories=%d totalRestoredBytes=%d warnings=%d%n",
                restoreResult.sessionId(),
                attempt.inputDirectory(),
                restoreResult.outputDirectory(),
                frameSet.frames().size(),
                decodedContent.decodedTileCount(),
                restoreResult.restoredFileCount(),
                restoreResult.restoredDirectoryCount(),
                restoreResult.totalRestoredBytes(),
                attempt.warnings().size()
        );
        renderWarnings(stdout, attempt);
    }

    private void renderDecodeFailure(PrintStream stderr, ReaderDecodeAttempt attempt) {
        stderr.printf(
                "DECODE_FAILED status=%s inputDirectory=%s message=%s%n",
                attempt.status(),
                attempt.inputDirectory(),
                attempt.message()
        );
        renderWarnings(stderr, attempt);
    }

    private void renderRestoreFailure(
            PrintStream stderr,
            ReaderDecodeAttempt attempt,
            ReaderRestoreResult restoreResult
    ) {
        stderr.printf(
                "RESTORE_FAILED status=%s sessionId=%s inputDirectory=%s outputDirectory=%s message=%s%n",
                restoreResult.status(),
                restoreResult.sessionId(),
                attempt.inputDirectory(),
                restoreResult.outputDirectory(),
                restoreResult.message()
        );
        renderWarnings(stderr, attempt);
    }

    private void renderWarnings(PrintStream stream, ReaderDecodeAttempt attempt) {
        for (ReaderWarning warning : attempt.warnings()) {
            stream.printf("WARNING code=%s message=%s%n", warning.code(), warning.message());
        }
    }

    private static String usage() {
        return "Usage: jab4j-reader-cli --input <imageSequence-or-session-directory> --output <restore-directory>";
    }
}

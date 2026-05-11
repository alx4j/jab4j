package com.alx4j.jab4j.reader.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.reader.app.ReaderApplicationService;
import com.alx4j.jab4j.reader.app.ReaderDecodeAttempt;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.CaptureReceiverRequest;
import com.alx4j.jab4j.reader.capture.CaptureReceiverResult;
import com.alx4j.jab4j.reader.capture.CaptureReceiverService;
import com.alx4j.jab4j.reader.capture.CaptureReceiverStatus;
import com.alx4j.jab4j.reader.capture.CaptureReceiverSummary;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarning;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;

/**
 * Local command-line entry point for restoring current writer {@code imageSequence} PNG exports or extracted capture
 * frame sets.
 *
 * <p>The CLI accepts the exact {@code imageSequence} directory or a parent session directory with exactly one
 * {@code imageSequence} child through {@code --input}. Extracted PNG capture frames use the explicit
 * {@code --capture-input} mode. Direct video, HEIC, live camera, upload, and SaaS capture remain out of scope.</p>
 */
public final class ReaderCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderCli.class);
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_RUNTIME_FAILURE = 1;
    private static final int EXIT_USAGE_ERROR = 2;

    private final ReaderApplicationService readerApplicationService;
    private final Function<CaptureReceiverRequest, CaptureReceiverResult> captureReceiver;
    private final ReaderCliParser parser;

    /**
     * Creates a CLI with the default baseline reader, capture receiver, and parser.
     */
    public ReaderCli() {
        this(new ReaderApplicationService(), new CaptureReceiverService()::receive, new ReaderCliParser());
    }

    ReaderCli(ReaderApplicationService readerApplicationService, ReaderCliParser parser) {
        this(readerApplicationService, new CaptureReceiverService()::receive, parser);
    }

    /**
     * Creates a CLI with explicit baseline reader, capture receiver, and parser collaborators.
     *
     * @param readerApplicationService exact PNG baseline reader service
     * @param captureReceiver capture receiver function
     * @param parser argument parser
     */
    ReaderCli(
            ReaderApplicationService readerApplicationService,
            Function<CaptureReceiverRequest, CaptureReceiverResult> captureReceiver,
            ReaderCliParser parser
    ) {
        this.readerApplicationService = Objects.requireNonNull(
                readerApplicationService,
                "readerApplicationService must not be null"
        );
        this.captureReceiver = Objects.requireNonNull(captureReceiver, "captureReceiver must not be null");
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
            return options.captureInputPath().isPresent()
                    ? runCaptureReceiver(options, stdout, stderr)
                    : runBaselineRestore(options, stdout, stderr);
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

    private int runBaselineRestore(ReaderCliOptions options, PrintStream stdout, PrintStream stderr) {
        Path inputPath = options.inputPath().orElseThrow();
        LOGGER.info("Reader CLI starting inputPath={} outputPath={}", inputPath, options.outputPath());
        ReaderDecodeAttempt attempt = readerApplicationService.startDecode(inputPath);
        if (!attempt.decoded()) {
            LOGGER.warn(
                    "Reader CLI decode failed inputPath={} status={} message={}",
                    inputPath,
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
    }

    private int runCaptureReceiver(ReaderCliOptions options, PrintStream stdout, PrintStream stderr) {
        Path captureInputPath = options.captureInputPath().orElseThrow();
        CaptureReceiverRequest request = CaptureReceiverRequest.restore(List.of(captureInputPath), options.outputPath());
        LOGGER.info(
                "Reader CLI capture receiver starting captureInputPath={} outputPath={}",
                captureInputPath,
                options.outputPath()
        );

        CaptureReceiverResult result;
        try {
            result = Objects.requireNonNull(captureReceiver.apply(request), "capture receiver result must not be null");
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Reader CLI capture receiver failed captureInputPath={} outputPath={} message={} causeType={}",
                    captureInputPath,
                    options.outputPath(),
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            stderr.println("CAPTURE_FAILED message=" + exception.getMessage());
            return EXIT_RUNTIME_FAILURE;
        }

        PrintStream resultStream = result.failed() ? stderr : stdout;
        renderCaptureResult(resultStream, captureInputPath, options.outputPath(), result);
        if (result.failed()) {
            LOGGER.warn(
                    "Reader CLI capture receiver returned failure captureInputPath={} outputPath={} status={} message={}",
                    captureInputPath,
                    options.outputPath(),
                    result.status(),
                    result.message()
            );
            return EXIT_RUNTIME_FAILURE;
        }

        LOGGER.info(
                "Reader CLI capture receiver completed captureInputPath={} outputPath={} status={} submittedFrames={} acceptedCandidates={} decodedTiles={} restoredFiles={}",
                captureInputPath,
                options.outputPath(),
                result.status(),
                result.summary().submittedFrameCount(),
                result.summary().acceptedCandidateCount(),
                result.summary().decodedTileCount(),
                result.summary().restoredFileCount()
        );
        return EXIT_SUCCESS;
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

    private void renderCaptureResult(
            PrintStream stream,
            Path captureInputPath,
            Path outputPath,
            CaptureReceiverResult result
    ) {
        CaptureReceiverSummary summary = result.summary();
        stream.printf(
                "%s captureInput=%s outputDirectory=%s submittedFrames=%d readableFrames=%d acceptedCandidates=%d rejectedFrames=%d duplicateFrames=%d uncertainFrames=%d decodedTiles=%d restoredFiles=%d message=%s%n",
                captureStatusLine(result.status()),
                normalized(captureInputPath),
                normalized(outputPath),
                summary.submittedFrameCount(),
                summary.readableFrameCount(),
                summary.acceptedCandidateCount(),
                summary.rejectedFrameCount(),
                summary.duplicateFrameCount(),
                summary.uncertainFrameCount(),
                summary.decodedTileCount(),
                summary.restoredFileCount(),
                result.message()
        );
        result.restoreResult().ifPresent(restoreResult -> stream.printf(
                "CAPTURE_RESTORE status=%s sessionId=%s outputDirectory=%s restoredFiles=%d restoredDirectories=%d totalRestoredBytes=%d message=%s%n",
                restoreResult.status(),
                restoreResult.sessionId(),
                restoreResult.outputDirectory(),
                restoreResult.restoredFileCount(),
                restoreResult.restoredDirectoryCount(),
                restoreResult.totalRestoredBytes(),
                restoreResult.message()
        ));
        for (CaptureFrameDiagnostic diagnostic : result.diagnostics()) {
            renderCaptureDiagnostic(stream, diagnostic);
        }
    }

    private void renderCaptureDiagnostic(PrintStream stream, CaptureFrameDiagnostic diagnostic) {
        stream.printf("CAPTURE_DIAGNOSTIC code=%s", diagnostic.code());
        diagnostic.sourceId().ifPresent(sourceId -> stream.printf(" sourceId=%s", sourceId));
        diagnostic.callerOrder().ifPresent(callerOrder -> stream.printf(" callerOrder=%d", callerOrder));
        stream.printf(" message=%s%n", diagnostic.message());
    }

    private String captureStatusLine(CaptureReceiverStatus status) {
        return switch (status) {
            case RESTORED -> "CAPTURE_RESTORED";
            case ELIGIBLE -> "CAPTURE_ELIGIBLE";
            case INCOMPLETE -> "CAPTURE_INCOMPLETE";
            case REJECTED -> "CAPTURE_REJECTED";
            case RESTORE_FAILED -> "CAPTURE_FAILED";
        };
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

    private Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: jab4j-reader-cli --input <imageSequence-or-session-directory> --output <restore-directory>",
                "       jab4j-reader-cli --capture-input <frames-directory> --output <restore-directory>",
                "Input: current writer imageSequence PNG export directory, or a parent session directory with exactly one imageSequence child.",
                "Capture input: extracted PNG frame directory evaluated by the capture receiver; frame-sequence.txt is not required.",
                "frame-sequence.txt is a writer-export validation helper for lossless PNG frames.",
                "Unsupported: direct .mov/.mp4 video, HEIC, live camera, mobile app, upload, or SaaS capture."
        );
    }
}

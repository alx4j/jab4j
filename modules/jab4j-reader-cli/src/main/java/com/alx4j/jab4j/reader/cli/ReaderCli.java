package com.alx4j.jab4j.reader.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverResult;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverService;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverStatus;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSummary;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarning;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;

/**
 * Local command-line entry point for restoring current writer {@code imageSequence} PNG exports, extracted capture
 * frame sets, or explicit MVP-3 capture media inputs.
 *
 * <p>The CLI accepts the exact {@code imageSequence} directory or a parent session directory with exactly one
 * {@code imageSequence} child through {@code --input}. Extracted PNG capture frames use the explicit
 * {@code --capture-input} mode. MVP-3 media uses {@code --capture-media-input} without changing the existing modes.
 * Direct video, HEIC, live camera, upload, and SaaS capture remain out of scope unless a media receiver or video
 * adapter is added by the reader module.</p>
 */
public final class ReaderCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderCli.class);
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_RUNTIME_FAILURE = 1;
    private static final int EXIT_USAGE_ERROR = 2;

    private final ReaderApplicationService readerApplicationService;
    private final Function<CaptureReceiverRequest, CaptureReceiverResult> captureReceiver;
    private final Function<CaptureMediaReceiverRequest, CaptureMediaReceiverResult> captureMediaReceiver;
    private final ReaderCliParser parser;

    /**
     * Creates a CLI with the default baseline reader, capture receiver, and parser.
     */
    public ReaderCli() {
        this(
                new ReaderApplicationService(),
                new CaptureReceiverService()::receive,
                new CaptureMediaReceiverService()::receive,
                new ReaderCliParser()
        );
    }

    ReaderCli(ReaderApplicationService readerApplicationService, ReaderCliParser parser) {
        this(
                readerApplicationService,
                new CaptureReceiverService()::receive,
                new CaptureMediaReceiverService()::receive,
                parser
        );
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
        this(readerApplicationService, captureReceiver, new CaptureMediaReceiverService()::receive, parser);
    }

    ReaderCli(
            ReaderApplicationService readerApplicationService,
            Function<CaptureReceiverRequest, CaptureReceiverResult> captureReceiver,
            Function<CaptureMediaReceiverRequest, CaptureMediaReceiverResult> captureMediaReceiver,
            ReaderCliParser parser
    ) {
        this.readerApplicationService = Objects.requireNonNull(
                readerApplicationService,
                "readerApplicationService must not be null"
        );
        this.captureReceiver = Objects.requireNonNull(captureReceiver, "captureReceiver must not be null");
        this.captureMediaReceiver = Objects.requireNonNull(
                captureMediaReceiver,
                "captureMediaReceiver must not be null"
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
            if (options.captureInputPath().isPresent()) {
                return runCaptureReceiver(options, stdout, stderr);
            }
            if (options.captureMediaInputPath().isPresent()) {
                return runCaptureMediaReceiver(options, stdout, stderr);
            }
            return runBaselineRestore(options, stdout, stderr);
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
        Path outputPath = options.outputPath().orElseThrow();
        LOGGER.info("Reader CLI starting inputPath={} outputPath={}", inputPath, outputPath);
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
                readerApplicationService.restoreDecodedContent(attempt, outputPath);
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
        Path outputPath = options.outputPath().orElseThrow();
        CaptureReceiverRequest request = CaptureReceiverRequest.restore(List.of(captureInputPath), outputPath);
        LOGGER.info(
                "Reader CLI capture receiver starting captureInputPath={} outputPath={}",
                captureInputPath,
                outputPath
        );

        CaptureReceiverResult result;
        try {
            result = Objects.requireNonNull(captureReceiver.apply(request), "capture receiver result must not be null");
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Reader CLI capture receiver failed captureInputPath={} outputPath={} message={} causeType={}",
                    captureInputPath,
                    outputPath,
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            stderr.println("CAPTURE_FAILED message=" + exception.getMessage());
            return EXIT_RUNTIME_FAILURE;
        }

        PrintStream resultStream = result.failed() ? stderr : stdout;
        renderCaptureResult(resultStream, captureInputPath, outputPath, result);
        if (result.failed()) {
            LOGGER.warn(
                    "Reader CLI capture receiver returned failure captureInputPath={} outputPath={} status={} message={}",
                    captureInputPath,
                    outputPath,
                    result.status(),
                    result.message()
            );
            return EXIT_RUNTIME_FAILURE;
        }

        LOGGER.info(
                "Reader CLI capture receiver completed captureInputPath={} outputPath={} status={} submittedFrames={} acceptedCandidates={} decodedTiles={} restoredFiles={}",
                captureInputPath,
                outputPath,
                result.status(),
                result.summary().submittedFrameCount(),
                result.summary().acceptedCandidateCount(),
                result.summary().decodedTileCount(),
                result.summary().restoredFileCount()
        );
        return EXIT_SUCCESS;
    }

    private int runCaptureMediaReceiver(ReaderCliOptions options, PrintStream stdout, PrintStream stderr) {
        Path captureMediaInputPath = options.captureMediaInputPath().orElseThrow();
        Optional<Path> outputPath = options.outputPath();
        CaptureMediaReceiverRequest request = captureMediaRequest(captureMediaInputPath, outputPath);
        LOGGER.info(
                "Reader CLI capture media receiver starting captureMediaInputPath={} restoreRequested={} outputPath={}",
                captureMediaInputPath,
                request.restoreRequested(),
                outputPath.orElse(null)
        );

        CaptureMediaReceiverResult result;
        try {
            result = Objects.requireNonNull(
                    captureMediaReceiver.apply(request),
                    "capture media receiver result must not be null"
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Reader CLI capture media receiver failed captureMediaInputPath={} outputPath={} message={} causeType={}",
                    captureMediaInputPath,
                    outputPath.orElse(null),
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            stderr.println("CAPTURE_MEDIA_FAILED message=" + exception.getMessage());
            return EXIT_RUNTIME_FAILURE;
        }

        PrintStream resultStream = result.failed() ? stderr : stdout;
        renderCaptureMediaResult(resultStream, captureMediaInputPath, outputPath, result);
        if (result.failed()) {
            LOGGER.warn(
                    "Reader CLI capture media receiver returned failure captureMediaInputPath={} restoreRequested={} status={} message={}",
                    captureMediaInputPath,
                    request.restoreRequested(),
                    result.status(),
                    result.message()
            );
            return EXIT_RUNTIME_FAILURE;
        }

        LOGGER.info(
                "Reader CLI capture media receiver completed captureMediaInputPath={} restoreRequested={} status={} submittedMedia={} acceptedCandidates={} decodedTiles={} restoredFiles={}",
                captureMediaInputPath,
                request.restoreRequested(),
                result.status(),
                result.summary().submittedMediaCount(),
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

    private void renderCaptureMediaResult(
            PrintStream stream,
            Path captureMediaInputPath,
            Optional<Path> outputPath,
            CaptureMediaReceiverResult result
    ) {
        CaptureMediaSummary summary = result.summary();
        stream.printf(
                "%s captureMediaInput=%s restoreRequested=%s outputDirectory=%s submittedMedia=%d readableMedia=%d acceptedCandidates=%d rejectedCandidates=%d uncertainCandidates=%d duplicateMediaFrames=%d recoveredUniqueFrames=%d decodedTiles=%d restoredFiles=%d message=%s%n",
                captureMediaStatusLine(result),
                normalized(captureMediaInputPath),
                outputPath.isPresent(),
                outputDirectoryLabel(outputPath),
                summary.submittedMediaCount(),
                summary.readableMediaCount(),
                summary.acceptedCandidateCount(),
                summary.rejectedCandidateCount(),
                summary.uncertainCandidateCount(),
                summary.duplicateMediaFrameCount(),
                summary.recoveredUniqueFrameCount(),
                summary.decodedTileCount(),
                summary.restoredFileCount(),
                result.message()
        );
        result.restoreResult().ifPresent(restoreResult -> stream.printf(
                "CAPTURE_MEDIA_RESTORE status=%s sessionId=%s outputDirectory=%s restoredFiles=%d restoredDirectories=%d totalRestoredBytes=%d message=%s%n",
                restoreResult.status(),
                restoreResult.sessionId(),
                restoreResult.outputDirectory(),
                restoreResult.restoredFileCount(),
                restoreResult.restoredDirectoryCount(),
                restoreResult.totalRestoredBytes(),
                restoreResult.message()
        ));
        for (CaptureMediaDiagnostic diagnostic : result.diagnostics()) {
            renderCaptureMediaDiagnostic(stream, diagnostic);
        }
    }

    private void renderCaptureMediaDiagnostic(PrintStream stream, CaptureMediaDiagnostic diagnostic) {
        stream.printf(
                "CAPTURE_MEDIA_DIAGNOSTIC code=%s severity=%s blocking=%s",
                diagnostic.code(),
                diagnostic.severity(),
                diagnostic.blocking()
        );
        diagnostic.sourceKind().ifPresent(sourceKind -> stream.printf(" sourceKind=%s", mediaSourceKindLabel(sourceKind)));
        diagnostic.sourceId().ifPresent(sourceId -> stream.printf(" sourceId=%s", displaySourceId(sourceId)));
        diagnostic.callerOrder().ifPresent(callerOrder -> stream.printf(" callerOrder=%d", callerOrder));
        diagnostic.timestampMillis().ifPresent(timestampMillis -> stream.printf(" timestampMillis=%d", timestampMillis));
        diagnostic.frameNumber().ifPresent(frameNumber -> stream.printf(" frameNumber=%d", frameNumber));
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

    private String captureMediaStatusLine(CaptureMediaReceiverResult result) {
        if (result.status() == CaptureMediaReceiverStatus.REJECTED
                && result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().unsupportedMedia())) {
            return "CAPTURE_MEDIA_UNSUPPORTED";
        }
        return switch (result.status()) {
            case RESTORED -> "CAPTURE_MEDIA_RESTORED";
            case ELIGIBLE -> "CAPTURE_MEDIA_ELIGIBLE";
            case INCOMPLETE -> "CAPTURE_MEDIA_INCOMPLETE";
            case REJECTED -> "CAPTURE_MEDIA_REJECTED";
            case RESTORE_FAILED -> "CAPTURE_MEDIA_FAILED";
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

    private String outputDirectoryLabel(Optional<Path> outputPath) {
        return outputPath.map(this::normalized)
                .map(Path::toString)
                .orElse("-");
    }

    private CaptureMediaReceiverRequest captureMediaRequest(Path captureMediaInputPath, Optional<Path> outputPath) {
        CaptureMediaSourceKind sourceKind = inferCaptureMediaSourceKind(captureMediaInputPath);
        return outputPath
                .map(path -> CaptureMediaReceiverRequest.restore(sourceKind, List.of(captureMediaInputPath), path))
                .orElseGet(() -> CaptureMediaReceiverRequest.evaluateOnly(sourceKind, List.of(captureMediaInputPath)));
    }

    private CaptureMediaSourceKind inferCaptureMediaSourceKind(Path inputPath) {
        if (Files.isDirectory(inputPath)) {
            return CaptureMediaSourceKind.EXTRACTED_FRAME_FOLDER;
        }
        String extension = extension(inputPath);
        if (extension.equals("mov") || extension.equals("mp4")) {
            return CaptureMediaSourceKind.VIDEO_FILE;
        }
        return CaptureMediaSourceKind.STILL_IMAGE_FILE;
    }

    private String extension(Path inputPath) {
        String fileName = inputPath.getFileName().toString().toLowerCase(Locale.ROOT);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    private String mediaSourceKindLabel(CaptureMediaSourceKind sourceKind) {
        return switch (sourceKind) {
            case STILL_IMAGE_FILE -> "STILL_IMAGE";
            case EXTRACTED_FRAME_FOLDER -> "EXTRACTED_FRAME_FOLDER";
            case VIDEO_FILE -> "VIDEO";
        };
    }

    private String displaySourceId(String sourceId) {
        try {
            Path fileName = Path.of(sourceId).getFileName();
            return fileName == null ? sourceId : fileName.toString();
        } catch (RuntimeException exception) {
            return sourceId;
        }
    }

    private static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: jab4j-reader-cli --input <imageSequence-or-session-directory> --output <restore-directory>",
                "       jab4j-reader-cli --capture-input <frames-directory> --output <restore-directory>",
                "       jab4j-reader-cli --capture-media-input <media-path> [--output <restore-directory>]",
                "Input: current writer imageSequence PNG export directory, or a parent session directory with exactly one imageSequence child.",
                "Capture input: extracted PNG frame directory evaluated by the capture receiver; frame-sequence.txt is not required.",
                "Capture media input: MVP-3 PNG/JPEG still image, extracted-frame folder, or direct video path evaluated only when the media receiver is available.",
                "frame-sequence.txt is a writer-export validation helper for lossless PNG frames.",
                "Unsupported in the first media slice: full real photo recovery, HEIC, direct .mov/.mp4 decoding, live camera, mobile app, upload, or SaaS capture."
        );
    }

}

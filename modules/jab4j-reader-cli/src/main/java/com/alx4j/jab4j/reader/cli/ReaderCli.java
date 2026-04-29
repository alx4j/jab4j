package com.alx4j.jab4j.reader.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.app.ReaderApplicationService;
import com.alx4j.jab4j.reader.app.ReaderDecodeAttempt;
import com.alx4j.jab4j.reader.content.DecodedFrameContent;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarning;

/**
 * Command-line entry point for validating writer-exported frame sets before reader decoding.
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
            Path inputPath = parser.parse(args);
            LOGGER.info("Reader CLI starting inputPath={}", inputPath);
            ReaderDecodeAttempt attempt = readerApplicationService.startDecode(inputPath);
            if (!attempt.decoded()) {
                LOGGER.warn(
                        "Reader CLI did not decode inputPath={} status={} message={}",
                        inputPath,
                        attempt.status(),
                        attempt.message()
                );
                stderr.println(attempt.status() + " " + attempt.message());
                return EXIT_RUNTIME_FAILURE;
            }

            ReaderFrameSet frameSet = attempt.frameSet().orElseThrow();
            DecodedFrameSetContent decodedContent = attempt.decodedContent().orElseThrow();
            stdout.printf(
                    "CONTENT_DECODED inputDirectory=%s sessionId=%s frames=%d decodedTiles=%d layoutProfileId=%s finalSessionDigest=%s warnings=%d%n",
                    attempt.inputDirectory(),
                    frameSet.sessionId(),
                    frameSet.frames().size(),
                    decodedContent.decodedTileCount(),
                    decodedContent.layoutProfileId(),
                    frameSet.finalSessionDigest(),
                    attempt.warnings().size()
            );
            for (DecodedFrameContent frame : decodedContent.frames()) {
                stdout.printf(
                        "FRAME frameIndex=%d frameType=%s layoutProfileId=%s decodedTiles=%d%n",
                        frame.frameIndex(),
                        frame.frameType(),
                        frame.layoutProfileId(),
                        frame.tilePayloads().size()
                );
                for (TilePayload payload : frame.tilePayloads()) {
                    stdout.printf(
                            "TILE frameIndex=%d tileIndex=%d totalTiles=%d payloadKind=%s payloadSequenceNumber=%d payloadBytes=%d payloadCrc32c=%s flags=%d%n",
                            payload.frameIndex(),
                            payload.tileIndex().value(),
                            payload.totalTilesInFrame(),
                            payload.payloadKind(),
                            payload.payloadSequenceNumber(),
                            payload.payloadByteLength(),
                            Integer.toUnsignedString(payload.payloadCrc32c()),
                            payload.flags()
                    );
                }
            }
            for (ReaderWarning warning : attempt.warnings()) {
                stdout.printf("WARNING %s %s%n", warning.code(), warning.message());
            }
            LOGGER.info(
                    "Reader CLI decoded inputDirectory={} sessionId={} frames={} decodedTiles={} warnings={}",
                    attempt.inputDirectory(),
                    frameSet.sessionId(),
                    frameSet.frames().size(),
                    decodedContent.decodedTileCount(),
                    attempt.warnings().size()
            );
            return EXIT_SUCCESS;
        } catch (ReaderCliException exception) {
            LOGGER.warn("Reader CLI usage error message={}", exception.getMessage());
            stderr.println(exception.getMessage());
            stderr.println(usage());
            return EXIT_USAGE_ERROR;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Reader CLI execution failed message={} causeType={}",
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            stderr.println("FAILED " + exception.getMessage());
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

    private static String usage() {
        return "Usage: jab4j-reader-cli --input <imageSequence-or-session-directory>";
    }
}

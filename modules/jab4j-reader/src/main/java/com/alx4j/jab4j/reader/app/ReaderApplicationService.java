package com.alx4j.jab4j.reader.app;

import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.restore.ReaderRestoreRequest;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;
import com.alx4j.jab4j.reader.restore.ReaderRestoreService;
import com.alx4j.jab4j.reader.writer.WriterImageSequenceInputAdapter;

/**
 * Reader application boundary for the current local MVP decode-and-restore flow.
 *
 * <p>The default service currently starts from writer {@code imageSequence} PNG exports only. Downstream restore
 * remains driven by decoded transfer content rather than by the local writer folder layout.</p>
 */
public final class ReaderApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderApplicationService.class);

    private final WriterImageSequenceInputAdapter writerImageSequenceInputAdapter;
    private final ReaderContentDecoder readerContentDecoder;
    private final ReaderRestoreService readerRestoreService;

    /**
     * Creates a reader service with the default writer-export {@code imageSequence} input adapter.
     */
    public ReaderApplicationService() {
        this(new WriterImageSequenceInputAdapter(), new ReaderContentDecoder(), new ReaderRestoreService());
    }

    private ReaderApplicationService(
            WriterImageSequenceInputAdapter writerImageSequenceInputAdapter,
            ReaderContentDecoder readerContentDecoder,
            ReaderRestoreService readerRestoreService
    ) {
        this.writerImageSequenceInputAdapter = Objects.requireNonNull(
                writerImageSequenceInputAdapter,
                "writerImageSequenceInputAdapter must not be null"
        );
        this.readerContentDecoder = Objects.requireNonNull(readerContentDecoder, "readerContentDecoder must not be null");
        this.readerRestoreService = Objects.requireNonNull(readerRestoreService, "readerRestoreService must not be null");
    }

    /**
     * Validates current writer {@code imageSequence} PNG input and decodes its accepted frame content.
     *
     * @param inputPath exact {@code imageSequence} directory or parent session directory with exactly one
     *         {@code imageSequence} child
     * @return structured accepted or rejected decode-attempt result
     */
    public ReaderDecodeAttempt startDecode(Path inputPath) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Path normalizedInputPath = inputPath.toAbsolutePath().normalize();
        try {
            WriterImageSequenceInputAdapter.ValidatedInput validatedInput =
                    writerImageSequenceInputAdapter.validate(normalizedInputPath);
            ReaderFrameSet frameSet = validatedInput.frameSet();
            try {
                DecodedFrameSetContent decodedContent = readerContentDecoder.decode(frameSet);
                ReaderDecodeAttempt attempt = ReaderDecodeAttempt.contentDecoded(
                        validatedInput.imageSequenceDirectory(),
                        frameSet,
                        decodedContent,
                        validatedInput.warnings()
                );
                LOGGER.info(
                        "Reader content decoded sessionId={} frames={} decodedTiles={} layoutProfileId={} inputDirectory={} warnings={}",
                        frameSet.sessionId(),
                        frameSet.frames().size(),
                        decodedContent.decodedTileCount(),
                        decodedContent.layoutProfileId(),
                        validatedInput.imageSequenceDirectory(),
                        validatedInput.warnings().size()
                );
                return attempt;
            } catch (ReaderContentDecodeException exception) {
                LOGGER.warn(
                        "Reader content decode failed status={} sessionId={} frames={} inputDirectory={} message={}",
                        exception.status(),
                        frameSet.sessionId(),
                        frameSet.frames().size(),
                        validatedInput.imageSequenceDirectory(),
                        exception.getMessage()
                );
                return ReaderDecodeAttempt.contentDecodeFailed(
                        validatedInput.imageSequenceDirectory(),
                        frameSet,
                        validatedInput.warnings(),
                        exception.status(),
                        exception.getMessage()
                );
            }
        } catch (ReaderInputException exception) {
            LOGGER.warn(
                    "Reader input rejected inputPath={} message={}",
                    normalizedInputPath,
                    exception.getMessage()
            );
            return ReaderDecodeAttempt.rejected(normalizedInputPath, exception.getMessage());
        }
    }

    /**
     * Restores decoded frame content into the selected output directory.
     *
     * @param request decoded restore request
     * @return structured restore result
     */
    public ReaderRestoreResult restoreDecodedContent(ReaderRestoreRequest request) {
        return readerRestoreService.restore(request);
    }

    /**
     * Restores a successful decode attempt into the selected output directory.
     *
     * @param decodeAttempt successful reader decode attempt
     * @param outputDirectory caller-selected output directory
     * @return structured restore result
     */
    public ReaderRestoreResult restoreDecodedContent(ReaderDecodeAttempt decodeAttempt, Path outputDirectory) {
        Objects.requireNonNull(decodeAttempt, "decodeAttempt must not be null");
        if (!decodeAttempt.decoded()) {
            throw new IllegalArgumentException("restore requires a CONTENT_DECODED reader decode attempt");
        }
        return restoreDecodedContent(ReaderRestoreRequest.from(
                decodeAttempt.frameSet().orElseThrow(),
                decodeAttempt.decodedContent().orElseThrow(),
                outputDirectory
        ));
    }
}

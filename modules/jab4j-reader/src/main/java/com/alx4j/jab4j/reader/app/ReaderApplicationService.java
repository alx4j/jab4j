package com.alx4j.jab4j.reader.app;

import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.writer.WriterImageSequenceInputAdapter;

/**
 * Reader application boundary for validating input and starting a decode attempt.
 */
public final class ReaderApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderApplicationService.class);

    private final WriterImageSequenceInputAdapter writerImageSequenceInputAdapter;
    private final ReaderContentDecoder readerContentDecoder;

    /**
     * Creates a reader service with the default writer-export input adapter.
     */
    public ReaderApplicationService() {
        this(new WriterImageSequenceInputAdapter(), new ReaderContentDecoder());
    }

    private ReaderApplicationService(
            WriterImageSequenceInputAdapter writerImageSequenceInputAdapter,
            ReaderContentDecoder readerContentDecoder
    ) {
        this.writerImageSequenceInputAdapter = Objects.requireNonNull(
                writerImageSequenceInputAdapter,
                "writerImageSequenceInputAdapter must not be null"
        );
        this.readerContentDecoder = Objects.requireNonNull(readerContentDecoder, "readerContentDecoder must not be null");
    }

    /**
     * Validates a writer-exported imageSequence folder and represents the start of reader decoding.
     *
     * @param inputPath exact imageSequence directory or parent session directory
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
}

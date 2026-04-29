package com.alx4j.jab4j.reader.app;

import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.reader.writer.WriterImageSequenceInputAdapter;

/**
 * Reader application boundary for validating input and starting a decode attempt.
 */
public final class ReaderApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderApplicationService.class);

    private final WriterImageSequenceInputAdapter writerImageSequenceInputAdapter;

    /**
     * Creates a reader service with the default writer-export input adapter.
     */
    public ReaderApplicationService() {
        this(new WriterImageSequenceInputAdapter());
    }

    private ReaderApplicationService(WriterImageSequenceInputAdapter writerImageSequenceInputAdapter) {
        this.writerImageSequenceInputAdapter = Objects.requireNonNull(
                writerImageSequenceInputAdapter,
                "writerImageSequenceInputAdapter must not be null"
        );
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
            ReaderDecodeAttempt attempt = ReaderDecodeAttempt.decodeAttemptStarted(
                    validatedInput.imageSequenceDirectory(),
                    validatedInput.frameSet(),
                    validatedInput.warnings()
            );
            LOGGER.info(
                    "Reader decode attempt started sessionId={} frames={} inputDirectory={} warnings={}",
                    validatedInput.frameSet().sessionId(),
                    validatedInput.frameSet().frames().size(),
                    validatedInput.imageSequenceDirectory(),
                    validatedInput.warnings().size()
            );
            return attempt;
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

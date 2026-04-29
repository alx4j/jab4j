package com.alx4j.jab4j.reader.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarning;

/**
 * Structured result for one reader input validation and decode-attempt boundary.
 *
 * @param status decode-attempt status
 * @param inputDirectory normalized imageSequence directory when available
 * @param frameSet accepted source-neutral frame set when validation succeeds
 * @param warnings structured non-fatal warnings emitted during normalization
 * @param message human-readable result detail
 */
public record ReaderDecodeAttempt(
        ReaderDecodeStatus status,
        Path inputDirectory,
        Optional<ReaderFrameSet> frameSet,
        List<ReaderWarning> warnings,
        String message
) {

    /**
     * Creates a validated reader decode-attempt result.
     *
     * @param status decode-attempt status
     * @param inputDirectory normalized imageSequence directory when available
     * @param frameSet accepted frame set when validation succeeds
     * @param warnings structured warnings
     * @param message result detail
     */
    public ReaderDecodeAttempt {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(inputDirectory, "inputDirectory must not be null");
        frameSet = Objects.requireNonNull(frameSet, "frameSet must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        if (warnings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("warnings must not contain null values");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (status == ReaderDecodeStatus.DECODE_ATTEMPT_STARTED && frameSet.isEmpty()) {
            throw new IllegalArgumentException("accepted decode attempts must include a frameSet");
        }
        if (status == ReaderDecodeStatus.INPUT_REJECTED && frameSet.isPresent()) {
            throw new IllegalArgumentException("rejected decode attempts must not include a frameSet");
        }
    }

    /**
     * Creates an accepted result after the decode-attempt boundary is reached.
     *
     * @param inputDirectory normalized imageSequence directory
     * @param frameSet accepted frame set
     * @param warnings structured normalization warnings
     * @return accepted reader decode-attempt result
     */
    public static ReaderDecodeAttempt decodeAttemptStarted(
            Path inputDirectory,
            ReaderFrameSet frameSet,
            List<ReaderWarning> warnings
    ) {
        ReaderFrameSet acceptedFrameSet = Objects.requireNonNull(frameSet, "frameSet must not be null");
        return new ReaderDecodeAttempt(
                ReaderDecodeStatus.DECODE_ATTEMPT_STARTED,
                inputDirectory,
                Optional.of(acceptedFrameSet),
                warnings,
                "Decode attempt started after validating writer-exported imageSequence input"
        );
    }

    /**
     * Creates a rejected result for invalid reader input.
     *
     * @param inputDirectory normalized requested input directory
     * @param message rejection detail
     * @return rejected reader decode-attempt result
     */
    public static ReaderDecodeAttempt rejected(Path inputDirectory, String message) {
        return new ReaderDecodeAttempt(
                ReaderDecodeStatus.INPUT_REJECTED,
                inputDirectory,
                Optional.empty(),
                List.of(),
                message
        );
    }

    /**
     * Indicates whether the input passed validation and reached the decode-attempt boundary.
     *
     * @return true when validation succeeded
     */
    public boolean accepted() {
        return status == ReaderDecodeStatus.DECODE_ATTEMPT_STARTED;
    }
}

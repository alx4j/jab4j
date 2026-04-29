package com.alx4j.jab4j.reader.app;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarning;

/**
 * Structured result for one reader input validation and decode-attempt boundary.
 *
 * @param status decode-attempt status
 * @param inputDirectory normalized imageSequence directory when available
 * @param frameSet accepted source-neutral frame set when validation succeeds
 * @param decodedContent decoded frame content when content decoding succeeds
 * @param warnings structured non-fatal warnings emitted during normalization
 * @param message human-readable result detail
 */
public record ReaderDecodeAttempt(
        ReaderDecodeStatus status,
        Path inputDirectory,
        Optional<ReaderFrameSet> frameSet,
        Optional<DecodedFrameSetContent> decodedContent,
        List<ReaderWarning> warnings,
        String message
) {

    private static final EnumSet<ReaderDecodeStatus> CONTENT_FAILURE_STATUSES = EnumSet.of(
            ReaderDecodeStatus.UNSUPPORTED_LAYOUT,
            ReaderDecodeStatus.UNSUPPORTED_VERSION,
            ReaderDecodeStatus.CONTENT_CORRUPTED,
            ReaderDecodeStatus.INCONSISTENT_CONTENT
    );

    /**
     * Creates a validated reader decode-attempt result.
     *
     * @param status decode-attempt status
     * @param inputDirectory normalized imageSequence directory when available
     * @param frameSet accepted frame set when validation succeeds
     * @param decodedContent decoded frame content when content decoding succeeds
     * @param warnings structured warnings
     * @param message result detail
     */
    public ReaderDecodeAttempt {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(inputDirectory, "inputDirectory must not be null");
        frameSet = Objects.requireNonNull(frameSet, "frameSet must not be null");
        decodedContent = Objects.requireNonNull(decodedContent, "decodedContent must not be null");
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
        if (status == ReaderDecodeStatus.CONTENT_DECODED && (frameSet.isEmpty() || decodedContent.isEmpty())) {
            throw new IllegalArgumentException("decoded attempts must include a frameSet and decodedContent");
        }
        if (status != ReaderDecodeStatus.CONTENT_DECODED && decodedContent.isPresent()) {
            throw new IllegalArgumentException("only decoded attempts may include decodedContent");
        }
        if (status == ReaderDecodeStatus.INPUT_REJECTED && frameSet.isPresent()) {
            throw new IllegalArgumentException("rejected decode attempts must not include a frameSet");
        }
        if (CONTENT_FAILURE_STATUSES.contains(status) && frameSet.isEmpty()) {
            throw new IllegalArgumentException("content-decode failures must include the accepted frameSet");
        }
        if (CONTENT_FAILURE_STATUSES.contains(status) && decodedContent.isPresent()) {
            throw new IllegalArgumentException("content-decode failures must not include decodedContent");
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
                Optional.empty(),
                warnings,
                "Decode attempt started after validating writer-exported imageSequence input"
        );
    }

    /**
     * Creates a result containing successfully decoded frame content.
     *
     * @param inputDirectory normalized imageSequence directory
     * @param frameSet accepted frame set
     * @param decodedContent decoded frame content
     * @param warnings structured normalization warnings
     * @return successful reader decode result
     */
    public static ReaderDecodeAttempt contentDecoded(
            Path inputDirectory,
            ReaderFrameSet frameSet,
            DecodedFrameSetContent decodedContent,
            List<ReaderWarning> warnings
    ) {
        DecodedFrameSetContent content = Objects.requireNonNull(decodedContent, "decodedContent must not be null");
        return new ReaderDecodeAttempt(
                ReaderDecodeStatus.CONTENT_DECODED,
                inputDirectory,
                Optional.of(Objects.requireNonNull(frameSet, "frameSet must not be null")),
                Optional.of(content),
                warnings,
                "Frame content decoded from supported writer-exported PNG frames"
        );
    }

    /**
     * Creates a structured content-decode failure after input validation succeeded.
     *
     * @param inputDirectory normalized imageSequence directory
     * @param frameSet accepted frame set
     * @param warnings structured normalization warnings
     * @param status content-decode failure status
     * @param message failure detail
     * @return failed reader content-decode result
     */
    public static ReaderDecodeAttempt contentDecodeFailed(
            Path inputDirectory,
            ReaderFrameSet frameSet,
            List<ReaderWarning> warnings,
            ReaderDecodeStatus status,
            String message
    ) {
        if (!CONTENT_FAILURE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be a content-decode failure status");
        }
        return new ReaderDecodeAttempt(
                status,
                inputDirectory,
                Optional.of(Objects.requireNonNull(frameSet, "frameSet must not be null")),
                Optional.empty(),
                warnings,
                message
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
        return frameSet.isPresent();
    }

    /**
     * Indicates whether accepted input content was decoded successfully.
     *
     * @return true when content decoding succeeded
     */
    public boolean decoded() {
        return status == ReaderDecodeStatus.CONTENT_DECODED;
    }
}

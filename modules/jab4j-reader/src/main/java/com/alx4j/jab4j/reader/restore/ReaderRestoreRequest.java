package com.alx4j.jab4j.reader.restore;

import java.nio.file.Path;
import java.util.Objects;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;

/**
 * Source-neutral restore input after frame content has been decoded.
 *
 * @param sessionId accepted transfer session id
 * @param finalSessionDigest accepted final transfer session digest
 * @param decodedContent decoded payload envelopes from the reader content boundary
 * @param outputDirectory caller-selected restore output directory
 */
public record ReaderRestoreRequest(
        SessionId sessionId,
        String finalSessionDigest,
        DecodedFrameSetContent decodedContent,
        Path outputDirectory
) {

    /**
     * Creates a validated restore request.
     *
     * @param sessionId accepted transfer session id
     * @param finalSessionDigest accepted final transfer session digest
     * @param decodedContent decoded payload envelopes
     * @param outputDirectory caller-selected output directory
     */
    public ReaderRestoreRequest {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new IllegalArgumentException("finalSessionDigest must not be blank");
        }
        Objects.requireNonNull(decodedContent, "decodedContent must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
    }

    /**
     * Creates a restore request from an accepted frame set and decoded content.
     *
     * @param frameSet accepted frame set
     * @param decodedContent decoded payload envelopes
     * @param outputDirectory caller-selected output directory
     * @return restore request carrying accepted frame-set context
     */
    public static ReaderRestoreRequest from(
            ReaderFrameSet frameSet,
            DecodedFrameSetContent decodedContent,
            Path outputDirectory
    ) {
        ReaderFrameSet acceptedFrameSet = Objects.requireNonNull(frameSet, "frameSet must not be null");
        return new ReaderRestoreRequest(
                acceptedFrameSet.sessionId(),
                acceptedFrameSet.finalSessionDigest(),
                decodedContent,
                outputDirectory
        );
    }
}

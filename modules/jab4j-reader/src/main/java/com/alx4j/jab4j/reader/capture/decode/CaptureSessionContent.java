package com.alx4j.jab4j.reader.capture.decode;

import java.util.Objects;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;

/**
 * Complete decoded capture session content ready for source-neutral reader restore.
 *
 * @param sessionId decoded session id
 * @param finalSessionDigest final session digest recovered from SESSION_END content
 * @param decodedContent decoded frame-set content
 */
public record CaptureSessionContent(
        SessionId sessionId,
        String finalSessionDigest,
        DecodedFrameSetContent decodedContent
) {

    /**
     * Creates complete capture session content.
     *
     * @param sessionId decoded session id
     * @param finalSessionDigest recovered final session digest
     * @param decodedContent decoded frame-set content
     */
    public CaptureSessionContent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new IllegalArgumentException("finalSessionDigest must not be blank");
        }
        Objects.requireNonNull(decodedContent, "decodedContent must not be null");
        if (!sessionId.equals(decodedContent.sessionId())) {
            throw new IllegalArgumentException("decodedContent sessionId must match sessionId");
        }
    }
}

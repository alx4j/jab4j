package com.alx4j.jab4j.reader.capture.media.cv.legacy;

import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;

/**
 * Ranked source-space JAB frame candidate emitted by the region detector.
 *
 * @param profile likely rendered layout profile
 * @param corners source-space candidate corners
 * @param sourceLeftPx source-space left coordinate
 * @param sourceTopPx source-space top coordinate
 * @param sourceRightExclusivePx exclusive source-space right coordinate
 * @param sourceBottomExclusivePx exclusive source-space bottom coordinate
 * @param score JAB-specific evidence score
 */
record JabFrameCandidate(
        LayoutProfile profile,
        FrameCorners corners,
        int sourceLeftPx,
        int sourceTopPx,
        int sourceRightExclusivePx,
        int sourceBottomExclusivePx,
        JabFrameCandidateScore score
) {

    /**
     * Creates a validated source-space candidate.
     */
    JabFrameCandidate {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(corners, "corners must not be null");
        if (sourceLeftPx < 0 || sourceTopPx < 0) {
            throw new IllegalArgumentException("candidate source origin must be non-negative");
        }
        if (sourceRightExclusivePx <= sourceLeftPx || sourceBottomExclusivePx <= sourceTopPx) {
            throw new IllegalArgumentException("candidate source bounds must be positive");
        }
        Objects.requireNonNull(score, "score must not be null");
    }

    /**
     * Returns the candidate width in source pixels.
     *
     * @return source width
     */
    int widthPx() {
        return sourceRightExclusivePx - sourceLeftPx;
    }

    /**
     * Returns the candidate height in source pixels.
     *
     * @return source height
     */
    int heightPx() {
        return sourceBottomExclusivePx - sourceTopPx;
    }
}

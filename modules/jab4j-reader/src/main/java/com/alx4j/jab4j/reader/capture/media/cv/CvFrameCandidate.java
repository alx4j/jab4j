package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;

/**
 * Backend-neutral source-space JAB frame candidate emitted by a CV backend.
 *
 * @param layoutProfile likely rendered layout profile
 * @param frameCorners source-space candidate corners
 * @param sourceLeftPx source-space left coordinate
 * @param sourceTopPx source-space top coordinate
 * @param sourceRightExclusivePx exclusive source-space right coordinate
 * @param sourceBottomExclusivePx exclusive source-space bottom coordinate
 * @param score backend-neutral evidence score
 */
public record CvFrameCandidate(
        LayoutProfile layoutProfile,
        FrameCorners frameCorners,
        int sourceLeftPx,
        int sourceTopPx,
        int sourceRightExclusivePx,
        int sourceBottomExclusivePx,
        CvCandidateScore score
) {

    /**
     * Creates a validated source-space frame candidate.
     *
     * @param layoutProfile likely rendered layout profile
     * @param frameCorners source-space candidate corners
     * @param sourceLeftPx source-space left coordinate
     * @param sourceTopPx source-space top coordinate
     * @param sourceRightExclusivePx exclusive source-space right coordinate
     * @param sourceBottomExclusivePx exclusive source-space bottom coordinate
     * @param score backend-neutral evidence score
     */
    public CvFrameCandidate {
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        Objects.requireNonNull(frameCorners, "frameCorners must not be null");
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
    public int widthPx() {
        return sourceRightExclusivePx - sourceLeftPx;
    }

    /**
     * Returns the candidate height in source pixels.
     *
     * @return source height
     */
    public int heightPx() {
        return sourceBottomExclusivePx - sourceTopPx;
    }
}

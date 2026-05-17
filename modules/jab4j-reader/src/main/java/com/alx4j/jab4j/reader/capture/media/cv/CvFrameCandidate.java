package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.Objects;
import java.util.Optional;
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
 * @param geometrySource optional source of the candidate quadrilateral geometry
 * @param sourceRegionRank one-based rank of the source-space region before profile alternatives are expanded
 * @param profileAlternativeRank one-based rank of this profile alternative within the source region
 * @param profileAlternativeCount number of profile alternatives emitted for the source region
 */
public record CvFrameCandidate(
        LayoutProfile layoutProfile,
        FrameCorners frameCorners,
        int sourceLeftPx,
        int sourceTopPx,
        int sourceRightExclusivePx,
        int sourceBottomExclusivePx,
        CvCandidateScore score,
        Optional<String> geometrySource,
        int sourceRegionRank,
        int profileAlternativeRank,
        int profileAlternativeCount
) {

    /**
     * Creates a source-space frame candidate without explicit ambiguity metadata.
     *
     * @param layoutProfile likely rendered layout profile
     * @param frameCorners source-space candidate corners
     * @param sourceLeftPx source-space left coordinate
     * @param sourceTopPx source-space top coordinate
     * @param sourceRightExclusivePx exclusive source-space right coordinate
     * @param sourceBottomExclusivePx exclusive source-space bottom coordinate
     * @param score backend-neutral evidence score
     */
    public CvFrameCandidate(
            LayoutProfile layoutProfile,
            FrameCorners frameCorners,
            int sourceLeftPx,
            int sourceTopPx,
            int sourceRightExclusivePx,
            int sourceBottomExclusivePx,
            CvCandidateScore score
    ) {
        this(
                layoutProfile,
                frameCorners,
                sourceLeftPx,
                sourceTopPx,
                sourceRightExclusivePx,
                sourceBottomExclusivePx,
                score,
                Optional.empty(),
                1,
                1,
                1
        );
    }

    /**
     * Creates a source-space frame candidate with profile-alternative metadata and no explicit geometry source.
     *
     * @param layoutProfile likely rendered layout profile
     * @param frameCorners source-space candidate corners
     * @param sourceLeftPx source-space left coordinate
     * @param sourceTopPx source-space top coordinate
     * @param sourceRightExclusivePx exclusive source-space right coordinate
     * @param sourceBottomExclusivePx exclusive source-space bottom coordinate
     * @param score backend-neutral evidence score
     * @param sourceRegionRank one-based rank of the source-space region before profile alternatives are expanded
     * @param profileAlternativeRank one-based rank of this profile alternative within the source region
     * @param profileAlternativeCount number of profile alternatives emitted for the source region
     */
    public CvFrameCandidate(
            LayoutProfile layoutProfile,
            FrameCorners frameCorners,
            int sourceLeftPx,
            int sourceTopPx,
            int sourceRightExclusivePx,
            int sourceBottomExclusivePx,
            CvCandidateScore score,
            int sourceRegionRank,
            int profileAlternativeRank,
            int profileAlternativeCount
    ) {
        this(
                layoutProfile,
                frameCorners,
                sourceLeftPx,
                sourceTopPx,
                sourceRightExclusivePx,
                sourceBottomExclusivePx,
                score,
                Optional.empty(),
                sourceRegionRank,
                profileAlternativeRank,
                profileAlternativeCount
        );
    }

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
     * @param geometrySource optional source of the candidate quadrilateral geometry
     * @param sourceRegionRank one-based rank of the source-space region before profile alternatives are expanded
     * @param profileAlternativeRank one-based rank of this profile alternative within the source region
     * @param profileAlternativeCount number of profile alternatives emitted for the source region
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
        geometrySource = Objects.requireNonNull(geometrySource, "geometrySource must not be null");
        geometrySource.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("geometrySource must not be blank when present");
            }
        });
        if (sourceRegionRank <= 0) {
            throw new IllegalArgumentException("sourceRegionRank must be positive");
        }
        if (profileAlternativeRank <= 0 || profileAlternativeCount <= 0) {
            throw new IllegalArgumentException("profile alternative ranks must be positive");
        }
        if (profileAlternativeRank > profileAlternativeCount) {
            throw new IllegalArgumentException("profileAlternativeRank must not exceed profileAlternativeCount");
        }
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

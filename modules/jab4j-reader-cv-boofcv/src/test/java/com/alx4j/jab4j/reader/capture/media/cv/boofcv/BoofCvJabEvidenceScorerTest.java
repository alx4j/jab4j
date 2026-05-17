package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CvCandidateScore;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;

@DisplayName("BoofCV JAB evidence scorer")
class BoofCvJabEvidenceScorerTest {

    private final CaptureRenderedLayoutCatalog layoutCatalog = new CaptureRenderedLayoutCatalog();
    private final BoofCvJabEvidenceScorer scorer =
            new BoofCvJabEvidenceScorer(layoutCatalog, new FixedLayoutPlanner());
    private final LayoutProfile layoutProfile = layoutCatalog.profiles().get(1);

    @Test
    @DisplayName("Plausible validation admits narrowly weak grid evidence")
    void plausibleValidationAdmitsNarrowlyWeakGridEvidence() {
        CvFrameCandidate nearMiss = candidate(0.119d);
        CvFrameCandidate weakerGrid = candidate(0.114d);

        assertAll(
                () -> assertEquals(
                        BoofCvJabEvidenceScorer.AdmissionBand.PLAUSIBLE_VALIDATION,
                        scorer.admissionBand(nearMiss)
                ),
                () -> assertEquals(
                        BoofCvJabEvidenceScorer.RejectionReason.NONE,
                        scorer.rejectionReason(nearMiss)
                ),
                () -> assertEquals(
                        BoofCvJabEvidenceScorer.AdmissionBand.REJECTED,
                        scorer.admissionBand(weakerGrid)
                ),
                () -> assertEquals(
                        BoofCvJabEvidenceScorer.RejectionReason.WEAK_GRID,
                        scorer.rejectionReason(weakerGrid)
                )
        );
    }

    @Test
    @DisplayName("One source region emits ranked alternatives for plausible supported profiles")
    void oneSourceRegionEmitsRankedAlternativesForPlausibleSupportedProfiles() {
        MediaInputFrame frame = mediaFrame(3000, 1700);
        BoofCvCandidateRegionProposer.CandidateRegion topRegion = region(100, 100, 2660, 1540);
        BoofCvCandidateRegionProposer.CandidateRegion nextRegion = region(120, 120, 2040, 1200);

        List<CvFrameCandidate> candidates = scorer.scoreRegions(frame, List.of(topRegion, nextRegion));

        assertAll(
                () -> assertTrue(candidates.size() >= 5),
                () -> assertEquals(1, candidates.get(0).sourceRegionRank()),
                () -> assertEquals(1, candidates.get(0).profileAlternativeRank()),
                () -> assertEquals(3, candidates.get(0).profileAlternativeCount()),
                () -> assertEquals(1, candidates.get(1).sourceRegionRank()),
                () -> assertEquals(2, candidates.get(1).profileAlternativeRank()),
                () -> assertEquals(1, candidates.get(2).sourceRegionRank()),
                () -> assertEquals(3, candidates.get(2).profileAlternativeRank()),
                () -> assertEquals(2, candidates.get(3).sourceRegionRank()),
                () -> assertTrue(candidates.subList(0, 3).stream()
                        .map(candidate -> candidate.layoutProfile().profileId())
                        .toList()
                        .containsAll(List.of(
                                "desktop-1440p-balanced",
                                "desktop-1080p-safe",
                                "debug-low-density"
                        )))
        );
    }

    @Test
    @DisplayName("Oversized camera regions still emit lower-density profile alternatives")
    void oversizedCameraRegionsStillEmitLowerDensityProfileAlternatives() {
        MediaInputFrame frame = mediaFrame(4032, 3024);
        BoofCvCandidateRegionProposer.CandidateRegion monitorRegion = region(448, 788, 3425, 2428);

        List<CvFrameCandidate> candidates = scorer.scoreRegions(frame, List.of(monitorRegion));

        assertAll(
                () -> assertEquals(3, candidates.size()),
                () -> assertEquals(3, candidates.get(0).profileAlternativeCount()),
                () -> assertTrue(candidates.stream()
                        .map(candidate -> candidate.layoutProfile().profileId())
                        .toList()
                        .containsAll(List.of(
                                "desktop-1080p-safe",
                                "desktop-1440p-balanced",
                                "debug-low-density"
                        )))
        );
    }

    private CvFrameCandidate candidate(double gridScore) {
        return new CvFrameCandidate(
                layoutProfile,
                new FrameCorners(100.0d, 100.0d, 1100.0d, 110.0d, 1110.0d, 700.0d, 90.0d, 710.0d),
                100,
                100,
                1100,
                700,
                new CvCandidateScore(
                        0.376d,
                        0.33d,
                        0.20d,
                        0.309d,
                        0.399d,
                        gridScore,
                        0.95d,
                        CvCandidateScore.NOT_MEASURED,
                        CvCandidateScore.NOT_MEASURED,
                        CvCandidateScore.NOT_MEASURED
                )
        );
    }

    private MediaInputFrame mediaFrame(int widthPixels, int heightPixels) {
        return new MediaInputFrame(
                "boofcv-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                widthPixels,
                heightPixels,
                "png",
                "source-hash",
                new int[widthPixels * heightPixels]
        );
    }

    private BoofCvCandidateRegionProposer.CandidateRegion region(
            int left,
            int top,
            int rightExclusive,
            int bottomExclusive
    ) {
        return new BoofCvCandidateRegionProposer.CandidateRegion(
                left,
                top,
                rightExclusive,
                bottomExclusive,
                16,
                new FrameCorners(
                        left,
                        top,
                        rightExclusive,
                        top,
                        rightExclusive,
                        bottomExclusive,
                        left,
                        bottomExclusive
                )
        );
    }
}

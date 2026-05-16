package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.cv.CvCandidateScore;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
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
}

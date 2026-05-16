package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.cv.CvBackendIdentity;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;

/**
 * Optional shaded BoofCV backend registered through the reader CV service boundary.
 *
 * <p>The backend uses BoofCV only for low-level source-region proposal, then accepts candidates only after
 * JAB-specific layout, border, sync-band, and tile-grid evidence passes conservative thresholds.</p>
 */
public final class BoofCvCaptureMediaCvBackend implements CaptureMediaCvBackend {

    private static final double MIN_FRAME_COVERAGE_RATIO = 0.20d;
    private static final String BACKEND_ID = new String(new char[] { 'b', 'o', 'o', 'f', 'c', 'v' });
    private static final String NOT_FOUND_MESSAGE =
            "Media normalization did not find a clean supported rendered frame region";
    private static final String FAILURE_MESSAGE = "Capture-media CV backend failed while evaluating the frame";
    private static final CvBackendIdentity IDENTITY = new CvBackendIdentity(
            BACKEND_ID,
            Optional.of("com.alx4j:jab4j-reader-cv-boofcv"),
            Optional.ofNullable(BoofCvCaptureMediaCvBackend.class.getPackage().getImplementationVersion()),
            List.of(
                    "optional-shaded-adapter",
                    "argb-to-planar-rgb-copy",
                    "grayscale-threshold-contour-candidate-proposal",
                    "reader-layout-profile-scoring",
                    "jab-border-sync-grid-evidence",
                    "source-space-cv-frame-candidates",
                    "stable-backend-failure-mapping",
                    "no-default-selection"
            )
    );

    private final BoofCvCandidateRegionProposer regionProposer;
    private final BoofCvJabEvidenceScorer evidenceScorer;
    private final BoofCvPerspectiveCorrector perspectiveCorrector;
    private final boolean normalizeAcceptedCandidates;

    /**
     * Creates a BoofCV backend instance for explicit developer selection.
     */
    public BoofCvCaptureMediaCvBackend() {
        this(new CaptureRenderedLayoutCatalog(), new FixedLayoutPlanner(), false);
    }

    /**
     * Creates an explicit test/manual variant that emits backend-normalized frames instead of source-space candidates.
     *
     * @return BoofCV backend with perspective correction enabled for accepted candidates
     */
    static BoofCvCaptureMediaCvBackend withPerspectiveCorrection() {
        return new BoofCvCaptureMediaCvBackend(
                new CaptureRenderedLayoutCatalog(),
                new FixedLayoutPlanner(),
                true
        );
    }

    private BoofCvCaptureMediaCvBackend(
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            boolean normalizeAcceptedCandidates
    ) {
        this.regionProposer = new BoofCvCandidateRegionProposer();
        this.evidenceScorer = new BoofCvJabEvidenceScorer(layoutCatalog, layoutPlanner);
        this.perspectiveCorrector = new BoofCvPerspectiveCorrector();
        this.normalizeAcceptedCandidates = normalizeAcceptedCandidates;
    }

    /**
     * Returns stable metadata for the optional shaded BoofCV adapter.
     *
     * @return backend identity metadata
     */
    @Override
    public CvBackendIdentity identity() {
        return IDENTITY;
    }

    /**
     * Detects JAB frame candidates with BoofCV proposal evidence and reader-owned layout scoring.
     *
     * @param frame decoded media input frame
     * @return accepted source-space candidates, stable rejection, or backend-failure result
     */
    @Override
    public CvDetectionResult detect(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        try {
            BoofCvCandidateRegionProposer.ProposalResult proposal = regionProposer.propose(frame);
            List<CvFrameCandidate> scoredCandidates = evidenceScorer.scoreRegions(frame, proposal.regions());
            List<CvFrameCandidate> evidenceCandidates = evidenceScorer.distinctEvidenceCandidates(scoredCandidates);
            Map<String, Double> metrics = metrics(proposal, scoredCandidates, evidenceCandidates);
            if (evidenceCandidates.isEmpty()) {
                return rejectedWithCandidates(scoredCandidates, metrics, NOT_FOUND_MESSAGE);
            }

            List<CvFrameCandidate> acceptedCandidates = evidenceCandidates.stream()
                    .filter(candidate -> candidate.score().frameCoverageRatio() >= MIN_FRAME_COVERAGE_RATIO)
                    .toList();
            if (acceptedCandidates.isEmpty()) {
                return CvDetectionResult.tooSmall(
                        evidenceCandidates,
                        metrics,
                        "Detected JAB frame region is below the minimum generated coverage threshold"
                );
            }
            return acceptedResult(frame, acceptedCandidates, metrics);
        } catch (RuntimeException exception) {
            return CvDetectionResult.backendFailure(Map.of("backendFailureCount", 1.0d), FAILURE_MESSAGE);
        }
    }

    private CvDetectionResult acceptedResult(
            MediaInputFrame frame,
            List<CvFrameCandidate> acceptedCandidates,
            Map<String, Double> metrics
    ) {
        if (normalizeAcceptedCandidates) {
            List<CvNormalizedFrame> normalizedFrames = acceptedCandidates.stream()
                    .map(candidate -> correctPerspective(frame, candidate))
                    .toList();
            return CvDetectionResult.acceptedNormalizedFrames(normalizedFrames);
        }
        return new CvDetectionResult(
                CvDetectionStatus.ACCEPTED,
                acceptedCandidates,
                List.of(),
                Optional.empty(),
                acceptedMetrics(metrics, acceptedCandidates.size()),
                "CV backend accepted frame candidates"
        );
    }

    private CvNormalizedFrame correctPerspective(MediaInputFrame frame, CvFrameCandidate candidate) {
        return perspectiveCorrector.correct(
                frame,
                candidate.layoutProfile(),
                candidate.frameCorners(),
                CaptureMediaQualityMetrics.perspectiveCorrected(
                        candidate.score().frameCoverageRatio(),
                        candidate.score().skewScore()
                )
        );
    }

    private CvDetectionResult rejectedWithCandidates(
            List<CvFrameCandidate> candidates,
            Map<String, Double> metrics,
            String message
    ) {
        return new CvDetectionResult(
                CvDetectionStatus.REJECTED,
                candidates,
                List.of(),
                Optional.of(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND),
                metrics,
                message
        );
    }

    private Map<String, Double> metrics(
            BoofCvCandidateRegionProposer.ProposalResult proposal,
            List<CvFrameCandidate> scoredCandidates,
            List<CvFrameCandidate> evidenceCandidates
    ) {
        Map<String, Double> metrics = new LinkedHashMap<>(proposal.metrics());
        metrics.put("boofCvScoredCandidateCount", (double) scoredCandidates.size());
        metrics.put("boofCvJabEvidenceCandidateCount", (double) evidenceCandidates.size());
        scoredCandidates.stream().findFirst().ifPresent(candidate -> {
            metrics.put("boofCvSelectedLeftPx", (double) candidate.sourceLeftPx());
            metrics.put("boofCvSelectedTopPx", (double) candidate.sourceTopPx());
            metrics.put("boofCvSelectedRightExclusivePx", (double) candidate.sourceRightExclusivePx());
            metrics.put("boofCvSelectedBottomExclusivePx", (double) candidate.sourceBottomExclusivePx());
            metrics.put("boofCvSelectedCornerTopLeftX", candidate.frameCorners().topLeftX());
            metrics.put("boofCvSelectedCornerTopLeftY", candidate.frameCorners().topLeftY());
            metrics.put("boofCvSelectedCornerBottomRightX", candidate.frameCorners().bottomRightX());
            metrics.put("boofCvSelectedCornerBottomRightY", candidate.frameCorners().bottomRightY());
            metrics.put("boofCvSelectedCandidateScore", candidate.score().totalScore());
            metrics.put("boofCvSelectedFrameCoverageRatio", candidate.score().frameCoverageRatio());
            metrics.put("boofCvSelectedBorderContrastScore", candidate.score().borderContrastScore());
            metrics.put("boofCvSelectedSyncBandScore", candidate.score().syncBandScore());
            metrics.put("boofCvSelectedGridScore", candidate.score().gridScore());
            metrics.put("boofCvSelectedLayoutAspectScore", candidate.score().layoutAspectScore());
        });
        return Map.copyOf(metrics);
    }

    private Map<String, Double> acceptedMetrics(Map<String, Double> metrics, int acceptedCount) {
        Map<String, Double> acceptedMetrics = new LinkedHashMap<>(metrics);
        acceptedMetrics.put("boofCvAcceptedCandidateCount", (double) acceptedCount);
        return Map.copyOf(acceptedMetrics);
    }
}

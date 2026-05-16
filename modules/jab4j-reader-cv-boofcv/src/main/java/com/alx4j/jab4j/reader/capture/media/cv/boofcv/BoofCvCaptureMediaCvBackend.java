package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
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

    private static final int MAX_PLAUSIBLE_VALIDATION_CANDIDATES = 2;
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
            List<CvFrameCandidate> strictEvidenceCandidates =
                    evidenceScorer.distinctEvidenceCandidates(scoredCandidates);
            List<CvFrameCandidate> plausibleValidationCandidates =
                    evidenceScorer.distinctPlausibleValidationCandidates(
                            scoredCandidates,
                            strictEvidenceCandidates,
                            MAX_PLAUSIBLE_VALIDATION_CANDIDATES
                    );
            List<CvFrameCandidate> acceptedStrictCandidates = strictEvidenceCandidates.stream()
                    .filter(this::hasMinimumFrameCoverage)
                    .toList();
            List<CvFrameCandidate> acceptedCandidates = acceptedCandidates(
                    acceptedStrictCandidates,
                    plausibleValidationCandidates
            );
            Map<String, Double> metrics = metrics(
                    proposal,
                    scoredCandidates,
                    strictEvidenceCandidates,
                    plausibleValidationCandidates,
                    acceptedCandidates
            );
            if (acceptedCandidates.isEmpty() && strictEvidenceCandidates.isEmpty()) {
                return rejectedWithCandidates(scoredCandidates, metrics, NOT_FOUND_MESSAGE);
            }

            if (acceptedCandidates.isEmpty()) {
                return CvDetectionResult.tooSmall(
                        strictEvidenceCandidates,
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
            return new CvDetectionResult(
                    CvDetectionStatus.ACCEPTED,
                    List.of(),
                    normalizedFrames,
                    Optional.empty(),
                    metrics,
                    "CV backend accepted normalized frames"
            );
        }
        return new CvDetectionResult(
                CvDetectionStatus.ACCEPTED,
                acceptedCandidates,
                List.of(),
                Optional.empty(),
                metrics,
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
            List<CvFrameCandidate> strictEvidenceCandidates,
            List<CvFrameCandidate> plausibleValidationCandidates,
            List<CvFrameCandidate> acceptedCandidates
    ) {
        Map<String, Double> metrics = new LinkedHashMap<>(proposal.metrics());
        metrics.put("boofCvScoredCandidateCount", (double) scoredCandidates.size());
        metrics.put("boofCvJabEvidenceCandidateCount", (double) strictEvidenceCandidates.size());
        metrics.put("boofCvStrictEvidenceCandidateCount", (double) strictEvidenceCandidates.size());
        metrics.put("boofCvPlausibleValidationCandidateCount", (double) plausibleValidationCandidates.size());
        int rejectedCandidateCount = Math.max(
                0,
                scoredCandidates.size() - strictEvidenceCandidates.size() - plausibleValidationCandidates.size()
        );
        metrics.put("boofCvRejectedScoredCandidateCount", (double) rejectedCandidateCount);
        metrics.put("boofCvRejectedCandidateCount", (double) rejectedCandidateCount);
        metrics.put("boofCvAcceptedCandidateCount", (double) acceptedCandidates.size());
        metrics.put("boofCvSelectedAdmissionBandCode", selectedAdmissionBand(scoredCandidates, acceptedCandidates)
                .code());
        metrics.put("boofCvSelectedRejectionReasonCode", selectedRejectionReason(scoredCandidates, acceptedCandidates)
                .code());
        selectedCandidate(scoredCandidates, acceptedCandidates).ifPresent(candidate -> {
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

    private List<CvFrameCandidate> acceptedCandidates(
            List<CvFrameCandidate> acceptedStrictCandidates,
            List<CvFrameCandidate> plausibleValidationCandidates
    ) {
        return Stream.concat(
                        acceptedStrictCandidates.stream(),
                        plausibleValidationCandidates.stream()
                )
                .toList();
    }

    private boolean hasMinimumFrameCoverage(CvFrameCandidate candidate) {
        return candidate.score().frameCoverageRatio() >= BoofCvJabEvidenceScorer.MIN_FRAME_COVERAGE_RATIO;
    }

    private BoofCvJabEvidenceScorer.AdmissionBand selectedAdmissionBand(
            List<CvFrameCandidate> scoredCandidates,
            List<CvFrameCandidate> acceptedCandidates
    ) {
        return selectedCandidate(scoredCandidates, acceptedCandidates)
                .map(evidenceScorer::admissionBand)
                .orElse(BoofCvJabEvidenceScorer.AdmissionBand.REJECTED);
    }

    private BoofCvJabEvidenceScorer.RejectionReason selectedRejectionReason(
            List<CvFrameCandidate> scoredCandidates,
            List<CvFrameCandidate> acceptedCandidates
    ) {
        if (!acceptedCandidates.isEmpty()) {
            return BoofCvJabEvidenceScorer.RejectionReason.NONE;
        }
        return selectedCandidate(scoredCandidates, acceptedCandidates)
                .map(evidenceScorer::rejectionReason)
                .orElse(BoofCvJabEvidenceScorer.RejectionReason.NO_PLAUSIBLE_PROPOSAL);
    }

    private Optional<CvFrameCandidate> selectedCandidate(
            List<CvFrameCandidate> scoredCandidates,
            List<CvFrameCandidate> acceptedCandidates
    ) {
        if (!acceptedCandidates.isEmpty()) {
            return Optional.of(acceptedCandidates.get(0));
        }
        return scoredCandidates.stream().findFirst();
    }
}

package com.alx4j.jab4j.reader.capture.media.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPolygon;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.CoordinateObservationSource;
import com.alx4j.jab4j.reader.capture.media.evidence.FinderRole;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidenceStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureType;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

@DisplayName("Capture media geometry fitter")
class CaptureMediaGeometryFitterTest {

    private static final int FRAME_WIDTH = 1000;
    private static final int FRAME_HEIGHT = 800;
    private static final String PIXEL_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final String LAYOUT_PROFILE_ID = "geometry-test-layout";
    private static final Rect TOP_LEFT = new Rect(100.0d, 100.0d, 130.0d, 130.0d);
    private static final Rect TOP_RIGHT = new Rect(700.0d, 100.0d, 730.0d, 130.0d);
    private static final Rect BOTTOM_LEFT = new Rect(100.0d, 500.0d, 130.0d, 530.0d);
    private static final Rect BOTTOM_RIGHT = new Rect(700.0d, 500.0d, 730.0d, 530.0d);

    private final CaptureMediaGeometryFitter fitter = new CaptureMediaGeometryFitter();

    @Test
    @DisplayName("Accepts independent source-space control points")
    void acceptsIndependentSourceSpaceControlPoints() {
        GeometryFitEvidence evidence = fitter.fit(normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT)),
                detectedPatternEvidence(sourceSpaceFeatures()));

        GeometryCandidateEvidence candidate = evidence.retainedCandidates().get(0);
        assertAll(
                () -> assertEquals(GeometryFitStatus.ACCEPTED, evidence.status()),
                () -> assertEquals(1, evidence.retainedCandidates().size()),
                () -> assertTrue(evidence.selectedGeometryCandidateId().isPresent()),
                () -> assertTrue(candidate.retainedForSampling()),
                () -> assertTrue(candidate.invertible()),
                () -> assertEquals(16, candidate.matchedPointCount()),
                () -> assertEquals(0.0d, candidate.reprojectionMetrics().maxErrorPixels(), 0.000001d),
                () -> assertEquals(0.0d, candidate.reprojectionMetrics().maxErrorModules(), 0.000001d),
                () -> assertEquals(1.0d, candidate.score(), 0.000001d)
        );
    }

    @Test
    @DisplayName("Withholds fits with too few control points")
    void withholdsFitsWithTooFewControlPoints() {
        PatternFeatureEvidence triangle = feature(
                FinderRole.TOP_LEFT,
                new CanonicalPolygon(List.of(
                        new CanonicalPoint(100.0d, 100.0d),
                        new CanonicalPoint(130.0d, 100.0d),
                        new CanonicalPoint(115.0d, 130.0d)
                )),
                new SourcePolygon(List.of(
                        affinePoint(100.0d, 100.0d),
                        affinePoint(130.0d, 100.0d),
                        affinePoint(115.0d, 130.0d)
                )),
                CoordinateObservationSource.SOURCE_SPACE
        );

        GeometryFitEvidence evidence = fitter.fit(normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT)),
                detectedPatternEvidence(List.of(triangle)));

        assertAll(
                () -> assertEquals(GeometryFitStatus.WITHHELD, evidence.status()),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.TOO_FEW_POINTS)),
                () -> assertFalse(evidence.selectedGeometryCandidateId().isPresent()),
                () -> assertEquals(3, evidence.retainedCandidates().get(0).matchedPointCount())
        );
    }

    @Test
    @DisplayName("Rejects duplicate control points")
    void rejectsDuplicateControlPoints() {
        PatternFeatureEvidence duplicate = feature(
                FinderRole.TOP_LEFT,
                new CanonicalPolygon(List.of(
                        new CanonicalPoint(100.0d, 100.0d),
                        new CanonicalPoint(130.0d, 100.0d),
                        new CanonicalPoint(130.0d, 100.0d),
                        new CanonicalPoint(100.0d, 130.0d)
                )),
                sourcePolygon(TOP_LEFT),
                CoordinateObservationSource.SOURCE_SPACE
        );

        GeometryFitEvidence evidence = fitter.fit(normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT)),
                detectedPatternEvidence(List.of(duplicate)));

        assertAll(
                () -> assertEquals(GeometryFitStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.DUPLICATE_POINTS)),
                () -> assertTrue(evidence.retainedCandidates().get(0).degeneracyFlags().contains("DUPLICATE_POINTS"))
        );
    }

    @Test
    @DisplayName("Rejects collinear control points")
    void rejectsCollinearControlPoints() {
        PatternFeatureEvidence collinear = feature(
                FinderRole.TOP_LEFT,
                new CanonicalPolygon(List.of(
                        new CanonicalPoint(100.0d, 100.0d),
                        new CanonicalPoint(130.0d, 100.0d),
                        new CanonicalPoint(160.0d, 100.0d),
                        new CanonicalPoint(190.0d, 100.0d)
                )),
                new SourcePolygon(List.of(
                        affinePoint(100.0d, 100.0d),
                        affinePoint(130.0d, 100.0d),
                        affinePoint(160.0d, 100.0d),
                        affinePoint(190.0d, 100.0d)
                )),
                CoordinateObservationSource.SOURCE_SPACE
        );

        GeometryFitEvidence evidence = fitter.fit(normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT)),
                detectedPatternEvidence(List.of(collinear)));

        assertAll(
                () -> assertEquals(GeometryFitStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.DEGENERATE_POINTS)),
                () -> assertTrue(evidence.retainedCandidates().get(0).degeneracyFlags().contains("DEGENERATE_POINTS"))
        );
    }

    @Test
    @DisplayName("Rejects mirrored or swapped finder roles")
    void rejectsMirroredOrSwappedFinderRoles() {
        List<PatternFeatureEvidence> features = List.of(
                feature(FinderRole.TOP_LEFT, canonicalPolygon(TOP_LEFT), sourcePolygon(BOTTOM_RIGHT),
                        CoordinateObservationSource.SOURCE_SPACE),
                feature(FinderRole.TOP_RIGHT, canonicalPolygon(TOP_RIGHT), sourcePolygon(BOTTOM_LEFT),
                        CoordinateObservationSource.SOURCE_SPACE),
                feature(FinderRole.BOTTOM_LEFT, canonicalPolygon(BOTTOM_LEFT), sourcePolygon(TOP_RIGHT),
                        CoordinateObservationSource.SOURCE_SPACE),
                feature(FinderRole.BOTTOM_RIGHT, canonicalPolygon(BOTTOM_RIGHT), sourcePolygon(TOP_LEFT),
                        CoordinateObservationSource.SOURCE_SPACE)
        );

        GeometryFitEvidence evidence = fitter.fit(normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT)),
                detectedPatternEvidence(features));

        assertAll(
                () -> assertEquals(GeometryFitStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.MIRRORED_OR_SWAPPED_ROLES)),
                () -> assertTrue(evidence.retainedCandidates().get(0).degeneracyFlags()
                        .contains("MIRRORED_OR_SWAPPED_ROLES"))
        );
    }

    @Test
    @DisplayName("Rejects high reprojection error and keeps retained candidates stable")
    void rejectsHighReprojectionErrorAndKeepsRetainedCandidatesStable() {
        List<PatternFeatureEvidence> features = new ArrayList<>(sourceSpaceFeatures());
        List<SourcePoint> noisy = new ArrayList<>(sourcePolygon(BOTTOM_RIGHT).vertices());
        SourcePoint shifted = noisy.get(2);
        noisy.set(2, new SourcePoint(shifted.x() + 40.0d, shifted.y() + 40.0d));
        features.set(3, feature(FinderRole.BOTTOM_RIGHT, canonicalPolygon(BOTTOM_RIGHT), new SourcePolygon(noisy),
                CoordinateObservationSource.SOURCE_SPACE));

        PatternEvidence patternEvidence = detectedPatternEvidence(features);
        GeometryFitEvidence first = fitter.fit(normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT)),
                patternEvidence);
        GeometryFitEvidence second = fitter.fit(normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT)),
                patternEvidence);

        assertAll(
                () -> assertEquals(GeometryFitStatus.REJECTED, first.status()),
                () -> assertTrue(first.reasonCodes().contains(CaptureMediaEvidenceReasonCode.HIGH_REPROJECTION_ERROR)),
                () -> assertTrue(first.retainedCandidates().size() <= CaptureMediaCandidateId.MAX_GEOMETRY_CANDIDATE_RANK),
                () -> assertEquals(candidateScores(first), candidateScores(second)),
                () -> assertEquals(candidateStatuses(first), candidateStatuses(second))
        );
    }

    @Test
    @DisplayName("Withholds normalized-candidate-only geometry while retaining metrics")
    void withholdsNormalizedCandidateOnlyGeometryWhileRetainingMetrics() {
        FrameCorners sourceCorners = new FrameCorners(
                20.0d,
                30.0d,
                920.0d,
                50.0d,
                950.0d,
                760.0d,
                40.0d,
                730.0d
        );
        GeometryFitEvidence evidence = fitter.fit(normalizedFrame(sourceCorners),
                detectedPatternEvidence(normalizedCandidateFeatures()));

        GeometryCandidateEvidence candidate = evidence.retainedCandidates().get(0);
        assertAll(
                () -> assertEquals(GeometryFitStatus.WITHHELD, evidence.status()),
                () -> assertFalse(evidence.selectedGeometryCandidateId().isPresent()),
                () -> assertFalse(candidate.retainedForSampling()),
                () -> assertTrue(candidate.invertible()),
                () -> assertEquals(0.0d, candidate.reprojectionMetrics().maxErrorPixels(), 0.000001d),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NORMALIZED_CANDIDATE_ONLY)),
                () -> assertTrue(candidate.degeneracyFlags().contains("NORMALIZED_CANDIDATE_ONLY"))
        );
    }

    @Test
    @DisplayName("Bridges BoofCV candidate corners into provisional sampling geometry when pattern evidence is weak")
    void bridgesBoofCvCandidateCornersIntoProvisionalSamplingGeometryWhenPatternEvidenceIsWeak() {
        FrameCorners sourceCorners = new FrameCorners(
                20.0d,
                30.0d,
                920.0d,
                50.0d,
                950.0d,
                760.0d,
                40.0d,
                730.0d
        );

        GeometryFitEvidence evidence = fitter.fit(
                normalizedFrame(sourceCorners, Optional.of("boofcv-fitted-quadrilateral")),
                weakPatternEvidence()
        );

        GeometryCandidateEvidence candidate = evidence.retainedCandidates().get(0);
        assertAll(
                () -> assertEquals(GeometryFitStatus.ACCEPTED, evidence.status()),
                () -> assertTrue(evidence.selectedGeometryCandidateId().isPresent()),
                () -> assertEquals(1, evidence.retainedCandidates().size()),
                () -> assertEquals(GeometryFitStatus.ACCEPTED, candidate.status()),
                () -> assertTrue(candidate.retainedForSampling()),
                () -> assertTrue(candidate.invertible()),
                () -> assertEquals(4, candidate.matchedPointCount()),
                () -> assertEquals(0.25d, candidate.score(), 0.000001d),
                () -> assertTrue(candidate.canonicalCoordinateSystem()
                        .startsWith("provisional-cv-normalized-frame-pixels:")),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.PROVISIONAL_CV_GEOMETRY)),
                () -> assertTrue(candidate.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE))
        );
    }

    @Test
    @DisplayName("Does not bridge BoofCV corners over rejected strict pattern geometry")
    void doesNotBridgeBoofCvCornersOverRejectedStrictPatternGeometry() {
        PatternFeatureEvidence duplicate = feature(
                FinderRole.TOP_LEFT,
                new CanonicalPolygon(List.of(
                        new CanonicalPoint(100.0d, 100.0d),
                        new CanonicalPoint(130.0d, 100.0d),
                        new CanonicalPoint(130.0d, 100.0d),
                        new CanonicalPoint(100.0d, 130.0d)
                )),
                sourcePolygon(TOP_LEFT),
                CoordinateObservationSource.SOURCE_SPACE
        );

        GeometryFitEvidence evidence = fitter.fit(
                normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                        Optional.of("boofcv-fitted-quadrilateral")),
                detectedPatternEvidence(List.of(duplicate))
        );

        assertAll(
                () -> assertEquals(GeometryFitStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.DUPLICATE_POINTS)),
                () -> assertFalse(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.PROVISIONAL_CV_GEOMETRY)),
                () -> assertFalse(evidence.retainedCandidates().get(0).retainedForSampling())
        );
    }

    @Test
    @DisplayName("Does not bridge BoofCV corners over ambiguous pattern evidence")
    void doesNotBridgeBoofCvCornersOverAmbiguousPatternEvidence() {
        GeometryFitEvidence evidence = fitter.fit(
                normalizedFrame(FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                        Optional.of("boofcv-fitted-quadrilateral")),
                ambiguousPatternEvidence()
        );

        assertAll(
                () -> assertEquals(GeometryFitStatus.NOT_AVAILABLE, evidence.status()),
                () -> assertFalse(evidence.selectedGeometryCandidateId().isPresent()),
                () -> assertTrue(evidence.retainedCandidates().isEmpty()),
                () -> assertFalse(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.PROVISIONAL_CV_GEOMETRY))
        );
    }

    private List<Double> candidateScores(GeometryFitEvidence evidence) {
        return evidence.retainedCandidates().stream().map(GeometryCandidateEvidence::score).toList();
    }

    private List<GeometryFitStatus> candidateStatuses(GeometryFitEvidence evidence) {
        return evidence.retainedCandidates().stream().map(GeometryCandidateEvidence::status).toList();
    }

    private List<PatternFeatureEvidence> sourceSpaceFeatures() {
        return List.of(
                feature(FinderRole.TOP_LEFT, canonicalPolygon(TOP_LEFT), sourcePolygon(TOP_LEFT),
                        CoordinateObservationSource.SOURCE_SPACE),
                feature(FinderRole.TOP_RIGHT, canonicalPolygon(TOP_RIGHT), sourcePolygon(TOP_RIGHT),
                        CoordinateObservationSource.SOURCE_SPACE),
                feature(FinderRole.BOTTOM_LEFT, canonicalPolygon(BOTTOM_LEFT), sourcePolygon(BOTTOM_LEFT),
                        CoordinateObservationSource.SOURCE_SPACE),
                feature(FinderRole.BOTTOM_RIGHT, canonicalPolygon(BOTTOM_RIGHT), sourcePolygon(BOTTOM_RIGHT),
                        CoordinateObservationSource.SOURCE_SPACE)
        );
    }

    private List<PatternFeatureEvidence> normalizedCandidateFeatures() {
        return List.of(
                feature(FinderRole.TOP_LEFT, canonicalPolygon(TOP_LEFT), normalizedPolygon(TOP_LEFT),
                        CoordinateObservationSource.NORMALIZED_CANDIDATE),
                feature(FinderRole.TOP_RIGHT, canonicalPolygon(TOP_RIGHT), normalizedPolygon(TOP_RIGHT),
                        CoordinateObservationSource.NORMALIZED_CANDIDATE),
                feature(FinderRole.BOTTOM_LEFT, canonicalPolygon(BOTTOM_LEFT), normalizedPolygon(BOTTOM_LEFT),
                        CoordinateObservationSource.NORMALIZED_CANDIDATE),
                feature(FinderRole.BOTTOM_RIGHT, canonicalPolygon(BOTTOM_RIGHT), normalizedPolygon(BOTTOM_RIGHT),
                        CoordinateObservationSource.NORMALIZED_CANDIDATE)
        );
    }

    private PatternEvidence detectedPatternEvidence(List<PatternFeatureEvidence> features) {
        return new PatternEvidence(
                1,
                patternCandidateId(),
                LAYOUT_PROFILE_ID,
                PatternEvidenceStatus.DETECTED,
                features,
                Map.of("rotation-0", 1.0d),
                Map.of(LAYOUT_PROFILE_ID, 1.0d),
                false,
                List.of(),
                List.of(),
                1.0d,
                1.0d
        );
    }

    private PatternEvidence weakPatternEvidence() {
        return new PatternEvidence(
                1,
                patternCandidateId(),
                LAYOUT_PROFILE_ID,
                PatternEvidenceStatus.NOT_FOUND,
                List.of(),
                Map.of(),
                Map.of(),
                false,
                List.of(
                        CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE,
                        CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE
                ),
                List.of(),
                0.0d,
                0.0d
        );
    }

    private PatternEvidence ambiguousPatternEvidence() {
        return new PatternEvidence(
                1,
                patternCandidateId(),
                LAYOUT_PROFILE_ID,
                PatternEvidenceStatus.AMBIGUOUS,
                List.of(),
                Map.of("rotation-0", 0.6d, "rotation-90", 0.55d),
                Map.of(LAYOUT_PROFILE_ID, 0.6d),
                false,
                List.of(
                        CaptureMediaEvidenceReasonCode.MULTIPLE_ORIENTATIONS,
                        CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE
                ),
                List.of(),
                0.6d,
                0.05d
        );
    }

    private PatternFeatureEvidence feature(
            FinderRole role,
            CanonicalPolygon canonical,
            SourcePolygon source,
            CoordinateObservationSource observationSource
    ) {
        return new PatternFeatureEvidence(
                PatternFeatureType.FINDER,
                Optional.of(role),
                OptionalInt.of(0),
                "balanced-v1-side-2",
                canonical,
                source,
                9,
                9,
                1.0d,
                30.0d,
                0.0d,
                observationSource,
                List.of()
        );
    }

    private CanonicalPolygon canonicalPolygon(Rect rect) {
        return new CanonicalPolygon(List.of(
                new CanonicalPoint(rect.left(), rect.top()),
                new CanonicalPoint(rect.right(), rect.top()),
                new CanonicalPoint(rect.right(), rect.bottom()),
                new CanonicalPoint(rect.left(), rect.bottom())
        ));
    }

    private SourcePolygon sourcePolygon(Rect rect) {
        return new SourcePolygon(List.of(
                affinePoint(rect.left(), rect.top()),
                affinePoint(rect.right(), rect.top()),
                affinePoint(rect.right(), rect.bottom()),
                affinePoint(rect.left(), rect.bottom())
        ));
    }

    private SourcePolygon normalizedPolygon(Rect rect) {
        return new SourcePolygon(List.of(
                new SourcePoint(rect.left(), rect.top()),
                new SourcePoint(rect.right(), rect.top()),
                new SourcePoint(rect.right(), rect.bottom()),
                new SourcePoint(rect.left(), rect.bottom())
        ));
    }

    private SourcePoint affinePoint(double x, double y) {
        return new SourcePoint((1.20d * x) + 40.0d, (1.10d * y) + 60.0d);
    }

    private NormalizedCaptureFrame normalizedFrame(FrameCorners frameCorners) {
        return new NormalizedCaptureFrame(
                "geometry-test.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                PIXEL_SHA256,
                LAYOUT_PROFILE_ID,
                frameCorners,
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                new int[FRAME_WIDTH * FRAME_HEIGHT]
        );
    }

    private NormalizedCaptureFrame normalizedFrame(FrameCorners frameCorners, Optional<String> geometrySource) {
        return new NormalizedCaptureFrame(
                "geometry-test.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                PIXEL_SHA256,
                LAYOUT_PROFILE_ID,
                Optional.empty(),
                Optional.empty(),
                frameCorners,
                CaptureMediaQualityMetrics.perspectiveCorrected(0.72d, 0.08d),
                new int[FRAME_WIDTH * FRAME_HEIGHT],
                Optional.empty(),
                geometrySource,
                1,
                1,
                1
        );
    }

    private CaptureMediaCandidateId patternCandidateId() {
        return CaptureMediaCandidateId.patternEvidence(CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                "geometry-test.png",
                PIXEL_SHA256,
                1,
                1,
                1
        ));
    }

    private record Rect(double left, double top, double right, double bottom) {
    }
}

package com.alx4j.jab4j.reader.capture.media.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPolygon;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitModelType;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSampleStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingAggregationMethod;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;

@DisplayName("Local residual analyzer")
class LocalResidualAnalyzerTest {

    private static final String PIXEL_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final int MODULE_WIDTH = 6;
    private static final int MODULE_HEIGHT = 6;

    private final LocalResidualAnalyzer analyzer = new LocalResidualAnalyzer();

    @Test
    @DisplayName("Returns not available when accepted geometry or source sampling is missing")
    void returnsNotAvailableWhenPrerequisitesAreMissing() {
        LocalRefinementEvidence noGeometry = analyzer.analyze(rejectedGeometry(), sampling(allReadable()));
        LocalRefinementEvidence noSampling = analyzer.analyze(acceptedGeometry(), unavailableSampling());

        assertAll(
                () -> assertEquals(LocalRefinementStatus.NOT_AVAILABLE, noGeometry.status()),
                () -> assertTrue(noGeometry.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GLOBAL_GEOMETRY)),
                () -> assertEquals(LocalRefinementStatus.NOT_AVAILABLE, noSampling.status()),
                () -> assertTrue(noSampling.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NO_SOURCE_SPACE_SAMPLING_EVIDENCE)),
                () -> assertFalse(noSampling.appliedToSampling())
        );
    }

    @Test
    @DisplayName("Rejects sparse reliable controls")
    void rejectsSparseReliableControls() {
        Map<Coordinate, ModuleSampleStatus> statuses = allGeometryFailures();
        statuses.put(new Coordinate(0, 0), ModuleSampleStatus.READABLE);
        statuses.put(new Coordinate(5, 0), ModuleSampleStatus.READABLE);
        statuses.put(new Coordinate(0, 5), ModuleSampleStatus.READABLE);
        statuses.put(new Coordinate(5, 5), ModuleSampleStatus.READABLE);

        LocalRefinementEvidence evidence = analyzer.analyze(acceptedGeometry(), sampling(statuses));

        assertAll(
                () -> assertEquals(LocalRefinementStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.LOCAL_EVIDENCE_TOO_SPARSE)),
                () -> assertEquals(4.0d, evidence.baseSamplingMetrics().get("control.matchedCount"))
        );
    }

    @Test
    @DisplayName("Rejects concentrated controls")
    void rejectsConcentratedControls() {
        Map<Coordinate, ModuleSampleStatus> statuses = allGeometryFailures();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                statuses.put(new Coordinate(x, y), ModuleSampleStatus.READABLE);
            }
        }

        LocalRefinementEvidence evidence = analyzer.analyze(acceptedGeometry(), sampling(statuses));

        assertAll(
                () -> assertEquals(LocalRefinementStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.CONTROL_POINTS_NOT_DISTRIBUTED)),
                () -> assertEquals(1.0d, evidence.baseSamplingMetrics().get("control.distributedQuadrantCount"))
        );
    }

    @Test
    @DisplayName("Rejects color classification as the primary blocker")
    void rejectsColorPrimaryBlocker() {
        Map<Coordinate, ModuleSampleStatus> statuses = allColorFailures();
        statuses.put(new Coordinate(0, 0), ModuleSampleStatus.READABLE);
        statuses.put(new Coordinate(5, 0), ModuleSampleStatus.READABLE);
        statuses.put(new Coordinate(0, 5), ModuleSampleStatus.READABLE);
        statuses.put(new Coordinate(5, 5), ModuleSampleStatus.READABLE);

        LocalRefinementEvidence evidence = analyzer.analyze(acceptedGeometry(), sampling(statuses));

        assertAll(
                () -> assertEquals(LocalRefinementStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.COLOR_CLASSIFICATION_IS_PRIMARY_BLOCKER)),
                () -> assertEquals(1.0d, evidence.improvementMetrics().get("colorPrimaryBlockerLikely"))
        );
    }

    @Test
    @DisplayName("Leaves globally sufficient residuals on the global path")
    void leavesGloballySufficientResidualsOnGlobalPath() {
        LocalRefinementEvidence evidence = analyzer.analyze(acceptedGeometry(), sampling(allReadable()));

        assertAll(
                () -> assertEquals(LocalRefinementStatus.LEFT_GLOBAL, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NO_MEASURABLE_IMPROVEMENT)),
                () -> assertEquals(36.0d, evidence.baseSamplingMetrics().get("control.matchedCount")),
                () -> assertEquals(0.0d, evidence.improvementMetrics().get("localGeometricDriftLikely")),
                () -> assertFalse(evidence.appliedToSampling())
        );
    }

    @Test
    @DisplayName("Rejects likely local drift diagnostics without applying correction")
    void rejectsLikelyLocalDriftDiagnosticsWithoutApplyingCorrection() {
        Map<Coordinate, ModuleSampleStatus> statuses = allReadable();
        statuses.put(new Coordinate(1, 1), ModuleSampleStatus.AMBIGUOUS);
        statuses.put(new Coordinate(2, 1), ModuleSampleStatus.AMBIGUOUS);
        statuses.put(new Coordinate(1, 2), ModuleSampleStatus.AMBIGUOUS);
        statuses.put(new Coordinate(2, 2), ModuleSampleStatus.AMBIGUOUS);

        LocalRefinementEvidence evidence = analyzer.analyze(acceptedGeometry(), sampling(statuses));

        assertAll(
                () -> assertEquals(LocalRefinementStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.LOCAL_GEOMETRIC_DRIFT_SUSPECTED)),
                () -> assertEquals(1.0d, evidence.improvementMetrics().get("localGeometricDriftLikely")),
                () -> assertTrue(evidence.maxLocalDisplacementModules() > 0.0d),
                () -> assertFalse(evidence.appliedToSampling())
        );
    }

    @Test
    @DisplayName("Marks broad residual patterns ambiguous")
    void marksBroadResidualPatternsAmbiguous() {
        Map<Coordinate, ModuleSampleStatus> statuses = allReadable();
        statuses.put(new Coordinate(0, 0), ModuleSampleStatus.AMBIGUOUS);
        statuses.put(new Coordinate(5, 0), ModuleSampleStatus.AMBIGUOUS);
        statuses.put(new Coordinate(0, 5), ModuleSampleStatus.AMBIGUOUS);
        statuses.put(new Coordinate(5, 5), ModuleSampleStatus.AMBIGUOUS);

        LocalRefinementEvidence evidence = analyzer.analyze(acceptedGeometry(), sampling(statuses));

        assertAll(
                () -> assertEquals(LocalRefinementStatus.AMBIGUOUS, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.AMBIGUOUS_LOCAL_REFINEMENT)),
                () -> assertEquals(1.0d, evidence.improvementMetrics().get("ambiguousResidualPattern"))
        );
    }

    @Test
    @DisplayName("Rejects broad high-residual evidence as unsafe to refine")
    void rejectsBroadHighResidualEvidenceAsUnsafeToRefine() {
        Map<Coordinate, ModuleSampleStatus> statuses = allReadable();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                statuses.put(new Coordinate(x, y), ModuleSampleStatus.AMBIGUOUS);
            }
        }

        LocalRefinementEvidence evidence = analyzer.analyze(acceptedGeometry(), sampling(statuses));

        assertAll(
                () -> assertEquals(LocalRefinementStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.LOCAL_MODEL_TOO_COMPLEX)),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.OVERFIT_RISK)),
                () -> assertEquals(1.0d, evidence.improvementMetrics().get("overfitRisk")),
                () -> assertFalse(evidence.appliedToSampling())
        );
    }

    private GeometryFitEvidence acceptedGeometry() {
        GeometryCandidateEvidence candidate = geometryCandidate(GeometryFitStatus.ACCEPTED);
        return new GeometryFitEvidence(
                1,
                patternCandidateId(),
                GeometryFitStatus.ACCEPTED,
                List.of(candidate),
                candidate.candidateId().geometryCandidateId(),
                Optional.empty(),
                List.of()
        );
    }

    private GeometryFitEvidence rejectedGeometry() {
        return new GeometryFitEvidence(
                1,
                patternCandidateId(),
                GeometryFitStatus.WITHHELD,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GEOMETRY)
        );
    }

    private GeometryCandidateEvidence geometryCandidate(GeometryFitStatus status) {
        return new GeometryCandidateEvidence(
                geometryCandidateId(),
                1,
                status,
                GeometryFitModelType.HOMOGRAPHY,
                "test-canonical",
                "test-source",
                List.of(
                        10.0d, 0.0d, 0.0d,
                        0.0d, 10.0d, 0.0d,
                        0.0d, 0.0d, 1.0d
                ),
                1.0d,
                List.of(),
                true,
                16,
                16,
                16,
                16,
                0,
                0,
                ReprojectionMetrics.zero(),
                1.0d,
                1.0d,
                true,
                false,
                List.of()
        );
    }

    private ModuleSamplingEvidence unavailableSampling() {
        return new ModuleSamplingEvidence(
                1,
                samplingCandidateId(),
                ModuleSamplingStatus.NOT_AVAILABLE,
                geometryCandidateId().geometryCandidateId().orElseThrow(),
                "test-layout",
                MODULE_WIDTH,
                MODULE_HEIGHT,
                "test-canonical",
                "geometry-fit:HOMOGRAPHY",
                0.50d,
                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                "central-region",
                MODULE_WIDTH * MODULE_HEIGHT,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                false,
                0,
                0,
                List.of(),
                List.of(),
                List.of(CaptureMediaEvidenceReasonCode.SOURCE_PIXELS_UNAVAILABLE)
        );
    }

    private ModuleSamplingEvidence sampling(Map<Coordinate, ModuleSampleStatus> statuses) {
        List<ModuleEvidence> modules = new ArrayList<>();
        int readable = 0;
        int ambiguous = 0;
        int unreadable = 0;
        for (int y = 0; y < MODULE_HEIGHT; y++) {
            for (int x = 0; x < MODULE_WIDTH; x++) {
                ModuleSampleStatus status = statuses.getOrDefault(new Coordinate(x, y), ModuleSampleStatus.READABLE);
                modules.add(module(x, y, status));
                if (status == ModuleSampleStatus.READABLE) {
                    readable++;
                } else if (status == ModuleSampleStatus.AMBIGUOUS) {
                    ambiguous++;
                } else if (status == ModuleSampleStatus.UNREADABLE) {
                    unreadable++;
                }
            }
        }
        boolean sampled = readable == MODULE_WIDTH * MODULE_HEIGHT;
        return new ModuleSamplingEvidence(
                1,
                samplingCandidateId(),
                sampled ? ModuleSamplingStatus.SAMPLED : ModuleSamplingStatus.PARTIAL,
                geometryCandidateId().geometryCandidateId().orElseThrow(),
                "test-layout",
                MODULE_WIDTH,
                MODULE_HEIGHT,
                "test-canonical",
                "geometry-fit:HOMOGRAPHY",
                0.50d,
                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                "central-region",
                MODULE_WIDTH * MODULE_HEIGHT,
                readable + ambiguous + unreadable,
                readable,
                ambiguous,
                unreadable,
                0,
                0,
                Map.of("mean", 80.0d),
                Map.of("mean", 0.0d),
                sampled,
                sampled ? 1 : 0,
                0,
                sampled ? List.of("TILE_DECODE") : List.of(),
                modules,
                List.of()
        );
    }

    private ModuleEvidence module(int x, int y, ModuleSampleStatus status) {
        boolean readable = status == ModuleSampleStatus.READABLE;
        boolean colorFailure = status == ModuleSampleStatus.UNREADABLE;
        List<CaptureMediaEvidenceReasonCode> reasonCodes = status == ModuleSampleStatus.READABLE
                ? List.of()
                : colorFailure
                        ? List.of(CaptureMediaEvidenceReasonCode.LOW_COLOR_MARGIN)
                        : List.of();
        return new ModuleEvidence(
                x,
                y,
                canonicalPolygon(x, y),
                sourcePolygon(x, y),
                innerSourcePolygon(x, y),
                9,
                readable ? 0xFF000000 : 0xFF202020,
                colorFailure ? 10.0d : 0.0d,
                readable ? OptionalInt.of(0) : OptionalInt.empty(),
                1.0d,
                readable ? 80.0d : 50.0d,
                readable ? 79.0d : 49.0d,
                status,
                reasonCodes
        );
    }

    private Map<Coordinate, ModuleSampleStatus> allReadable() {
        return new LinkedHashMap<>();
    }

    private Map<Coordinate, ModuleSampleStatus> allGeometryFailures() {
        Map<Coordinate, ModuleSampleStatus> statuses = new LinkedHashMap<>();
        for (int y = 0; y < MODULE_HEIGHT; y++) {
            for (int x = 0; x < MODULE_WIDTH; x++) {
                statuses.put(new Coordinate(x, y), ModuleSampleStatus.AMBIGUOUS);
            }
        }
        return statuses;
    }

    private Map<Coordinate, ModuleSampleStatus> allColorFailures() {
        Map<Coordinate, ModuleSampleStatus> statuses = new LinkedHashMap<>();
        for (int y = 0; y < MODULE_HEIGHT; y++) {
            for (int x = 0; x < MODULE_WIDTH; x++) {
                statuses.put(new Coordinate(x, y), ModuleSampleStatus.UNREADABLE);
            }
        }
        return statuses;
    }

    private CanonicalPolygon canonicalPolygon(int x, int y) {
        return new CanonicalPolygon(List.of(
                new CanonicalPoint(x, y),
                new CanonicalPoint(x + 1.0d, y),
                new CanonicalPoint(x + 1.0d, y + 1.0d),
                new CanonicalPoint(x, y + 1.0d)
        ));
    }

    private SourcePolygon sourcePolygon(int x, int y) {
        double left = x * 10.0d;
        double top = y * 10.0d;
        return new SourcePolygon(List.of(
                new SourcePoint(left, top),
                new SourcePoint(left + 10.0d, top),
                new SourcePoint(left + 10.0d, top + 10.0d),
                new SourcePoint(left, top + 10.0d)
        ));
    }

    private SourcePolygon innerSourcePolygon(int x, int y) {
        double left = (x * 10.0d) + 2.5d;
        double top = (y * 10.0d) + 2.5d;
        return new SourcePolygon(List.of(
                new SourcePoint(left, top),
                new SourcePoint(left + 5.0d, top),
                new SourcePoint(left + 5.0d, top + 5.0d),
                new SourcePoint(left, top + 5.0d)
        ));
    }

    private CaptureMediaCandidateId samplingCandidateId() {
        return CaptureMediaCandidateId.samplingCandidate(geometryCandidateId(), 50, 1);
    }

    private CaptureMediaCandidateId geometryCandidateId() {
        return CaptureMediaCandidateId.geometryCandidate(patternCandidateId(), 1);
    }

    private CaptureMediaCandidateId patternCandidateId() {
        return CaptureMediaCandidateId.patternEvidence(CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                "local-residual-test.png",
                PIXEL_SHA256,
                1,
                1,
                1
        ));
    }

    private record Coordinate(int x, int y) {
    }
}

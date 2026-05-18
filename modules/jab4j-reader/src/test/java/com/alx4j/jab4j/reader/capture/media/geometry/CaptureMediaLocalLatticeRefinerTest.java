package com.alx4j.jab4j.reader.capture.media.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
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
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaPaletteSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.tile.TileCodecProfiles;

@DisplayName("Capture media local lattice refiner")
class CaptureMediaLocalLatticeRefinerTest {

    private static final String PIXEL_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final int FRAME_SIDE = 100;
    private static final int BLACK = 0xFF000000;

    @Test
    @DisplayName("Produces diagnostics only and leaves sampling unapplied")
    void producesDiagnosticsOnlyAndLeavesSamplingUnapplied() {
        LocalRefinementEvidence evidence =
                new CaptureMediaLocalLatticeRefiner().diagnose(acceptedGeometry(), sampledModules());

        assertAll(
                () -> assertEquals(LocalRefinementStatus.LEFT_GLOBAL, evidence.status()),
                () -> assertFalse(evidence.appliedToSampling()),
                () -> assertTrue(evidence.controlPoints().stream()
                        .allMatch(point -> point.residualBeforeModules() == point.residualAfterModules())),
                () -> assertEquals(
                        evidence.baseSamplingMetrics().get("readableModuleCount"),
                        evidence.improvementMetrics().get("readableModulesAfter")
                )
        );
    }

    @Test
    @DisplayName("Applies local-grid sampling only when refined evidence improves the baseline")
    void appliesLocalGridSamplingOnlyWhenRefinedEvidenceImprovesBaseline() {
        CaptureMediaLocalLatticeRefiner refiner = new CaptureMediaLocalLatticeRefiner();
        SourceSpaceValidationSample baselineSample =
                new SourceSpaceValidationSample(List.of(shiftedSparseSampling()), List.of());

        CaptureMediaLocalLatticeRefiner.RefinementResult result = refiner.applyIfBeneficial(
                sourceFrame(),
                normalizedFrame(),
                acceptedGeometry(),
                baselineSample,
                sourceSpaceSampler()
        );

        ModuleSamplingEvidence selected = result.validationSample().evidence()
                .stream()
                .filter(candidate -> candidate.geometrySource().endsWith("+local-grid"))
                .findFirst()
                .orElseThrow();
        assertAll(
                () -> assertEquals(LocalRefinementStatus.APPLIED, result.evidence().status()),
                () -> assertTrue(result.evidence().appliedToSampling()),
                () -> assertEquals(1, result.validationSample().evidence().size()),
                () -> assertTrue(result.evidence().reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.LOCAL_REFINEMENT_IMPROVED_SAMPLING)),
                () -> assertTrue(selected.geometrySource().endsWith("+local-grid")),
                () -> assertTrue(hasMeasuredImprovement(result.evidence()))
        );
    }

    @Test
    @DisplayName("Leaves baseline sampling selected when local refinement rollback switch is disabled")
    void leavesBaselineSamplingSelectedWhenLocalRefinementRollbackSwitchIsDisabled() {
        CaptureMediaLocalLatticeRefiner refiner =
                new CaptureMediaLocalLatticeRefiner(new LocalResidualAnalyzer(), false);
        SourceSpaceValidationSample baselineSample =
                new SourceSpaceValidationSample(List.of(shiftedSparseSampling()), List.of());

        CaptureMediaLocalLatticeRefiner.RefinementResult result = refiner.applyIfBeneficial(
                sourceFrame(),
                normalizedFrame(),
                acceptedGeometry(),
                baselineSample,
                sourceSpaceSampler()
        );

        assertAll(
                () -> assertEquals(baselineSample, result.validationSample()),
                () -> assertEquals(LocalRefinementStatus.NOT_AVAILABLE, result.evidence().status()),
                () -> assertFalse(result.evidence().appliedToSampling()),
                () -> assertTrue(result.evidence().reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.LOCAL_REFINEMENT_DISABLED))
        );
    }

    private GeometryFitEvidence acceptedGeometry() {
        GeometryCandidateEvidence candidate = new GeometryCandidateEvidence(
                geometryCandidateId(),
                1,
                GeometryFitStatus.ACCEPTED,
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

    private ModuleSamplingEvidence sampledModules() {
        List<ModuleEvidence> modules = List.of(
                module(0, 0),
                module(1, 0),
                module(2, 0),
                module(0, 1),
                module(1, 1),
                module(2, 1),
                module(0, 2),
                module(1, 2),
                module(2, 2)
        );
        return new ModuleSamplingEvidence(
                1,
                CaptureMediaCandidateId.samplingCandidate(geometryCandidateId(), 50, 1),
                ModuleSamplingStatus.SAMPLED,
                geometryCandidateId().geometryCandidateId().orElseThrow(),
                "test-layout",
                3,
                3,
                "test-canonical",
                "geometry-fit:HOMOGRAPHY",
                0.50d,
                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                "central-region",
                9,
                9,
                9,
                0,
                0,
                0,
                0,
                Map.of("mean", 80.0d),
                Map.of("mean", 0.0d),
                true,
                1,
                0,
                List.of("TILE_DECODE"),
                modules,
                List.of()
        );
    }

    private ModuleSamplingEvidence shiftedSparseSampling() {
        List<ModuleEvidence> modules = List.of(
                shiftedModule(1, 1),
                shiftedModule(10, 1),
                shiftedModule(19, 1),
                shiftedModule(1, 10),
                shiftedModule(19, 10),
                shiftedModule(1, 19),
                shiftedModule(10, 19),
                shiftedModule(19, 19)
        );
        return new ModuleSamplingEvidence(
                1,
                CaptureMediaCandidateId.samplingCandidate(geometryCandidateId(), 50, 1),
                ModuleSamplingStatus.SAMPLED,
                geometryCandidateId().geometryCandidateId().orElseThrow(),
                "source-space-test-layout",
                21,
                21,
                "test-canonical",
                "geometry-fit:HOMOGRAPHY",
                0.50d,
                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                "central-region",
                441,
                modules.size(),
                modules.size(),
                0,
                0,
                0,
                0,
                Map.of("mean", 80.0d),
                Map.of("mean", 0.0d),
                false,
                0,
                0,
                List.of(),
                modules,
                List.of()
        );
    }

    private boolean hasMeasuredImprovement(LocalRefinementEvidence evidence) {
        Map<String, Double> metrics = evidence.improvementMetrics();
        return metrics.get("decodedTilesAfter") > metrics.get("decodedTilesBefore")
                || metrics.get("tileDecodeAttemptsAfter") > metrics.get("tileDecodeAttemptsBefore")
                || metrics.get("readableModulesAfter") > metrics.get("readableModulesBefore")
                || metrics.get("problemModulesAfter") < metrics.get("problemModulesBefore")
                || metrics.get("samplingConfidenceAfter") >= metrics.get("samplingConfidenceBefore") + 1.0d;
    }

    private ModuleEvidence module(int x, int y) {
        return new ModuleEvidence(
                x,
                y,
                canonicalPolygon(x, y),
                sourcePolygon(x, y),
                sourcePolygon(x, y),
                9,
                0xFF000000,
                0.0d,
                OptionalInt.of(0),
                1.0d,
                11.0d,
                10.0d,
                ModuleSampleStatus.READABLE,
                List.of()
        );
    }

    private ModuleEvidence shiftedModule(int x, int y) {
        return new ModuleEvidence(
                x,
                y,
                canonicalPolygon(x, y),
                sourcePolygon(x, y),
                shiftedInnerSourcePolygon(x, y),
                9,
                0xFF000000,
                0.0d,
                OptionalInt.of(0),
                1.0d,
                80.0d,
                79.0d,
                ModuleSampleStatus.READABLE,
                List.of()
        );
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

    private SourcePolygon shiftedInnerSourcePolygon(int x, int y) {
        double left = (x * 10.0d) + 1.0d;
        double top = (y * 10.0d) + 1.0d;
        return new SourcePolygon(List.of(
                new SourcePoint(left, top),
                new SourcePoint(left + 10.0d, top),
                new SourcePoint(left + 10.0d, top + 10.0d),
                new SourcePoint(left, top + 10.0d)
        ));
    }

    private CaptureMediaSourceSpaceModuleSampler sourceSpaceSampler() {
        return new CaptureMediaSourceSpaceModuleSampler(
                new CaptureRenderedLayoutCatalog(List.of(layoutProfile())),
                TileCodecProfiles.balancedV1(),
                new ModuleLatticeProjector(),
                new CaptureMediaPaletteSampler()
        );
    }

    private MediaInputFrame sourceFrame() {
        int[] pixels = new int[FRAME_SIDE * FRAME_SIDE];
        java.util.Arrays.fill(pixels, BLACK);
        return new MediaInputFrame(
                "local-refiner-test.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_SIDE,
                FRAME_SIDE,
                "png",
                PIXEL_SHA256,
                pixels
        );
    }

    private NormalizedCaptureFrame normalizedFrame() {
        int[] pixels = new int[FRAME_SIDE * FRAME_SIDE];
        java.util.Arrays.fill(pixels, BLACK);
        return new NormalizedCaptureFrame(
                "local-refiner-test.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_SIDE,
                FRAME_SIDE,
                FRAME_SIDE,
                FRAME_SIDE,
                "png",
                PIXEL_SHA256,
                layoutProfile().profileId(),
                FrameCorners.exactFrame(FRAME_SIDE, FRAME_SIDE),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private LayoutProfile layoutProfile() {
        return new LayoutProfile(
                "source-space-test-layout",
                1,
                1,
                FRAME_SIDE,
                FRAME_SIDE,
                0,
                0,
                "solidWhite",
                0,
                0,
                "black",
                "preserveAspect"
        );
    }

    private CaptureMediaCandidateId geometryCandidateId() {
        return CaptureMediaCandidateId.geometryCandidate(patternCandidateId(), 1);
    }

    private CaptureMediaCandidateId patternCandidateId() {
        return CaptureMediaCandidateId.patternEvidence(CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                "local-refiner-test.png",
                PIXEL_SHA256,
                1,
                1,
                1
        ));
    }
}

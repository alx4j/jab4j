package com.alx4j.jab4j.reader.capture.media.evidence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Capture media evidence contracts")
class CaptureMediaEvidenceContractsTest {

    private static final String PIXEL_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    @DisplayName("Coordinate DTOs and pattern evidence defensively copy collections")
    void coordinateDtosAndPatternEvidenceDefensivelyCopyCollections() {
        List<SourcePoint> sourceVertices = new ArrayList<>(List.of(
                new SourcePoint(0.0d, 0.0d),
                new SourcePoint(1.0d, 0.0d),
                new SourcePoint(1.0d, 1.0d)
        ));
        SourcePolygon sourcePolygon = new SourcePolygon(sourceVertices);
        sourceVertices.add(new SourcePoint(0.0d, 1.0d));

        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>(
                List.of(CaptureMediaEvidenceReasonCode.ALIGNMENT_NOT_EXPECTED)
        );
        List<PatternFeatureEvidence> features = new ArrayList<>(
                List.of(patternFeature(reasonCodes))
        );
        Map<String, Double> orientations = new HashMap<>();
        orientations.put("rotation-0", 1.0d);
        Map<String, Double> profiles = new HashMap<>();
        profiles.put("balanced-v1", 1.0d);

        PatternEvidence evidence = new PatternEvidence(
                1,
                patternCandidateId(),
                "balanced-v1",
                PatternEvidenceStatus.DETECTED,
                features,
                orientations,
                profiles,
                false,
                reasonCodes,
                List.of(),
                0.95d,
                0.25d
        );
        features.clear();
        orientations.clear();
        profiles.clear();
        reasonCodes.clear();

        assertAll(
                () -> assertEquals(3, sourcePolygon.vertices().size()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> sourcePolygon.vertices().add(new SourcePoint(2.0d, 2.0d))),
                () -> assertEquals(1, evidence.features().size()),
                () -> assertEquals(1, evidence.orientationCandidates().size()),
                () -> assertEquals(1, evidence.layoutProfileCandidates().size()),
                () -> assertEquals(1, evidence.reasonCodes().size()),
                () -> assertThrows(UnsupportedOperationException.class, () -> evidence.features().clear()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> evidence.orientationCandidates().put("rotation-90", 0.5d)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> evidence.reasonCodes().add(CaptureMediaEvidenceReasonCode.DOWNSTREAM_CONFLICT))
        );
    }

    @Test
    @DisplayName("Geometry, sampling, and refinement evidence defensively copy inputs")
    void geometrySamplingAndRefinementEvidenceDefensivelyCopyInputs() {
        CaptureMediaCandidateId geometryId = geometryCandidateId();
        List<Double> transform = new ArrayList<>(Arrays.asList(1.0d, 0.0d, 0.0d, 0.0d, 1.0d, 0.0d, 0.0d, 0.0d, 1.0d));
        List<String> degeneracyFlags = new ArrayList<>(List.of("none"));
        List<CaptureMediaEvidenceReasonCode> reasons = new ArrayList<>(
                List.of(CaptureMediaEvidenceReasonCode.ALIGNMENT_NOT_EXPECTED)
        );
        GeometryCandidateEvidence geometry = new GeometryCandidateEvidence(
                geometryId,
                1,
                GeometryFitStatus.ACCEPTED,
                GeometryFitModelType.HOMOGRAPHY,
                "canonical-layout",
                "source-pixels",
                transform,
                1.0d,
                degeneracyFlags,
                true,
                4,
                4,
                4,
                4,
                0,
                0,
                reprojectionMetrics(),
                0.92d,
                0.20d,
                true,
                false,
                reasons
        );
        List<GeometryCandidateEvidence> retained = new ArrayList<>(List.of(geometry));
        GeometryFitEvidence fit = new GeometryFitEvidence(
                1,
                CaptureMediaCandidateId.patternEvidence(proposalCandidateId()),
                GeometryFitStatus.ACCEPTED,
                retained,
                geometry.candidateId().geometryCandidateId(),
                Optional.empty(),
                reasons
        );

        CaptureMediaCandidateId samplingId = CaptureMediaCandidateId.samplingCandidate(geometryId, 50, 1);
        ModuleEvidence module = moduleEvidence();
        Map<String, Double> confidenceSummary = new HashMap<>();
        confidenceSummary.put("mean", 0.45d);
        Map<String, Double> varianceSummary = new HashMap<>();
        varianceSummary.put("max", 12.0d);
        Map<String, Double> moduleConfidenceSummary = new HashMap<>();
        moduleConfidenceSummary.put("mean", 1.0d);
        Map<String, Double> footprintSummary = new HashMap<>();
        footprintSummary.put("min", 1.0d);
        List<ModuleEvidence> modules = new ArrayList<>(List.of(module));
        ModuleSamplingEvidence sampling = new ModuleSamplingEvidence(
                1,
                samplingId,
                ModuleSamplingStatus.SAMPLED,
                samplingId.geometryCandidateId().orElseThrow(),
                "balanced-v1",
                1,
                1,
                "canonical-layout",
                "geometry-v1",
                0.50d,
                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                "central-region",
                1,
                1,
                1,
                0,
                0,
                0,
                0,
                confidenceSummary,
                varianceSummary,
                moduleConfidenceSummary,
                footprintSummary,
                0,
                0,
                observedPaletteEvidence(samplingId),
                true,
                1,
                1,
                List.of(),
                List.of(),
                modules,
                reasons
        );

        CaptureMediaCandidateId refinementId = CaptureMediaCandidateId.refinementCandidate(samplingId);
        List<LocalControlPointEvidence> controlPoints = new ArrayList<>(List.of(localControlPoint()));
        Map<String, Double> baseSamplingMetrics = new HashMap<>();
        baseSamplingMetrics.put("readableModules", 1.0d);
        Map<String, Double> improvementMetrics = new HashMap<>();
        improvementMetrics.put("readableModulesAfter", 1.0d);
        LocalRefinementEvidence refinement = new LocalRefinementEvidence(
                1,
                refinementId,
                LocalRefinementStatus.LEFT_GLOBAL,
                refinementId.geometryCandidateId().orElseThrow(),
                refinementId.samplingCandidateId().orElseThrow(),
                reprojectionMetrics(),
                baseSamplingMetrics,
                controlPoints,
                LocalRefinementModelType.LOCAL_GRID,
                2,
                2,
                0.25d,
                1.5d,
                "bounded-smooth",
                1.0d,
                1.0d,
                improvementMetrics,
                false,
                reasons
        );

        transform.clear();
        degeneracyFlags.clear();
        retained.clear();
        confidenceSummary.clear();
        varianceSummary.clear();
        moduleConfidenceSummary.clear();
        footprintSummary.clear();
        modules.clear();
        baseSamplingMetrics.clear();
        improvementMetrics.clear();
        controlPoints.clear();
        reasons.clear();

        assertAll(
                () -> assertEquals(9, geometry.transformParameters().size()),
                () -> assertEquals(1, geometry.degeneracyFlags().size()),
                () -> assertEquals(1, fit.retainedCandidates().size()),
                () -> assertEquals(1, sampling.confidenceMarginSummary().size()),
                () -> assertEquals(1, sampling.colorVarianceSummary().size()),
                () -> assertEquals(1, sampling.moduleConfidenceSummary().size()),
                () -> assertEquals(1, sampling.geometryFootprintQualitySummary().size()),
                () -> assertEquals(1, sampling.modules().size()),
                () -> assertEquals(1, refinement.baseSamplingMetrics().size()),
                () -> assertEquals(1, refinement.improvementMetrics().size()),
                () -> assertEquals(1, refinement.controlPoints().size()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> geometry.transformParameters().add(2.0d)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> fit.retainedCandidates().clear()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> sampling.modules().clear()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> refinement.improvementMetrics().put("later", 2.0d))
        );
    }

    @Test
    @DisplayName("Contracts reject invalid and inconsistent evidence")
    void contractsRejectInvalidAndInconsistentEvidence() {
        CaptureMediaCandidateId samplingId = CaptureMediaCandidateId.samplingCandidate(geometryCandidateId(), 50, 1);
        CaptureMediaCandidateId refinementId = CaptureMediaCandidateId.refinementCandidate(samplingId);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new SourcePoint(Double.NaN, 0.0d)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CanonicalPolygon(List.of(new CanonicalPoint(0.0d, 0.0d),
                                new CanonicalPoint(1.0d, 0.0d)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PatternFeatureEvidence(
                                PatternFeatureType.FINDER,
                                Optional.empty(),
                                OptionalInt.of(0),
                                "balanced-v1",
                                canonicalPolygon(),
                                sourcePolygon(),
                                9,
                                9,
                                1.0d,
                                1.0d,
                                0.0d,
                                CoordinateObservationSource.SOURCE_SPACE,
                                List.of()
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ReprojectionMetrics(
                                1.0d,
                                1.0d,
                                2.0d,
                                1.5d,
                                0.1d,
                                0.1d,
                                0.2d,
                                0.3d,
                                List.of(),
                                List.of()
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ModuleEvidence(
                                0,
                                0,
                                canonicalPolygon(),
                                sourcePolygon(),
                                sourcePolygon(),
                                9,
                                0xFF000000,
                                0.0d,
                                OptionalInt.of(0),
                                2.0d,
                                1.0d,
                                0.0d,
                                1.0d,
                                1.0d,
                                100.0d,
                                25.0d,
                                5.0d,
                                1.0d,
                                0.0d,
                                ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                                ObservedPaletteClassificationMode.EXACT,
                                "exact-rendered-palette-v1",
                                ModuleSampleStatus.READABLE,
                                List.of()
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ModuleSamplingEvidence(
                                1,
                                samplingId,
                                ModuleSamplingStatus.SAMPLED,
                                samplingId.geometryCandidateId().orElseThrow(),
                                "balanced-v1",
                                2,
                                2,
                                "canonical-layout",
                                "geometry-v1",
                                0.5d,
                                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                                "central-region",
                                3,
                                1,
                                1,
                                0,
                                0,
                                0,
                                0,
                                Map.of(),
                                Map.of(),
                                Map.of(),
                                Map.of(),
                                0,
                                0,
                                observedPaletteEvidence(samplingId),
                                true,
                                1,
                                0,
                                List.of(),
                                List.of("TILE_DECODE"),
                                List.of(),
                                List.of()
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LocalRefinementEvidence(
                                1,
                                refinementId,
                                LocalRefinementStatus.LEFT_GLOBAL,
                                refinementId.geometryCandidateId().orElseThrow(),
                                refinementId.samplingCandidateId().orElseThrow(),
                                reprojectionMetrics(),
                                Map.of(),
                                List.of(),
                                LocalRefinementModelType.LOCAL_GRID,
                                2,
                                2,
                                0.25d,
                                1.5d,
                                "bounded-smooth",
                                1.0d,
                                1.0d,
                                Map.of(),
                                true,
                                List.of()
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PatternEvidence(
                                1,
                                patternCandidateId(),
                                "balanced-v1",
                                PatternEvidenceStatus.REJECTED,
                                List.of(),
                                Map.of(),
                                Map.of(),
                                false,
                                List.of(
                                        CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE,
                                        CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE
                                ),
                                List.of(),
                                0.0d,
                                0.0d
                        ))
        );
    }

    @Test
    @DisplayName("Enums preserve sidecar status and reason names")
    void enumsPreserveSidecarStatusAndReasonNames() {
        Set<String> reasonNames = EnumSet.allOf(CaptureMediaEvidenceReasonCode.class)
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(
                        List.of("DETECTED", "REJECTED", "AMBIGUOUS", "NOT_FOUND"),
                        names(PatternEvidenceStatus.values())
                ),
                () -> assertEquals(
                        List.of("NOT_AVAILABLE", "ACCEPTED", "REJECTED", "AMBIGUOUS", "WITHHELD"),
                        names(GeometryFitStatus.values())
                ),
                () -> assertEquals(
                        List.of("NOT_AVAILABLE", "SAMPLED", "PARTIAL", "REJECTED", "WITHHELD"),
                        names(ModuleSamplingStatus.values())
                ),
                () -> assertEquals(
                        List.of("READABLE", "AMBIGUOUS", "UNREADABLE", "CLIPPED", "OUT_OF_BOUNDS"),
                        names(ModuleSampleStatus.values())
                ),
                () -> assertEquals(
                        List.of("EXACT", "OBSERVED", "HYBRID_OBSERVED_EXACT", "WITHHELD"),
                        names(ObservedPaletteClassificationMode.values())
                ),
                () -> assertEquals(
                        List.of("SRGB_EUCLIDEAN_V1", "LINEAR_RGB_V1", "CIE_LAB_V1"),
                        names(ObservedPaletteColorMethod.values())
                ),
                () -> assertEquals(
                        List.of("NOT_AVAILABLE", "APPLIED", "REJECTED", "LEFT_GLOBAL", "AMBIGUOUS"),
                        names(LocalRefinementStatus.values())
                ),
                () -> assertTrue(reasonNames.contains("NOT_SUPPORTED_FOR_PROFILE")),
                () -> assertTrue(reasonNames.contains("NO_FEATURE_EVIDENCE")),
                () -> assertTrue(reasonNames.contains("HIGH_REPROJECTION_ERROR")),
                () -> assertTrue(reasonNames.contains("SOURCE_PIXELS_UNAVAILABLE")),
                () -> assertTrue(reasonNames.contains("PAYLOAD_CRC_FAILED")),
                () -> assertTrue(reasonNames.contains("NO_ACCEPTED_GLOBAL_GEOMETRY")),
                () -> assertTrue(reasonNames.contains("SMOOTHNESS_CONSTRAINT_FAILED")),
                () -> assertTrue(reasonNames.contains("LOCAL_GEOMETRIC_DRIFT_SUSPECTED")),
                () -> assertTrue(reasonNames.contains("RESTORE_GATES_NOT_MET")),
                () -> assertTrue(reasonNames.contains("PROVISIONAL_CV_GEOMETRY"))
        );
    }

    private static List<String> names(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).toList();
    }

    private PatternFeatureEvidence patternFeature(List<CaptureMediaEvidenceReasonCode> reasonCodes) {
        return new PatternFeatureEvidence(
                PatternFeatureType.FINDER,
                Optional.of(FinderRole.TOP_LEFT),
                OptionalInt.of(0),
                "balanced-v1",
                canonicalPolygon(),
                sourcePolygon(),
                9,
                9,
                1.0d,
                3.0d,
                0.0d,
                CoordinateObservationSource.SOURCE_SPACE,
                reasonCodes
        );
    }

    private ModuleEvidence moduleEvidence() {
        return new ModuleEvidence(
                0,
                0,
                canonicalPolygon(),
                sourcePolygon(),
                sourcePolygon(),
                9,
                0xFF000000,
                0.0d,
                OptionalInt.of(0),
                1.0d,
                2.0d,
                1.0d,
                1.0d,
                1.0d,
                100.0d,
                25.0d,
                5.0d,
                1.0d,
                0.0d,
                ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                ObservedPaletteClassificationMode.EXACT,
                "exact-rendered-palette-v1",
                ModuleSampleStatus.READABLE,
                List.of()
        );
    }

    private ObservedPaletteEvidence observedPaletteEvidence(CaptureMediaCandidateId samplingId) {
        return new ObservedPaletteEvidence(
                1,
                samplingId,
                ObservedPaletteStatus.FALLBACK_EXACT,
                "exact-rendered-palette-v1",
                ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                ObservedPaletteClassificationMode.EXACT,
                0.0d,
                0.0d,
                Optional.empty(),
                0.0d,
                "mvp10-palette-thresholds-v1",
                Optional.of("test fallback"),
                ObservedPaletteSafetyDecision.FALLBACK_EXACT,
                List.of(),
                List.of(CaptureMediaEvidenceReasonCode.EXACT_PALETTE_FALLBACK_SELECTED)
        );
    }

    private LocalControlPointEvidence localControlPoint() {
        return new LocalControlPointEvidence(
                "cp-1",
                new CanonicalPoint(0.5d, 0.5d),
                new SourcePoint(5.0d, 5.0d),
                Optional.of(new SourcePoint(5.1d, 5.0d)),
                0.1d,
                0.0d,
                0.01d,
                0.0d,
                true,
                true,
                1.0d,
                List.of()
        );
    }

    private ReprojectionMetrics reprojectionMetrics() {
        return new ReprojectionMetrics(
                0.1d,
                0.1d,
                0.2d,
                0.3d,
                0.01d,
                0.01d,
                0.02d,
                0.03d,
                List.of(0.1d),
                List.of(0.01d)
        );
    }

    private CanonicalPolygon canonicalPolygon() {
        return new CanonicalPolygon(List.of(
                new CanonicalPoint(0.0d, 0.0d),
                new CanonicalPoint(1.0d, 0.0d),
                new CanonicalPoint(1.0d, 1.0d),
                new CanonicalPoint(0.0d, 1.0d)
        ));
    }

    private SourcePolygon sourcePolygon() {
        return new SourcePolygon(List.of(
                new SourcePoint(0.0d, 0.0d),
                new SourcePoint(10.0d, 0.0d),
                new SourcePoint(10.0d, 10.0d),
                new SourcePoint(0.0d, 10.0d)
        ));
    }

    private CaptureMediaCandidateId proposalCandidateId() {
        return CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                "photo-001.jpg",
                PIXEL_SHA256,
                1,
                1,
                1
        );
    }

    private CaptureMediaCandidateId patternCandidateId() {
        return CaptureMediaCandidateId.patternEvidence(proposalCandidateId());
    }

    private CaptureMediaCandidateId geometryCandidateId() {
        return CaptureMediaCandidateId.geometryCandidate(patternCandidateId(), 1);
    }
}

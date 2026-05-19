package com.alx4j.jab4j.reader.capture.media.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverResult;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverService;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.CoordinateObservationSource;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidenceStatus;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaGeometryFitter;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaPatternEvidenceDetector;
import com.alx4j.jab4j.reader.capture.media.input.CaptureMediaInputIntake;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.MediaIntakeResult;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample;

@DisplayName("MVP-9 generated fixture evidence gates")
class CaptureMediaGeneratedEvidenceGateTest {

    @TempDir
    Path tempDir;

    private final CaptureMediaInputIntake intake = new CaptureMediaInputIntake();
    private final CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer();
    private final CaptureMediaPatternEvidenceDetector patternDetector = new CaptureMediaPatternEvidenceDetector();
    private final CaptureMediaGeometryFitter geometryFitter = new CaptureMediaGeometryFitter();
    private final CaptureMediaSourceSpaceModuleSampler sourceSpaceSampler = new CaptureMediaSourceSpaceModuleSampler();

    @ParameterizedTest(name = "{0}")
    @MethodSource("sourceSpacePositiveFixtures")
    @DisplayName("Generated positives reach accepted geometry from source-space pattern evidence")
    void generatedPositivesReachAcceptedGeometryFromSourceSpacePatternEvidence(
            String scenarioId,
            FixtureFactory fixtureFactory
    ) throws Exception {
        GeneratedCaptureMediaFixture fixture = fixtureFactory.generate(tempDir);

        FixtureEvidence evidence = evidenceForFirstFile(fixture);

        assertAll(
                () -> assertEquals(scenarioId, fixture.scenarioId()),
                () -> assertEquals(PatternEvidenceStatus.DETECTED, evidence.pattern().status(),
                        evidence::describePattern),
                () -> assertTrue(evidence.pattern().features().stream()
                                .allMatch(feature -> feature.observationSource()
                                        == CoordinateObservationSource.SOURCE_SPACE),
                        evidence::describePattern),
                () -> assertFalse(evidence.geometry().retainedCandidates().isEmpty(),
                        "geometry evidence should retain the accepted source-space fit"),
                () -> assertEquals(GeometryFitStatus.ACCEPTED, evidence.geometry().status(),
                        evidence::describeGeometry),
                () -> assertFalse(evidence.geometry().reasonCodes()
                                .contains(CaptureMediaEvidenceReasonCode.NORMALIZED_CANDIDATE_ONLY),
                        evidence::describeGeometry),
                () -> assertEquals(GeometryFitStatus.ACCEPTED,
                        evidence.geometry().retainedCandidates().get(0).status()),
                () -> assertTrue(evidence.geometry().retainedCandidates().get(0).matchedPointCount() > 0),
                () -> assertTrue(evidence.geometry().retainedCandidates().get(0).retainedForSampling()),
                () -> assertTrue(evidence.geometry().selectedGeometryCandidateId().isPresent()),
                () -> assertFalse(List.of(ModuleSamplingStatus.NOT_AVAILABLE, ModuleSamplingStatus.WITHHELD)
                                .contains(evidence.sampling().status()),
                        evidence::describeSampling),
                () -> assertFalse(evidence.sampling().reasonCodes()
                                .contains(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GEOMETRY),
                        evidence::describeSampling),
                () -> assertFalse(evidence.localRefinement().status() == LocalRefinementStatus.NOT_AVAILABLE,
                        evidence::describeLocalRefinement),
                () -> assertFalse(evidence.localRefinement().appliedToSampling()),
                () -> assertFalse(evidence.localRefinement().reasonCodes()
                                .contains(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GLOBAL_GEOMETRY),
                        evidence::describeLocalRefinement),
                () -> assertFalse(evidence.localRefinement().reasonCodes()
                                .contains(CaptureMediaEvidenceReasonCode.NO_SOURCE_SPACE_SAMPLING_EVIDENCE),
                        evidence::describeLocalRefinement)
        );
    }

    @Test
    @DisplayName("Generated invalid-payload positive never restores after accepted source-space geometry")
    void generatedInvalidPayloadPositiveNeverRestoresAfterAcceptedSourceSpaceGeometry() throws Exception {
        GeneratedCaptureMediaFixture fixture =
                CaptureMediaCorpusFixtures.generatedCameraLikeMonitorInvalidPayloadPng(tempDir);
        FixtureEvidence evidence = evidenceForFirstFile(fixture);
        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().restore(
                CaptureMediaReceiverRequest.restoreStillImages(
                        List.of(fixture.mediaDirectory()),
                        tempDir.resolve("restore-invalid-payload")
                )
        );

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.DETECTED, evidence.pattern().status()),
                () -> assertEquals(GeometryFitStatus.ACCEPTED, evidence.geometry().status(),
                        evidence::describeGeometry),
                () -> assertFalse(evidence.geometry().reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NORMALIZED_CANDIDATE_ONLY)),
                () -> assertFalse(List.of(ModuleSamplingStatus.NOT_AVAILABLE, ModuleSamplingStatus.WITHHELD)
                        .contains(evidence.sampling().status()), evidence::describeSampling),
                () -> assertEquals(0, evidence.sampling().acceptedPayloadCount(), evidence::describeSampling),
                () -> assertFalse(evidence.sampling().reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GEOMETRY)),
                () -> assertFalse(evidence.localRefinement().status() == LocalRefinementStatus.NOT_AVAILABLE),
                () -> assertFalse(result.restored()),
                () -> assertFalse(result.eligibleForRestore()),
                () -> assertEquals(0, result.summary().recoveredUniqueFrameCount()),
                () -> assertEquals(0, result.summary().decodedTileCount()),
                () -> assertEquals(0, result.summary().restoredFileCount()),
                () -> assertTrue(result.restoreResult().isEmpty())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mvp10BaselineFixtures")
    @DisplayName("MVP-10 generated fixtures record current reader baseline metrics")
    void mvp10GeneratedFixturesRecordCurrentReaderBaselineMetrics(
            String scenarioId,
            FixtureFactory fixtureFactory,
            CaptureMediaFixtureMetrics expectedMetrics
    ) throws Exception {
        GeneratedCaptureMediaFixture fixture = fixtureFactory.generate(tempDir);
        Path outputDirectory = tempDir.resolve("restore-baseline-" + scenarioId);

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().restore(
                CaptureMediaReceiverRequest.restoreStillImages(List.of(fixture.mediaDirectory()), outputDirectory)
        );
        CaptureMediaFixtureMetrics actualMetrics = CaptureMediaFixtureMetrics.from(
                fixture.scenarioId(),
                result,
                samplingEvidenceForFirstFile(fixture)
        );

        assertEquals(expectedMetrics, actualMetrics, () -> "actual baseline: " + actualMetrics);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mvp10SafetyGateFixtures")
    @DisplayName("MVP-10 ambiguous, false-positive, and structurally invalid fixtures fail closed")
    void mvp10AmbiguousFalsePositiveAndStructurallyInvalidFixturesFailClosed(
            String scenarioId,
            FixtureFactory fixtureFactory
    ) throws Exception {
        GeneratedCaptureMediaFixture fixture = fixtureFactory.generate(tempDir);
        Path outputDirectory = tempDir.resolve("restore-safety-" + scenarioId);

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().restore(
                CaptureMediaReceiverRequest.restoreStillImages(List.of(fixture.mediaDirectory()), outputDirectory)
        );
        CaptureMediaFixtureMetrics metrics = CaptureMediaFixtureMetrics.from(
                fixture.scenarioId(),
                result,
                samplingEvidenceForFirstFile(fixture)
        );

        assertAll(
                () -> assertEquals(scenarioId, fixture.scenarioId()),
                () -> assertFalse(result.restored()),
                () -> assertFalse(result.eligibleForRestore()),
                () -> assertEquals(0, result.summary().decodedTileCount()),
                () -> assertEquals(0, result.summary().recoveredUniqueFrameCount()),
                () -> assertEquals(0, result.summary().restoredFileCount()),
                () -> assertTrue(result.restoreResult().isEmpty()),
                () -> assertFalse(Files.exists(outputDirectory.resolve("payload"))),
                () -> assertTrue(List.of(
                                "SCREEN_OR_FRAME_NOT_FOUND",
                                "COLOR_OR_COMPRESSION_SHIFT",
                                "TILE_DECODE_OR_ENVELOPE_FAILURE"
                        ).contains(metrics.primaryDiagnosticCode()),
                        () -> "unexpected primary diagnostic: " + metrics.primaryDiagnosticCode())
        );
    }

    @Test
    @DisplayName("Generated local-distortion fixture exists before applied refinement and remains diagnostics-only")
    void generatedLocalDistortionFixtureExistsBeforeAppliedRefinementAndRemainsDiagnosticsOnly() throws Exception {
        GeneratedCaptureMediaFixture fixture = CaptureMediaCorpusFixtures.generatedLocalDistortionPng(tempDir);
        FixtureEvidence evidence = evidenceForFirstFile(fixture);
        Path outputDirectory = tempDir.resolve("restore-local-distortion");

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().restore(
                CaptureMediaReceiverRequest.restoreStillImages(List.of(fixture.mediaDirectory()), outputDirectory)
        );

        assertAll(
                () -> assertEquals(CaptureMediaCorpusFixtures.GENERATED_LOCAL_DISTORTION_PNG, fixture.scenarioId()),
                () -> assertNotNull(ImageIO.read(fixture.mediaFiles().get(0).toFile())),
                () -> assertEquals(PatternEvidenceStatus.DETECTED, evidence.pattern().status(),
                        evidence::describePattern),
                () -> assertEquals(GeometryFitStatus.ACCEPTED, evidence.geometry().status(),
                        evidence::describeGeometry),
                () -> assertFalse(evidence.geometry().reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NORMALIZED_CANDIDATE_ONLY)),
                () -> assertFalse(List.of(ModuleSamplingStatus.NOT_AVAILABLE, ModuleSamplingStatus.WITHHELD)
                                .contains(evidence.sampling().status()),
                        evidence::describeSampling),
                () -> assertFalse(evidence.localRefinement().status() == LocalRefinementStatus.NOT_AVAILABLE,
                        evidence::describeLocalRefinement),
                () -> assertFalse(evidence.localRefinement().appliedToSampling()),
                () -> assertFalse(result.restored()),
                () -> assertEquals(0, result.summary().restoredFileCount()),
                () -> assertFalse(Files.exists(outputDirectory.resolve("payload")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeFixtures")
    @DisplayName("Generated negative and partial fixtures do not gain decoded tiles or restored files")
    void generatedNegativeAndPartialFixturesDoNotGainDecodedTilesOrRestoredFiles(
            String scenarioId,
            FixtureFactory fixtureFactory
    ) throws Exception {
        GeneratedCaptureMediaFixture fixture = fixtureFactory.generate(tempDir);
        Path outputDirectory = tempDir.resolve("restore-" + scenarioId);

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().restore(
                CaptureMediaReceiverRequest.restoreStillImages(List.of(fixture.mediaDirectory()), outputDirectory)
        );

        assertAll(
                () -> assertEquals(scenarioId, fixture.scenarioId()),
                () -> assertFalse(result.restored()),
                () -> assertFalse(result.eligibleForRestore()),
                () -> assertEquals(0, result.summary().recoveredUniqueFrameCount()),
                () -> assertEquals(0, result.summary().decodedTileCount()),
                () -> assertEquals(0, result.summary().restoredFileCount()),
                () -> assertTrue(result.restoreResult().isEmpty()),
                () -> assertFalse(Files.exists(outputDirectory.resolve("payload")))
        );
    }

    private FixtureEvidence evidenceForFirstFile(GeneratedCaptureMediaFixture fixture) {
        MediaIntakeResult intakeResult = intake.read(List.of(fixture.mediaFiles().get(0)));
        assertTrue(intakeResult.diagnostics().isEmpty(), () -> intakeResult.diagnostics().toString());
        assertEquals(1, intakeResult.readableFrames().size());
        MediaInputFrame sourceFrame = intakeResult.readableFrames().get(0);
        NormalizedCaptureFrame normalizedFrame = null;
        try {
            MediaNormalizationResult normalization = normalizer.normalize(sourceFrame);
            assertTrue(normalization.accepted(), () -> normalization.diagnostics().toString());
            normalizedFrame = normalization.frame().orElseThrow();
            PatternEvidence pattern = patternDetector.detect(sourceFrame, normalizedFrame);
            GeometryFitEvidence geometry = geometryFitter.fit(normalizedFrame, pattern);
            SourceSpaceValidationSample sampling = sourceSpaceSampler.sampleAndValidate(
                    sourceFrame,
                    normalizedFrame,
                    geometry
            );
            assertFalse(sampling.evidence().isEmpty());
            ModuleSamplingEvidence firstSampling = sampling.evidence().get(0);
            LocalRefinementEvidence localRefinement = sampling.localRefinementEvidence()
                    .stream()
                    .findFirst()
                    .orElseThrow();
            return new FixtureEvidence(pattern, geometry, firstSampling, localRefinement);
        } finally {
            sourceFrame.releaseArgbPixels();
            if (normalizedFrame != null) {
                normalizedFrame.releaseArgbPixels();
            }
        }
    }

    private Optional<ModuleSamplingEvidence> samplingEvidenceForFirstFile(GeneratedCaptureMediaFixture fixture) {
        MediaIntakeResult intakeResult = intake.read(List.of(fixture.mediaFiles().get(0)));
        if (intakeResult.readableFrames().isEmpty()) {
            return Optional.empty();
        }
        MediaInputFrame sourceFrame = intakeResult.readableFrames().get(0);
        NormalizedCaptureFrame normalizedFrame = null;
        try {
            MediaNormalizationResult normalization = normalizer.normalize(sourceFrame);
            if (!normalization.accepted() || normalization.frame().isEmpty()) {
                return Optional.empty();
            }
            normalizedFrame = normalization.frame().orElseThrow();
            PatternEvidence pattern = patternDetector.detect(sourceFrame, normalizedFrame);
            GeometryFitEvidence geometry = geometryFitter.fit(normalizedFrame, pattern);
            SourceSpaceValidationSample sampling = sourceSpaceSampler.sampleAndValidate(
                    sourceFrame,
                    normalizedFrame,
                    geometry
            );
            return sampling.evidence().stream().findFirst();
        } finally {
            sourceFrame.releaseArgbPixels();
            if (normalizedFrame != null) {
                normalizedFrame.releaseArgbPixels();
            }
        }
    }

    private static Stream<Arguments> sourceSpacePositiveFixtures() {
        return Stream.of(
                Arguments.of(
                        CaptureMediaCorpusFixtures.GENERATED_EXACT_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::generatedExactPng
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.GENERATED_CAMERA_LIKE_MONITOR_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::generatedCameraLikeMonitorPng
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.GENERATED_CAMERA_LIKE_MONITOR_JPEG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::generatedCameraLikeMonitorJpeg
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.GENERATED_CAMERA_LIKE_MONITOR_INVALID_PAYLOAD_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::generatedCameraLikeMonitorInvalidPayloadPng
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.GENERATED_LOCAL_DISTORTION_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::generatedLocalDistortionPng
                )
        );
    }

    private static Stream<Arguments> mvp10BaselineFixtures() {
        return Stream.of(
                Arguments.of(
                        CaptureMediaCorpusFixtures.MVP10_SEPARABLE_COLOR_SHIFT_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10SeparableColorShiftPng,
                        expectedMetrics(
                                CaptureMediaCorpusFixtures.MVP10_SEPARABLE_COLOR_SHIFT_PNG,
                                CaptureMediaReceiverStatus.INCOMPLETE,
                                23,
                                23,
                                0,
                                23,
                                0,
                                0,
                                0,
                                0,
                                0,
                                "COLOR_OR_COMPRESSION_SHIFT",
                                "FALLBACK_EXACT",
                                "WITHHELD",
                                "none",
                                -1.0d,
                                -1.0d
                        )
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.MVP10_COMPRESSION_LIKE_JPEG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10CompressionLikeJpeg,
                        expectedMetrics(
                                CaptureMediaCorpusFixtures.MVP10_COMPRESSION_LIKE_JPEG,
                                CaptureMediaReceiverStatus.INCOMPLETE,
                                23,
                                23,
                                1,
                                22,
                                1,
                                1,
                                63,
                                770,
                                49,
                                "COLOR_OR_COMPRESSION_SHIFT",
                                "SAFE_FOR_CLASSIFICATION",
                                "PARTIAL",
                                "none",
                                0.0034572365765129787d,
                                0.5055508080335283d
                        )
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.MVP10_AMBIGUOUS_COLOR_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10AmbiguousColorPng,
                        expectedMetrics(
                                CaptureMediaCorpusFixtures.MVP10_AMBIGUOUS_COLOR_PNG,
                                CaptureMediaReceiverStatus.REJECTED,
                                1,
                                1,
                                0,
                                1,
                                0,
                                0,
                                0,
                                0,
                                0,
                                "SCREEN_OR_FRAME_NOT_FOUND",
                                "NOT_AVAILABLE",
                                "NOT_AVAILABLE",
                                "not_available",
                                -1.0d,
                                -1.0d
                        )
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.MVP10_FALSE_POSITIVE_COLOR_GRID_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10FalsePositiveColorGridPng,
                        expectedMetrics(
                                CaptureMediaCorpusFixtures.MVP10_FALSE_POSITIVE_COLOR_GRID_PNG,
                                CaptureMediaReceiverStatus.REJECTED,
                                1,
                                1,
                                0,
                                1,
                                0,
                                0,
                                0,
                                0,
                                0,
                                "SCREEN_OR_FRAME_NOT_FOUND",
                                "NOT_AVAILABLE",
                                "NOT_AVAILABLE",
                                "not_available",
                                -1.0d,
                                -1.0d
                        )
                ),
                Arguments.of(
                        CaptureMediaCorpusFixtures.MVP10_STRUCTURALLY_INVALID_COLOR_SHIFT_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10StructurallyInvalidColorShiftPng,
                        expectedMetrics(
                                CaptureMediaCorpusFixtures.MVP10_STRUCTURALLY_INVALID_COLOR_SHIFT_PNG,
                                CaptureMediaReceiverStatus.INCOMPLETE,
                                1,
                                1,
                                0,
                                1,
                                0,
                                0,
                                41,
                                151,
                                690,
                                "TILE_DECODE_OR_ENVELOPE_FAILURE",
                                "SAFE_FOR_CLASSIFICATION",
                                "PARTIAL",
                                "none",
                                0.02610696498308618d,
                                0.34059416086524297d
                        )
                )
        );
    }

    private static CaptureMediaFixtureMetrics expectedMetrics(
            String scenarioId,
            CaptureMediaReceiverStatus receiverStatus,
            int submittedMediaCount,
            int readableMediaCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            int decodedTileCount,
            int recoveredUniqueFrameCount,
            int samplingReadableModuleCount,
            int samplingAmbiguousModuleCount,
            int samplingUnreadableModuleCount,
            String primaryDiagnosticCode,
            String paletteFallbackStatus,
            String samplingStatus,
            String downstreamFailureStages,
            double minimumConfidenceMargin,
            double averageConfidenceMargin
    ) {
        return new CaptureMediaFixtureMetrics(
                scenarioId,
                receiverStatus,
                submittedMediaCount,
                readableMediaCount,
                acceptedCandidateCount,
                rejectedCandidateCount,
                0,
                decodedTileCount,
                recoveredUniqueFrameCount,
                false,
                false,
                0,
                primaryDiagnosticCode,
                paletteFallbackStatus,
                samplingStatus,
                samplingReadableModuleCount,
                samplingAmbiguousModuleCount,
                samplingUnreadableModuleCount,
                0,
                0,
                downstreamFailureStages,
                minimumConfidenceMargin,
                averageConfidenceMargin
        );
    }

    private static Stream<Arguments> mvp10SafetyGateFixtures() {
        return Stream.of(
                Arguments.of(CaptureMediaCorpusFixtures.MVP10_AMBIGUOUS_COLOR_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10AmbiguousColorPng),
                Arguments.of(CaptureMediaCorpusFixtures.MVP10_FALSE_POSITIVE_COLOR_GRID_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10FalsePositiveColorGridPng),
                Arguments.of(CaptureMediaCorpusFixtures.MVP10_STRUCTURALLY_INVALID_COLOR_SHIFT_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::mvp10StructurallyInvalidColorShiftPng)
        );
    }

    private static Stream<Arguments> negativeFixtures() {
        return Stream.of(
                Arguments.of(CaptureMediaCorpusFixtures.NO_JAB_FRAME,
                        (FixtureFactory) CaptureMediaCorpusFixtures::noJabFrame),
                Arguments.of(CaptureMediaCorpusFixtures.BRIGHT_MONITOR_WITHOUT_JAB,
                        (FixtureFactory) CaptureMediaCorpusFixtures::brightMonitorWithoutJab),
                Arguments.of(CaptureMediaCorpusFixtures.UI_CHROME_WITHOUT_JAB,
                        (FixtureFactory) CaptureMediaCorpusFixtures::uiChromeWithoutJab),
                Arguments.of(CaptureMediaCorpusFixtures.REPEATED_STRIPES_WITHOUT_JAB,
                        (FixtureFactory) CaptureMediaCorpusFixtures::repeatedStripesWithoutJab),
                Arguments.of(CaptureMediaCorpusFixtures.REPEATED_GRID_WITHOUT_JAB,
                        (FixtureFactory) CaptureMediaCorpusFixtures::repeatedGridWithoutJab),
                Arguments.of(CaptureMediaCorpusFixtures.SYNC_LIKE_STRIPES_WITHOUT_JAB,
                        (FixtureFactory) CaptureMediaCorpusFixtures::syncLikeStripesWithoutJab),
                Arguments.of(CaptureMediaCorpusFixtures.PARTIAL_CROPPED_FRAME,
                        (FixtureFactory) CaptureMediaCorpusFixtures::partialCroppedFrame),
                Arguments.of(CaptureMediaCorpusFixtures.AMBIGUOUS_MULTI_SYMBOL_PNG,
                        (FixtureFactory) CaptureMediaCorpusFixtures::ambiguousMultiSymbolPng)
        );
    }

    @FunctionalInterface
    private interface FixtureFactory {

        GeneratedCaptureMediaFixture generate(Path workspace) throws Exception;
    }

    private record FixtureEvidence(
            PatternEvidence pattern,
            GeometryFitEvidence geometry,
            ModuleSamplingEvidence sampling,
            LocalRefinementEvidence localRefinement
    ) {

        private String describePattern() {
            return "pattern status=%s reasons=%s confidence=%s".formatted(
                    pattern.status(),
                    pattern.reasonCodes(),
                    pattern.confidence()
            );
        }

        private String describeGeometry() {
            return "geometry status=%s reasons=%s candidates=%s".formatted(
                    geometry.status(),
                    geometry.reasonCodes(),
                    geometry.retainedCandidates().stream()
                            .map(FixtureEvidence::describeGeometryCandidate)
                            .toList()
            );
        }

        private static String describeGeometryCandidate(GeometryCandidateEvidence candidate) {
            return "%s matched=%d reasons=%s".formatted(
                    candidate.status(),
                    candidate.matchedPointCount(),
                    candidate.reasonCodes()
            );
        }

        private String describeSampling() {
            return "sampling status=%s reasons=%s tileDecodeAttempted=%s acceptedPayloads=%d".formatted(
                    sampling.status(),
                    sampling.reasonCodes(),
                    sampling.tileDecodeAttempted(),
                    sampling.acceptedPayloadCount()
            );
        }

        private String describeLocalRefinement() {
            return "local status=%s reasons=%s applied=%s".formatted(
                    localRefinement.status(),
                    localRefinement.reasonCodes(),
                    localRefinement.appliedToSampling()
            );
        }
    }
}

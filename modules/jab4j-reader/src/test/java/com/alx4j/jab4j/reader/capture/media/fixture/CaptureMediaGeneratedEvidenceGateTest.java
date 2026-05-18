package com.alx4j.jab4j.reader.capture.media.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

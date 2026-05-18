package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;

/**
 * Generated-image backend checks for the optional BoofCV capture-media implementation.
 */
@DisplayName("Optional BoofCV backend generated fixtures")
class BoofCvCaptureMediaCvBackendGeneratedTest {

    private final BoofCvCaptureMediaCvBackend backend = new BoofCvCaptureMediaCvBackend();

    @Test
    @DisplayName("CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-PNG returns bounded normalized candidates")
    void generatedCameraLikeMonitorPngReturnsBoundedNormalizedCandidates() {
        // CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-PNG: local generated camera-like monitor PNG fixture.
        assertAcceptedCandidateResult(
                BoofCvGeneratedFixtureFactory.CAMERA_LIKE_MONITOR_PNG,
                BoofCvGeneratedFixtureFactory::cameraLikeMonitorPng
        );
    }

    @Test
    @DisplayName("CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-JPEG returns bounded normalized candidates")
    void generatedCameraLikeMonitorJpegReturnsBoundedNormalizedCandidates() {
        // CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-JPEG: local generated camera-like monitor JPEG-decoded fixture.
        assertAcceptedCandidateResult(
                BoofCvGeneratedFixtureFactory.CAMERA_LIKE_MONITOR_JPEG,
                BoofCvGeneratedFixtureFactory::cameraLikeMonitorJpegDecoded
        );
    }

    @Test
    @DisplayName("Generated skewed blurred monitor evidence emits fitted quadrilateral geometry")
    void generatedSkewedBlurredMonitorEvidenceEmitsFittedQuadrilateralGeometry() {
        BoofCvCandidateRegionProposer.ProposalResult proposal = new BoofCvCandidateRegionProposer()
                .propose(BoofCvGeneratedFixtureFactory.skewedBlurredMonitorPng());
        BoofCvCandidateRegionProposer.CandidateRegion region = proposal.regions().get(0);

        assertAll(
                () -> assertEquals(
                        BoofCvCandidateRegionProposer.GeometrySource.BOOFCV_FITTED_QUADRILATERAL,
                        region.geometrySource()
                ),
                () -> assertEquals(4, region.fittedVertexCount()),
                () -> assertTrue(
                        Math.abs(region.corners().topLeftY() - region.corners().topRightY()) > 10.0d
                                || Math.abs(region.corners().topLeftX() - region.corners().bottomLeftX()) > 10.0d
                ),
                () -> assertTrue(
                        proposal.metrics().get("boofCvFittedQuadrilateralCandidateCount") >= 1.0d,
                        proposal.metrics().toString()
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("falsePositiveFixtures")
    @DisplayName("Generated false positives remain rejected without accepted candidates")
    void generatedFalsePositivesRemainRejectedWithoutAcceptedCandidates(
            String scenarioId,
            Supplier<MediaInputFrame> fixture
    ) {
        MediaInputFrame inputFrame = fixture.get();

        CvDetectionResult result = backend.detect(inputFrame);

        assertAll(
                () -> assertEquals(
                        CvDetectionStatus.REJECTED,
                        result.status(),
                        scenarioId + " must reject non-JAB generated evidence: " + result.metrics()
                ),
                () -> assertEquals(
                        CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        result.diagnosticCode().orElseThrow(),
                        scenarioId + " must map rejection to SCREEN_OR_FRAME_NOT_FOUND"
                ),
                () -> assertTrue(
                        result.normalizedFrames().isEmpty(),
                        scenarioId + " must not expose backend-normalized frames"
                ),
                () -> assertAcceptedCandidateCountMetric(scenarioId, result, 0),
                () -> assertEquals(
                        0.0d,
                        result.metrics().get("boofCvStrictEvidenceCandidateCount"),
                        scenarioId + " must not report strict evidence for non-JAB fixtures"
                ),
                () -> assertEquals(
                        0.0d,
                        result.metrics().get("boofCvPlausibleValidationCandidateCount"),
                        scenarioId + " must not admit plausible validation candidates for non-JAB fixtures"
                ),
                () -> assertEquals(
                        result.metrics().get("boofCvScoredCandidateCount"),
                        result.metrics().get("boofCvRejectedScoredCandidateCount"),
                        scenarioId + " must count every scored non-JAB candidate as rejected"
                ),
                () -> assertEquals(
                        0.0d,
                        result.metrics().get("boofCvSelectedAdmissionBandCode"),
                        scenarioId + " must report rejected selected admission band"
                ),
                () -> assertEquals(
                        1.0d,
                        result.metrics().get("boofCvSelectedBackendCode"),
                        scenarioId + " must report the selected BoofCV backend code"
                ),
                () -> assertEquals(
                        0.0d,
                        result.metrics().get("boofCvPreprocessingModeCode"),
                        scenarioId + " must report that no preprocessing output crossed the boundary"
                ),
                () -> assertTrue(
                        result.metrics().get("boofCvSelectedRejectionReasonCode") > 0.0d,
                        scenarioId + " must report a deterministic rejection reason code"
                ),
                () -> assertFiniteMetrics(scenarioId, result.metrics())
        );
    }

    private static Stream<Arguments> falsePositiveFixtures() {
        return Stream.of(
                Arguments.of(
                        BoofCvGeneratedFixtureFactory.BRIGHT_MONITOR_WITHOUT_JAB,
                        (Supplier<MediaInputFrame>) BoofCvGeneratedFixtureFactory::brightMonitorWithoutJab
                ),
                Arguments.of(
                        BoofCvGeneratedFixtureFactory.UI_CHROME_WITHOUT_JAB,
                        (Supplier<MediaInputFrame>) BoofCvGeneratedFixtureFactory::uiChromeWithoutJab
                ),
                Arguments.of(
                        BoofCvGeneratedFixtureFactory.REPEATED_STRIPES_WITHOUT_JAB,
                        (Supplier<MediaInputFrame>) BoofCvGeneratedFixtureFactory::repeatedStripesWithoutJab
                ),
                Arguments.of(
                        BoofCvGeneratedFixtureFactory.PARTIAL_CROPPED_FRAME,
                        (Supplier<MediaInputFrame>) BoofCvGeneratedFixtureFactory::partialCroppedFrameEvidence
                )
        );
    }

    private void assertAcceptedCandidateResult(String scenarioId, Supplier<MediaInputFrame> fixture) {
        MediaInputFrame firstFrame = fixture.get();
        CvDetectionResult first = backend.detect(firstFrame);
        CvDetectionResult second = backend.detect(fixture.get());
        CvNormalizedFrame firstNormalizedFrame = first.normalizedFrames().get(0);
        CvNormalizedFrame secondNormalizedFrame = second.normalizedFrames().get(0);
        CvSamplingEvidence evidence = firstNormalizedFrame.samplingEvidence().orElseThrow();

        assertAll(
                () -> assertEquals(
                        CvDetectionStatus.ACCEPTED,
                        first.status(),
                        scenarioId + " must be accepted by the optional production backend: " + first.message()
                ),
                () -> assertTrue(
                        first.candidates().isEmpty(),
                        scenarioId + " must not expose source-space candidates after BoofCV-owned correction"
                ),
                () -> assertEquals(
                        3,
                        first.normalizedFrames().size(),
                        scenarioId + " must expose the bounded normalized candidate output"
                ),
                () -> assertTrue(
                        first.diagnosticCode().isEmpty(),
                        scenarioId + " must not expose a diagnostic code for accepted output"
                ),
                () -> assertEquals(first.status(), second.status()),
                () -> assertEquals(first.metrics(), second.metrics()),
                () -> assertEquals(first.diagnosticMetrics(), second.diagnosticMetrics()),
                () -> assertEquals(firstNormalizedFrame.layoutProfile(), secondNormalizedFrame.layoutProfile()),
                () -> assertEquals(firstNormalizedFrame.frameCorners(), secondNormalizedFrame.frameCorners()),
                () -> assertEquals(firstNormalizedFrame.qualityMetrics(), secondNormalizedFrame.qualityMetrics()),
                () -> assertEquals(firstNormalizedFrame.samplingEvidence(), secondNormalizedFrame.samplingEvidence()),
                () -> assertEquals(firstNormalizedFrame.geometrySource(), secondNormalizedFrame.geometrySource()),
                () -> assertTrue(Arrays.equals(
                        firstNormalizedFrame.argbPixels(),
                        secondNormalizedFrame.argbPixels()
                )),
                () -> assertAcceptedCandidateCountMetric(scenarioId, first, 3),
                () -> assertEquals(
                        first.metrics().get("boofCvAcceptedSourceCandidateCount"),
                        first.metrics().get("boofCvStrictEvidenceCandidateCount"),
                        scenarioId + " must keep generated positives on the strict evidence path"
                ),
                () -> assertEquals(
                        0.0d,
                        first.metrics().get("boofCvPlausibleValidationCandidateCount"),
                        scenarioId + " must not downgrade generated positives to plausible validation"
                ),
                () -> assertEquals(
                        1.0d,
                        first.metrics().get("boofCvSelectedAdmissionBandCode"),
                        scenarioId + " must report strict selected admission band"
                ),
                () -> assertEquals(
                        0.0d,
                        first.metrics().get("boofCvSelectedRejectionReasonCode"),
                        scenarioId + " must not report rejection for accepted strict evidence"
                ),
                () -> assertEquals(
                        1.0d,
                        first.metrics().get("boofCvSelectedBackendCode"),
                        scenarioId + " must report the selected BoofCV backend code"
                ),
                () -> assertEquals(
                        1.0d,
                        first.metrics().get("boofCvPreprocessingModeCode"),
                        scenarioId + " must report perspective-correction preprocessing"
                ),
                () -> assertFiniteMetrics(scenarioId, first.metrics()),
                () -> assertEquals(
                        "debug-low-density",
                        firstNormalizedFrame.layoutProfile().profileId(),
                        scenarioId + " primary candidate should remain the generated fixture layout"
                ),
                () -> assertTrue(
                        firstNormalizedFrame.geometrySource().isPresent(),
                        scenarioId + " must preserve geometry-source evidence"
                ),
                () -> assertEquals(
                        3,
                        firstNormalizedFrame.profileAlternativeCount(),
                        scenarioId + " must preserve the source-region profile alternative count"
                ),
                () -> assertEquals(
                        "boofcv",
                        evidence.backendId(),
                        scenarioId + " must carry BoofCV sampling evidence into the sampler path"
                ),
                () -> assertTrue(
                        evidence.gridPhase().isPresent(),
                        scenarioId + " must carry frame-level grid-phase evidence"
                ),
                () -> assertEquals(
                        1.0d,
                        evidence.metrics().get("boofCvSelectedBackendCode"),
                        scenarioId + " must carry selected-backend evidence as finite metrics"
                ),
                () -> assertEquals(
                        1.0d,
                        evidence.metrics().get("boofCvPreprocessingModeCode"),
                        scenarioId + " must carry preprocessing-mode evidence as finite metrics"
                ),
                () -> assertTrue(
                        evidence.metrics().get("boofCvSamplingEvidenceConfidence") > 0.0d,
                        scenarioId + " must carry evidence confidence as finite metrics"
                ),
                () -> assertSelectedBoundsInsideSourceFrame(scenarioId, firstFrame, first),
                () -> assertFiniteScoreMetric(scenarioId, first, "boofCvSelectedCandidateScore"),
                () -> assertFiniteScoreMetric(scenarioId, first, "boofCvSelectedGridScore"),
                () -> assertFiniteScoreMetric(scenarioId, first, "boofCvSelectedGeometrySourceCode")
        );
    }

    private static void assertSelectedBoundsInsideSourceFrame(
            String scenarioId,
            MediaInputFrame inputFrame,
            CvDetectionResult result
    ) {
        assertAll(
                () -> assertTrue(
                        result.metrics().get("boofCvSelectedLeftPx") >= 0,
                        scenarioId + " selected candidate left bound must stay inside the source frame"
                ),
                () -> assertTrue(
                        result.metrics().get("boofCvSelectedTopPx") >= 0,
                        scenarioId + " selected candidate top bound must stay inside the source frame"
                ),
                () -> assertTrue(
                        result.metrics().get("boofCvSelectedRightExclusivePx") <= inputFrame.widthPixels(),
                        scenarioId + " selected candidate right bound must stay inside the source frame"
                ),
                () -> assertTrue(
                        result.metrics().get("boofCvSelectedBottomExclusivePx") <= inputFrame.heightPixels(),
                        scenarioId + " selected candidate bottom bound must stay inside the source frame"
                )
        );
    }

    private static void assertFiniteMetrics(String scenarioId, Map<String, Double> metrics) {
        metrics.forEach((name, value) -> assertTrue(
                value != null && Double.isFinite(value),
                scenarioId + " metric " + name + " must be finite"
        ));
    }

    private static void assertFiniteScoreMetric(
            String scenarioId,
            CvDetectionResult result,
            String metricName
    ) {
        assertTrue(
                result.metrics().containsKey(metricName)
                        && Double.isFinite(result.metrics().get(metricName)),
                scenarioId + " must expose finite selected metric " + metricName
        );
    }

    private static void assertAcceptedCandidateCountMetric(
            String scenarioId,
            CvDetectionResult result,
            int expectedCount
    ) {
        assertEquals(
                (double) expectedCount,
                result.metrics().getOrDefault("boofCvAcceptedCandidateCount", 0.0d),
                scenarioId + " must report BoofCV accepted candidate count"
        );
    }
}

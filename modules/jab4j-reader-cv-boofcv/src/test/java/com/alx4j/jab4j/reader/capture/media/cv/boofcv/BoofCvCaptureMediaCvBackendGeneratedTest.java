package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.cv.CvCandidateScore;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;

/**
 * Generated-image backend checks for the optional BoofCV capture-media implementation.
 */
@DisplayName("Optional BoofCV backend generated fixtures")
class BoofCvCaptureMediaCvBackendGeneratedTest {

    private final BoofCvCaptureMediaCvBackend backend = new BoofCvCaptureMediaCvBackend();

    @Test
    @DisplayName("CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-PNG returns accepted source-space candidates")
    void generatedCameraLikeMonitorPngReturnsAcceptedSourceSpaceCandidates() {
        // CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-PNG: local generated camera-like monitor PNG fixture.
        assertAcceptedCandidateResult(
                BoofCvGeneratedFixtureFactory.CAMERA_LIKE_MONITOR_PNG,
                BoofCvGeneratedFixtureFactory::cameraLikeMonitorPng
        );
    }

    @Test
    @DisplayName("CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-JPEG returns accepted source-space candidates")
    void generatedCameraLikeMonitorJpegReturnsAcceptedSourceSpaceCandidates() {
        // CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-JPEG: local generated camera-like monitor JPEG-decoded fixture.
        assertAcceptedCandidateResult(
                BoofCvGeneratedFixtureFactory.CAMERA_LIKE_MONITOR_JPEG,
                BoofCvGeneratedFixtureFactory::cameraLikeMonitorJpegDecoded
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

        assertAll(
                () -> assertEquals(
                        CvDetectionStatus.ACCEPTED,
                        first.status(),
                        scenarioId + " must be accepted by the optional production backend: " + first.message()
                ),
                () -> assertFalse(
                        first.candidates().isEmpty(),
                        scenarioId + " must expose real CvFrameCandidate output"
                ),
                () -> assertTrue(
                        first.normalizedFrames().isEmpty(),
                        scenarioId + " must leave normalized frames empty when source-space candidates are returned"
                ),
                () -> assertTrue(
                        first.diagnosticCode().isEmpty(),
                        scenarioId + " must not expose a diagnostic code for accepted output"
                ),
                () -> assertEquals(
                        first,
                        second,
                        scenarioId + " must produce deterministic repeated backend output"
                ),
                () -> assertEquals(
                        (double) first.candidates().size(),
                        first.diagnosticMetrics().get("detectedCandidateCount"),
                        scenarioId + " must report deterministic detected candidate count diagnostics"
                ),
                () -> assertAcceptedCandidateCountMetric(scenarioId, first, first.candidates().size()),
                () -> assertEquals(
                        (double) first.candidates().size(),
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
                () -> assertFiniteMetrics(scenarioId, first.metrics()),
                () -> assertAcceptedCandidates(scenarioId, firstFrame, first)
        );
    }

    private static void assertAcceptedCandidates(
            String scenarioId,
            MediaInputFrame inputFrame,
            CvDetectionResult result
    ) {
        for (CvFrameCandidate candidate : result.candidates()) {
            assertAll(
                    () -> assertTrue(
                            candidate.sourceLeftPx() >= 0,
                            scenarioId + " candidate left bound must stay inside the source frame"
                    ),
                    () -> assertTrue(
                            candidate.sourceTopPx() >= 0,
                            scenarioId + " candidate top bound must stay inside the source frame"
                    ),
                    () -> assertTrue(
                            candidate.sourceRightExclusivePx() <= inputFrame.widthPixels(),
                            scenarioId + " candidate right bound must stay inside the source frame"
                    ),
                    () -> assertTrue(
                            candidate.sourceBottomExclusivePx() <= inputFrame.heightPixels(),
                            scenarioId + " candidate bottom bound must stay inside the source frame"
                    ),
                    () -> assertEquals(
                            "debug-low-density",
                            candidate.layoutProfile().profileId(),
                            scenarioId + " candidate must resolve the generated fixture layout"
                    ),
                    () -> assertFiniteScoreMetrics(scenarioId, candidate.score())
            );
        }
    }

    private static void assertFiniteMetrics(String scenarioId, Map<String, Double> metrics) {
        metrics.forEach((name, value) -> assertTrue(
                value != null && Double.isFinite(value),
                scenarioId + " metric " + name + " must be finite"
        ));
    }

    private static void assertFiniteScoreMetrics(String scenarioId, CvCandidateScore score) {
        assertFiniteMetrics(scenarioId, score.metrics());
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

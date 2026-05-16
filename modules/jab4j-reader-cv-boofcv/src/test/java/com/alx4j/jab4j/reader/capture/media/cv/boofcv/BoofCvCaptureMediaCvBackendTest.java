package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackends;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

@DisplayName("Optional BoofCV capture-media backend")
class BoofCvCaptureMediaCvBackendTest {

    @Test
    @DisplayName("Service loader exposes the optional BoofCV backend")
    void serviceLoaderExposesOptionalBoofCvBackend() {
        Optional<CaptureMediaCvBackend> backend = ServiceLoader.load(CaptureMediaCvBackend.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> "boofcv".equals(candidate.identity().backendId()))
                .findFirst();

        assertAll(
                () -> assertTrue(backend.isPresent()),
                () -> assertEquals(
                        "com.alx4j:jab4j-reader-cv-boofcv",
                        backend.orElseThrow().identity().implementationArtifact().orElseThrow()
                ),
                () -> assertTrue(backend.orElseThrow().identity().featureFlags().contains("optional-shaded-adapter"))
        );
    }

    @Test
    @DisplayName("Reader selector uses optional BoofCV only after explicit developer selection")
    void readerSelectorUsesOptionalBoofCvOnlyAfterExplicitDeveloperSelection() {
        String previousBackend = System.getProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
        try {
            System.clearProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
            CaptureMediaFrameNormalizer defaultNormalizer = new CaptureMediaFrameNormalizer();
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "legacy");
            CaptureMediaFrameNormalizer explicitlyLegacyNormalizer = new CaptureMediaFrameNormalizer();
            Optional<CaptureMediaCvBackend> backend = CaptureMediaCvBackends.findExplicit("boofcv");
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "boofcv");
            CaptureMediaFrameNormalizer explicitlyBoofCvNormalizer = new CaptureMediaFrameNormalizer();

            assertAll(
                () -> assertTrue(backend.isPresent()),
                () -> assertEquals("boofcv", backend.orElseThrow().identity().backendId()),
                () -> assertEquals("legacy", defaultNormalizer.normalizationBackendId()),
                () -> assertEquals("legacy", explicitlyLegacyNormalizer.normalizationBackendId()),
                () -> assertEquals("boofcv", explicitlyBoofCvNormalizer.normalizationBackendId()),
                () -> assertFalse(CaptureMediaCvBackends.findExplicit("missing-backend").isPresent())
            );
        } finally {
            restoreBackendProperty(previousBackend);
        }
    }

    @Test
    @DisplayName("BoofCV backend runs a stable image probe through backend-neutral diagnostics")
    void boofCvBackendRunsStableImageProbeThroughBackendNeutralDiagnostics() {
        CvDetectionResult result = new BoofCvCaptureMediaCvBackend().detect(mediaFrame(8, 6));

        assertAll(
                () -> assertEquals(CvDetectionStatus.REJECTED, result.status()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        result.diagnosticCode().orElseThrow()),
                () -> assertEquals(8.0d, result.metrics().get("boofCvInputWidthPixels")),
                () -> assertEquals(6.0d, result.metrics().get("boofCvInputHeightPixels")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvConversionCopyCount")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvGrayscaleConversionCount")),
                () -> assertTrue(result.metrics().containsKey("boofCvExternalContourCount"))
        );
    }

    @Test
    @DisplayName("Default production backend returns source-space candidates for accepted generated monitor frames")
    void defaultProductionBackendReturnsSourceSpaceCandidatesForAcceptedGeneratedMonitorFrames() {
        CvDetectionResult result = new BoofCvCaptureMediaCvBackend()
                .detect(BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng());

        assertAll(
                () -> assertEquals(CvDetectionStatus.ACCEPTED, result.status()),
                () -> assertFalse(result.candidates().isEmpty()),
                () -> assertTrue(result.normalizedFrames().isEmpty()),
                () -> assertTrue(result.diagnosticCode().isEmpty()),
                () -> assertEquals(
                        (double) result.candidates().size(),
                        result.metrics().get("boofCvAcceptedCandidateCount")
                )
        );
    }

    @Test
    @DisplayName("Opt-in backend path returns normalized frames instead of source-space candidates")
    void optInBackendPathReturnsNormalizedFramesInsteadOfSourceSpaceCandidates() {
        MediaInputFrame frame = BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng();
        CvDetectionResult defaultResult = new BoofCvCaptureMediaCvBackend().detect(frame);
        CvFrameCandidate acceptedCandidate = defaultResult.candidates().get(0);
        CvDetectionResult optInResult = BoofCvCaptureMediaCvBackend.withPerspectiveCorrection().detect(frame);
        CvNormalizedFrame normalizedFrame = optInResult.normalizedFrames().get(0);
        CaptureMediaQualityMetrics expectedQuality = CaptureMediaQualityMetrics.perspectiveCorrected(
                acceptedCandidate.score().frameCoverageRatio(),
                acceptedCandidate.score().skewScore()
        );
        int[] normalizedPixels = normalizedFrame.argbPixels();

        assertAll(
                () -> assertEquals(CvDetectionStatus.ACCEPTED, optInResult.status()),
                () -> assertTrue(optInResult.candidates().isEmpty()),
                () -> assertEquals(defaultResult.candidates().size(), optInResult.normalizedFrames().size()),
                () -> assertTrue(optInResult.diagnosticCode().isEmpty()),
                () -> assertEquals(acceptedCandidate.layoutProfile(), normalizedFrame.layoutProfile()),
                () -> assertEquals(acceptedCandidate.frameCorners(), normalizedFrame.frameCorners()),
                () -> assertEquals(expectedQuality, normalizedFrame.qualityMetrics()),
                () -> assertEquals(
                        acceptedCandidate.layoutProfile().frameWidthPx()
                                * acceptedCandidate.layoutProfile().frameHeightPx(),
                        normalizedPixels.length
                ),
                () -> assertEquals(0xFF000000, normalizedPixels[0] & 0xFF000000),
                () -> assertEquals(
                        0xFF000000,
                        normalizedPixels[normalizedPixels.length - 1] & 0xFF000000
                )
        );
    }

    private MediaInputFrame mediaFrame(int widthPixels, int heightPixels) {
        int[] pixels = new int[widthPixels * heightPixels];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = index % 2 == 0 ? 0xFFFFFFFF : 0xFF000000;
        }
        return new MediaInputFrame(
                "boofcv-probe.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                widthPixels,
                heightPixels,
                "png",
                "probe-hash",
                pixels
        );
    }

    private void restoreBackendProperty(String previousBackend) {
        if (previousBackend == null) {
            System.clearProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
            return;
        }
        System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, previousBackend);
    }
}

package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CvBackendIdentity;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.core.image.GConvertImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F32;
import georegression.struct.point.Point2D_F32;

@DisplayName("BoofCV dependency and adapter smoke")
class BoofCvDependencySmokeTest {

    @Test
    @DisplayName("Selected BoofCV artifacts provide planned CV primitives on the test classpath")
    void selectedBoofCvArtifactsProvidePlannedCvPrimitivesOnTestClasspath() {
        Planar<GrayU8> rgb = new Planar<>(GrayU8.class, 4, 4, 3);
        paintWhiteBlock(rgb);
        GrayU8 gray = new GrayU8(4, 4);

        GConvertImage.average(rgb, gray);
        GrayU8 binary = ThresholdImageOps.threshold(gray, null, 128, false);
        List<Contour> contours = BinaryImageOps.contourExternal(binary, ConnectRule.EIGHT);

        Point2D_F32 mapped = new Point2D_F32();
        PixelTransformHomography_F32 transform = new PixelTransformHomography_F32(
                new Homography2D_F32(1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 2.0f, 0.0f, 0.0f, 1.0f)
        );
        transform.compute(3, 4, mapped);

        assertAll(
                () -> assertEquals(255, gray.get(1, 1)),
                () -> assertEquals(1, binary.get(1, 1)),
                () -> assertEquals(0, binary.get(0, 0)),
                () -> assertFalse(contours.isEmpty()),
                () -> assertEquals(4.0f, mapped.x, 0.0001f),
                () -> assertEquals(6.0f, mapped.y, 0.0001f),
                () -> assertDoesNotThrow(() -> Class.forName("boofcv.factory.feature.detect.line.FactoryDetectLine")),
                () -> assertDoesNotThrow(() -> Class.forName("boofcv.factory.feature.detect.interest.FactoryDetectPoint"))
        );
    }

    @Test
    @DisplayName("ARGB conversion copies RGB bands and records conversion metadata")
    void argbConversionCopiesRgbBandsAndRecordsConversionMetadata() {
        MediaInputFrame frame = new MediaInputFrame(
                "argb-conversion.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                2,
                2,
                "png",
                "source-hash",
                new int[] {
                        0x11223344, 0xFF556677,
                        0xAA8899AA, 0xFFCCDDEE
                }
        );

        BoofCvArgbToPlanarRgbConverter.ConvertedArgbImage converted =
                BoofCvArgbToPlanarRgbConverter.convert(frame);
        frame.releaseArgbPixels();

        assertAll(
                () -> assertEquals(2, converted.metadata().widthPixels()),
                () -> assertEquals(2, converted.metadata().heightPixels()),
                () -> assertEquals("RGB", converted.metadata().colorOrder()),
                () -> assertEquals(
                        "copied-argb-to-planar-u8-bands-alpha-ignored",
                        converted.metadata().copyBehavior()
                ),
                () -> assertEquals(0x22, converted.image().getBand(0).get(0, 0)),
                () -> assertEquals(0x33, converted.image().getBand(1).get(0, 0)),
                () -> assertEquals(0x44, converted.image().getBand(2).get(0, 0)),
                () -> assertEquals(0x55, converted.image().getBand(0).get(1, 0)),
                () -> assertEquals(0x99, converted.image().getBand(1).get(0, 1)),
                () -> assertEquals(0xEE, converted.image().getBand(2).get(1, 1))
        );
    }

    @Test
    @DisplayName("BoofCV skeleton exposes backend identity metadata")
    void boofCvSkeletonExposesBackendIdentityMetadata() {
        CvBackendIdentity identity = new BoofCvCaptureMediaCvBackend().identity();

        assertAll(
                () -> assertEquals("boofcv", identity.backendId()),
                () -> assertEquals(Optional.of("org.boofcv:boofcv-feature"), identity.implementationArtifact()),
                () -> assertTrue(identity.featureFlags().contains("test-scope-adapter-skeleton")),
                () -> assertTrue(identity.featureFlags().contains("argb-to-planar-rgb-copy")),
                () -> assertTrue(identity.featureFlags().contains("stable-backend-failure-mapping"))
        );
    }

    @Test
    @DisplayName("BoofCV skeleton returns deterministic rejection diagnostics")
    void boofCvSkeletonReturnsDeterministicRejectionDiagnostics() {
        BoofCvCaptureMediaCvBackend backend = new BoofCvCaptureMediaCvBackend();
        MediaInputFrame frame = mediaFrameWithWhiteBlock("boofcv-rejected.png");

        CvDetectionResult result = backend.detect(frame);

        assertAll(
                () -> assertEquals(CvDetectionStatus.REJECTED, result.status()),
                () -> assertEquals(Optional.of(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND),
                        result.diagnosticCode()),
                () -> assertEquals("Media normalization did not find a clean supported rendered frame region",
                        result.message()),
                () -> assertEquals(4.0d, result.metrics().get("boofCvInputWidthPixels")),
                () -> assertEquals(4.0d, result.metrics().get("boofCvInputHeightPixels")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvConversionCopyCount")),
                () -> assertEquals(128.0d, result.metrics().get("boofCvThreshold")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvExternalContourCount"))
        );
    }

    @Test
    @DisplayName("BoofCV runtime failure maps to stable backend failure")
    void boofCvRuntimeFailureMapsToStableBackendFailure() {
        BoofCvCaptureMediaCvBackend backend = new BoofCvCaptureMediaCvBackend(true);

        CvDetectionResult result = backend.detect(mediaFrameWithWhiteBlock("boofcv-failure.png"));

        assertAll(
                () -> assertEquals(CvDetectionStatus.BACKEND_FAILURE, result.status()),
                () -> assertEquals(Optional.of(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA), result.diagnosticCode()),
                () -> assertEquals("Capture-media CV backend failed while evaluating the frame", result.message()),
                () -> assertEquals(1.0d, result.metrics().get("backendFailureCount"))
        );
    }

    @Test
    @DisplayName("BoofCV skeleton can be explicitly selected through normalizer constructor")
    void boofCvSkeletonCanBeExplicitlySelectedThroughNormalizerConstructor() {
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(new BoofCvCaptureMediaCvBackend());

        MediaNormalizationResult result = normalizer.normalize(mediaFrameWithWhiteBlock("boofcv-normalizer.png"));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND, diagnostic.code()),
                () -> assertEquals("boofcv-normalizer.png", diagnostic.sourceId().orElseThrow()),
                () -> assertEquals(4.0d, diagnostic.metrics().get("boofCvInputWidthPixels")),
                () -> assertEquals(1.0d, diagnostic.metrics().get("boofCvConversionCopyCount")),
                () -> assertEquals(1.0d, diagnostic.metrics().get("boofCvExternalContourCount"))
        );
    }

    private void paintWhiteBlock(Planar<GrayU8> rgb) {
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 2; x++) {
                rgb.getBand(0).set(x, y, 255);
                rgb.getBand(1).set(x, y, 255);
                rgb.getBand(2).set(x, y, 255);
            }
        }
    }

    private MediaInputFrame mediaFrameWithWhiteBlock(String sourceId) {
        int[] pixels = new int[16];
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 2; x++) {
                pixels[(y * 4) + x] = 0xFFFFFFFF;
            }
        }
        return new MediaInputFrame(
                sourceId,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                4,
                4,
                "png",
                "source-hash",
                pixels
        );
    }
}

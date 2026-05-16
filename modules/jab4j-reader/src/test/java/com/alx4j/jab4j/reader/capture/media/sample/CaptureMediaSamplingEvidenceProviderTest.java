package com.alx4j.jab4j.reader.capture.media.sample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;

@DisplayName("Capture media sampling evidence provider")
class CaptureMediaSamplingEvidenceProviderTest {

    private static final LayoutProfile CAPTURE_LAYOUT = new LayoutProfile(
            "debug-low-density",
            1,
            2,
            1280,
            720,
            16,
            40,
            "solidWhite",
            48,
            24,
            "black",
            "preserveAspect"
    );
    private static final FixedLayoutPlan LAYOUT_PLAN = new FixedLayoutPlanner().plan(CAPTURE_LAYOUT);

    private final CaptureMediaSamplingEvidenceProvider provider = new CaptureMediaSamplingEvidenceProvider();

    @Test
    @DisplayName("Camera-derived normalized frames return bounded finite sampling evidence")
    void cameraDerivedNormalizedFramesReturnBoundedFiniteSamplingEvidence() {
        NormalizedCaptureFrame frame = cameraDerivedFrame(syncBandPixels());

        CvSamplingEvidence evidence = provider.evidenceFor(frame, LAYOUT_PLAN).orElseThrow();
        Map<String, Double> metrics = evidence.metrics();

        assertAll(
                () -> assertEquals("reader-normalized-argb", evidence.backendId()),
                () -> assertTrue(evidence.gridPhase().isPresent()),
                () -> assertTrue(evidence.confidence() >= 0.55d),
                () -> assertEquals(0.0d, evidence.gridPhaseOffsetXPx(), 0.001d),
                () -> assertEquals(0.0d, evidence.gridPhaseOffsetYPx(), 0.001d),
                () -> assertTrue(evidence.gridPhase().orElseThrow().localContrast() > 0.90d),
                () -> assertTrue(luminance(evidence.gridPhase().orElseThrow()
                        .localWhiteReferenceArgb().orElseThrow()) > 220),
                () -> assertTrue(luminance(evidence.gridPhase().orElseThrow()
                        .localBlackReferenceArgb().orElseThrow()) < 35),
                () -> assertTrue(metrics.containsKey("readerSamplingEvidenceConfidence")),
                () -> assertTrue(metrics.values().stream().allMatch(Double::isFinite))
        );
    }

    @Test
    @DisplayName("Low-confidence camera-derived frames return empty evidence")
    void lowConfidenceCameraDerivedFramesReturnEmptyEvidence() {
        int[] pixels = new int[CAPTURE_LAYOUT.frameWidthPx() * CAPTURE_LAYOUT.frameHeightPx()];
        Arrays.fill(pixels, 0xFF808080);
        NormalizedCaptureFrame frame = cameraDerivedFrame(pixels);

        Optional<CvSamplingEvidence> evidence = provider.evidenceFor(frame, LAYOUT_PLAN);

        assertTrue(evidence.isEmpty());
    }

    @Test
    @DisplayName("Exact rendered frames return empty evidence")
    void exactRenderedFramesReturnEmptyEvidence() {
        NormalizedCaptureFrame frame = normalizedFrame(
                syncBandPixels(),
                FrameCorners.exactFrame(CAPTURE_LAYOUT.frameWidthPx(), CAPTURE_LAYOUT.frameHeightPx()),
                CaptureMediaQualityMetrics.exactRenderedFrame()
        );

        Optional<CvSamplingEvidence> evidence = provider.evidenceFor(frame, LAYOUT_PLAN);

        assertTrue(evidence.isEmpty());
    }

    @Test
    @DisplayName("Clean axis-aligned normalized frames return empty evidence")
    void cleanAxisAlignedNormalizedFramesReturnEmptyEvidence() {
        NormalizedCaptureFrame frame = normalizedFrame(
                syncBandPixels(),
                new FrameCorners(100.0d, 200.0d, 1380.0d, 200.0d, 1380.0d, 920.0d, 100.0d, 920.0d),
                CaptureMediaQualityMetrics.axisAlignedInset(0.50d)
        );

        Optional<CvSamplingEvidence> evidence = provider.evidenceFor(frame, LAYOUT_PLAN);

        assertTrue(evidence.isEmpty());
    }

    @Test
    @DisplayName("Unsupported layout frames return empty evidence")
    void unsupportedLayoutFramesReturnEmptyEvidence() {
        NormalizedCaptureFrame frame = new NormalizedCaptureFrame(
                "camera-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                1600,
                1000,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                "png",
                "abc123",
                "unsupported-layout",
                cameraCorners(),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                syncBandPixels()
        );

        Optional<CvSamplingEvidence> evidence = provider.evidenceFor(frame, LAYOUT_PLAN);

        assertFalse(evidence.isPresent());
    }

    private int[] syncBandPixels() {
        int[] pixels = new int[CAPTURE_LAYOUT.frameWidthPx() * CAPTURE_LAYOUT.frameHeightPx()];
        Arrays.fill(pixels, 0xFF000000);
        int cellWidth = Math.max(8, LAYOUT_PLAN.separatorThicknessPx() * 2);
        int top = CAPTURE_LAYOUT.outerMarginPx();
        int bottomExclusive = top + CAPTURE_LAYOUT.topSyncBandPx();
        int left = CAPTURE_LAYOUT.outerMarginPx();
        int rightExclusive = CAPTURE_LAYOUT.frameWidthPx() - CAPTURE_LAYOUT.outerMarginPx();
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = left; col < rightExclusive; col++) {
                int segment = (col - left) / cellWidth;
                pixels[(row * CAPTURE_LAYOUT.frameWidthPx()) + col] =
                        segment % 2 == 0 ? 0xFFFFFFFF : 0xFF000000;
            }
        }
        return pixels;
    }

    private NormalizedCaptureFrame cameraDerivedFrame(int[] pixels) {
        return normalizedFrame(
                pixels,
                cameraCorners(),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d)
        );
    }

    private NormalizedCaptureFrame normalizedFrame(
            int[] pixels,
            FrameCorners corners,
            CaptureMediaQualityMetrics qualityMetrics
    ) {
        return new NormalizedCaptureFrame(
                "camera-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                1600,
                1000,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                "png",
                "abc123",
                CAPTURE_LAYOUT.profileId(),
                corners,
                qualityMetrics,
                pixels
        );
    }

    private FrameCorners cameraCorners() {
        return new FrameCorners(50.0d, 60.0d, 1250.0d, 80.0d, 1240.0d, 700.0d, 40.0d, 680.0d);
    }

    private int luminance(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (int) Math.round((0.2126d * red) + (0.7152d * green) + (0.0722d * blue));
    }
}

package com.alx4j.jab4j.reader.capture.media.normalize;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

@DisplayName("Capture media frame normalizer")
class CaptureMediaFrameNormalizerTest {

    private static final int DEBUG_FRAME_WIDTH = 1280;
    private static final int DEBUG_FRAME_HEIGHT = 720;
    private static final int INSET_X = 80;
    private static final int INSET_Y = 60;
    private static final int OUTER_MARGIN = 40;
    private static final int TOP_SYNC_BAND = 48;
    private static final int METADATA_BAND = 24;
    private static final int TILE_GAP = 16;
    private static final int BORDER_THICKNESS = 8;
    private static final int TILE_SLOT_WIDTH = 592;
    private static final int TILE_SLOT_HEIGHT = 568;
    private static final int GRID_ORIGIN_X = 40;
    private static final int GRID_ORIGIN_Y = 112;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DARK_GRAY = 0xFF202020;
    private static final int PHOTO_BACKGROUND = 0xFF303840;

    private final CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer();

    @Test
    @DisplayName("Exact supported rendered dimensions pass through without pixel or source changes")
    void exactSupportedRenderedDimensionsPassThroughWithoutPixelOrSourceChanges() {
        int[] pixels = new int[1280 * 720];
        Arrays.fill(pixels, 0xFF000000);
        pixels[0] = 0xFFFFFFFF;
        MediaInputFrame inputFrame = new MediaInputFrame(
                "capture-frame.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                3,
                1280,
                720,
                "png",
                "abc123",
                Optional.of(250L),
                Optional.of(7L),
                pixels
        );
        pixels[0] = 0xFFFF0000;

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        NormalizedCaptureFrame normalized = result.frame().orElseThrow();
        CaptureMediaQualityMetrics metrics = normalized.qualityMetrics();
        inputFrame.releaseArgbPixels();
        assertAll(
                () -> assertTrue(result.accepted()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals("capture-frame.png", normalized.sourceId()),
                () -> assertEquals(CaptureMediaSourceKind.STILL_IMAGE_FILE, normalized.sourceKind()),
                () -> assertEquals(3, normalized.callerOrder()),
                () -> assertEquals(1280, normalized.originalWidthPixels()),
                () -> assertEquals(720, normalized.originalHeightPixels()),
                () -> assertEquals(1280, normalized.normalizedWidthPixels()),
                () -> assertEquals(720, normalized.normalizedHeightPixels()),
                () -> assertEquals("png", normalized.formatName()),
                () -> assertEquals("abc123", normalized.pixelSha256()),
                () -> assertEquals(250L, normalized.timestampMillis().orElseThrow()),
                () -> assertEquals(7L, normalized.frameNumber().orElseThrow()),
                () -> assertEquals("debug-low-density", normalized.layoutProfileId()),
                () -> assertEquals(FrameCorners.exactFrame(1280, 720), normalized.frameCorners()),
                () -> assertEquals(1.0d, metrics.frameCoverageRatio()),
                () -> assertEquals(0.0d, metrics.skewScore()),
                () -> assertEquals(CaptureMediaQualityMetrics.NOT_MEASURED, metrics.blurScore()),
                () -> assertEquals(0xFFFFFFFF, normalized.argbPixelAt(0, 0)),
                () -> assertThrows(IllegalStateException.class, () -> inputFrame.argbPixelAt(0, 0))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("axisAlignedInsetVariants")
    @DisplayName("Generated axis-aligned supported frame insets are cropped across positions and margins")
    void generatedAxisAlignedSupportedFrameInsetsAreCroppedAcrossPositionsAndMargins(
            String scenario,
            int canvasWidth,
            int canvasHeight,
            int insetX,
            int insetY
    ) {
        int[] renderedFrame = renderedDebugFramePixels();
        int[] canvas = inset(renderedFrame, canvasWidth, canvasHeight, insetX, insetY);
        MediaInputFrame inputFrame = new MediaInputFrame(
                scenario + ".png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                1,
                canvasWidth,
                canvasHeight,
                "png",
                "source-hash",
                canvas
        );

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        NormalizedCaptureFrame normalized = result.frame().orElseThrow();
        CaptureMediaQualityMetrics metrics = normalized.qualityMetrics();
        assertAll(
                () -> assertTrue(result.accepted()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals(scenario + ".png", normalized.sourceId()),
                () -> assertEquals(CaptureMediaSourceKind.STILL_IMAGE_FILE, normalized.sourceKind()),
                () -> assertEquals(1, normalized.callerOrder()),
                () -> assertEquals(canvasWidth, normalized.originalWidthPixels()),
                () -> assertEquals(canvasHeight, normalized.originalHeightPixels()),
                () -> assertEquals(DEBUG_FRAME_WIDTH, normalized.normalizedWidthPixels()),
                () -> assertEquals(DEBUG_FRAME_HEIGHT, normalized.normalizedHeightPixels()),
                () -> assertEquals("source-hash", normalized.pixelSha256()),
                () -> assertEquals("debug-low-density", normalized.layoutProfileId()),
                () -> assertEquals(new FrameCorners(
                        insetX,
                        insetY,
                        insetX + DEBUG_FRAME_WIDTH,
                        insetY,
                        insetX + DEBUG_FRAME_WIDTH,
                        insetY + DEBUG_FRAME_HEIGHT,
                        insetX,
                        insetY + DEBUG_FRAME_HEIGHT
                ), normalized.frameCorners()),
                () -> assertEquals(
                        (double) (DEBUG_FRAME_WIDTH * DEBUG_FRAME_HEIGHT) / (canvasWidth * canvasHeight),
                        metrics.frameCoverageRatio()
                ),
                () -> assertEquals(0.0d, metrics.skewScore()),
                () -> assertEquals(CaptureMediaQualityMetrics.NOT_MEASURED, metrics.blurScore()),
                () -> assertEquals(WHITE, normalized.argbPixelAt(0, 0)),
                () -> assertEquals(BLACK, normalized.argbPixelAt(
                        GRID_ORIGIN_Y + (TILE_SLOT_HEIGHT / 2),
                        GRID_ORIGIN_X + (TILE_SLOT_WIDTH / 2)
                ))
        );
    }

    @Test
    @DisplayName("Generated inset without exact-color sync band evidence is rejected")
    void generatedInsetWithoutExactColorSyncBandEvidenceIsRejected() {
        int canvasWidth = DEBUG_FRAME_WIDTH + (2 * INSET_X);
        int canvasHeight = DEBUG_FRAME_HEIGHT + (2 * INSET_Y);
        int[] renderedFrame = renderedDebugFramePixels(false, true);
        int[] canvas = inset(renderedFrame, canvasWidth, canvasHeight, INSET_X, INSET_Y);
        MediaInputFrame inputFrame = mediaFrame("no-sync-band.png", canvasWidth, canvasHeight, canvas);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.frame().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking())
        );
    }

    @Test
    @DisplayName("Generated inset without tile-slot grid geometry evidence is rejected")
    void generatedInsetWithoutTileSlotGridGeometryEvidenceIsRejected() {
        int canvasWidth = DEBUG_FRAME_WIDTH + (2 * INSET_X);
        int canvasHeight = DEBUG_FRAME_HEIGHT + (2 * INSET_Y);
        int[] renderedFrame = renderedDebugFramePixels(true, false);
        int[] canvas = inset(renderedFrame, canvasWidth, canvasHeight, INSET_X, INSET_Y);
        MediaInputFrame inputFrame = mediaFrame("no-grid-geometry.png", canvasWidth, canvasHeight, canvas);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.frame().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking())
        );
    }

    @Test
    @DisplayName("Generated inset below frame coverage threshold is rejected as monitor too small")
    void generatedInsetBelowFrameCoverageThresholdIsRejectedAsMonitorTooSmall() {
        int canvasWidth = 3200;
        int canvasHeight = 1800;
        int[] renderedFrame = renderedDebugFramePixels();
        int[] canvas = inset(renderedFrame, canvasWidth, canvasHeight, 120, 90);
        MediaInputFrame inputFrame = mediaFrame("too-small-monitor.png", canvasWidth, canvasHeight, canvas);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.frame().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.MONITOR_TOO_SMALL, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking())
        );
    }

    @Test
    @DisplayName("Generated off-screen inset is rejected as partial when required border evidence is clipped")
    void generatedOffScreenInsetIsRejectedAsPartialWhenRequiredBorderEvidenceIsClipped() {
        int canvasWidth = DEBUG_FRAME_WIDTH + INSET_X;
        int canvasHeight = DEBUG_FRAME_HEIGHT + (2 * INSET_Y);
        int[] renderedFrame = renderedDebugFramePixels();
        int[] canvas = inset(renderedFrame, canvasWidth, canvasHeight, -INSET_X, INSET_Y);
        MediaInputFrame inputFrame = mediaFrame("partial-offscreen.png", canvasWidth, canvasHeight, canvas);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.frame().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.FRAME_PARTIALLY_OUTSIDE_IMAGE, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking())
        );
    }

    @Test
    @DisplayName("Generated multiple complete regions are rejected as ambiguous")
    void generatedMultipleCompleteRegionsAreRejectedAsAmbiguous() {
        int canvasWidth = (2 * DEBUG_FRAME_WIDTH) + 240;
        int canvasHeight = DEBUG_FRAME_HEIGHT + (2 * INSET_Y);
        int[] renderedFrame = renderedDebugFramePixels();
        int[] canvas = blankCanvas(canvasWidth, canvasHeight);
        paste(renderedFrame, canvas, canvasWidth, canvasHeight, 80, INSET_Y);
        paste(renderedFrame, canvas, canvasWidth, canvasHeight, 80 + DEBUG_FRAME_WIDTH + 80, INSET_Y);
        MediaInputFrame inputFrame = mediaFrame("ambiguous-regions.png", canvasWidth, canvasHeight, canvas);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.frame().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking())
        );
    }

    @Test
    @DisplayName("Generated moderate perspective skew is resampled into a supported layout")
    void generatedModeratePerspectiveSkewIsResampledIntoSupportedLayout() {
        int canvasWidth = 1600;
        int canvasHeight = 900;
        FrameCorners expectedCorners = new FrameCorners(150, 80, 1370, 110, 1280, 790, 230, 760);
        int[] renderedFrame = renderedDebugFramePixels();
        int[] canvas = perspectiveProject(renderedFrame, canvasWidth, canvasHeight, expectedCorners);
        MediaInputFrame inputFrame = mediaFrame("moderate-perspective.png", canvasWidth, canvasHeight, canvas);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        NormalizedCaptureFrame normalized = result.frame().orElseThrow();
        CaptureMediaQualityMetrics metrics = normalized.qualityMetrics();
        assertAll(
                () -> assertTrue(result.accepted()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals(DEBUG_FRAME_WIDTH, normalized.normalizedWidthPixels()),
                () -> assertEquals(DEBUG_FRAME_HEIGHT, normalized.normalizedHeightPixels()),
                () -> assertEquals("debug-low-density", normalized.layoutProfileId()),
                () -> assertCornersNear(expectedCorners, normalized.frameCorners(), 6.0d),
                () -> assertTrue(metrics.frameCoverageRatio() > 0.20d),
                () -> assertTrue(metrics.skewScore() > 0.0d),
                () -> assertTrue(metrics.skewScore() <= 0.35d),
                () -> assertEquals(WHITE, normalized.argbPixelAt(OUTER_MARGIN + (TOP_SYNC_BAND / 2), OUTER_MARGIN + 8)),
                () -> assertEquals(BLACK, normalized.argbPixelAt(OUTER_MARGIN + (TOP_SYNC_BAND / 2), OUTER_MARGIN + 24)),
                () -> assertEquals(BLACK, normalized.argbPixelAt(
                        GRID_ORIGIN_Y + (TILE_SLOT_HEIGHT / 2),
                        GRID_ORIGIN_X + (TILE_SLOT_WIDTH / 2)
                )),
                () -> assertEquals(WHITE, normalized.argbPixelAt(
                        GRID_ORIGIN_Y + (TILE_SLOT_HEIGHT / 2),
                        GRID_ORIGIN_X + TILE_SLOT_WIDTH + (TILE_GAP / 2)
                ))
        );
    }

    @Test
    @DisplayName("Generated severe perspective skew is rejected with perspective-too-severe diagnostics")
    void generatedSeverePerspectiveSkewIsRejectedWithPerspectiveTooSevereDiagnostics() {
        int canvasWidth = 1600;
        int canvasHeight = 900;
        FrameCorners severeCorners = new FrameCorners(450, 70, 1130, 110, 1440, 800, 120, 760);
        int[] renderedFrame = renderedDebugFramePixels();
        int[] canvas = perspectiveProject(renderedFrame, canvasWidth, canvasHeight, severeCorners);
        MediaInputFrame inputFrame = mediaFrame("severe-perspective.png", canvasWidth, canvasHeight, canvas);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.frame().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.PERSPECTIVE_TOO_SEVERE, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking())
        );
    }

    @Test
    @DisplayName("Unsupported dimensions are rejected without arbitrary photo recovery")
    void unsupportedDimensionsAreRejectedWithoutArbitraryPhotoRecovery() {
        MediaInputFrame inputFrame = new MediaInputFrame(
                "phone-photo.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                320,
                240,
                "png",
                "abc123",
                new int[320 * 240]
        );

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.frame().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        diagnostic.code()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertEquals(CaptureMediaSourceKind.STILL_IMAGE_FILE, diagnostic.sourceKind().orElseThrow()),
                () -> assertEquals("phone-photo.png", diagnostic.sourceId().orElseThrow()),
                () -> assertEquals(0, diagnostic.callerOrder().orElseThrow())
        );
    }

    @Test
    @DisplayName("Normalized frame releases pixels while preserving diagnostic metadata")
    void normalizedFrameReleasesPixelsWhilePreservingDiagnosticMetadata() {
        NormalizedCaptureFrame frame = new NormalizedCaptureFrame(
                "capture.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                4,
                2,
                2,
                1,
                1,
                "png",
                "pixel-hash",
                "debug-low-density",
                Optional.of(250L),
                Optional.of(7L),
                FrameCorners.exactFrame(1, 1),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                new int[] { WHITE }
        );

        frame.releaseArgbPixels();
        frame.releaseArgbPixels();

        assertAll(
                () -> assertEquals("capture.png", frame.sourceId()),
                () -> assertEquals(4, frame.callerOrder()),
                () -> assertEquals("pixel-hash", frame.pixelSha256()),
                () -> assertEquals(250L, frame.timestampMillis().orElseThrow()),
                () -> assertEquals(7L, frame.frameNumber().orElseThrow()),
                () -> assertEquals(FrameCorners.exactFrame(1, 1), frame.frameCorners()),
                () -> assertEquals(1.0d, frame.qualityMetrics().frameCoverageRatio()),
                () -> assertThrows(IllegalStateException.class, () -> frame.argbPixelAt(0, 0)),
                () -> assertThrows(IllegalStateException.class, frame::copyArgbPixels)
        );
    }

    private static Stream<Arguments> axisAlignedInsetVariants() {
        return Stream.of(
                Arguments.of("centered-uncropped-still", DEBUG_FRAME_WIDTH + (2 * INSET_X),
                        DEBUG_FRAME_HEIGHT + (2 * INSET_Y), INSET_X, INSET_Y),
                Arguments.of("offset-uncropped-still", 1600, 900, 37, 121),
                Arguments.of("wide-margin-uncropped-still", 1880, 1040, 300, 160)
        );
    }

    private MediaInputFrame mediaFrame(String sourceId, int widthPixels, int heightPixels, int[] pixels) {
        return new MediaInputFrame(
                sourceId,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                widthPixels,
                heightPixels,
                "png",
                "source-hash",
                pixels
        );
    }

    private int[] renderedDebugFramePixels() {
        return renderedDebugFramePixels(true, true);
    }

    private int[] renderedDebugFramePixels(boolean syncBandEnabled, boolean gridGeometryEnabled) {
        int[] pixels = new int[DEBUG_FRAME_WIDTH * DEBUG_FRAME_HEIGHT];
        Arrays.fill(pixels, BLACK);
        paintOuterBorder(pixels);
        if (syncBandEnabled) {
            paintSyncBand(pixels);
        }
        paintMetadataBand(pixels);
        if (gridGeometryEnabled) {
            paintTileSlotGridGeometry(pixels);
        }
        return pixels;
    }

    private void paintOuterBorder(int[] pixels) {
        for (int row = 0; row < DEBUG_FRAME_HEIGHT; row++) {
            for (int col = 0; col < DEBUG_FRAME_WIDTH; col++) {
                if (row < BORDER_THICKNESS
                        || row >= DEBUG_FRAME_HEIGHT - BORDER_THICKNESS
                        || col < BORDER_THICKNESS
                        || col >= DEBUG_FRAME_WIDTH - BORDER_THICKNESS) {
                    pixels[(row * DEBUG_FRAME_WIDTH) + col] = WHITE;
                }
            }
        }
    }

    private void paintSyncBand(int[] pixels) {
        int cellWidth = Math.max(8, BORDER_THICKNESS * 2);
        int left = OUTER_MARGIN;
        int top = OUTER_MARGIN;
        int rightExclusive = DEBUG_FRAME_WIDTH - OUTER_MARGIN;
        int bottomExclusive = top + TOP_SYNC_BAND;
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = left; col < rightExclusive; col++) {
                int segmentIndex = (col - left) / cellWidth;
                pixels[(row * DEBUG_FRAME_WIDTH) + col] = segmentIndex % 2 == 0 ? WHITE : BLACK;
            }
        }
    }

    private void paintMetadataBand(int[] pixels) {
        int top = OUTER_MARGIN + TOP_SYNC_BAND;
        int bottomExclusive = top + METADATA_BAND;
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = OUTER_MARGIN; col < DEBUG_FRAME_WIDTH - OUTER_MARGIN; col++) {
                pixels[(row * DEBUG_FRAME_WIDTH) + col] = DARK_GRAY;
            }
        }
    }

    private void paintTileSlotGridGeometry(int[] pixels) {
        int gapLeft = GRID_ORIGIN_X + TILE_SLOT_WIDTH;
        int gapRightExclusive = gapLeft + TILE_GAP;
        int gapBottomExclusive = GRID_ORIGIN_Y + TILE_SLOT_HEIGHT;
        for (int row = GRID_ORIGIN_Y; row < gapBottomExclusive; row++) {
            for (int col = gapLeft; col < gapRightExclusive; col++) {
                pixels[(row * DEBUG_FRAME_WIDTH) + col] = WHITE;
            }
        }
    }

    private int[] inset(int[] renderedFrame, int canvasWidth, int canvasHeight, int insetX, int insetY) {
        int[] canvas = blankCanvas(canvasWidth, canvasHeight);
        paste(renderedFrame, canvas, canvasWidth, canvasHeight, insetX, insetY);
        return canvas;
    }

    private int[] perspectiveProject(
            int[] renderedFrame,
            int canvasWidth,
            int canvasHeight,
            FrameCorners corners
    ) {
        int[] canvas = blankCanvas(canvasWidth, canvasHeight);
        PerspectiveTransform canvasToFrame = PerspectiveTransform.fromUnitSquareTo(corners).inverse();
        int left = clamp((int) Math.floor(minX(corners)), 0, canvasWidth - 1);
        int right = clamp((int) Math.ceil(maxX(corners)), 0, canvasWidth - 1);
        int top = clamp((int) Math.floor(minY(corners)), 0, canvasHeight - 1);
        int bottom = clamp((int) Math.ceil(maxY(corners)), 0, canvasHeight - 1);
        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) {
                PerspectiveTransform.PerspectivePoint source = canvasToFrame.map(col, row);
                if (source.x() < 0.0d || source.x() > 1.0d || source.y() < 0.0d || source.y() > 1.0d) {
                    continue;
                }
                int sourceX = clamp(
                        (int) Math.round(source.x() * (DEBUG_FRAME_WIDTH - 1)),
                        0,
                        DEBUG_FRAME_WIDTH - 1
                );
                int sourceY = clamp(
                        (int) Math.round(source.y() * (DEBUG_FRAME_HEIGHT - 1)),
                        0,
                        DEBUG_FRAME_HEIGHT - 1
                );
                canvas[(row * canvasWidth) + col] = renderedFrame[(sourceY * DEBUG_FRAME_WIDTH) + sourceX];
            }
        }
        return canvas;
    }

    private int[] blankCanvas(int canvasWidth, int canvasHeight) {
        int[] canvas = new int[canvasWidth * canvasHeight];
        Arrays.fill(canvas, PHOTO_BACKGROUND);
        return canvas;
    }

    private void paste(int[] renderedFrame, int[] canvas, int canvasWidth, int canvasHeight, int insetX, int insetY) {
        for (int row = 0; row < DEBUG_FRAME_HEIGHT; row++) {
            int destinationRow = insetY + row;
            if (destinationRow < 0 || destinationRow >= canvasHeight) {
                continue;
            }
            int sourceCol = Math.max(0, -insetX);
            int destinationCol = Math.max(0, insetX);
            int copyWidth = Math.min(DEBUG_FRAME_WIDTH - sourceCol, canvasWidth - destinationCol);
            if (copyWidth <= 0) {
                continue;
            }
            int sourceOffset = (row * DEBUG_FRAME_WIDTH) + sourceCol;
            int destinationOffset = (destinationRow * canvasWidth) + destinationCol;
            System.arraycopy(renderedFrame, sourceOffset, canvas, destinationOffset, copyWidth);
        }
    }

    private void assertCornersNear(FrameCorners expected, FrameCorners actual, double tolerance) {
        assertAll(
                () -> assertEquals(expected.topLeftX(), actual.topLeftX(), tolerance),
                () -> assertEquals(expected.topLeftY(), actual.topLeftY(), tolerance),
                () -> assertEquals(expected.topRightX(), actual.topRightX(), tolerance),
                () -> assertEquals(expected.topRightY(), actual.topRightY(), tolerance),
                () -> assertEquals(expected.bottomRightX(), actual.bottomRightX(), tolerance),
                () -> assertEquals(expected.bottomRightY(), actual.bottomRightY(), tolerance),
                () -> assertEquals(expected.bottomLeftX(), actual.bottomLeftX(), tolerance),
                () -> assertEquals(expected.bottomLeftY(), actual.bottomLeftY(), tolerance)
        );
    }

    private double minX(FrameCorners corners) {
        return Math.min(
                Math.min(corners.topLeftX(), corners.topRightX()),
                Math.min(corners.bottomRightX(), corners.bottomLeftX())
        );
    }

    private double maxX(FrameCorners corners) {
        return Math.max(
                Math.max(corners.topLeftX(), corners.topRightX()),
                Math.max(corners.bottomRightX(), corners.bottomLeftX())
        );
    }

    private double minY(FrameCorners corners) {
        return Math.min(
                Math.min(corners.topLeftY(), corners.topRightY()),
                Math.min(corners.bottomRightY(), corners.bottomLeftY())
        );
    }

    private double maxY(FrameCorners corners) {
        return Math.max(
                Math.max(corners.topLeftY(), corners.topRightY()),
                Math.max(corners.bottomRightY(), corners.bottomLeftY())
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

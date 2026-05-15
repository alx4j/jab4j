package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.cv.CvBackendIdentity;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.cv.legacy.LegacyCaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
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

    private static final int PHOTO_BACKGROUND = 0xFF303840;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DARK_GRAY = 0xFF202020;
    private static final int GENERATED_CANVAS_WIDTH = 1600;
    private static final int GENERATED_CANVAS_HEIGHT = 900;
    private static final int GENERATED_INSET_X = 80;
    private static final int GENERATED_INSET_Y = 60;

    private final CaptureRenderedLayoutCatalog layoutCatalog = new CaptureRenderedLayoutCatalog();
    private final FixedLayoutPlanner layoutPlanner = new FixedLayoutPlanner();

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
    @DisplayName("BoofCV adapter exposes backend identity metadata")
    void boofCvAdapterExposesBackendIdentityMetadata() {
        CvBackendIdentity identity = new BoofCvCaptureMediaCvBackend().identity();

        assertAll(
                () -> assertEquals("boofcv", identity.backendId()),
                () -> assertEquals(Optional.of("org.boofcv:boofcv-feature"), identity.implementationArtifact()),
                () -> assertTrue(identity.featureFlags().contains("test-scope-adapter")),
                () -> assertTrue(identity.featureFlags().contains("argb-to-planar-rgb-copy")),
                () -> assertTrue(identity.featureFlags().contains("grayscale-threshold-contour-candidate-evidence")),
                () -> assertTrue(identity.featureFlags().contains("reader-owned-layout-scoring")),
                () -> assertTrue(identity.featureFlags().contains("stable-backend-failure-mapping"))
        );
    }

    @Test
    @DisplayName("BoofCV adapter returns deterministic rejection diagnostics")
    void boofCvAdapterReturnsDeterministicRejectionDiagnostics() {
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
                () -> assertEquals(1.0d, result.metrics().get("boofCvGrayscaleConversionCount")),
                () -> assertEquals(178.0d, result.metrics().get("boofCvThreshold")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvExternalContourCount")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvComponentCandidateCount")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvScoredCandidateCount")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvJabEvidenceCandidateCount"))
        );
    }

    @Test
    @DisplayName("BoofCV proposer finds bounded regions from generated monitor evidence")
    void boofCvProposerFindsBoundedRegionsFromGeneratedMonitorEvidence() {
        BoofCvCandidateRegionProposer.ProposalResult proposal =
                new BoofCvCandidateRegionProposer().propose(generatedCameraLikeFrame("boofcv-proposer.png"));
        BoofCvCandidateRegionProposer.CandidateRegion region = proposal.regions().get(0);

        assertAll(
                () -> assertFalse(proposal.regions().isEmpty()),
                () -> assertEquals(GENERATED_CANVAS_WIDTH, proposal.widthPixels()),
                () -> assertEquals(GENERATED_CANVAS_HEIGHT, proposal.heightPixels()),
                () -> assertTrue(proposal.externalContourCount() > 0),
                () -> assertTrue(region.contourPointCount() >= 8),
                () -> assertEquals(GENERATED_INSET_X, region.leftPx(), 2),
                () -> assertEquals(GENERATED_INSET_Y, region.topPx(), 2),
                () -> assertEquals(GENERATED_INSET_X + debugProfile().frameWidthPx(), region.rightExclusivePx(), 2),
                () -> assertEquals(GENERATED_INSET_Y + debugProfile().frameHeightPx(), region.bottomExclusivePx(), 2),
                () -> assertEquals(GENERATED_INSET_X, region.corners().topLeftX(), 2.0d),
                () -> assertEquals(GENERATED_INSET_Y, region.corners().topLeftY(), 2.0d)
        );
    }

    @Test
    @DisplayName("BoofCV adapter accepts generated candidates and records comparison metrics")
    void boofCvAdapterAcceptsGeneratedCandidatesAndRecordsComparisonMetrics() {
        CvDetectionResult boofResult = new BoofCvCaptureMediaCvBackend()
                .detect(generatedCameraLikeFrame("boofcv-generated-monitor.png"));
        CvDetectionResult legacyResult = new LegacyCaptureMediaCvBackend()
                .detect(generatedCameraLikeFrame("legacy-generated-monitor.png"));
        CvFrameCandidate boofCandidate = boofResult.candidates().get(0);
        CvFrameCandidate legacyCandidate = legacyResult.candidates().get(0);

        assertAll(
                () -> assertEquals(CvDetectionStatus.ACCEPTED, boofResult.status()),
                () -> assertEquals(CvDetectionStatus.ACCEPTED, legacyResult.status()),
                () -> assertEquals("debug-low-density", boofCandidate.layoutProfile().profileId()),
                () -> assertEquals(1, boofResult.candidates().size()),
                () -> assertEquals(legacyResult.candidates().size(), boofResult.candidates().size()),
                () -> assertEquals(legacyCandidate.sourceLeftPx(), boofCandidate.sourceLeftPx(), 4),
                () -> assertEquals(legacyCandidate.sourceTopPx(), boofCandidate.sourceTopPx(), 4),
                () -> assertEquals(legacyCandidate.sourceRightExclusivePx(), boofCandidate.sourceRightExclusivePx(), 4),
                () -> assertEquals(legacyCandidate.sourceBottomExclusivePx(), boofCandidate.sourceBottomExclusivePx(), 4),
                () -> assertTrue(boofResult.metrics().get("boofCvComponentCandidateCount") >= 1.0d),
                () -> assertEquals(1.0d, boofResult.metrics().get("boofCvAcceptedCandidateCount")),
                () -> assertTrue(boofCandidate.score().totalScore() >= 0.45d),
                () -> assertTrue(boofCandidate.score().syncBandScore() >= 0.48d),
                () -> assertTrue(boofCandidate.score().gridScore() >= 0.36d)
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
    @DisplayName("BoofCV adapter can be explicitly selected through normalizer constructor")
    void boofCvAdapterCanBeExplicitlySelectedThroughNormalizerConstructor() {
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(new BoofCvCaptureMediaCvBackend());

        MediaNormalizationResult result = normalizer.normalize(mediaFrameWithWhiteBlock("boofcv-normalizer.png"));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND, diagnostic.code()),
                () -> assertEquals("boofcv-normalizer.png", diagnostic.sourceId().orElseThrow()),
                () -> assertEquals(4.0d, diagnostic.metrics().get("boofCvInputWidthPixels")),
                () -> assertEquals(1.0d, diagnostic.metrics().get("boofCvConversionCopyCount")),
                () -> assertEquals(1.0d, diagnostic.metrics().get("boofCvExternalContourCount")),
                () -> assertEquals(0.0d, diagnostic.metrics().get("boofCvComponentCandidateCount"))
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

    private MediaInputFrame generatedCameraLikeFrame(String sourceId) {
        LayoutProfile profile = debugProfile();
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
        int[] rendered = shiftPaletteColors(renderedFramePixels(profile, layoutPlan), 18);
        int[] canvas = blankCanvas(GENERATED_CANVAS_WIDTH, GENERATED_CANVAS_HEIGHT);
        fillRect(canvas, 48, 42, GENERATED_CANVAS_WIDTH - 96, GENERATED_CANVAS_HEIGHT - 84, 0xFF15191D);
        fillRect(canvas, 78, 70, GENERATED_CANVAS_WIDTH - 156, GENERATED_CANVAS_HEIGHT - 140, 0xFF242A31);
        paste(
                rendered,
                profile.frameWidthPx(),
                profile.frameHeightPx(),
                canvas,
                GENERATED_INSET_X,
                GENERATED_INSET_Y
        );
        return new MediaInputFrame(
                sourceId,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                GENERATED_CANVAS_WIDTH,
                GENERATED_CANVAS_HEIGHT,
                "png",
                "source-hash",
                canvas
        );
    }

    private LayoutProfile debugProfile() {
        return layoutCatalog.profiles().stream()
                .filter(profile -> "debug-low-density".equals(profile.profileId()))
                .findFirst()
                .orElseThrow();
    }

    private int[] renderedFramePixels(LayoutProfile profile, FixedLayoutPlan layoutPlan) {
        int[] pixels = new int[profile.frameWidthPx() * profile.frameHeightPx()];
        Arrays.fill(pixels, BLACK);
        paintOuterBorder(pixels, profile, layoutPlan.separatorThicknessPx());
        paintSyncBand(pixels, profile, layoutPlan);
        paintMetadataBand(pixels, profile);
        paintTileSlotGridGeometry(pixels, profile, layoutPlan);
        return pixels;
    }

    private void paintOuterBorder(int[] pixels, LayoutProfile profile, int borderThickness) {
        for (int row = 0; row < profile.frameHeightPx(); row++) {
            for (int col = 0; col < profile.frameWidthPx(); col++) {
                if (row < borderThickness
                        || row >= profile.frameHeightPx() - borderThickness
                        || col < borderThickness
                        || col >= profile.frameWidthPx() - borderThickness) {
                    pixels[(row * profile.frameWidthPx()) + col] = WHITE;
                }
            }
        }
    }

    private void paintSyncBand(int[] pixels, LayoutProfile profile, FixedLayoutPlan layoutPlan) {
        int cellWidth = Math.max(8, layoutPlan.separatorThicknessPx() * 2);
        int left = profile.outerMarginPx();
        int top = profile.outerMarginPx();
        int rightExclusive = profile.frameWidthPx() - profile.outerMarginPx();
        int bottomExclusive = top + profile.topSyncBandPx();
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = left; col < rightExclusive; col++) {
                int segmentIndex = (col - left) / cellWidth;
                pixels[(row * profile.frameWidthPx()) + col] = segmentIndex % 2 == 0 ? WHITE : BLACK;
            }
        }
    }

    private void paintMetadataBand(int[] pixels, LayoutProfile profile) {
        int top = profile.outerMarginPx() + profile.topSyncBandPx();
        int bottomExclusive = top + profile.metadataBandPx();
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = profile.outerMarginPx(); col < profile.frameWidthPx() - profile.outerMarginPx(); col++) {
                pixels[(row * profile.frameWidthPx()) + col] = DARK_GRAY;
            }
        }
    }

    private void paintTileSlotGridGeometry(int[] pixels, LayoutProfile profile, FixedLayoutPlan layoutPlan) {
        for (int col = 0; col < profile.cols() - 1; col++) {
            TilePlacement left = layoutPlan.tilePlacements().get(col);
            TilePlacement right = layoutPlan.tilePlacements().get(col + 1);
            int gapLeft = left.xPx() + left.widthPx();
            int gapRightExclusive = right.xPx();
            fillRect(
                    pixels,
                    profile.frameWidthPx(),
                    gapLeft,
                    layoutPlan.gridOriginYPx(),
                    gapRightExclusive - gapLeft,
                    layoutPlan.usableGridHeightPx(),
                    WHITE
            );
        }
        for (TilePlacement placement : layoutPlan.tilePlacements()) {
            paintTileBorder(pixels, profile, placement, layoutPlan.separatorThicknessPx());
        }
    }

    private void paintTileBorder(
            int[] pixels,
            LayoutProfile profile,
            TilePlacement placement,
            int borderThickness
    ) {
        for (int row = 0; row < placement.heightPx(); row++) {
            for (int col = 0; col < placement.widthPx(); col++) {
                if (row < borderThickness
                        || row >= placement.heightPx() - borderThickness
                        || col < borderThickness
                        || col >= placement.widthPx() - borderThickness) {
                    int x = placement.xPx() + col;
                    int y = placement.yPx() + row;
                    pixels[(y * profile.frameWidthPx()) + x] = WHITE;
                }
            }
        }
    }

    private int[] blankCanvas(int canvasWidth, int canvasHeight) {
        int[] pixels = new int[canvasWidth * canvasHeight];
        Arrays.fill(pixels, PHOTO_BACKGROUND);
        return pixels;
    }

    private void fillRect(int[] pixels, int left, int top, int width, int height, int color) {
        fillRect(pixels, GENERATED_CANVAS_WIDTH, left, top, width, height, color);
    }

    private void fillRect(int[] pixels, int canvasWidth, int left, int top, int width, int height, int color) {
        for (int row = top; row < top + height; row++) {
            for (int col = left; col < left + width; col++) {
                pixels[(row * canvasWidth) + col] = color;
            }
        }
    }

    private void paste(
            int[] source,
            int sourceWidth,
            int sourceHeight,
            int[] canvas,
            int insetX,
            int insetY
    ) {
        for (int row = 0; row < sourceHeight; row++) {
            int sourceOffset = row * sourceWidth;
            int destinationOffset = ((insetY + row) * GENERATED_CANVAS_WIDTH) + insetX;
            System.arraycopy(source, sourceOffset, canvas, destinationOffset, sourceWidth);
        }
    }

    private int[] shiftPaletteColors(int[] pixels, int colorShift) {
        int[] shifted = Arrays.copyOf(pixels, pixels.length);
        for (int index = 0; index < shifted.length; index++) {
            shifted[index] = shiftPaletteColor(shifted[index], colorShift);
        }
        return shifted;
    }

    private int shiftPaletteColor(int argb, int colorShift) {
        int red = shiftedChannel((argb >>> 16) & 0xFF, colorShift);
        int green = shiftedChannel((argb >>> 8) & 0xFF, colorShift);
        int blue = shiftedChannel(argb & 0xFF, colorShift);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private int shiftedChannel(int value, int colorShift) {
        return value < 128
                ? Math.min(255, value + colorShift)
                : Math.max(0, value - colorShift);
    }
}

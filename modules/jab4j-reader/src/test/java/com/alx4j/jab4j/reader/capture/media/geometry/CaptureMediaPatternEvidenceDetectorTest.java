package com.alx4j.jab4j.reader.capture.media.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.CoordinateObservationSource;
import com.alx4j.jab4j.reader.capture.media.evidence.FinderRole;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidenceStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureEvidence;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaPaletteSampler;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;

@DisplayName("Capture media pattern evidence detector")
class CaptureMediaPatternEvidenceDetectorTest {

    private static final int FRAME_WIDTH = 1280;
    private static final int FRAME_HEIGHT = 720;
    private static final String PIXEL_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final TileCodecProfile TILE_PROFILE = TileCodecProfiles.balancedV1();
    private static final int SIDE_VERSION = TILE_PROFILE.maxSideVersion();
    private static final LayoutProfile CAPTURE_LAYOUT = new LayoutProfile(
            "debug-low-density",
            1,
            2,
            FRAME_WIDTH,
            FRAME_HEIGHT,
            16,
            40,
            "solidWhite",
            48,
            24,
            "black",
            "preserveAspect"
    );
    private static final FixedLayoutPlan LAYOUT_PLAN = new FixedLayoutPlanner().plan(CAPTURE_LAYOUT);
    private static final int DIMENSION = TILE_PROFILE.dimensionForSideVersion(SIDE_VERSION);

    private final CaptureMediaPaletteSampler paletteSampler = new CaptureMediaPaletteSampler();
    private final SupportedTileFinderEvaluator finderEvaluator = new SupportedTileFinderEvaluator();
    private final CaptureMediaPatternEvidenceDetector detector = new CaptureMediaPatternEvidenceDetector();

    @Test
    @DisplayName("Exact generated finder roles are detected")
    void exactGeneratedFinderRolesAreDetected() {
        int[] pixels = blankPixels();
        paintSupportedFinders(pixels);

        PatternEvidence evidence = detector.detect(normalizedFrame(pixels, false));

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.DETECTED, evidence.status()),
                () -> assertEquals("debug-low-density", evidence.layoutProfileId()),
                () -> assertTrue(evidence.candidateId().patternEvidenceId().orElseThrow().endsWith("/pattern-v1")),
                () -> assertFalse(evidence.alignmentExpected()),
                () -> assertEquals(List.of(CaptureMediaEvidenceReasonCode.ALIGNMENT_NOT_EXPECTED),
                        evidence.reasonCodes()),
                () -> assertEquals(1.0d, evidence.confidence(), 0.000001d),
                () -> assertEquals(4, evidence.features().size()),
                () -> assertEquals(1.0d, evidence.orientationCandidates().get("rotation-0"), 0.000001d),
                () -> assertEquals(1.0d, evidence.layoutProfileCandidates().get("debug-low-density"), 0.000001d),
                () -> assertFinder(evidence, FinderRole.TOP_LEFT, 9, 9),
                () -> assertFinder(evidence, FinderRole.TOP_RIGHT, 9, 9),
                () -> assertFinder(evidence, FinderRole.BOTTOM_LEFT, 9, 9),
                () -> assertFinder(evidence, FinderRole.BOTTOM_RIGHT, 9, 9),
                () -> assertEquals(
                        CoordinateObservationSource.NORMALIZED_CANDIDATE,
                        feature(evidence, FinderRole.TOP_LEFT).observationSource()
                )
        );
    }

    @Test
    @DisplayName("Retained source pixels produce source-space finder evidence")
    void retainedSourcePixelsProduceSourceSpaceFinderEvidence() {
        int[] normalizedPixels = blankPixels();
        int[] sourcePixels = blankPixels();
        paintSupportedFinders(sourcePixels);
        NormalizedCaptureFrame frame = normalizedFrame(normalizedPixels, false);

        PatternEvidence sourceEvidence = detector.detect(sourceFrame(sourcePixels), frame);
        PatternEvidence normalizedEvidence = detector.detect(frame);

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.NOT_FOUND, normalizedEvidence.status()),
                () -> assertEquals(PatternEvidenceStatus.DETECTED, sourceEvidence.status()),
                () -> assertEquals(4, sourceEvidence.features().size()),
                () -> assertEquals(
                        CoordinateObservationSource.SOURCE_SPACE,
                        feature(sourceEvidence, FinderRole.TOP_LEFT).observationSource()
                ),
                () -> assertFinder(sourceEvidence, FinderRole.TOP_LEFT, 9, 9),
                () -> assertFinder(sourceEvidence, FinderRole.BOTTOM_RIGHT, 9, 9)
        );
    }

    @Test
    @DisplayName("Camera-derived recoverable finder roles are detected")
    void cameraDerivedRecoverableFinderRolesAreDetected() {
        int[] pixels = blankPixels();
        paintRoleMatches(pixels, FinderRole.TOP_LEFT, 6);
        paintRoleMatches(pixels, FinderRole.TOP_RIGHT, 6);
        paintRoleMatches(pixels, FinderRole.BOTTOM_LEFT, 6);

        PatternEvidence evidence = detector.detect(normalizedFrame(pixels, true));

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.DETECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.PARTIAL_FINDER_EVIDENCE)),
                () -> assertEquals(18.0d / 36.0d, evidence.confidence(), 0.000001d),
                () -> assertFinder(evidence, FinderRole.TOP_LEFT, 6, 9),
                () -> assertFinder(evidence, FinderRole.BOTTOM_RIGHT, 0, 9)
        );
    }

    private MediaInputFrame sourceFrame(int[] pixels) {
        return new MediaInputFrame(
                "pattern-candidate.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                PIXEL_SHA256,
                pixels
        );
    }

    @Test
    @DisplayName("Wrong role colors are rejected")
    void wrongRoleColorsAreRejected() {
        int[] pixels = blankPixels();
        paintRoleMatches(pixels, FinderRole.TOP_LEFT, 9);
        paintRoleMatches(pixels, FinderRole.TOP_RIGHT, 9);
        paintRoleColor(pixels, FinderRole.BOTTOM_LEFT, finderWindow(FinderRole.BOTTOM_RIGHT).expectedColor());
        paintRoleColor(pixels, FinderRole.BOTTOM_RIGHT, finderWindow(FinderRole.BOTTOM_LEFT).expectedColor());

        PatternEvidence evidence = detector.detect(normalizedFrame(pixels, false));

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.INSUFFICIENT_FINDER_MATCHES)),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.PARTIAL_FINDER_EVIDENCE)),
                () -> assertFinder(evidence, FinderRole.BOTTOM_LEFT, 0, 9),
                () -> assertFinder(evidence, FinderRole.BOTTOM_RIGHT, 0, 9)
        );
    }

    @Test
    @DisplayName("Insufficient partial finders are rejected")
    void insufficientPartialFindersAreRejected() {
        int[] pixels = blankPixels();
        paintRoleMatches(pixels, FinderRole.TOP_LEFT, 6);
        paintRoleMatches(pixels, FinderRole.TOP_RIGHT, 6);
        paintRoleMatches(pixels, FinderRole.BOTTOM_LEFT, 5);
        paintRoleMatches(pixels, FinderRole.BOTTOM_RIGHT, 5);

        PatternEvidence evidence = detector.detect(normalizedFrame(pixels, true));

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.REJECTED, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.INSUFFICIENT_FINDER_MATCHES)),
                () -> assertEquals(22.0d / 36.0d, evidence.confidence(), 0.000001d)
        );
    }

    @Test
    @DisplayName("Candidates without finder evidence are not found")
    void candidatesWithoutFinderEvidenceAreNotFound() {
        PatternEvidence evidence = detector.detect(normalizedFrame(blankPixels(), false));

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.NOT_FOUND, evidence.status()),
                () -> assertTrue(evidence.features().isEmpty()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE)),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE))
        );
    }

    @Test
    @DisplayName("Multiple passing layout profiles without dominance are ambiguous")
    void multiplePassingLayoutProfilesWithoutDominanceAreAmbiguous() {
        LayoutProfile duplicate = new LayoutProfile(
                "debug-low-density-copy",
                CAPTURE_LAYOUT.rows(),
                CAPTURE_LAYOUT.cols(),
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                CAPTURE_LAYOUT.tileGapPx(),
                CAPTURE_LAYOUT.outerMarginPx(),
                CAPTURE_LAYOUT.separatorStyle(),
                CAPTURE_LAYOUT.topSyncBandPx(),
                CAPTURE_LAYOUT.metadataBandPx(),
                CAPTURE_LAYOUT.backgroundStyle(),
                CAPTURE_LAYOUT.fitPolicy()
        );
        CaptureMediaPatternEvidenceDetector ambiguousDetector = new CaptureMediaPatternEvidenceDetector(
                new CaptureRenderedLayoutCatalog(List.of(CAPTURE_LAYOUT, duplicate)),
                new FixedLayoutPlanner(),
                TILE_PROFILE,
                new CaptureMediaPaletteSampler(),
                new SupportedTileFinderEvaluator()
        );
        int[] pixels = blankPixels();
        paintSupportedFinders(pixels);

        PatternEvidence evidence = ambiguousDetector.detect(normalizedFrame(pixels, false));

        assertAll(
                () -> assertEquals(PatternEvidenceStatus.AMBIGUOUS, evidence.status()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.MULTIPLE_PROFILE_CANDIDATES)),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.LOW_DOMINANCE_MARGIN)),
                () -> assertEquals(0.0d, evidence.dominanceMargin(), 0.000001d),
                () -> assertEquals(1.0d, evidence.layoutProfileCandidates().get("debug-low-density"), 0.000001d),
                () -> assertEquals(1.0d, evidence.layoutProfileCandidates().get("debug-low-density-copy"), 0.000001d)
        );
    }

    private void assertFinder(
            PatternEvidence evidence,
            FinderRole role,
            int matchedModuleCount,
            int expectedModuleCount
    ) {
        PatternFeatureEvidence feature = feature(evidence, role);
        assertAll(
                () -> assertEquals(matchedModuleCount, feature.matchedModuleCount()),
                () -> assertEquals(expectedModuleCount, feature.expectedModuleCount()),
                () -> assertEquals((double) matchedModuleCount / expectedModuleCount,
                        feature.confidence(),
                        0.000001d)
        );
    }

    private PatternFeatureEvidence feature(PatternEvidence evidence, FinderRole role) {
        return evidence.features()
                .stream()
                .filter(feature -> feature.finderRole().filter(candidate -> candidate == role).isPresent())
                .findFirst()
                .orElseThrow();
    }

    private int[] blankPixels() {
        int[] pixels = new int[FRAME_WIDTH * FRAME_HEIGHT];
        Arrays.fill(pixels, 0xFFFFFFFF);
        return pixels;
    }

    private NormalizedCaptureFrame normalizedFrame(int[] pixels, boolean cameraDerived) {
        return new NormalizedCaptureFrame(
                "pattern-candidate.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                PIXEL_SHA256,
                CAPTURE_LAYOUT.profileId(),
                FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                cameraDerived
                        ? CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d)
                        : CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private void paintSupportedFinders(int[] pixels) {
        for (SupportedTileFinderEvaluator.FinderWindow window : finderEvaluator.finderWindows(DIMENSION)) {
            paintRoleMatches(pixels, window.role(), 9);
        }
    }

    private void paintRoleMatches(int[] pixels, FinderRole role, int matchedModules) {
        SupportedTileFinderEvaluator.FinderWindow window = finderWindow(role);
        int painted = 0;
        for (int row = window.startRow(); row < window.startRow() + window.sizeModules(); row++) {
            for (int col = window.startCol(); col < window.startCol() + window.sizeModules(); col++) {
                int color = painted < matchedModules ? window.expectedColor() : wrongColor(window.expectedColor());
                paintModule(pixels, row, col, color);
                painted++;
            }
        }
    }

    private void paintRoleColor(int[] pixels, FinderRole role, int color) {
        SupportedTileFinderEvaluator.FinderWindow window = finderWindow(role);
        for (int row = window.startRow(); row < window.startRow() + window.sizeModules(); row++) {
            for (int col = window.startCol(); col < window.startCol() + window.sizeModules(); col++) {
                paintModule(pixels, row, col, color);
            }
        }
    }

    private void paintModule(int[] pixels, int moduleRow, int moduleCol, int paletteIndex) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        SamplingGeometry geometry = samplingGeometry();
        int left = placement.xPx()
                + geometry.contentOffsetXPx()
                + ((moduleCol + TILE_PROFILE.quietZoneModules()) * geometry.moduleSizePx());
        int top = placement.yPx()
                + geometry.contentOffsetYPx()
                + ((moduleRow + TILE_PROFILE.quietZoneModules()) * geometry.moduleSizePx());
        int color = paletteSampler.paletteArgb().get(paletteIndex);
        for (int row = top; row < top + geometry.moduleSizePx(); row++) {
            for (int col = left; col < left + geometry.moduleSizePx(); col++) {
                pixels[(row * FRAME_WIDTH) + col] = color;
            }
        }
    }

    private SamplingGeometry samplingGeometry() {
        int border = LAYOUT_PLAN.separatorThicknessPx();
        int innerWidth = LAYOUT_PLAN.tileSlotWidthPx() - (2 * border);
        int innerHeight = LAYOUT_PLAN.tileSlotHeightPx() - (2 * border);
        int logicalSide = DIMENSION + (2 * TILE_PROFILE.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        return new SamplingGeometry(moduleSize, offsetX, offsetY);
    }

    private SupportedTileFinderEvaluator.FinderWindow finderWindow(FinderRole role) {
        return finderEvaluator.finderWindows(DIMENSION)
                .stream()
                .filter(window -> window.role() == role)
                .findFirst()
                .orElseThrow();
    }

    private int wrongColor(int expectedColor) {
        return (expectedColor + 1) % paletteSampler.paletteArgb().size();
    }

    private record SamplingGeometry(int moduleSizePx, int contentOffsetXPx, int contentOffsetYPx) {
    }
}

package com.alx4j.jab4j.reader.capture.media.fixture;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.api.model.CodecProfile;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.Manifest;
import com.alx4j.jab4j.api.model.ParityGroupSizingStrategy;
import com.alx4j.jab4j.api.model.PlaybackProfile;
import com.alx4j.jab4j.api.model.ProtocolVersion;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.SessionProfile;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.api.model.TransferSession;
import com.alx4j.jab4j.api.model.TransportProfile;
import com.alx4j.jab4j.catalog.DeclaredInputRoot;
import com.alx4j.jab4j.catalog.DeterministicPackager;
import com.alx4j.jab4j.catalog.PackagingResult;
import com.alx4j.jab4j.render.frame.FrameRasterRenderer;
import com.alx4j.jab4j.render.frame.RenderedFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;
import com.alx4j.jab4j.transfer.TransportSessionPlan;
import com.alx4j.jab4j.transfer.TransportSessionPlanner;

/**
 * Generates capture media corpus fixtures without committing binary media assets.
 */
final class CaptureMediaCorpusFixtures {

    static final String GENERATED_EXACT_PNG = "CM-MVP3-GENERATED-EXACT-PNG";
    static final String GENERATED_UNCROPPED_INSET = "CM-MVP3-GENERATED-UNCROPPED-INSET";
    static final String GENERATED_CAMERA_LIKE_MONITOR_PNG = "CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-PNG";
    static final String GENERATED_CAMERA_LIKE_MONITOR_JPEG = "CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-JPEG";
    static final String GENERATED_CAMERA_LIKE_MONITOR_INVALID_PAYLOAD_PNG =
            "CM-MVP7-GENERATED-CAMERA-LIKE-MONITOR-INVALID-PAYLOAD-PNG";
    static final String GENERATED_SKEWED_BLURRED_MONITOR_PNG = "CM-MVP7-GENERATED-SKEWED-BLURRED-MONITOR-PNG";
    static final String DUPLICATE_FRAMES = "CM-MVP3-DUPLICATE-FRAMES";
    static final String MISSING_UNIQUE_FRAMES = "CM-MVP3-MISSING-UNIQUE-FRAMES";
    static final String NO_JAB_FRAME = "CM-MVP3-NO-JAB-FRAME";
    static final String BRIGHT_MONITOR_WITHOUT_JAB = "CM-MVP4-GENERATED-BRIGHT-MONITOR-WITHOUT-JAB";
    static final String UI_CHROME_WITHOUT_JAB = "CM-MVP4-GENERATED-UI-CHROME-WITHOUT-JAB";
    static final String REPEATED_STRIPES_WITHOUT_JAB = "CM-MVP4-GENERATED-REPEATED-STRIPES-WITHOUT-JAB";
    static final String REPEATED_GRID_WITHOUT_JAB = "CM-MVP9-GENERATED-REPEATED-GRID-WITHOUT-JAB";
    static final String SYNC_LIKE_STRIPES_WITHOUT_JAB = "CM-MVP9-GENERATED-SYNC-LIKE-STRIPES-WITHOUT-JAB";
    static final String PARTIAL_CROPPED_FRAME = "CM-MVP4-GENERATED-PARTIAL-CROPPED-FRAME";
    static final String AMBIGUOUS_MULTI_SYMBOL_PNG = "CM-MVP9-GENERATED-AMBIGUOUS-MULTI-SYMBOL-PNG";
    static final String GENERATED_LOCAL_DISTORTION_PNG = "CM-MVP9-GENERATED-LOCAL-DISTORTION-PNG";
    static final String UNSUPPORTED_HEIC_PLACEHOLDER = "CM-MVP3-UNSUPPORTED-HEIC-PLACEHOLDER";
    static final String CORRUPTED_UNREADABLE_IMAGE = "CM-MVP3-CORRUPTED-UNREADABLE-IMAGE";
    static final String EXTERNAL_IPHONE_STILLS = "CM-MVP3-EXTERNAL-IPHONE-STILLS";
    static final String EXTRACTED_VIDEO_FRAMES = "CM-MVP3-EXTRACTED-VIDEO-FRAMES";
    static final String EXTRACTED_VIDEO_QUALITY_MIX = "CM-MVP3-EXTRACTED-VIDEO-QUALITY-MIX";
    static final String FUTURE_DIRECT_VIDEO = "CM-MVP3-FUTURE-DIRECT-VIDEO";

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"));
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-12T10:00:00Z");
    private static final ProtocolVersion PROTOCOL_VERSION = new ProtocolVersion("1.0", 1);
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
    private static final String ROOT_ALIAS = "payload";

    private static final DeterministicPackager PACKAGER = new DeterministicPackager();
    private static final TransportSessionPlanner SESSION_PLANNER = new TransportSessionPlanner();
    private static final FixedLayoutPlanner LAYOUT_PLANNER = new FixedLayoutPlanner();
    private static final TilePayloadEnvelopeCodec ENVELOPE_CODEC = new TilePayloadEnvelopeCodec();
    private static final TileRasterRenderer TILE_RENDERER = new TileRasterRenderer();
    private static final FrameRasterRenderer FRAME_RENDERER = new FrameRasterRenderer();

    private CaptureMediaCorpusFixtures() {
    }

    /**
     * Generates writer-rendered exact PNG frames for the media corpus baseline.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture generatedExactPng(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, GENERATED_EXACT_PNG);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = writeRenderedFrames(mediaDirectory, frameSet.renderedFrames(), "exact-frame");
        return new GeneratedCaptureMediaFixture(
                GENERATED_EXACT_PNG,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates uncropped still images where each exact PNG frame is inset into a larger photo-like canvas.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture generatedUncroppedInset(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, GENERATED_UNCROPPED_INSET);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = new ArrayList<>(frameSet.renderedFrames().size());
        for (int index = 0; index < frameSet.renderedFrames().size(); index++) {
            Path path = mediaDirectory.resolve("uncropped-still-%04d.png".formatted(index));
            writePng(path, insetIntoPhotoCanvas(frameSet.renderedFrames().get(index)));
            files.add(path);
        }
        return new GeneratedCaptureMediaFixture(
                GENERATED_UNCROPPED_INSET,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates shifted camera-like PNG monitor photos that require tolerant JAB frame region detection.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture generatedCameraLikeMonitorPng(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, GENERATED_CAMERA_LIKE_MONITOR_PNG);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = new ArrayList<>(frameSet.renderedFrames().size());
        for (int index = 0; index < frameSet.renderedFrames().size(); index++) {
            Path path = mediaDirectory.resolve("camera-like-monitor-%04d.png".formatted(index));
            writePng(path, cameraLikeMonitorImage(frameSet.renderedFrames().get(index)));
            files.add(path);
        }
        return new GeneratedCaptureMediaFixture(
                GENERATED_CAMERA_LIKE_MONITOR_PNG,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates shifted camera-like JPEG monitor photos for normalization-only detector validation.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture generatedCameraLikeMonitorJpeg(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, GENERATED_CAMERA_LIKE_MONITOR_JPEG);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = new ArrayList<>(frameSet.renderedFrames().size());
        for (int index = 0; index < frameSet.renderedFrames().size(); index++) {
            Path path = mediaDirectory.resolve("camera-like-monitor-%04d.jpeg".formatted(index));
            writeJpeg(path, cameraLikeMonitorImage(frameSet.renderedFrames().get(index)));
            files.add(path);
        }
        return new GeneratedCaptureMediaFixture(
                GENERATED_CAMERA_LIKE_MONITOR_JPEG,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates a camera-like monitor PNG with frame geometry evidence but intentionally invalid tile payload interiors.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture generatedCameraLikeMonitorInvalidPayloadPng(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, GENERATED_CAMERA_LIKE_MONITOR_INVALID_PAYLOAD_PNG);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        BufferedImage frameImage = toImage(frameSet.renderedFrames().get(0));
        clearTilePayloadInteriors(frameImage);
        Path path = mediaDirectory.resolve("camera-like-monitor-invalid-payload.png");
        writePng(path, cameraLikeMonitorImage(colorShiftedImage(frameImage, 18)));
        return new GeneratedCaptureMediaFixture(
                GENERATED_CAMERA_LIKE_MONITOR_INVALID_PAYLOAD_PNG,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                List.of(path),
                0
        );
    }

    /**
     * Generates a mildly skewed and blurred monitor-like PNG still from the first rendered frame.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture generatedSkewedBlurredMonitorPng(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, GENERATED_SKEWED_BLURRED_MONITOR_PNG);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        Path path = mediaDirectory.resolve("skewed-blurred-monitor.png");
        writePng(path, skewedBlurredMonitorImage(frameSet.renderedFrames().get(0)));
        return new GeneratedCaptureMediaFixture(
                GENERATED_SKEWED_BLURRED_MONITOR_PNG,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                List.of(path),
                1
        );
    }

    /**
     * Generates an extracted-frame folder with one byte-equivalent duplicate frame.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture duplicateFrames(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, DUPLICATE_FRAMES);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = new ArrayList<>(writeRenderedFrames(mediaDirectory, frameSet.renderedFrames(), "extracted-frame"));
        Path duplicate = mediaDirectory.resolve("extracted-frame-0000-duplicate.png");
        Files.copy(files.get(0), duplicate);
        files.add(duplicate);
        return new GeneratedCaptureMediaFixture(
                DUPLICATE_FRAMES,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates an extracted-frame folder with the last unique frame omitted.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture missingUniqueFrames(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, MISSING_UNIQUE_FRAMES);
        if (frameSet.renderedFrames().size() < 2) {
            throw new IllegalStateException("missing-frame fixture requires at least two rendered frames");
        }
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = writeRenderedFrames(
                mediaDirectory,
                frameSet.renderedFrames().subList(0, frameSet.renderedFrames().size() - 1),
                "extracted-frame"
        );
        return new GeneratedCaptureMediaFixture(
                MISSING_UNIQUE_FRAMES,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates a valid PNG that intentionally contains no JAB frame.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture noJabFrame(Path workspace) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, NO_JAB_FRAME);
        Path mediaDirectory = Files.createDirectories(scenarioDirectory.resolve("media"));
        Path image = mediaDirectory.resolve("no-jab-frame.png");
        writePng(image, nonJabImage());
        return new GeneratedCaptureMediaFixture(NO_JAB_FRAME, scenarioDirectory, mediaDirectory, List.of(image), 0);
    }

    /**
     * Generates a bright monitor-like rectangle with no JAB border, sync band, or tile-grid evidence.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture brightMonitorWithoutJab(Path workspace) throws IOException {
        return generatedSinglePng(
                workspace,
                BRIGHT_MONITOR_WITHOUT_JAB,
                "bright-monitor-without-jab.png",
                brightMonitorWithoutJabImage()
        );
    }

    /**
     * Generates a monitor-like application UI with chrome and panels but no JAB tile-grid evidence.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture uiChromeWithoutJab(Path workspace) throws IOException {
        return generatedSinglePng(
                workspace,
                UI_CHROME_WITHOUT_JAB,
                "ui-chrome-without-jab.png",
                uiChromeWithoutJabImage()
        );
    }

    /**
     * Generates repeated medium-contrast stripes that resemble sync-band evidence but not a valid JAB frame.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture repeatedStripesWithoutJab(Path workspace) throws IOException {
        return generatedSinglePng(
                workspace,
                REPEATED_STRIPES_WITHOUT_JAB,
                "repeated-stripes-without-jab.png",
                repeatedStripesWithoutJabImage()
        );
    }

    /**
     * Generates a repeated colorful grid that should remain a false-positive negative for MVP-9 evidence gates.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture repeatedGridWithoutJab(Path workspace) throws IOException {
        return generatedSinglePng(
                workspace,
                REPEATED_GRID_WITHOUT_JAB,
                "repeated-grid-without-jab.png",
                repeatedGridWithoutJabImage()
        );
    }

    /**
     * Generates sync-band-like stripes without the full rendered frame and tile-grid evidence.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture syncLikeStripesWithoutJab(Path workspace) throws IOException {
        return generatedSinglePng(
                workspace,
                SYNC_LIKE_STRIPES_WITHOUT_JAB,
                "sync-like-stripes-without-jab.png",
                syncLikeStripesWithoutJabImage()
        );
    }

    /**
     * Generates a media image with clipped JAB-like evidence that must not be accepted as a complete frame.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture partialCroppedFrame(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, PARTIAL_CROPPED_FRAME);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        Path image = mediaDirectory.resolve("partial-cropped-frame.png");
        writePng(image, partialCroppedFrameImage(frameSet.renderedFrames().get(0)));
        return new GeneratedCaptureMediaFixture(
                PARTIAL_CROPPED_FRAME,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                List.of(image),
                0
        );
    }

    /**
     * Generates a two-symbol image that current normalization treats as an ambiguous exact rendered-frame input.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture ambiguousMultiSymbolPng(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, AMBIGUOUS_MULTI_SYMBOL_PNG);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        Path image = mediaDirectory.resolve("ambiguous-multi-symbol.png");
        writePng(image, ambiguousMultiSymbolImage(frameSet.renderedFrames().get(0)));
        return new GeneratedCaptureMediaFixture(
                AMBIGUOUS_MULTI_SYMBOL_PNG,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                List.of(image),
                0
        );
    }

    /**
     * Generates a non-uniformly distorted frame for local residual diagnostics before applied refinement is enabled.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture generatedLocalDistortionPng(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, GENERATED_LOCAL_DISTORTION_PNG);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        Path image = mediaDirectory.resolve("local-distortion.png");
        writePng(image, locallyDistortedFrameImage(frameSet.renderedFrames().get(0)));
        return new GeneratedCaptureMediaFixture(
                GENERATED_LOCAL_DISTORTION_PNG,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                List.of(image),
                1
        );
    }

    /**
     * Generates a tiny `.heic` placeholder for unsupported-format diagnostics.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture unsupportedHeicPlaceholder(Path workspace) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, UNSUPPORTED_HEIC_PLACEHOLDER);
        Path mediaDirectory = Files.createDirectories(scenarioDirectory.resolve("media"));
        Path image = mediaDirectory.resolve("iphone-still-placeholder.heic");
        Files.writeString(image, "unsupported HEIC placeholder for MVP-3 corpus\n", StandardCharsets.UTF_8);
        return new GeneratedCaptureMediaFixture(
                UNSUPPORTED_HEIC_PLACEHOLDER,
                scenarioDirectory,
                mediaDirectory,
                List.of(image),
                0
        );
    }

    /**
     * Generates a file named as PNG whose content is not decodable image data.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture corruptedUnreadableImage(Path workspace) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, CORRUPTED_UNREADABLE_IMAGE);
        Path mediaDirectory = Files.createDirectories(scenarioDirectory.resolve("media"));
        Path image = mediaDirectory.resolve("corrupted-unreadable.png");
        Files.writeString(image, "this is not a valid PNG image\n", StandardCharsets.UTF_8);
        return new GeneratedCaptureMediaFixture(
                CORRUPTED_UNREADABLE_IMAGE,
                scenarioDirectory,
                mediaDirectory,
                List.of(image),
                0
        );
    }

    /**
     * Generates PNG files named like frames extracted from video by an external tool.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture extractedVideoFrames(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, EXTRACTED_VIDEO_FRAMES);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = writeRenderedFrames(mediaDirectory, frameSet.renderedFrames(), "extracted-video-frame");
        return new GeneratedCaptureMediaFixture(
                EXTRACTED_VIDEO_FRAMES,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates extracted video-frame PNGs with enough clean unique frames plus recoverable and rejected quality frames.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture extractedVideoFramesWithQualityMix(Path workspace) throws IOException {
        GeneratedFrameSet frameSet = generateFrameSet(workspace, EXTRACTED_VIDEO_QUALITY_MIX);
        Path mediaDirectory = Files.createDirectories(frameSet.scenarioDirectory().resolve("media"));
        List<Path> files = new ArrayList<>(writeRenderedFrames(
                mediaDirectory,
                frameSet.renderedFrames(),
                "extracted-video-frame"
        ));

        Path shifted = mediaDirectory.resolve("extracted-video-frame-9000-compression-shift.png");
        writePng(shifted, colorShiftedImage(frameSet.renderedFrames().get(0), 18));
        files.add(shifted);

        Path overexposed = mediaDirectory.resolve("extracted-video-frame-9001-overexposed.png");
        writePng(overexposed, overexposedImage(frameSet.renderedFrames().get(0)));
        files.add(overexposed);

        Path blurred = mediaDirectory.resolve("extracted-video-frame-9002-blurred.png");
        writePng(blurred, blurredImage(frameSet.renderedFrames().get(0)));
        files.add(blurred);

        return new GeneratedCaptureMediaFixture(
                EXTRACTED_VIDEO_QUALITY_MIX,
                frameSet.scenarioDirectory(),
                mediaDirectory,
                files,
                frameSet.renderedFrames().size()
        );
    }

    /**
     * Generates tiny `.mov` and `.mp4` placeholders for future direct-video diagnostics.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated media fixture
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureMediaFixture futureDirectVideoPlaceholders(Path workspace) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, FUTURE_DIRECT_VIDEO);
        Path mediaDirectory = Files.createDirectories(scenarioDirectory.resolve("media"));
        Path mov = mediaDirectory.resolve("direct-video-placeholder.mov");
        Path mp4 = mediaDirectory.resolve("direct-video-placeholder.mp4");
        Files.writeString(mov, "future direct MOV placeholder; no decoder fixture\n", StandardCharsets.UTF_8);
        Files.writeString(mp4, "future direct MP4 placeholder; no decoder fixture\n", StandardCharsets.UTF_8);
        return new GeneratedCaptureMediaFixture(FUTURE_DIRECT_VIDEO, scenarioDirectory, mediaDirectory, List.of(mov, mp4), 0);
    }

    /**
     * Describes external iPhone stills that are intentionally not committed.
     *
     * @return metadata-only external scenario
     */
    static PlannedCaptureMediaScenario externalIphoneStillsScenario() {
        return new PlannedCaptureMediaScenario(EXTERNAL_IPHONE_STILLS, "real_iphone_still_sequence", "external_private");
    }

    /**
     * Returns all scenario ids owned by the first corpus/fixture slice.
     *
     * @return stable scenario ids expected in `capture-media-corpus/manifest.tsv`
     */
    static Set<String> plannedScenarioIds() {
        return Set.copyOf(new LinkedHashSet<>(List.of(
                GENERATED_EXACT_PNG,
                GENERATED_UNCROPPED_INSET,
                GENERATED_CAMERA_LIKE_MONITOR_PNG,
                GENERATED_CAMERA_LIKE_MONITOR_JPEG,
                GENERATED_CAMERA_LIKE_MONITOR_INVALID_PAYLOAD_PNG,
                GENERATED_SKEWED_BLURRED_MONITOR_PNG,
                DUPLICATE_FRAMES,
                MISSING_UNIQUE_FRAMES,
                NO_JAB_FRAME,
                BRIGHT_MONITOR_WITHOUT_JAB,
                UI_CHROME_WITHOUT_JAB,
                REPEATED_STRIPES_WITHOUT_JAB,
                REPEATED_GRID_WITHOUT_JAB,
                SYNC_LIKE_STRIPES_WITHOUT_JAB,
                PARTIAL_CROPPED_FRAME,
                AMBIGUOUS_MULTI_SYMBOL_PNG,
                GENERATED_LOCAL_DISTORTION_PNG,
                UNSUPPORTED_HEIC_PLACEHOLDER,
                CORRUPTED_UNREADABLE_IMAGE,
                EXTERNAL_IPHONE_STILLS,
                EXTRACTED_VIDEO_FRAMES,
                EXTRACTED_VIDEO_QUALITY_MIX,
                FUTURE_DIRECT_VIDEO
        )));
    }

    private static GeneratedFrameSet generateFrameSet(Path workspace, String scenarioId) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, scenarioId);
        Path sourceRoot = writeSourceRoot(scenarioDirectory);
        PackagingResult packagingResult = PACKAGER.buildPackagingResult(List.of(new DeclaredInputRoot(sourceRoot, ROOT_ALIAS)));
        TransportSessionPlan plan = SESSION_PLANNER.plan(draftSession(packagingResult.manifest()), packagingResult);
        List<RenderedFrame> renderedFrames = renderFrames(plan);
        return new GeneratedFrameSet(scenarioDirectory, renderedFrames);
    }

    private static TransferSession draftSession(Manifest manifest) {
        return new TransferSession(
                FIXED_SESSION_ID,
                FIXED_CREATED_AT,
                PROTOCOL_VERSION,
                "jab4j-reader-capture-media-fixture",
                new SessionProfile(
                        "capture-media-fixture-debug",
                        CAPTURE_LAYOUT,
                        new CodecProfile("balanced-v1", "binary", true),
                        new TransportProfile(
                                "capture-media-fixture-v1",
                                PROTOCOL_VERSION,
                                16,
                                2,
                                1,
                                ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                                2,
                                3,
                                2
                        ),
                        new PlaybackProfile("capture-media-fixture", 8, 0, 1, 1, false)
                ),
                manifest,
                manifest.files()
        );
    }

    private static List<RenderedFrame> renderFrames(TransportSessionPlan plan) {
        return plan.frameDescriptors().stream()
                .map(CaptureMediaCorpusFixtures::renderFrame)
                .toList();
    }

    private static RenderedFrame renderFrame(FrameDescriptor frameDescriptor) {
        FixedLayoutPlan layoutPlan = LAYOUT_PLANNER.plan(frameDescriptor.layout());
        List<RenderedTile> renderedTiles = new ArrayList<>(frameDescriptor.tiles().size());
        for (TilePayload payload : frameDescriptor.tiles()) {
            byte[] envelope = ENVELOPE_CODEC.serialize(payload);
            LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
            renderedTiles.add(TILE_RENDERER.render(logicalTile, layoutPlan));
        }
        return FRAME_RENDERER.render(frameDescriptor, renderedTiles);
    }

    private static List<Path> writeRenderedFrames(
            Path mediaDirectory,
            List<RenderedFrame> renderedFrames,
            String filePrefix
    ) throws IOException {
        List<Path> files = new ArrayList<>(renderedFrames.size());
        for (int index = 0; index < renderedFrames.size(); index++) {
            Path path = mediaDirectory.resolve("%s-%04d.png".formatted(filePrefix, index));
            writePng(path, toImage(renderedFrames.get(index)));
            files.add(path);
        }
        return List.copyOf(files);
    }

    private static BufferedImage insetIntoPhotoCanvas(RenderedFrame frame) {
        BufferedImage frameImage = toImage(frame);
        int insetX = 80;
        int insetY = 60;
        int canvasWidth = frame.widthPixels() + 160;
        int canvasHeight = frame.heightPixels() + 120;
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(new Color(0xFF15191D, true));
            graphics.fillRect(32, 28, canvasWidth - 64, canvasHeight - 56);
            graphics.drawImage(frameImage, insetX, insetY, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static BufferedImage cameraLikeMonitorImage(RenderedFrame frame) {
        return cameraLikeMonitorImage(colorShiftedImage(frame, 18));
    }

    private static BufferedImage cameraLikeMonitorImage(BufferedImage frameImage) {
        int insetX = 210;
        int insetY = 140;
        int canvasWidth = 1700;
        int canvasHeight = 1000;
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(new Color(0xFF15191D, true));
            graphics.fillRect(48, 42, canvasWidth - 96, canvasHeight - 84);
            graphics.setColor(new Color(0xFF242A31, true));
            graphics.fillRect(78, 70, canvasWidth - 156, canvasHeight - 140);
            graphics.setColor(new Color(0xFF3E4650, true));
            graphics.fillRect(78, 70, canvasWidth - 156, 48);
            graphics.drawImage(frameImage, insetX, insetY, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static void clearTilePayloadInteriors(BufferedImage image) {
        FixedLayoutPlan layoutPlan = LAYOUT_PLANNER.plan(CAPTURE_LAYOUT);
        int border = layoutPlan.separatorThicknessPx();
        int preservedCornerPx = Math.max(96, layoutPlan.tileSlotWidthPx() / 4);
        for (TilePlacement placement : layoutPlan.tilePlacements()) {
            int left = placement.xPx() + border;
            int top = placement.yPx() + border;
            int rightExclusive = placement.xPx() + placement.widthPx() - border;
            int bottomExclusive = placement.yPx() + placement.heightPx() - border;
            for (int y = top; y < bottomExclusive; y++) {
                for (int x = left; x < rightExclusive; x++) {
                    if (insidePreservedFinderCorner(
                            x,
                            y,
                            left,
                            top,
                            rightExclusive,
                            bottomExclusive,
                            preservedCornerPx
                    )) {
                        continue;
                    }
                    image.setRGB(x, y, ((x + y) & 0x20) == 0 ? 0xFFFFFFFF : 0xFFE8E8E8);
                }
            }
        }
    }

    private static boolean insidePreservedFinderCorner(
            int x,
            int y,
            int left,
            int top,
            int rightExclusive,
            int bottomExclusive,
            int preservedCornerPx
    ) {
        boolean leftCorner = x - left < preservedCornerPx;
        boolean rightCorner = rightExclusive - x <= preservedCornerPx;
        boolean topCorner = y - top < preservedCornerPx;
        boolean bottomCorner = bottomExclusive - y <= preservedCornerPx;
        return (leftCorner || rightCorner) && (topCorner || bottomCorner);
    }

    private static BufferedImage skewedBlurredMonitorImage(RenderedFrame frame) {
        BufferedImage frameImage = boxBlurredImage(colorShiftedImage(frame, 22));
        BufferedImage canvas = monitorCanvas();
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(frameImage, new AffineTransform(
                    1.0d,
                    0.035d,
                    -0.075d,
                    1.0d,
                    260.0d,
                    150.0d
            ), null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static BufferedImage nonJabImage() {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFF46515C, true));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(0xFFB8C1CC, true));
            graphics.fillRect(80, 96, 480, 288);
            graphics.setColor(new Color(0xFF6D7782, true));
            graphics.fillRect(120, 140, 400, 44);
            graphics.fillRect(120, 216, 300, 36);
            graphics.fillRect(120, 284, 360, 36);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static GeneratedCaptureMediaFixture generatedSinglePng(
            Path workspace,
            String scenarioId,
            String fileName,
            BufferedImage image
    ) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, scenarioId);
        Path mediaDirectory = Files.createDirectories(scenarioDirectory.resolve("media"));
        Path path = mediaDirectory.resolve(fileName);
        writePng(path, image);
        return new GeneratedCaptureMediaFixture(scenarioId, scenarioDirectory, mediaDirectory, List.of(path), 0);
    }

    private static BufferedImage brightMonitorWithoutJabImage() {
        BufferedImage image = monitorCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFFF4F7FA, true));
            graphics.fillRect(290, 220, 1100, 520);
            graphics.setColor(new Color(0xFFDCE3EA, true));
            graphics.fillRect(340, 280, 1000, 72);
            graphics.setColor(new Color(0xFFCAD3DC, true));
            graphics.fillRect(340, 410, 460, 220);
            graphics.fillRect(870, 410, 460, 220);
            graphics.setColor(new Color(0xFFEEF2F5, true));
            graphics.fillRect(390, 460, 360, 42);
            graphics.fillRect(920, 460, 360, 42);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage uiChromeWithoutJabImage() {
        BufferedImage image = monitorCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFFEEF2F5, true));
            graphics.fillRect(210, 140, 1280, 720);
            graphics.setColor(new Color(0xFF26313A, true));
            graphics.fillRect(210, 140, 1280, 56);
            graphics.setColor(new Color(0xFF52606D, true));
            graphics.fillRect(250, 158, 180, 20);
            graphics.fillRect(470, 158, 120, 20);
            graphics.setColor(new Color(0xFFB9C4CE, true));
            for (int row = 0; row < 4; row++) {
                int top = 240 + (row * 128);
                graphics.fillRect(270, top, 1040, 54);
                graphics.fillRect(270, top + 76, 760, 22);
            }
            graphics.setColor(new Color(0xFFE4E9EE, true));
            graphics.fillRect(1330, 240, 96, 560);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage repeatedStripesWithoutJabImage() {
        int canvasWidth = 900;
        int canvasHeight = 600;
        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(new Color(0xFF15191D, true));
            graphics.fillRect(40, 36, canvasWidth - 80, canvasHeight - 72);
            graphics.setColor(new Color(0xFF11161B, true));
            graphics.fillRect(96, 92, 700, 380);
            for (int col = 0; col < 42; col++) {
                graphics.setColor(new Color(col % 2 == 0 ? 0xFF9EA7B0 : 0xFF69727C, true));
                graphics.fillRect(128 + (col * 14), 136, 9, 280);
            }
            graphics.setColor(new Color(0xFF87909A, true));
            for (int row = 0; row < 5; row++) {
                graphics.fillRect(128, 146 + (row * 52), 590, 6);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage repeatedGridWithoutJabImage() {
        BufferedImage image = monitorCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            int left = 260;
            int top = 180;
            int cellSize = 42;
            int rows = 12;
            int cols = 20;
            int[] colors = {
                    0xFFEF4444,
                    0xFF22C55E,
                    0xFF3B82F6,
                    0xFFEAB308,
                    0xFF14B8A6,
                    0xFF94A3B8
            };
            graphics.setColor(new Color(0xFF101820, true));
            graphics.fillRect(left - 18, top - 18, (cols * cellSize) + 36, (rows * cellSize) + 36);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    graphics.setColor(new Color(colors[(row + (col * 2)) % colors.length], true));
                    graphics.fillRect(left + (col * cellSize), top + (row * cellSize), cellSize - 6, cellSize - 6);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage syncLikeStripesWithoutJabImage() {
        BufferedImage image = monitorCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            int left = 230;
            int top = 220;
            int width = 1200;
            int height = 420;
            graphics.setColor(new Color(0xFF0F172A, true));
            graphics.fillRect(left, top, width, height);
            for (int row = 0; row < 7; row++) {
                int stripeTop = top + 40 + (row * 46);
                for (int col = 0; col < 60; col++) {
                    graphics.setColor(new Color(col % 2 == 0 ? 0xFFE5E7EB : 0xFF111827, true));
                    graphics.fillRect(left + 32 + (col * 18), stripeTop, 12, 18);
                }
            }
            graphics.setColor(new Color(0xFF64748B, true));
            graphics.fillRect(left + 32, top + height - 70, width - 64, 16);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage partialCroppedFrameImage(RenderedFrame frame) {
        BufferedImage frameImage = toImage(frame);
        int canvasWidth = 900;
        int canvasHeight = frame.heightPixels() + 120;
        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.drawImage(frameImage, -900, 60, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage ambiguousMultiSymbolImage(RenderedFrame frame) {
        BufferedImage frameImage = toImage(frame);
        int gap = 80;
        int insetX = 80;
        int insetY = 60;
        int canvasWidth = (2 * frame.widthPixels()) + (2 * insetX) + gap;
        int canvasHeight = frame.heightPixels() + (2 * insetY);
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.drawImage(frameImage, insetX, insetY, null);
            graphics.drawImage(frameImage, insetX + frame.widthPixels() + gap, insetY, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static BufferedImage locallyDistortedFrameImage(RenderedFrame frame) {
        BufferedImage source = toImage(frame);
        BufferedImage distorted = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        int stableTop = CAPTURE_LAYOUT.outerMarginPx()
                + CAPTURE_LAYOUT.topSyncBandPx()
                + CAPTURE_LAYOUT.metadataBandPx();
        for (int y = 0; y < distorted.getHeight(); y++) {
            for (int x = 0; x < distorted.getWidth(); x++) {
                if (y < stableTop) {
                    distorted.setRGB(x, y, source.getRGB(x, y));
                    continue;
                }
                double edgeFalloff = edgeFalloff(x, y, distorted.getWidth(), distorted.getHeight());
                double verticalBand = Math.sin(((double) x / distorted.getWidth()) * Math.PI * 3.0d);
                double horizontalBand = Math.sin(((double) y / distorted.getHeight()) * Math.PI * 2.0d);
                double tileAreaWeight = Math.min(1.0d, Math.max(0.0d, ((double) y - stableTop) / 160.0d));
                int sourceX = clamp((int) Math.round(x - (edgeFalloff * tileAreaWeight * 7.0d * verticalBand)),
                        0,
                        source.getWidth() - 1);
                int sourceY = clamp((int) Math.round(y - (edgeFalloff * tileAreaWeight * 4.0d * horizontalBand)),
                        0,
                        source.getHeight() - 1);
                distorted.setRGB(x, y, source.getRGB(sourceX, sourceY));
            }
        }
        return distorted;
    }

    private static double edgeFalloff(int x, int y, int width, int height) {
        double horizontal = Math.min(x, width - 1 - x) / (double) Math.max(1, width - 1);
        double vertical = Math.min(y, height - 1 - y) / (double) Math.max(1, height - 1);
        return Math.min(1.0d, Math.min(horizontal, vertical) * 10.0d);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static BufferedImage monitorCanvas() {
        int canvasWidth = 1700;
        int canvasHeight = 1000;
        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(new Color(0xFF15191D, true));
            graphics.fillRect(48, 42, canvasWidth - 96, canvasHeight - 84);
            graphics.setColor(new Color(0xFF242A31, true));
            graphics.fillRect(78, 70, canvasWidth - 156, canvasHeight - 140);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage colorShiftedImage(RenderedFrame frame, int colorShift) {
        BufferedImage image = toImage(frame);
        return colorShiftedImage(image, colorShift);
    }

    private static BufferedImage colorShiftedImage(BufferedImage image, int colorShift) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, shiftPaletteColor(image.getRGB(x, y), colorShift));
            }
        }
        return image;
    }

    private static BufferedImage boxBlurredImage(BufferedImage image) {
        float weight = 1.0f / 9.0f;
        float[] weights = {
                weight, weight, weight,
                weight, weight, weight,
                weight, weight, weight
        };
        BufferedImage blurred = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        return new ConvolveOp(new Kernel(3, 3, weights), ConvolveOp.EDGE_NO_OP, null).filter(image, blurred);
    }

    private static int shiftPaletteColor(int argb, int colorShift) {
        int red = shiftedChannel((argb >>> 16) & 0xFF, colorShift);
        int green = shiftedChannel((argb >>> 8) & 0xFF, colorShift);
        int blue = shiftedChannel(argb & 0xFF, colorShift);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int shiftedChannel(int value, int colorShift) {
        return value < 128
                ? Math.min(255, value + colorShift)
                : Math.max(0, value - colorShift);
    }

    private static BufferedImage overexposedImage(RenderedFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage blurredImage(RenderedFrame frame) {
        BufferedImage image = toImage(frame);
        FixedLayoutPlan layoutPlan = LAYOUT_PLANNER.plan(CAPTURE_LAYOUT);
        int cellWidth = Math.max(8, layoutPlan.separatorThicknessPx() * 2);
        int left = CAPTURE_LAYOUT.outerMarginPx();
        int top = CAPTURE_LAYOUT.outerMarginPx();
        int rightExclusive = CAPTURE_LAYOUT.frameWidthPx() - CAPTURE_LAYOUT.outerMarginPx();
        int bottomExclusive = top + CAPTURE_LAYOUT.topSyncBandPx();
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = left; col < rightExclusive; col++) {
                int segmentIndex = (col - left) / cellWidth;
                image.setRGB(col, row, segmentIndex % 2 == 0 ? 0xFF969696 : 0xFF5A5A5A);
            }
        }
        return image;
    }

    private static BufferedImage toImage(RenderedFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        int pixelIndex = 0;
        for (int y = 0; y < frame.heightPixels(); y++) {
            for (int x = 0; x < frame.widthPixels(); x++) {
                image.setRGB(x, y, frame.argbPixels().get(pixelIndex));
                pixelIndex++;
            }
        }
        return image;
    }

    private static void writePng(Path path, BufferedImage image) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG ImageIO writer is available");
        }
    }

    private static void writeJpeg(Path path, BufferedImage image) throws IOException {
        if (!ImageIO.write(toRgbImage(image), "jpeg", path.toFile())) {
            throw new IOException("No JPEG ImageIO writer is available");
        }
    }

    private static BufferedImage toRgbImage(BufferedImage image) {
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    private static Path writeSourceRoot(Path scenarioDirectory) throws IOException {
        Path sourceRoot = Files.createDirectories(scenarioDirectory.resolve("source"));
        Files.createDirectories(sourceRoot.resolve("docs"));
        Files.writeString(sourceRoot.resolve("docs").resolve("message.txt"), "capture media fixture\n", StandardCharsets.UTF_8);
        Files.write(sourceRoot.resolve("empty.bin"), new byte[0]);
        return sourceRoot;
    }

    private static Path createScenarioDirectory(Path workspace, String scenarioId) throws IOException {
        Files.createDirectories(workspace);
        return Files.createTempDirectory(workspace, scenarioId + "-");
    }

    private record GeneratedFrameSet(
            Path scenarioDirectory,
            List<RenderedFrame> renderedFrames
    ) {

        private GeneratedFrameSet {
            Objects.requireNonNull(scenarioDirectory, "scenarioDirectory must not be null");
            renderedFrames = List.copyOf(Objects.requireNonNull(renderedFrames, "renderedFrames must not be null"));
            if (renderedFrames.isEmpty()) {
                throw new IllegalStateException("media corpus frame set must contain at least one rendered frame");
            }
        }
    }
}

/**
 * Generated capture media fixture metadata.
 *
 * @param scenarioId stable corpus scenario id
 * @param scenarioDirectory generated scenario root
 * @param mediaDirectory generated media input directory
 * @param mediaFiles generated media files in deterministic order
 * @param expectedUniqueFrameCount expected number of unique writer-backed frames, or zero for diagnostics-only fixtures
 */
record GeneratedCaptureMediaFixture(
        String scenarioId,
        Path scenarioDirectory,
        Path mediaDirectory,
        List<Path> mediaFiles,
        int expectedUniqueFrameCount
) {

    /**
     * Creates immutable generated media fixture metadata.
     *
     * @param scenarioId stable corpus scenario id
     * @param scenarioDirectory generated scenario root
     * @param mediaDirectory generated media input directory
     * @param mediaFiles generated media files
     * @param expectedUniqueFrameCount expected unique writer-backed frame count
     */
    GeneratedCaptureMediaFixture {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(scenarioDirectory, "scenarioDirectory must not be null");
        Objects.requireNonNull(mediaDirectory, "mediaDirectory must not be null");
        mediaFiles = List.copyOf(Objects.requireNonNull(mediaFiles, "mediaFiles must not be null"));
        if (mediaFiles.isEmpty()) {
            throw new IllegalArgumentException("mediaFiles must not be empty");
        }
        if (mediaFiles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("mediaFiles must not contain null values");
        }
        if (expectedUniqueFrameCount < 0) {
            throw new IllegalArgumentException("expectedUniqueFrameCount must be non-negative");
        }
    }
}

/**
 * Metadata-only corpus scenario for external or future samples that are not committed.
 *
 * @param scenarioId stable corpus scenario id
 * @param sourceKind logical media source category
 * @param assetAvailability asset availability policy
 */
record PlannedCaptureMediaScenario(
        String scenarioId,
        String sourceKind,
        String assetAvailability
) {

    /**
     * Creates validated metadata for a planned media scenario.
     *
     * @param scenarioId stable corpus scenario id
     * @param sourceKind logical media source category
     * @param assetAvailability asset availability policy
     */
    PlannedCaptureMediaScenario {
        requireText(scenarioId, "scenarioId");
        requireText(sourceKind, "sourceKind");
        requireText(assetAvailability, "assetAvailability");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}

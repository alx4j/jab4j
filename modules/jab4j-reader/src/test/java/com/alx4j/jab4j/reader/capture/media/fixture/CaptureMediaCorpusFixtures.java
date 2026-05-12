package com.alx4j.jab4j.reader.capture.media.fixture;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
import com.alx4j.jab4j.api.model.FrameType;
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
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;
import com.alx4j.jab4j.transfer.TransportSessionPlan;
import com.alx4j.jab4j.transfer.TransportSessionPlanner;

/**
 * Generates MVP-3 capture media corpus fixtures without committing binary media assets.
 */
final class CaptureMediaCorpusFixtures {

    static final String GENERATED_EXACT_PNG = "CM-MVP3-GENERATED-EXACT-PNG";
    static final String GENERATED_UNCROPPED_INSET = "CM-MVP3-GENERATED-UNCROPPED-INSET";
    static final String DUPLICATE_FRAMES = "CM-MVP3-DUPLICATE-FRAMES";
    static final String MISSING_UNIQUE_FRAMES = "CM-MVP3-MISSING-UNIQUE-FRAMES";
    static final String NO_JAB_FRAME = "CM-MVP3-NO-JAB-FRAME";
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
                DUPLICATE_FRAMES,
                MISSING_UNIQUE_FRAMES,
                NO_JAB_FRAME,
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

    private static BufferedImage colorShiftedImage(RenderedFrame frame, int colorShift) {
        BufferedImage image = toImage(frame);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, shiftPaletteColor(image.getRGB(x, y), colorShift));
            }
        }
        return image;
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

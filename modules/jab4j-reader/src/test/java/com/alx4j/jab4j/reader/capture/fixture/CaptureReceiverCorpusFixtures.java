package com.alx4j.jab4j.reader.capture.fixture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
import com.alx4j.jab4j.output.ExportArtifacts;
import com.alx4j.jab4j.output.ExportMode;
import com.alx4j.jab4j.output.PreparedFrameExporter;
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
 * Generates small capture receiver test folders from the same core planning, rendering, and export utilities used by
 * writer-style image sequences.
 */
final class CaptureReceiverCorpusFixtures {

    static final String EXACT_PNG_BASELINE = "CR-MVP2-EXACT-PNG-BASELINE";
    static final String CLEAN_EXTRACTED_PNG = "CR-MVP2-CLEAN-EXTRACTED-PNG";
    static final String MISSING_FRAME_CONTENT = "CR-MVP2-MISSING-FRAME-CONTENT";
    static final String DUPLICATE_EQUIVALENT_FRAME = "CR-MVP2-DUPLICATE-EQUIVALENT-FRAME";
    static final String UNRELATED_IMAGE = "CR-MVP2-UNRELATED-IMAGE";
    static final String UNREADABLE_IMAGE = "CR-MVP2-UNREADABLE-IMAGE";
    static final String UNSUPPORTED_DIMENSIONS = "CR-MVP2-UNSUPPORTED-DIMENSIONS";
    static final String CORRUPTED_TILE_CONTENT = "CR-MVP2-CORRUPTED-TILE-CONTENT";

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-03-22T18:00:00Z");
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
    private static final PreparedFrameExporter EXPORTER = new PreparedFrameExporter();

    private CaptureReceiverCorpusFixtures() {
    }

    /**
     * Generates a complete capture-derived PNG folder without writer sequence metadata.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated capture fixture and its source writer artifacts
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureFixture cleanExtractedPng(Path workspace) throws IOException {
        return generatedWriterBackedFixture(workspace, CLEAN_EXTRACTED_PNG, CopyMode.ALL, false);
    }

    /**
     * Generates a capture-derived PNG folder with one writer-rendered frame omitted.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated capture fixture and its source writer artifacts
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureFixture missingFrameContent(Path workspace) throws IOException {
        return generatedWriterBackedFixture(workspace, MISSING_FRAME_CONTENT, CopyMode.MISSING_LAST, false);
    }

    /**
     * Generates a capture-derived PNG folder with one byte-equivalent duplicate frame.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated capture fixture and its source writer artifacts
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureFixture duplicateEquivalentFrame(Path workspace) throws IOException {
        return generatedWriterBackedFixture(workspace, DUPLICATE_EQUIVALENT_FRAME, CopyMode.DUPLICATE_FIRST, false);
    }

    /**
     * Generates a capture-derived PNG folder whose frame pixels contain a corrupted tile envelope.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated capture fixture and its source writer artifacts
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedCaptureFixture corruptedTileContent(Path workspace) throws IOException {
        return generatedWriterBackedFixture(workspace, CORRUPTED_TILE_CONTENT, CopyMode.ALL, true);
    }

    /**
     * Generates a supported-size PNG that is not a JAB frame.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated image folder
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedImageFolder unrelatedImage(Path workspace) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, UNRELATED_IMAGE);
        Path captureDirectory = Files.createDirectories(scenarioDirectory.resolve("capture-frames"));
        Path image = writeSolidPng(
                captureDirectory.resolve("unrelated-0000.png"),
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                0xFF6E7781
        );
        return new GeneratedImageFolder(UNRELATED_IMAGE, scenarioDirectory, captureDirectory, List.of(image));
    }

    /**
     * Generates a file named as a PNG that image decoders cannot read.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated image folder
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedImageFolder unreadableImage(Path workspace) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, UNREADABLE_IMAGE);
        Path captureDirectory = Files.createDirectories(scenarioDirectory.resolve("capture-frames"));
        Path image = captureDirectory.resolve("unreadable-0000.png");
        Files.writeString(image, "this is not a png image", StandardCharsets.UTF_8);
        return new GeneratedImageFolder(UNREADABLE_IMAGE, scenarioDirectory, captureDirectory, List.of(image));
    }

    /**
     * Generates a valid PNG whose dimensions do not match any supported rendered layout.
     *
     * @param workspace parent directory for temporary scenario files
     * @return generated image folder
     * @throws IOException if fixture files cannot be written
     */
    static GeneratedImageFolder unsupportedDimensions(Path workspace) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, UNSUPPORTED_DIMENSIONS);
        Path captureDirectory = Files.createDirectories(scenarioDirectory.resolve("capture-frames"));
        Path image = writeSolidPng(captureDirectory.resolve("unsupported-dimensions-0000.png"), 321, 241, 0xFF2F343A);
        return new GeneratedImageFolder(UNSUPPORTED_DIMENSIONS, scenarioDirectory, captureDirectory, List.of(image));
    }

    private static GeneratedCaptureFixture generatedWriterBackedFixture(
            Path workspace,
            String scenarioId,
            CopyMode copyMode,
            boolean corruptTileContent
    ) throws IOException {
        Path scenarioDirectory = createScenarioDirectory(workspace, scenarioId);
        Path sourceRoot = writeSourceRoot(scenarioDirectory);
        PackagingResult packagingResult = PACKAGER.buildPackagingResult(List.of(new DeclaredInputRoot(sourceRoot, ROOT_ALIAS)));
        TransportSessionPlan plan = SESSION_PLANNER.plan(draftSession(packagingResult.manifest()), packagingResult);
        Long corruptedFrameIndex = corruptTileContent ? firstDataFrameIndex(plan) : null;
        List<RenderedFrame> renderedFrames = renderFrames(plan, corruptedFrameIndex);
        ExportArtifacts exportArtifacts = EXPORTER.export(
                ExportMode.IMAGE_SEQUENCE,
                FIXED_SESSION_ID,
                plan.finalSessionDigest(),
                renderedFrames,
                scenarioDirectory.resolve("writer-export")
        );

        Path captureFramesDirectory = Files.createDirectories(scenarioDirectory.resolve("capture-frames"));
        List<Path> captureFrameFiles = copyCaptureFrameFiles(exportArtifacts, captureFramesDirectory, copyMode);
        return new GeneratedCaptureFixture(
                scenarioId,
                scenarioDirectory,
                sourceRoot,
                exportArtifacts.exportDirectory(),
                captureFramesDirectory,
                captureFrameFiles,
                plan,
                renderedFrames
        );
    }

    private static TransferSession draftSession(Manifest manifest) {
        return new TransferSession(
                FIXED_SESSION_ID,
                FIXED_CREATED_AT,
                PROTOCOL_VERSION,
                "jab4j-reader-capture-fixture",
                new SessionProfile(
                        "capture-fixture-debug",
                        CAPTURE_LAYOUT,
                        new CodecProfile("balanced-v1", "binary", true),
                        new TransportProfile(
                                "capture-fixture-v1",
                                PROTOCOL_VERSION,
                                16,
                                2,
                                1,
                                ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                                2,
                                3,
                                2
                        ),
                        new PlaybackProfile("capture-fixture", 8, 0, 1, 1, false)
                ),
                manifest,
                manifest.files()
        );
    }

    private static List<RenderedFrame> renderFrames(TransportSessionPlan plan, Long corruptedFrameIndex) {
        return plan.frameDescriptors().stream()
                .map(frame -> renderFrame(frame, Objects.equals(frame.frameIndex(), corruptedFrameIndex)))
                .toList();
    }

    private static RenderedFrame renderFrame(FrameDescriptor frameDescriptor, boolean corruptFirstTileEnvelope) {
        FixedLayoutPlan layoutPlan = LAYOUT_PLANNER.plan(frameDescriptor.layout());
        List<RenderedTile> renderedTiles = new ArrayList<>(frameDescriptor.tiles().size());
        for (int index = 0; index < frameDescriptor.tiles().size(); index++) {
            TilePayload payload = frameDescriptor.tiles().get(index);
            byte[] envelope = ENVELOPE_CODEC.serialize(payload);
            if (corruptFirstTileEnvelope && index == 0) {
                envelope = corruptEnvelope(envelope);
            }
            LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
            renderedTiles.add(TILE_RENDERER.render(logicalTile, layoutPlan));
        }
        return FRAME_RENDERER.render(frameDescriptor, renderedTiles);
    }

    private static byte[] corruptEnvelope(byte[] envelope) {
        byte[] corrupted = envelope.clone();
        corrupted[corrupted.length - 1] = (byte) (corrupted[corrupted.length - 1] ^ 0x01);
        return corrupted;
    }

    private static Long firstDataFrameIndex(TransportSessionPlan plan) {
        return plan.frameDescriptors().stream()
                .filter(frame -> frame.frameType() == FrameType.DATA)
                .findFirst()
                .or(() -> plan.frameDescriptors().stream().findFirst())
                .map(FrameDescriptor::frameIndex)
                .orElseThrow(() -> new IllegalStateException("transport plan did not produce frames"));
    }

    private static List<Path> copyCaptureFrameFiles(
            ExportArtifacts exportArtifacts,
            Path captureFramesDirectory,
            CopyMode copyMode
    ) throws IOException {
        List<Path> imageFiles = exportArtifacts.exportedFiles().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("image-"))
                .map(entry -> entry.getValue())
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        if (imageFiles.isEmpty()) {
            throw new IllegalStateException("writer-backed fixture did not export PNG frames");
        }
        if (copyMode == CopyMode.MISSING_LAST && imageFiles.size() < 2) {
            throw new IllegalStateException("missing-frame fixture requires at least two exported frames");
        }

        int copyCount = copyMode == CopyMode.MISSING_LAST ? imageFiles.size() - 1 : imageFiles.size();
        List<Path> captureFiles = new ArrayList<>(copyCount + 1);
        for (int index = 0; index < copyCount; index++) {
            Path target = captureFramesDirectory.resolve("capture-frame-%04d.png".formatted(index));
            Files.copy(imageFiles.get(index), target);
            captureFiles.add(target);
        }
        if (copyMode == CopyMode.DUPLICATE_FIRST) {
            Path duplicate = captureFramesDirectory.resolve("capture-frame-0000-duplicate.png");
            Files.copy(imageFiles.get(0), duplicate);
            captureFiles.add(duplicate);
        }
        return List.copyOf(captureFiles);
    }

    private static Path writeSourceRoot(Path scenarioDirectory) throws IOException {
        Path sourceRoot = Files.createDirectories(scenarioDirectory.resolve("source"));
        Files.createDirectories(sourceRoot.resolve("docs"));
        Files.writeString(sourceRoot.resolve("docs").resolve("message.txt"), "capture receiver fixture\n", StandardCharsets.UTF_8);
        Files.write(sourceRoot.resolve("empty.bin"), new byte[0]);
        return sourceRoot;
    }

    private static Path createScenarioDirectory(Path workspace, String scenarioId) throws IOException {
        Files.createDirectories(workspace);
        return Files.createTempDirectory(workspace, scenarioId + "-");
    }

    private static Path writeSolidPng(Path path, int width, int height, int argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                image.setRGB(col, row, argb);
            }
        }
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG ImageIO writer is available");
        }
        return path;
    }

    private enum CopyMode {
        ALL,
        MISSING_LAST,
        DUPLICATE_FIRST
    }
}

/**
 * A generated capture frame folder backed by a deterministic writer-style transport plan and export.
 *
 * @param scenarioId stable corpus scenario id
 * @param scenarioDirectory generated scenario root
 * @param sourceRoot generated writer source root
 * @param writerImageSequenceDirectory exact writer export directory, including metadata
 * @param captureFramesDirectory capture-style frame folder without writer metadata
 * @param captureFrameFiles capture frame files in deterministic traversal order
 * @param sessionPlan transport session used to render frames
 * @param renderedFrames rendered frame rasters aligned to the transport plan
 */
record GeneratedCaptureFixture(
        String scenarioId,
        Path scenarioDirectory,
        Path sourceRoot,
        Path writerImageSequenceDirectory,
        Path captureFramesDirectory,
        List<Path> captureFrameFiles,
        TransportSessionPlan sessionPlan,
        List<RenderedFrame> renderedFrames
) {

    /**
     * Creates immutable generated-fixture metadata.
     *
     * @param scenarioId stable corpus scenario id
     * @param scenarioDirectory generated scenario root
     * @param sourceRoot generated writer source root
     * @param writerImageSequenceDirectory exact writer export directory
     * @param captureFramesDirectory capture-style frame folder
     * @param captureFrameFiles capture frame files
     * @param sessionPlan transport session used to render frames
     * @param renderedFrames rendered frame rasters aligned to the transport plan
     */
    GeneratedCaptureFixture {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(scenarioDirectory, "scenarioDirectory must not be null");
        Objects.requireNonNull(sourceRoot, "sourceRoot must not be null");
        Objects.requireNonNull(writerImageSequenceDirectory, "writerImageSequenceDirectory must not be null");
        Objects.requireNonNull(captureFramesDirectory, "captureFramesDirectory must not be null");
        captureFrameFiles = List.copyOf(Objects.requireNonNull(captureFrameFiles, "captureFrameFiles must not be null"));
        Objects.requireNonNull(sessionPlan, "sessionPlan must not be null");
        renderedFrames = List.copyOf(Objects.requireNonNull(renderedFrames, "renderedFrames must not be null"));
    }
}

/**
 * A generated non-writer-backed image folder for negative capture intake tests.
 *
 * @param scenarioId stable corpus scenario id
 * @param scenarioDirectory generated scenario root
 * @param captureFramesDirectory capture-style frame folder
 * @param captureFrameFiles generated image files in deterministic traversal order
 */
record GeneratedImageFolder(
        String scenarioId,
        Path scenarioDirectory,
        Path captureFramesDirectory,
        List<Path> captureFrameFiles
) {

    /**
     * Creates immutable generated image-folder metadata.
     *
     * @param scenarioId stable corpus scenario id
     * @param scenarioDirectory generated scenario root
     * @param captureFramesDirectory capture-style frame folder
     * @param captureFrameFiles generated image files
     */
    GeneratedImageFolder {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(scenarioDirectory, "scenarioDirectory must not be null");
        Objects.requireNonNull(captureFramesDirectory, "captureFramesDirectory must not be null");
        captureFrameFiles = List.copyOf(Objects.requireNonNull(captureFrameFiles, "captureFrameFiles must not be null"));
    }
}

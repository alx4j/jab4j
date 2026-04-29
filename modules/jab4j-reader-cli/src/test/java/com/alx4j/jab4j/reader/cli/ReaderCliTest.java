package com.alx4j.jab4j.reader.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import com.alx4j.jab4j.writer.app.WriterApplicationService;
import com.alx4j.jab4j.writer.app.WriterJobObserver;
import com.alx4j.jab4j.writer.app.WriterRunRequest;
import com.alx4j.jab4j.writer.config.RuntimeConfig;
import com.alx4j.jab4j.writer.config.RuntimeConfigPatch;

@DisplayName("Reader CLI execution")
class ReaderCliTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-03-22T18:00:00Z");
    private static final ProtocolVersion FIXED_PROTOCOL_VERSION = new ProtocolVersion("1.0", 1);
    private static final String FIXED_DIGEST = "digest-123";
    private static final int RESTORE_FIXTURE_CHUNK_BYTES = 256;
    private static final LayoutProfile DEFAULT_PROFILE = new LayoutProfile(
            "desktop-1080p-safe",
            2,
            2,
            1920,
            1080,
            24,
            48,
            "solidWhite",
            64,
            32,
            "black",
            "preserveAspect"
    );

    @TempDir
    Path tempDir;

    private final DeterministicPackager packager = new DeterministicPackager();
    private final TransportSessionPlanner planner = new TransportSessionPlanner();
    private final FixedLayoutPlanner layoutPlanner = new FixedLayoutPlanner();
    private final TilePayloadEnvelopeCodec envelopeCodec = new TilePayloadEnvelopeCodec();
    private final TileRasterRenderer tileRasterRenderer = new TileRasterRenderer();
    private final FrameRasterRenderer frameRasterRenderer = new FrameRasterRenderer();

    @Test
    @DisplayName("Valid imageSequence input restores content and prints a concise summary")
    void validImageSequenceInputRestoresContentAndPrintsConciseSummary() {
        RestoreFixture fixture = writeRestorableImageSequence("valid-export");
        Path output = tempDir.resolve("restore-success");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", fixture.imageSequence().toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(0, exitCode),
                () -> assertTrue(stdoutText.contains("RESTORED ")),
                () -> assertTrue(stdoutText.contains("sessionId=" + FIXED_SESSION_ID)),
                () -> assertTrue(stdoutText.contains("inputDirectory=" + normalized(fixture.imageSequence()))),
                () -> assertTrue(stdoutText.contains("outputDirectory=" + normalized(output))),
                () -> assertTrue(stdoutText.contains("frames=" + fixture.frameCount())),
                () -> assertTrue(stdoutText.contains("decodedTiles=" + fixture.decodedTileCount())),
                () -> assertTrue(stdoutText.contains("restoredFiles=2")),
                () -> assertTrue(stdoutText.contains("restoredDirectories=2")),
                () -> assertTrue(stdoutText.contains("totalRestoredBytes=" + fixture.restoredBytes())),
                () -> assertTrue(stdoutText.contains("warnings=0")),
                () -> assertFalse(stdoutText.contains("FRAME ")),
                () -> assertFalse(stdoutText.contains("TILE ")),
                () -> assertEquals("", stderr.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(Files.isDirectory(output.resolve("payload/docs"))),
                () -> assertTrue(Files.isDirectory(output.resolve("payload/empty-dir"))),
                () -> assertArrayEquals(
                        Files.readAllBytes(fixture.sourceRoot().resolve("docs/alpha.txt")),
                        Files.readAllBytes(output.resolve("payload/docs/alpha.txt"))
                ),
                () -> assertEquals(0, Files.size(output.resolve("payload/empty.bin")))
        );
    }

    @Test
    @DisplayName("Actual writer export restores through the reader CLI")
    void actualWriterExportRestoresThroughReaderCli() {
        assertActualWriterExportRestoresThroughReaderCli(null, "actual-writer-default");
        assertActualWriterExportRestoresThroughReaderCli("debug-low-density", "actual-writer-debug");
    }

    private void assertActualWriterExportRestoresThroughReaderCli(String profile, String testName) {
        Path sourceRoot = sourceTree(testName + "-source");
        WriterApplicationService writer = new WriterApplicationService(
                () -> FIXED_CREATED_AT,
                () -> FIXED_SESSION_ID,
                tempDir.resolve(testName + "-diagnostics"),
                tempDir.resolve(testName + "-exports")
        );
        var writerResult = writer.run(new WriterRunRequest(
                new RuntimeConfigPatch(
                        profile == null ? null : new RuntimeConfigPatch.AppPatch(profile, null, null),
                        new RuntimeConfigPatch.InputPatch(List.of(
                                new RuntimeConfig.InputRootConfig(sourceRoot.toString(), null)
                        )),
                        null,
                        null,
                        new RuntimeConfigPatch.TransportPatch(
                                null,
                                RESTORE_FIXTURE_CHUNK_BYTES,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        null,
                        new RuntimeConfigPatch.ExportPatch(Boolean.TRUE, "imageSequence"),
                        null
                ),
                true
        ), WriterJobObserver.noOp());
        Path output = tempDir.resolve(testName + "-restore");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", writerResult.exportArtifacts().exportDirectory().toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(0, exitCode),
                () -> assertTrue(stdoutText.contains("RESTORED ")),
                () -> assertTrue(stdoutText.contains("sessionId=" + FIXED_SESSION_ID)),
                () -> assertEquals("", stderr.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(Files.isDirectory(output.resolve("root-001/docs"))),
                () -> assertTrue(Files.isDirectory(output.resolve("root-001/empty-dir"))),
                () -> assertArrayEquals(
                        Files.readAllBytes(sourceRoot.resolve("docs/alpha.txt")),
                        Files.readAllBytes(output.resolve("root-001/docs/alpha.txt"))
                ),
                () -> assertEquals(0, Files.size(output.resolve("root-001/empty.bin")))
        );
    }

    @Test
    @DisplayName("Accepted duplicate frames render warnings on the success stream")
    void acceptedDuplicateFramesRenderWarningsOnSuccessStream() {
        RestoreFixture fixture = writeRestorableImageSequence("duplicate-export");
        copyFirstPngAsDuplicate(fixture.imageSequence());
        Path output = tempDir.resolve("restore-duplicate");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", fixture.imageSequence().toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(0, exitCode),
                () -> assertTrue(stdoutText.contains("RESTORED ")),
                () -> assertTrue(stdoutText.contains("warnings=1")),
                () -> assertTrue(stdoutText.contains("WARNING code=DUPLICATE_FRAME_IGNORED")),
                () -> assertTrue(stdoutText.contains("message=Ignored byte-identical duplicate frame")),
                () -> assertEquals("", stderr.toString(StandardCharsets.UTF_8))
        );
    }

    @Test
    @DisplayName("Rejected reader input returns a decode failure without creating restore output")
    void rejectedReaderInputReturnsDecodeFailureWithoutCreatingRestoreOutput() {
        Path frameSequence = tempDir.resolve("metadata-only").resolve("frameSequence");
        Path output = tempDir.resolve("restore-rejected");
        createDirectories(frameSequence);
        writeMetadata(frameSequence, pixelSha256(0xFF112233));
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", frameSequence.toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderrText.contains("DECODE_FAILED status=INPUT_REJECTED")),
                () -> assertTrue(stderrText.contains("inputDirectory=" + normalized(frameSequence))),
                () -> assertTrue(stderrText.contains("message=PNG frames are required")),
                () -> assertFalse(Files.exists(output))
        );
    }

    @Test
    @DisplayName("Content decode failures print a decode failure and do not restore")
    void contentDecodeFailuresPrintDecodeFailureAndDoNotRestore() {
        Path imageSequence = writeUnsupportedLayoutImageSequence("unsupported-layout", 0xFF112233);
        Path output = tempDir.resolve("restore-unsupported");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", imageSequence.toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderrText.contains("DECODE_FAILED status=UNSUPPORTED_LAYOUT")),
                () -> assertTrue(stderrText.contains("message=Unsupported rendered frame dimensions")),
                () -> assertFalse(Files.exists(output))
        );
    }

    @Test
    @DisplayName("Output conflicts print a restore failure and preserve existing files")
    void outputConflictsPrintRestoreFailureAndPreserveExistingFiles() {
        RestoreFixture fixture = writeRestorableImageSequence("conflict-export");
        copyFirstPngAsDuplicate(fixture.imageSequence());
        Path output = tempDir.resolve("restore-conflict");
        createDirectories(output.resolve("payload"));
        writeString(output.resolve("payload/existing.txt"), "keep");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", fixture.imageSequence().toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderrText.contains("RESTORE_FAILED status=OUTPUT_CONFLICT")),
                () -> assertTrue(stderrText.contains("sessionId=" + FIXED_SESSION_ID)),
                () -> assertTrue(stderrText.contains("inputDirectory=" + normalized(fixture.imageSequence()))),
                () -> assertTrue(stderrText.contains("outputDirectory=" + normalized(output))),
                () -> assertTrue(stderrText.contains("message=Restore output path already exists")),
                () -> assertTrue(stderrText.contains("WARNING code=DUPLICATE_FRAME_IGNORED")),
                () -> assertFalse(stderrText.contains("RESTORED ")),
                () -> assertEquals("keep", Files.readString(output.resolve("payload/existing.txt")))
        );
    }

    @Test
    @DisplayName("Usage errors return a usage exit code")
    void usageErrorsReturnUsageExitCode() {
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", "/tmp/export"},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(2, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderrText.contains("USAGE_ERROR message=One --output path is required")),
                () -> assertTrue(stderrText.contains(
                        "Usage: jab4j-reader-cli --input <imageSequence-or-session-directory> --output <restore-directory>"
                )),
                () -> assertTrue(stderrText.contains("Input: current writer imageSequence PNG export directory")),
                () -> assertTrue(stderrText.contains("frame-sequence.txt is an MVP writer-export validation helper")),
                () -> assertTrue(stderrText.contains("Unsupported in this MVP: iPhone, camera, video, upload, or SaaS capture."))
        );
    }

    private RestoreFixture writeRestorableImageSequence(String parentName) {
        Path sourceRoot = sourceTree(parentName + "-source");
        TransportSessionPlan plan = writerPlan(sourceRoot, "payload", RESTORE_FIXTURE_CHUNK_BYTES);
        List<RenderedFrame> frames = plan.frameDescriptors().stream()
                .map(this::renderFrame)
                .toList();
        Path imageSequence = new PreparedFrameExporter().export(
                ExportMode.IMAGE_SEQUENCE,
                FIXED_SESSION_ID,
                plan.finalSessionDigest(),
                frames,
                tempDir.resolve(parentName)
        ).exportDirectory();
        int decodedTileCount = plan.frameDescriptors().stream()
                .mapToInt(frame -> frame.tiles().size())
                .sum();
        return new RestoreFixture(
                imageSequence,
                sourceRoot,
                plan.frameDescriptors().size(),
                decodedTileCount,
                size(sourceRoot.resolve("docs/alpha.txt"))
        );
    }

    private TransportSessionPlan writerPlan(Path sourceRoot, String alias, int chunkBytes) {
        PackagingResult packagingResult = packager.buildPackagingResult(List.of(new DeclaredInputRoot(sourceRoot, alias)));
        return planner.plan(draftSession(packagingResult.manifest(), chunkBytes), packagingResult);
    }

    private TransferSession draftSession(Manifest manifest, int chunkBytes) {
        return new TransferSession(
                FIXED_SESSION_ID,
                FIXED_CREATED_AT,
                FIXED_PROTOCOL_VERSION,
                "build-reader-cli-test",
                new SessionProfile(
                        "desktop-safe",
                        DEFAULT_PROFILE,
                        new CodecProfile("balanced-v1", "binary", true),
                        new TransportProfile(
                                "safe-v1",
                                FIXED_PROTOCOL_VERSION,
                                chunkBytes,
                                2,
                                1,
                                ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                                2,
                                3,
                                2
                        ),
                        new PlaybackProfile("desktop-safe", 8, 1, 2, 2, true)
                ),
                manifest,
                manifest.files()
        );
    }

    private RenderedFrame renderFrame(FrameDescriptor frameDescriptor) {
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(frameDescriptor.layout());
        List<RenderedTile> tiles = frameDescriptor.tiles().stream()
                .map(payload -> renderTile(payload, layoutPlan))
                .toList();
        return frameRasterRenderer.render(frameDescriptor, tiles);
    }

    private RenderedTile renderTile(TilePayload payload, FixedLayoutPlan layoutPlan) {
        byte[] envelope = envelopeCodec.serialize(payload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
        return tileRasterRenderer.render(logicalTile, layoutPlan);
    }

    private Path sourceTree(String directoryName) {
        Path root = tempDir.resolve(directoryName);
        createDirectories(root.resolve("docs"));
        createDirectories(root.resolve("empty-dir"));
        writeString(root.resolve("docs/alpha.txt"), "alpha beta gamma");
        writeBytes(root.resolve("empty.bin"), new byte[0]);
        return root;
    }

    private Path writeUnsupportedLayoutImageSequence(String parentName, int color) {
        Path imageSequence = tempDir.resolve(parentName).resolve("imageSequence");
        createDirectories(imageSequence);
        writeMetadata(imageSequence, pixelSha256(color));
        writePng(imageSequence.resolve("frame-0000-sync.png"), color);
        return imageSequence;
    }

    private String pixelSha256(int color) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * Integer.BYTES);
        for (int index = 0; index < 4; index++) {
            buffer.putInt(color);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(buffer.array()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void writeMetadata(Path directory, String pixelSha256) {
        writeString(directory.resolve("frame-sequence.txt"), """
                sessionId=%s
                finalSessionDigest=%s
                frameCount=1
                frame=0\t%s\t%s
                """.formatted(FIXED_SESSION_ID, FIXED_DIGEST, FrameType.SYNC, pixelSha256));
    }

    private void copyFirstPngAsDuplicate(Path imageSequence) {
        try (Stream<Path> files = Files.list(imageSequence)) {
            Path original = files
                    .filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected at least one exported PNG frame"));
            String fileName = original.getFileName().toString();
            String duplicateName = fileName.substring(0, fileName.length() - ".png".length()) + "-copy.png";
            Files.copy(original, imageSequence.resolve(duplicateName));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create duplicate test frame", exception);
        }
    }

    private Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect test file " + path, exception);
        }
    }

    private void writePng(Path path, int color) {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                image.setRGB(col, row, color);
            }
        }
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test PNG " + path, exception);
        }
    }

    private void writeString(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test file " + path, exception);
        }
    }

    private void writeBytes(Path path, byte[] content) {
        try {
            Files.write(path, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test file " + path, exception);
        }
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test directory " + path, exception);
        }
    }

    private record RestoreFixture(
            Path imageSequence,
            Path sourceRoot,
            int frameCount,
            int decodedTileCount,
            long restoredBytes
    ) {
    }
}

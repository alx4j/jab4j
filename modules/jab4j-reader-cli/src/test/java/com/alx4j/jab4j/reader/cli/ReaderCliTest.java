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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
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
import com.alx4j.jab4j.reader.app.ReaderApplicationService;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.CaptureReceiverRequest;
import com.alx4j.jab4j.reader.capture.CaptureReceiverResult;
import com.alx4j.jab4j.reader.capture.CaptureReceiverSummary;
import com.alx4j.jab4j.writer.app.WriterApplicationService;
import com.alx4j.jab4j.writer.app.WriterJobObserver;
import com.alx4j.jab4j.writer.app.WriterRunRequest;
import com.alx4j.jab4j.writer.app.WriterRunResult;
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
    private static final String REFERENCE_BASELINE_ID = "reference-mixed-payload-001";
    private static final String REFERENCE_BASELINE_LABEL = "exact-png-reference";
    private static final String REFERENCE_BASELINE_PROFILE = "debug-low-density";
    private static final String REPRESENTATIVE_PAYLOAD_ALIAS = "payload";
    private static final long REPRESENTATIVE_PAYLOAD_MAX_BYTES = 1024L;
    private static final int EXACT_PNG_BASELINE_CHUNK_BYTES = 128;
    private static final int EXACT_PNG_BASELINE_MAX_FRAME_COUNT = 32;
    private static final long EXACT_PNG_BASELINE_MAX_FRAME_BYTES = 10L * 1024L * 1024L;
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
    @DisplayName("Exact PNG baseline restores the representative mixed payload")
    void exactPngBaselineRestoresRepresentativeMixedPayload() {
        Path sourceRoot = representativePayload("representative-payload");
        long sourcePayloadBytes = totalRegularFileBytes(sourceRoot);
        WriterRunResult writerResult = referenceBaselineWriterExport(sourceRoot);
        Path output = tempDir.resolve("exact-png-restore");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", writerResult.exportArtifacts().exportDirectory().toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode, () -> stderr.toString(StandardCharsets.UTF_8));
        BaselineMetadata metadata = baselineMetadata(sourceRoot, writerResult, output);

        assertAll(
                () -> assertTrue(sourcePayloadBytes <= REPRESENTATIVE_PAYLOAD_MAX_BYTES),
                () -> assertTrue(stdoutText.contains("RESTORED ")),
                () -> assertTrue(stdoutText.contains("sessionId=" + FIXED_SESSION_ID)),
                () -> assertTrue(stdoutText.contains("frames=" + metadata.frameCount())),
                () -> assertTrue(stdoutText.contains("restoredFiles=3")),
                () -> assertTrue(stdoutText.contains("restoredDirectories=1")),
                () -> assertTrue(stdoutText.contains("totalRestoredBytes=" + sourcePayloadBytes)),
                () -> assertEquals("", stderr.toString(StandardCharsets.UTF_8)),
                () -> assertEquals(REFERENCE_BASELINE_ID, metadata.baselineId()),
                () -> assertEquals(REFERENCE_BASELINE_LABEL, metadata.referenceLabel()),
                () -> assertEquals(REFERENCE_BASELINE_PROFILE, metadata.selectedProfile()),
                () -> assertEquals(writerResult.reproducibilityMetadata().writerBuildId(), metadata.writerBuildId()),
                () -> assertEquals(writerResult.finalSessionDigest(), metadata.finalSessionDigest()),
                () -> assertEquals(
                        expectedPayloadManifest(sourceRoot, REPRESENTATIVE_PAYLOAD_ALIAS),
                        metadata.sourcePayloadManifest()
                ),
                () -> assertEquals(metadata.sourcePayloadManifest(), metadata.expectedRestoreManifest()),
                () -> assertTrue(metadata.sourcePayloadManifest().contains(
                        new BaselineManifestEntry(
                                "payload/notes.txt",
                                "FILE",
                                size(sourceRoot.resolve("notes.txt")),
                                sha256(sourceRoot.resolve("notes.txt"))
                        )
                )),
                () -> assertTrue(metadata.sourcePayloadManifest().contains(
                        new BaselineManifestEntry("payload/nested", "DIRECTORY", 0L, "-")
                )),
                () -> assertTrue(metadata.sourcePayloadManifest().contains(
                        new BaselineManifestEntry(
                                "payload/nested/sample-256.bin",
                                "FILE",
                                256L,
                                sha256(sourceRoot.resolve("nested/sample-256.bin"))
                        )
                )),
                () -> assertTrue(metadata.sourcePayloadManifest().contains(
                        new BaselineManifestEntry(
                                "payload/empty.dat",
                                "FILE",
                                0L,
                                sha256(sourceRoot.resolve("empty.dat"))
                        )
                )),
                () -> assertEquals(writerResult.renderedFrameHashes().size(), metadata.frameCount()),
                () -> assertTrue(metadata.frameCount() <= EXACT_PNG_BASELINE_MAX_FRAME_COUNT),
                () -> assertTrue(metadata.totalExactFrameBytes() <= EXACT_PNG_BASELINE_MAX_FRAME_BYTES),
                () -> assertTrue(metadata.frameSequenceSha256().matches("[0-9a-f]{64}")),
                () -> assertTrue(metadata.exactFrameManifest().stream()
                        .allMatch(frame -> frame.width() == 1280 && frame.height() == 720)),
                () -> assertEquals(
                        writerResult.renderedFrameHashes(),
                        frameSequencePixelHashes(writerResult.exportArtifacts().exportDirectory())
                )
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
    @DisplayName("Capture input invokes the capture receiver and prints stable diagnostics")
    void captureInputInvokesCaptureReceiverAndPrintsStableDiagnostics() {
        Path captureInput = tempDir.resolve("capture-frames");
        Path output = tempDir.resolve("capture-restore");
        List<CaptureReceiverRequest> requests = new ArrayList<>();
        ReaderCli cli = new ReaderCli(
                new ReaderApplicationService(),
                request -> {
                    requests.add(request);
                    return CaptureReceiverResult.incomplete(
                            new CaptureReceiverSummary(3, 2, 1, 1, 0, 0, 4, 0),
                            List.of(CaptureFrameDiagnostic.forSource(
                                    CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                                    "frame-0002.png",
                                    2,
                                    "Required capture frame content is missing"
                            )),
                            "Capture input is missing required content"
                    );
                },
                new ReaderCliParser()
        );
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--capture-input", captureInput.toString(), "--output", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertEquals(1, requests.size()),
                () -> assertTrue(requests.get(0).restoreRequested()),
                () -> assertEquals(List.of(normalized(captureInput)), requests.get(0).inputSources()),
                () -> assertEquals(Optional.of(normalized(output)), requests.get(0).outputDirectory()),
                () -> assertTrue(stderrText.contains("CAPTURE_INCOMPLETE ")),
                () -> assertTrue(stderrText.contains("captureInput=" + normalized(captureInput))),
                () -> assertTrue(stderrText.contains("outputDirectory=" + normalized(output))),
                () -> assertTrue(stderrText.contains("submittedFrames=3")),
                () -> assertTrue(stderrText.contains("readableFrames=2")),
                () -> assertTrue(stderrText.contains("acceptedCandidates=1")),
                () -> assertTrue(stderrText.contains("rejectedFrames=1")),
                () -> assertTrue(stderrText.contains("decodedTiles=4")),
                () -> assertTrue(stderrText.contains("restoredFiles=0")),
                () -> assertTrue(stderrText.contains("message=Capture input is missing required content")),
                () -> assertTrue(stderrText.contains(
                        "CAPTURE_DIAGNOSTIC code=MISSING_REQUIRED_CONTENT sourceId=frame-0002.png callerOrder=2"
                ))
        );
    }

    @Test
    @DisplayName("Capture media direct video returns stable unsupported diagnostics when no media service is present")
    void captureMediaDirectVideoReturnsStableUnsupportedDiagnosticsWithoutService() {
        Path captureMediaInput = tempDir.resolve("phone-capture.mov");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--capture-media-input", captureMediaInput.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderrText.contains("CAPTURE_MEDIA_UNSUPPORTED ")),
                () -> assertTrue(stderrText.contains("captureMediaInput=" + normalized(captureMediaInput))),
                () -> assertTrue(stderrText.contains("restoreRequested=false")),
                () -> assertTrue(stderrText.contains("outputDirectory=-")),
                () -> assertTrue(stderrText.contains("submittedMedia=1")),
                () -> assertTrue(stderrText.contains("readableMedia=0")),
                () -> assertTrue(stderrText.contains("rejectedCandidates=1")),
                () -> assertTrue(stderrText.contains("restoredFiles=0")),
                () -> assertTrue(stderrText.contains("message=Direct .mov/.mp4 capture media input is unsupported")),
                () -> assertTrue(stderrText.contains(
                        "CAPTURE_MEDIA_DIAGNOSTIC code=UNSUPPORTED_CONTAINER severity=ERROR blocking=true sourceKind=VIDEO sourceId=phone-capture.mov"
                )),
                () -> assertTrue(stderrText.contains("no adapter is configured"))
        );
    }

    @Test
    @DisplayName("Capture media direct video preserves restore-requested output in unsupported diagnostics")
    void captureMediaDirectVideoPreservesRestoreRequestedOutputInUnsupportedDiagnostics() {
        Path captureMediaInput = tempDir.resolve("phone-capture.mp4");
        Path output = tempDir.resolve("media-restore");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {
                        "--capture-media-input", captureMediaInput.toString(),
                        "--output", output.toString()
                },
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderrText.contains("CAPTURE_MEDIA_UNSUPPORTED ")),
                () -> assertTrue(stderrText.contains("captureMediaInput=" + normalized(captureMediaInput))),
                () -> assertTrue(stderrText.contains("restoreRequested=true")),
                () -> assertTrue(stderrText.contains("outputDirectory=" + normalized(output))),
                () -> assertTrue(stderrText.contains(
                        "CAPTURE_MEDIA_DIAGNOSTIC code=UNSUPPORTED_CONTAINER severity=ERROR blocking=true sourceKind=VIDEO sourceId=phone-capture.mp4"
                ))
        );
    }

    @Test
    @DisplayName("Capture media HEIC returns stable unsupported image diagnostics")
    void captureMediaHeicReturnsStableUnsupportedImageDiagnostics() {
        Path captureMediaInput = tempDir.resolve("phone-photo.heic");
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--capture-media-input", captureMediaInput.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderrText.contains("CAPTURE_MEDIA_UNSUPPORTED ")),
                () -> assertTrue(stderrText.contains("captureMediaInput=" + normalized(captureMediaInput))),
                () -> assertTrue(stderrText.contains(
                        "CAPTURE_MEDIA_DIAGNOSTIC code=UNSUPPORTED_IMAGE_FORMAT severity=ERROR blocking=true sourceKind=STILL_IMAGE sourceId=phone-photo.heic"
                )),
                () -> assertTrue(stderrText.contains("HEIC/HEIF capture media input is unsupported"))
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
                () -> assertTrue(stderrText.contains(
                        "jab4j-reader-cli --capture-input <frames-directory> --output <restore-directory>"
                )),
                () -> assertTrue(stderrText.contains(
                        "jab4j-reader-cli --capture-media-input <media-path> [--output <restore-directory>]"
                )),
                () -> assertTrue(stderrText.contains("Input: current writer imageSequence PNG export directory")),
                () -> assertTrue(stderrText.contains("Capture input: extracted PNG frame directory")),
                () -> assertTrue(stderrText.contains("Capture media input: MVP-3 still image")),
                () -> assertTrue(stderrText.contains("frame-sequence.txt is a writer-export validation helper")),
                () -> assertTrue(stderrText.contains(
                        "Unsupported in the first media slice: full real photo recovery, HEIC, direct .mov/.mp4 decoding"
                ))
        );
    }

    private WriterRunResult referenceBaselineWriterExport(Path sourceRoot) {
        WriterApplicationService writer = new WriterApplicationService(
                () -> FIXED_CREATED_AT,
                () -> FIXED_SESSION_ID,
                tempDir.resolve("reference-baseline-diagnostics"),
                tempDir.resolve("reference-baseline-exports")
        );
        return writer.run(new WriterRunRequest(referenceBaselineOverrides(sourceRoot), true), WriterJobObserver.noOp());
    }

    private RuntimeConfigPatch referenceBaselineOverrides(Path sourceRoot) {
        return new RuntimeConfigPatch(
                new RuntimeConfigPatch.AppPatch(
                        REFERENCE_BASELINE_PROFILE,
                        null,
                        new RuntimeConfigPatch.ResourceLimitsPatch(
                                8,
                                REPRESENTATIVE_PAYLOAD_MAX_BYTES,
                                4096,
                                EXACT_PNG_BASELINE_MAX_FRAME_COUNT,
                                EXACT_PNG_BASELINE_MAX_FRAME_COUNT
                        )
                ),
                new RuntimeConfigPatch.InputPatch(List.of(
                        new RuntimeConfig.InputRootConfig(sourceRoot.toString(), REPRESENTATIVE_PAYLOAD_ALIAS)
                )),
                null,
                null,
                new RuntimeConfigPatch.TransportPatch(null, EXACT_PNG_BASELINE_CHUNK_BYTES, null, null, null, null, null),
                new RuntimeConfigPatch.PlaybackPatch(10_000, 0, 0, 0, false),
                new RuntimeConfigPatch.ExportPatch(Boolean.TRUE, "imageSequence"),
                new RuntimeConfigPatch.DiagnosticsPatch(Boolean.FALSE, Boolean.TRUE, Boolean.TRUE)
        );
    }

    private Path representativePayload(String directoryName) {
        Path root = tempDir.resolve(directoryName);
        createDirectories(root.resolve("nested"));
        writeString(root.resolve("notes.txt"), """
                Exact writer-export PNG restore reference baseline.
                Camera video remains experimental and is not decoded by this automated fixture.
                """);
        byte[] bytes = new byte[256];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) index;
        }
        writeBytes(root.resolve("nested/sample-256.bin"), bytes);
        writeBytes(root.resolve("empty.dat"), new byte[0]);
        return root;
    }

    private BaselineMetadata baselineMetadata(Path sourceRoot, WriterRunResult writerResult, Path restoreOutput) {
        Path imageSequence = writerResult.exportArtifacts().exportDirectory();
        List<BaselineFrameEntry> exactFrameManifest = exactFrameManifest(imageSequence);
        return new BaselineMetadata(
                REFERENCE_BASELINE_ID,
                REFERENCE_BASELINE_LABEL,
                writerResult.reproducibilityMetadata().selectedProfile(),
                writerResult.reproducibilityMetadata().writerBuildId(),
                writerResult.finalSessionDigest(),
                expectedPayloadManifest(sourceRoot, REPRESENTATIVE_PAYLOAD_ALIAS),
                sha256(imageSequence.resolve("frame-sequence.txt")),
                exactFrameManifest,
                restoreManifest(restoreOutput),
                exactFrameManifest.stream().mapToLong(BaselineFrameEntry::size).sum()
        );
    }

    private List<BaselineManifestEntry> expectedPayloadManifest(Path sourceRoot, String alias) {
        List<BaselineManifestEntry> entries = new ArrayList<>();
        entries.add(new BaselineManifestEntry(alias, "DIRECTORY", 0L, "-"));
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> !path.equals(sourceRoot))
                    .sorted(Comparator.comparing(path -> sourceRoot.relativize(path).toString()))
                    .map(path -> payloadManifestEntry(sourceRoot, alias, path))
                    .forEach(entries::add);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect source payload " + sourceRoot, exception);
        }
        return List.copyOf(entries);
    }

    private List<BaselineManifestEntry> restoreManifest(Path restoreOutput) {
        try (Stream<Path> paths = Files.walk(restoreOutput)) {
            return paths.filter(path -> !path.equals(restoreOutput))
                    .sorted(Comparator.comparing(path -> restoreOutput.relativize(path).toString()))
                    .map(path -> manifestEntry(restoreOutput.relativize(path).toString(), path))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect restored payload " + restoreOutput, exception);
        }
    }

    private BaselineManifestEntry payloadManifestEntry(Path sourceRoot, String alias, Path path) {
        String relativePath = sourceRoot.relativize(path).toString();
        return manifestEntry(alias + "/" + relativePath, path);
    }

    private BaselineManifestEntry manifestEntry(String relativePath, Path path) {
        String normalizedRelativePath = relativePath.replace('\\', '/');
        if (Files.isDirectory(path)) {
            return new BaselineManifestEntry(normalizedRelativePath, "DIRECTORY", 0L, "-");
        }
        return new BaselineManifestEntry(normalizedRelativePath, "FILE", size(path), sha256(path));
    }

    private List<BaselineFrameEntry> exactFrameManifest(Path imageSequence) {
        try (Stream<Path> paths = Files.list(imageSequence)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::baselineFrameEntry)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect exact frame sequence " + imageSequence, exception);
        }
    }

    private List<String> frameSequencePixelHashes(Path imageSequence) {
        try (Stream<String> lines = Files.lines(imageSequence.resolve("frame-sequence.txt"), StandardCharsets.UTF_8)) {
            return lines.filter(line -> line.startsWith("frame="))
                    .map(line -> line.split("\t"))
                    .map(parts -> parts[2])
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read frame sequence metadata " + imageSequence, exception);
        }
    }

    private BaselineFrameEntry baselineFrameEntry(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new IllegalStateException("Failed to read exact frame dimensions " + path);
            }
            return new BaselineFrameEntry(
                    path.getFileName().toString(),
                    image.getWidth(),
                    image.getHeight(),
                    size(path),
                    sha256(path)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read exact frame " + path, exception);
        }
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

    private long totalRegularFileBytes(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(this::size)
                    .sum();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect test payload " + root, exception);
        }
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect test file " + path, exception);
        }
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to hash test file " + path, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
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

    private record BaselineMetadata(
            String baselineId,
            String referenceLabel,
            String selectedProfile,
            String writerBuildId,
            String finalSessionDigest,
            List<BaselineManifestEntry> sourcePayloadManifest,
            String frameSequenceSha256,
            List<BaselineFrameEntry> exactFrameManifest,
            List<BaselineManifestEntry> expectedRestoreManifest,
            long totalExactFrameBytes
    ) {

        private BaselineMetadata {
            sourcePayloadManifest = List.copyOf(sourcePayloadManifest);
            exactFrameManifest = List.copyOf(exactFrameManifest);
            expectedRestoreManifest = List.copyOf(expectedRestoreManifest);
        }

        private int frameCount() {
            return exactFrameManifest.size();
        }
    }

    private record BaselineManifestEntry(String path, String type, long size, String sha256) {
    }

    private record BaselineFrameEntry(String fileName, int width, int height, long size, String sha256) {
    }
}

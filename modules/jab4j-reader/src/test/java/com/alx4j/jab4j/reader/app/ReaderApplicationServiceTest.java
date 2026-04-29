package com.alx4j.jab4j.reader.app;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32C;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.output.ExportMode;
import com.alx4j.jab4j.output.PreparedFrameExporter;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarningCode;
import com.alx4j.jab4j.render.frame.FrameRasterRenderer;
import com.alx4j.jab4j.render.frame.FrameRenderOptions;
import com.alx4j.jab4j.render.frame.RenderedFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

@DisplayName("Reader application service")
class ReaderApplicationServiceTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final String FIXED_DIGEST = "digest-123";
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

    private final ReaderApplicationService service = new ReaderApplicationService();
    private final FixedLayoutPlanner layoutPlanner = new FixedLayoutPlanner();
    private final TilePayloadEnvelopeCodec envelopeCodec = new TilePayloadEnvelopeCodec();
    private final TileRasterRenderer tileRasterRenderer = new TileRasterRenderer();
    private final FrameRasterRenderer frameRasterRenderer = new FrameRasterRenderer();

    @Test
    @DisplayName("Current writer imageSequence exports are decoded from the exact directory")
    void currentWriterImageSequenceExportsAreDecodedFromExactDirectory() {
        RenderedFrame syncFrame = renderedPayloadFrame(DEFAULT_PROFILE, 0L, FrameType.SYNC, 0, bytes("sync"));
        RenderedFrame dataFrame = renderedPayloadFrame(DEFAULT_PROFILE, 1L, FrameType.DATA, 2, bytes("data"));
        Path imageSequence = exportRenderedFrames("writer-export", List.of(syncFrame, dataFrame));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        ReaderFrameSet frameSet = attempt.frameSet().orElseThrow();
        DecodedFrameSetContent decodedContent = attempt.decodedContent().orElseThrow();
        assertAll(
                () -> assertEquals(ReaderDecodeStatus.CONTENT_DECODED, attempt.status()),
                () -> assertTrue(attempt.accepted()),
                () -> assertTrue(attempt.decoded()),
                () -> assertEquals(imageSequence.toAbsolutePath().normalize(), attempt.inputDirectory()),
                () -> assertEquals(FIXED_SESSION_ID, frameSet.sessionId()),
                () -> assertEquals(FIXED_DIGEST, frameSet.finalSessionDigest()),
                () -> assertEquals(List.of(0L, 1L), frameSet.frames().stream().map(frame -> frame.frameIndex()).toList()),
                () -> assertEquals(syncFrame.diagnostics().get("pixelSha256"), frameSet.frames().get(0).pixelSha256()),
                () -> assertEquals(dataFrame.diagnostics().get("pixelSha256"), frameSet.frames().get(1).pixelSha256()),
                () -> assertEquals(DEFAULT_PROFILE.profileId(), decodedContent.layoutProfileId()),
                () -> assertEquals(2, decodedContent.decodedTileCount()),
                () -> assertArrayEquals(bytes("data"), decodedContent.frames().get(1).tilePayloads().get(0).body()),
                () -> assertTrue(attempt.warnings().isEmpty())
        );
    }

    @Test
    @DisplayName("Supported rendered profile dimensions decode available payloads and ignore empty slots")
    void supportedRenderedProfileDimensionsDecodeAvailablePayloadsAndIgnoreEmptySlots() {
        for (LayoutProfile profile : supportedProfiles()) {
            int lastTileIndex = (profile.rows() * profile.cols()) - 1;
            boolean debugOverlay = "debug-low-density".equals(profile.profileId());
            RenderedFrame frame = renderedPayloadFrame(
                    profile,
                    0L,
                    FrameType.DATA,
                    lastTileIndex,
                    bytes(profile.profileId()),
                    1,
                    debugOverlay,
                    UnaryOperator.identity()
            );
            Path imageSequence = exportRenderedFrames("profile-" + profile.profileId(), List.of(frame));

            ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

            DecodedFrameSetContent decodedContent = attempt.decodedContent().orElseThrow();
            TilePayload payload = decodedContent.frames().get(0).tilePayloads().get(0);
            assertAll(
                    profile.profileId(),
                    () -> assertEquals(ReaderDecodeStatus.CONTENT_DECODED, attempt.status()),
                    () -> assertEquals(profile.profileId(), decodedContent.layoutProfileId()),
                    () -> assertEquals(1, decodedContent.decodedTileCount()),
                    () -> assertEquals(lastTileIndex, payload.tileIndex().value()),
                    () -> assertEquals(profile.rows() * profile.cols(), payload.totalTilesInFrame()),
                    () -> assertArrayEquals(bytes(profile.profileId()), payload.body())
            );
        }
    }

    @Test
    @DisplayName("Parent session directories auto-detect one imageSequence child")
    void parentSessionDirectoriesAutoDetectOneImageSequenceChild() {
        Path imageSequence = exportRenderedFrames(
                "parent-session",
                List.of(renderedPayloadFrame(DEFAULT_PROFILE, 0L, FrameType.SYNC, 0, bytes("parent")))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence.getParent());

        assertAll(
                () -> assertTrue(attempt.accepted()),
                () -> assertTrue(attempt.decoded()),
                () -> assertEquals(imageSequence.toAbsolutePath().normalize(), attempt.inputDirectory()),
                () -> assertEquals(1, attempt.frameSet().orElseThrow().frames().size())
        );
    }

    @Test
    @DisplayName("Broader roots are rejected instead of scanned recursively")
    void broaderRootsAreRejectedInsteadOfScannedRecursively() {
        Path imageSequence = writeImageSequence(
                "exports/" + FIXED_SESSION_ID,
                List.of(frame(0L, FrameType.SYNC, 0xFF111111)),
                List.of(file(frame(0L, FrameType.SYNC, 0xFF111111)))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence.getParent().getParent());

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertEquals(ReaderDecodeStatus.INPUT_REJECTED, attempt.status()),
                () -> assertTrue(attempt.message().contains("broader roots are not scanned"))
        );
    }

    @Test
    @DisplayName("Missing frame-sequence metadata is rejected clearly")
    void missingFrameSequenceMetadataIsRejectedClearly() {
        Path imageSequence = tempDir.resolve("missing-metadata").resolve("imageSequence");
        createDirectories(imageSequence);
        writePng(imageSequence.resolve("frame-0000-sync.png"), frame(0L, FrameType.SYNC, 0xFF111111));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("require frame-sequence.txt"))
        );
    }

    @Test
    @DisplayName("Missing PNG frames listed in metadata are rejected by frame identity")
    void missingPngFramesListedInMetadataAreRejectedByFrameIdentity() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame dataFrame = frame(1L, FrameType.DATA, 0xFF222222);
        Path imageSequence = writeImageSequence(
                "missing-listed-frame",
                List.of(syncFrame, dataFrame),
                List.of(file(syncFrame))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertEquals(ReaderDecodeStatus.INPUT_REJECTED, attempt.status()),
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("frame-0001-DATA")),
                () -> assertTrue(attempt.message().contains("has no matching PNG frame"))
        );
    }

    @Test
    @DisplayName("Metadata-only frameSequence exports are rejected because PNG frames are required")
    void metadataOnlyFrameSequenceExportsAreRejectedBecausePngFramesAreRequired() {
        Path frameSequence = tempDir.resolve("metadata-only").resolve("frameSequence");
        createDirectories(frameSequence);
        writeMetadata(frameSequence, List.of(frame(0L, FrameType.SYNC, 0xFF111111)));

        ReaderDecodeAttempt attempt = service.startDecode(frameSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("PNG frames are required"))
        );
    }

    @Test
    @DisplayName("Support files and filesystem order do not control accepted frame order")
    void supportFilesAndFilesystemOrderDoNotControlAcceptedFrameOrder() {
        RenderedFrame syncFrame = renderedPayloadFrame(DEFAULT_PROFILE, 0L, FrameType.SYNC, 0, bytes("sync"));
        RenderedFrame dataFrame = renderedPayloadFrame(DEFAULT_PROFILE, 1L, FrameType.DATA, 0, bytes("data"));
        Path imageSequence = exportRenderedFrames("support-files", List.of(syncFrame, dataFrame));
        writeString(imageSequence.resolve("notes.txt"), "ignored");
        writeString(imageSequence.resolve("frame-sequence.json"), "{}");

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertTrue(attempt.accepted()),
                () -> assertTrue(attempt.decoded()),
                () -> assertEquals(
                        List.of(FrameType.SYNC, FrameType.DATA),
                        attempt.frameSet().orElseThrow().frames().stream().map(frame -> frame.frameType()).toList()
                )
        );
    }

    @Test
    @DisplayName("Unrecognized PNG frame files are rejected")
    void unrecognizedPngFrameFilesAreRejected() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        Path imageSequence = writeImageSequence("no-recognizable-frames", List.of(syncFrame), List.of());
        writePng(imageSequence.resolve("preview.png"), syncFrame);

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertEquals(ReaderDecodeStatus.INPUT_REJECTED, attempt.status()),
                () -> assertTrue(attempt.message().contains("Unrecognized PNG frame file preview.png"))
        );
    }

    @Test
    @DisplayName("Extra recognized PNG frames missing from metadata are rejected by frame identity")
    void extraRecognizedPngFramesMissingFromMetadataAreRejectedByFrameIdentity() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame dataFrame = frame(1L, FrameType.DATA, 0xFF222222);
        Path imageSequence = writeImageSequence(
                "extra-recognized-frame",
                List.of(syncFrame),
                List.of(file(syncFrame), file(dataFrame))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertEquals(ReaderDecodeStatus.INPUT_REJECTED, attempt.status()),
                () -> assertTrue(attempt.message().contains("frame-0001-DATA")),
                () -> assertTrue(attempt.message().contains("not present in frame-sequence.txt"))
        );
    }

    @Test
    @DisplayName("Metadata and PNG pixel hash disagreement is rejected")
    void metadataAndPngPixelHashDisagreementIsRejected() {
        TestFrame metadataFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame pngFrame = frame(0L, FrameType.SYNC, 0xFF999999);
        Path imageSequence = writeImageSequence(
                "hash-mismatch",
                List.of(metadataFrame),
                List.of(file(pngFrame, metadataFrame.fileName()))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("does not match frame-sequence.txt hash"))
        );
    }

    @Test
    @DisplayName("Unknown frame dimensions produce an unsupported-layout result")
    void unknownFrameDimensionsProduceUnsupportedLayoutResult() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        Path imageSequence = writeImageSequence("unsupported-layout", List.of(syncFrame), List.of(file(syncFrame)));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertTrue(attempt.accepted()),
                () -> assertFalse(attempt.decoded()),
                () -> assertEquals(ReaderDecodeStatus.UNSUPPORTED_LAYOUT, attempt.status()),
                () -> assertTrue(attempt.message().contains("Unsupported rendered frame dimensions"))
        );
    }

    @Test
    @DisplayName("Ambiguous supported dimensions produce an unsupported-layout result")
    void ambiguousSupportedDimensionsProduceUnsupportedLayoutResult() {
        LayoutProfile duplicate = new LayoutProfile(
                "duplicate-desktop",
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
        SupportedRenderedLayoutCatalog catalog = new SupportedRenderedLayoutCatalog(List.of(DEFAULT_PROFILE, duplicate));

        ReaderContentDecodeException exception = assertThrows(
                ReaderContentDecodeException.class,
                () -> catalog.resolve(1920, 1080)
        );

        assertAll(
                () -> assertEquals(ReaderDecodeStatus.UNSUPPORTED_LAYOUT, exception.status()),
                () -> assertTrue(exception.getMessage().contains("Ambiguous rendered frame dimensions"))
        );
    }

    @Test
    @DisplayName("Unsupported tile payload protocol versions produce an unsupported-version result")
    void unsupportedTilePayloadProtocolVersionsProduceUnsupportedVersionResult() {
        RenderedFrame frame = renderedPayloadFrame(
                DEFAULT_PROFILE,
                0L,
                FrameType.DATA,
                0,
                bytes("version"),
                2,
                false,
                UnaryOperator.identity()
        );
        Path imageSequence = exportRenderedFrames("unsupported-version", List.of(frame));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertEquals(ReaderDecodeStatus.UNSUPPORTED_VERSION, attempt.status()),
                () -> assertTrue(attempt.accepted()),
                () -> assertFalse(attempt.decoded()),
                () -> assertTrue(attempt.message().contains("Unsupported protocol compatibility version"))
        );
    }

    @Test
    @DisplayName("Envelope CRC failures produce a corrupted-content result")
    void envelopeCrcFailuresProduceCorruptedContentResult() {
        RenderedFrame frame = renderedPayloadFrame(
                DEFAULT_PROFILE,
                0L,
                FrameType.DATA,
                0,
                bytes("crc"),
                1,
                false,
                envelope -> {
                    byte[] corrupted = envelope.clone();
                    corrupted[corrupted.length - 1] ^= 0x01;
                    return corrupted;
                }
        );
        Path imageSequence = exportRenderedFrames("corrupted-envelope", List.of(frame));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertEquals(ReaderDecodeStatus.CONTENT_CORRUPTED, attempt.status()),
                () -> assertTrue(attempt.accepted()),
                () -> assertFalse(attempt.decoded()),
                () -> assertTrue(attempt.message().contains("Envelope payload CRC32C"))
        );
    }

    @Test
    @DisplayName("Decoded payload identity must match the accepted frame identity")
    void decodedPayloadIdentityMustMatchAcceptedFrameIdentity() {
        RenderedFrame renderedFrame = renderedPayloadFrame(DEFAULT_PROFILE, 0L, FrameType.DATA, 0, bytes("identity"));
        Path imageSequence = writeRenderedImageSequence(
                "identity-mismatch",
                FIXED_SESSION_ID,
                List.of(new RenderedFrameFile(1L, FrameType.DATA, renderedFrame))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertEquals(ReaderDecodeStatus.INCONSISTENT_CONTENT, attempt.status()),
                () -> assertTrue(attempt.accepted()),
                () -> assertFalse(attempt.decoded()),
                () -> assertTrue(attempt.message().contains("inconsistent frame index"))
        );
    }

    @Test
    @DisplayName("Byte-identical duplicate frame identities are ignored with a warning")
    void byteIdenticalDuplicateFrameIdentitiesAreIgnoredWithWarning() {
        RenderedFrame syncFrame = renderedPayloadFrame(DEFAULT_PROFILE, 0L, FrameType.SYNC, 0, bytes("sync"));
        RenderedFrame dataFrame = renderedPayloadFrame(DEFAULT_PROFILE, 1L, FrameType.DATA, 0, bytes("data"));
        Path imageSequence = exportRenderedFrames("duplicate-identical", List.of(syncFrame, dataFrame));
        copyFile(imageSequence.resolve("frame-0001-data.png"), imageSequence.resolve("frame-0001-data-copy.png"));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertTrue(attempt.accepted()),
                () -> assertTrue(attempt.decoded()),
                () -> assertEquals(2, attempt.frameSet().orElseThrow().frames().size()),
                () -> assertEquals(1, attempt.warnings().size()),
                () -> assertEquals(ReaderWarningCode.DUPLICATE_FRAME_IGNORED, attempt.warnings().get(0).code())
        );
    }

    @Test
    @DisplayName("Conflicting duplicate frame identities are rejected")
    void conflictingDuplicateFrameIdentitiesAreRejected() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame dataFrame = frame(1L, FrameType.DATA, 0xFF222222);
        TestFrame conflictingDataFrame = frame(1L, FrameType.DATA, 0xFF333333);
        Path imageSequence = writeImageSequence(
                "duplicate-conflicting",
                List.of(syncFrame, dataFrame),
                List.of(file(syncFrame), file(dataFrame), file(conflictingDataFrame, "frame-0001-data-copy.png"))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("conflicting PNG bytes"))
        );
    }

    private Path exportRenderedFrames(String parentName, List<RenderedFrame> frames) {
        PreparedFrameExporter exporter = new PreparedFrameExporter();
        return exporter.export(
                ExportMode.IMAGE_SEQUENCE,
                FIXED_SESSION_ID,
                FIXED_DIGEST,
                frames,
                tempDir.resolve(parentName)
        ).exportDirectory();
    }

    private Path writeRenderedImageSequence(String parentName, SessionId sessionId, List<RenderedFrameFile> frameFiles) {
        Path imageSequence = tempDir.resolve(parentName).resolve("imageSequence");
        createDirectories(imageSequence);
        StringBuilder builder = new StringBuilder();
        builder.append("sessionId=").append(sessionId).append('\n');
        builder.append("finalSessionDigest=").append(FIXED_DIGEST).append('\n');
        builder.append("frameCount=").append(frameFiles.size()).append('\n');
        for (RenderedFrameFile frameFile : frameFiles) {
            builder.append("frame=").append(frameFile.frameIndex())
                    .append('\t').append(frameFile.frameType())
                    .append('\t').append(frameFile.renderedFrame().diagnostics().get("pixelSha256"))
                    .append('\n');
            writePng(imageSequence.resolve(frameFile.fileName()), frameFile.renderedFrame());
        }
        writeString(imageSequence.resolve("frame-sequence.txt"), builder.toString());
        return imageSequence;
    }

    private RenderedFrame renderedPayloadFrame(
            LayoutProfile profile,
            long frameIndex,
            FrameType frameType,
            int tileIndex,
            byte[] body
    ) {
        return renderedPayloadFrame(
                profile,
                frameIndex,
                frameType,
                tileIndex,
                body,
                1,
                false,
                UnaryOperator.identity()
        );
    }

    private RenderedFrame renderedPayloadFrame(
            LayoutProfile profile,
            long frameIndex,
            FrameType frameType,
            int tileIndex,
            byte[] body,
            int protocolCompatibilityVersion,
            boolean debugOverlay,
            UnaryOperator<byte[]> envelopeMutation
    ) {
        TilePayload payload = tilePayload(
                profile,
                frameIndex,
                frameType,
                tileIndex,
                body,
                protocolCompatibilityVersion
        );
        byte[] envelope = envelopeMutation.apply(envelopeCodec.serialize(payload));
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
        RenderedTile renderedTile = tileRasterRenderer.render(logicalTile, layoutPlan);
        FrameDescriptor descriptor = new FrameDescriptor(frameIndex, frameType, profile, List.of(payload));
        return frameRasterRenderer.render(
                descriptor,
                List.of(renderedTile),
                debugOverlay ? FrameRenderOptions.debugOverlay() : FrameRenderOptions.transportSafeDefaults()
        );
    }

    private TilePayload tilePayload(
            LayoutProfile profile,
            long frameIndex,
            FrameType frameType,
            int tileIndex,
            byte[] body,
            int protocolCompatibilityVersion
    ) {
        return new TilePayload(
                protocolCompatibilityVersion,
                FIXED_SESSION_ID,
                frameType,
                frameIndex,
                new TileIndex(tileIndex),
                profile.rows() * profile.cols(),
                profile.profileId(),
                payloadKindFor(frameType),
                frameIndex * 10 + tileIndex,
                body.length,
                crc32c(body),
                0,
                body
        );
    }

    private int crc32c(byte[] bytes) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }

    private PayloadKind payloadKindFor(FrameType frameType) {
        return switch (frameType) {
            case SYNC -> PayloadKind.SYNC_METADATA;
            case SESSION_HEADER -> PayloadKind.SESSION_HEADER;
            case MANIFEST -> PayloadKind.MANIFEST_FRAGMENT;
            case DATA -> PayloadKind.FILE_CHUNK;
            case PARITY -> PayloadKind.PARITY_SHARD;
            case END -> PayloadKind.SESSION_END;
        };
    }

    private List<LayoutProfile> supportedProfiles() {
        return List.of(
                DEFAULT_PROFILE,
                new LayoutProfile(
                        "desktop-1440p-balanced",
                        2,
                        3,
                        2560,
                        1440,
                        56,
                        20,
                        "solidWhite",
                        72,
                        36,
                        "black",
                        "preserveAspect"
                ),
                new LayoutProfile(
                        "debug-low-density",
                        1,
                        2,
                        1280,
                        720,
                        40,
                        16,
                        "solidWhite",
                        48,
                        24,
                        "black",
                        "preserveAspect"
                )
        );
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private Path writeImageSequence(String parentName, List<TestFrame> metadataFrames, List<FrameFile> frameFiles) {
        Path imageSequence = tempDir.resolve(parentName).resolve("imageSequence");
        createDirectories(imageSequence);
        writeMetadata(imageSequence, metadataFrames);
        for (FrameFile frameFile : frameFiles) {
            writePng(imageSequence.resolve(frameFile.fileName()), frameFile.frame());
        }
        return imageSequence;
    }

    private void writeMetadata(Path directory, List<TestFrame> frames) {
        StringBuilder builder = new StringBuilder();
        builder.append("sessionId=").append(FIXED_SESSION_ID).append('\n');
        builder.append("finalSessionDigest=").append(FIXED_DIGEST).append('\n');
        builder.append("frameCount=").append(frames.size()).append('\n');
        for (TestFrame frame : frames) {
            builder.append("frame=").append(frame.frameIndex())
                    .append('\t').append(frame.frameType())
                    .append('\t').append(frame.pixelSha256())
                    .append('\n');
        }
        writeString(directory.resolve("frame-sequence.txt"), builder.toString());
    }

    private FrameFile file(TestFrame frame) {
        return file(frame, frame.fileName());
    }

    private FrameFile file(TestFrame frame, String fileName) {
        return new FrameFile(frame, fileName);
    }

    private TestFrame frame(long frameIndex, FrameType frameType, int color) {
        return new TestFrame(frameIndex, frameType, 2, 2, List.of(color, color, color, color));
    }

    private void writePng(Path path, TestFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < frame.heightPixels(); row++) {
            for (int col = 0; col < frame.widthPixels(); col++) {
                image.setRGB(col, row, frame.argbPixels().get((row * frame.widthPixels()) + col));
            }
        }
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test PNG " + path, exception);
        }
    }

    private void writePng(Path path, RenderedFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < frame.heightPixels(); row++) {
            for (int col = 0; col < frame.widthPixels(); col++) {
                image.setRGB(col, row, frame.argbPixels().get((row * frame.widthPixels()) + col));
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

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test directory " + path, exception);
        }
    }

    private void copyFile(Path source, Path target) {
        try {
            Files.copy(source, target);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy test file " + source + " to " + target, exception);
        }
    }

    private record RenderedFrameFile(long frameIndex, FrameType frameType, RenderedFrame renderedFrame) {

        private String fileName() {
            return String.format(
                    Locale.ROOT,
                    "frame-%04d-%s.png",
                    frameIndex,
                    frameType.name().toLowerCase(Locale.ROOT)
            );
        }
    }

    private record FrameFile(TestFrame frame, String fileName) {
    }

    private record TestFrame(
            long frameIndex,
            FrameType frameType,
            int widthPixels,
            int heightPixels,
            List<Integer> argbPixels
    ) {

        private String fileName() {
            return String.format(
                    Locale.ROOT,
                    "frame-%04d-%s.png",
                    frameIndex,
                    frameType.name().toLowerCase(Locale.ROOT)
            );
        }

        private String pixelSha256() {
            ByteBuffer buffer = ByteBuffer.allocate(argbPixels.size() * Integer.BYTES);
            for (int pixel : argbPixels) {
                buffer.putInt(pixel);
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(buffer.array()));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available", exception);
            }
        }

        private RenderedFrame toRenderedFrame() {
            return new RenderedFrame(
                    frameIndex,
                    frameType,
                    widthPixels,
                    heightPixels,
                    argbPixels,
                    Map.of("pixelSha256", pixelSha256())
            );
        }
    }
}
